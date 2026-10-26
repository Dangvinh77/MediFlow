package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.FollowUpRecommendationRequest;
import com.mediflow.clinical.application.dto.response.FollowUpRecommendationDTO;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.FollowUpRecommendationCreatedFact;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationOutboxPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.FollowUpRecommendation;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/** Creates and reads immutable follow-up advice against a completed V2 source episode. */
@Service
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class FollowUpRecommendationApplicationService {
    private static final String POST_PATH = "/api/v2/records/%s/follow-up-recommendations";
    private static final String GET_PATH = "/api/v2/records/%s/follow-up-recommendation";

    private final MedicalRecordRepositoryPort records;
    private final AppointmentRepositoryPort appointments;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final ConsultationSessionRepositoryPort sessions;
    private final FollowUpRecommendationRepositoryPort recommendations;
    private final FollowUpRecommendationOutboxPort outbox;
    private final CurrentClinicalActorPort actors;
    private final SchedulingAuthorityPort authority;
    private final IdempotentCommandPort idempotency;
    private final CorrelationIdProvider correlationIds;
    private final Clock clock;

    public FollowUpRecommendationApplicationService(
            MedicalRecordRepositoryPort records,
            AppointmentRepositoryPort appointments,
            OutpatientRegistrationRepositoryPort registrations,
            ConsultationSessionRepositoryPort sessions,
            FollowUpRecommendationRepositoryPort recommendations,
            FollowUpRecommendationOutboxPort outbox,
            CurrentClinicalActorPort actors,
            SchedulingAuthorityPort authority,
            IdempotentCommandPort idempotency,
            CorrelationIdProvider correlationIds,
            @org.springframework.beans.factory.annotation.Qualifier("clinicalSchedulingClock") Clock clock) {
        this.records = records;
        this.appointments = appointments;
        this.registrations = registrations;
        this.sessions = sessions;
        this.recommendations = recommendations;
        this.outbox = outbox;
        this.actors = actors;
        this.authority = authority;
        this.idempotency = idempotency;
        this.correlationIds = correlationIds;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<FollowUpRecommendationDTO> create(
            UUID sourceRecordId, FollowUpRecommendationRequest request, String idempotencyKey) {
        if (sourceRecordId == null || request == null) {
            throw invalidRequest("Record id and follow-up recommendation are required");
        }
        validateRequest(request);
        requireIdempotencyKey(idempotencyKey);
        ActorIdentity actor = requireActor("DOCTOR");
        String correlationId = correlationIds.currentOrCreate().toString();
        requireCurrentDoctor(actor, correlationId);

        String canonicalPath = POST_PATH.formatted(sourceRecordId);
        return idempotency.execute(actor.accountId().toString(), "POST", canonicalPath, idempotencyKey,
                fingerprint(request), FollowUpRecommendationDTO.class, 201,
                dto -> GET_PATH.formatted(dto.sourceRecordId()),
                () -> createOnce(sourceRecordId, request, actor, correlationId));
    }

    public FollowUpRecommendationDTO get(UUID sourceRecordId) {
        if (sourceRecordId == null) throw notFound();
        ActorIdentity actor = requireActor("ADMIN", "NURSE", "DOCTOR", "PATIENT");
        MedicalRecord record = records.findById(sourceRecordId).orElseThrow(FollowUpRecommendationApplicationService::notFound);
        authorizeRead(actor, record);
        FollowUpRecommendation recommendation = recommendations.findBySourceRecordId(sourceRecordId)
                .orElseThrow(FollowUpRecommendationApplicationService::notFound);
        return toDto(recommendation);
    }

    private FollowUpRecommendationDTO createOnce(UUID sourceRecordId, FollowUpRecommendationRequest request,
            ActorIdentity actor, String correlationId) {
        MedicalRecord record = records.findByIdForUpdate(sourceRecordId)
                .orElseThrow(FollowUpRecommendationApplicationService::notFound);
        if (record.getStatus() != MedicalRecordStatus.COMPLETED || record.getAppointmentId() == null
                || !actor.staffId().equals(record.getDoctorId())
                || request.recommendedFrom().isBefore(record.getExaminationDate())) {
            throw notFound();
        }

        UUID sourceRegistrationId = record.getAppointmentId();
        Appointment appointment = appointments.findByIdForUpdate(sourceRegistrationId)
                .orElseThrow(FollowUpRecommendationApplicationService::notFound);
        if (!sourceRegistrationId.equals(appointment.getAppointmentId())
                || appointment.getCareContractVersion() != 2
                || appointment.getStatus() != AppointmentStatus.COMPLETED
                || !record.getPatientId().equals(appointment.getPatientId())
                || !record.getDoctorId().equals(appointment.getDoctorId())
                || !record.getDepartmentId().equals(appointment.getDepartmentId())) {
            throw notFound();
        }

        OutpatientRegistration registration = registrations.findByIdForUpdate(sourceRegistrationId)
                .orElseThrow(FollowUpRecommendationApplicationService::notFound);
        if (!sourceRegistrationId.equals(registration.registrationId())
                || registration.status() != OutpatientRegistrationStatus.COMPLETED
                || !record.getPatientId().equals(registration.patientId())) {
            throw notFound();
        }
        var session = sessions.findById(registration.sessionId())
                .orElseThrow(FollowUpRecommendationApplicationService::notFound);
        if (!record.getDoctorId().equals(session.configuration().doctorId())
                || !record.getDepartmentId().equals(session.configuration().departmentId())) {
            throw notFound();
        }
        if (recommendations.findBySourceRecordId(record.getRecordId()).isPresent()) {
            throw new DuplicateResourceException("FOLLOW_UP_RECOMMENDATION_EXISTS",
                    "A follow-up recommendation already exists for this completed record");
        }

        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        FollowUpRecommendation recommendation;
        try {
            recommendation = new FollowUpRecommendation(UUID.randomUUID(), record.getRecordId(),
                    sourceRegistrationId, record.getPatientId(), record.getDepartmentId(), record.getDoctorId(),
                    request.recommendedFrom(), request.recommendedThrough(), request.reason(),
                    request.preparationInstructions(), createdAt, actor.accountId(), actor.staffId());
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleException("FOLLOW_UP_RECOMMENDATION_INVALID", invalid.getMessage());
        }

        UUID eventId = UUID.randomUUID();
        FollowUpRecommendationCreatedFact fact = new FollowUpRecommendationCreatedFact(
                recommendation.recommendationId(), recommendation.sourceRecordId(),
                recommendation.sourceRegistrationId(), recommendation.patientId(), recommendation.departmentId(),
                recommendation.preferredDoctorId(), recommendation.recommendedFrom(), recommendation.recommendedThrough());
        DomainEventEnvelope<FollowUpRecommendationCreatedFact> event = new DomainEventEnvelope<>(eventId,
                "followup.recommendation.created", 1, createdAt, correlationId, "clinical-service", fact);

        // The audit row references the outbox event; the enclosing idempotency transaction makes both atomic.
        outbox.append(event);
        recommendations.save(recommendation, eventId, correlationId);
        return toDto(recommendation);
    }

    private void authorizeRead(ActorIdentity actor, MedicalRecord record) {
        switch (actor.role()) {
            case "PATIENT" -> {
                if (actor.patientId() == null || !actor.patientId().equals(record.getPatientId())) throw notFound();
            }
            case "DOCTOR" -> {
                if (actor.staffId() == null || !actor.staffId().equals(record.getDoctorId())) throw notFound();
                requireCurrentDoctor(actor, correlationIds.currentOrCreate().toString());
            }
            case "ADMIN", "NURSE" -> requireCurrentClinicalReadAuthority(actor, record.getDepartmentId());
            default -> throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor cannot read follow-up advice");
        }
    }

    private void requireCurrentClinicalReadAuthority(ActorIdentity actor, UUID departmentId) {
        SchedulingAuthoritySnapshot snapshot = authority.authority(actor.accountId(), departmentId,
                correlationIds.currentOrCreate().toString());
        boolean current = actor.role().equals(snapshot.currentRole())
                && Boolean.TRUE.equals(snapshot.accountExists()) && Boolean.TRUE.equals(snapshot.accountActive())
                && Boolean.TRUE.equals(snapshot.departmentActive());
        if (!current || "NURSE".equals(actor.role())
                && (actor.staffId() == null || !actor.staffId().equals(snapshot.staffId())
                    || !Boolean.TRUE.equals(snapshot.staffActive())
                    || !departmentId.equals(snapshot.departmentId()))) {
            throw notFound();
        }
    }

    private void requireCurrentDoctor(ActorIdentity actor, String correlationId) {
        CurrentDoctorAuthoritySnapshot snapshot = authority.currentDoctorAuthority(actor.accountId(), correlationId);
        if (!Boolean.TRUE.equals(snapshot.accountExists()) || !Boolean.TRUE.equals(snapshot.accountActive())
                || !"DOCTOR".equals(snapshot.currentRole()) || actor.staffId() == null
                || !actor.staffId().equals(snapshot.staffId()) || !Boolean.TRUE.equals(snapshot.staffActive())
                || !Boolean.TRUE.equals(snapshot.departmentActive())) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED",
                    "Current Core account and staff authority is not active");
        }
    }

    private ActorIdentity requireActor(String... roles) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "A verified clinical actor is required");
        }
        if (java.util.Arrays.stream(roles).noneMatch(actor.role()::equals)) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor role cannot use this recommendation command");
        }
        return actor;
    }

    private static void validateRequest(FollowUpRecommendationRequest request) {
        if (request.recommendedFrom() == null || request.recommendedThrough() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000
                || request.preparationInstructions() != null && request.preparationInstructions().length() > 2000) {
            throw invalidRequest("Follow-up recommendation fields are invalid");
        }
        if (request.recommendedThrough().isBefore(request.recommendedFrom())) {
            throw new BusinessRuleException("FOLLOW_UP_WINDOW_INVALID", "Recommended-through date must not precede recommended-from date");
        }
    }

    public static FollowUpRecommendationDTO toDto(FollowUpRecommendation recommendation) {
        return new FollowUpRecommendationDTO(recommendation.recommendationId(), recommendation.sourceRecordId(),
                recommendation.sourceRegistrationId(), recommendation.patientId(), recommendation.departmentId(),
                recommendation.preferredDoctorId(), recommendation.recommendedFrom(), recommendation.recommendedThrough(),
                recommendation.reason(), recommendation.preparationInstructions(), recommendation.createdAt());
    }

    private static String fingerprint(FollowUpRecommendationRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120
                || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw invalidRequest("Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    private static BusinessRuleException invalidRequest(String message) {
        return new BusinessRuleException("INVALID_REQUEST", message);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Follow-up recommendation source was not found");
    }
}
