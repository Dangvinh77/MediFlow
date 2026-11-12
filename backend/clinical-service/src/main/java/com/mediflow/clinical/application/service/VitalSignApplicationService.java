package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.VitalSignCreateRequest;
import com.mediflow.clinical.application.dto.response.VitalSignObservationDTO;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.VitalSignRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.VitalSignObservation;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/** Append-only pulse, pressure, temperature and weight taken at the department desk before the exam. */
@Service
@ConditionalOnClinicIntake
public class VitalSignApplicationService {
    private final ConsultationSessionRepositoryPort sessions;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final VitalSignRepositoryPort vitals;
    private final ClinicQueueApplicationService desk;
    private final OutpatientRegistrationApplicationService registrationAccess;
    private final CurrentClinicalActorPort actors;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    public VitalSignApplicationService(ConsultationSessionRepositoryPort sessions, OutpatientRegistrationRepositoryPort registrations,
            VitalSignRepositoryPort vitals, ClinicQueueApplicationService desk, OutpatientRegistrationApplicationService registrationAccess,
            CurrentClinicalActorPort actors, IdempotentCommandPort idempotency, @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.sessions = sessions;
        this.registrations = registrations;
        this.vitals = vitals;
        this.desk = desk;
        this.registrationAccess = registrationAccess;
        this.actors = actors;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<VitalSignObservationDTO> record(UUID registrationId, VitalSignCreateRequest request, String key) {
        OutpatientRegistration registration = registration(registrationId);
        ConsultationSession session = session(registration.sessionId());
        ActorIdentity actor = actors.current();
        UUID departmentId = session.configuration().departmentId();
        desk.requireDepartmentNurse(actor, departmentId);
        boolean present = registration.checkedInAt() != null && (registration.status() == OutpatientRegistrationStatus.CONFIRMED
                || registration.status() == OutpatientRegistrationStatus.IN_EXAM);
        if (!present) throw new BusinessRuleException("VITAL_SIGNS_NOT_ARRIVED", "Vital signs are recorded after the patient has arrived");
        requireKey(key);
        String path = "/api/v2/outpatient-registrations/" + registrationId + "/vital-signs";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key, fingerprint(registrationId + ":" + request),
                VitalSignObservationDTO.class, 201, ignored -> null, () -> {
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    return dto(vitals.insert(observation(registration, departmentId, actor, request, now)));
                });
    }

    public List<VitalSignObservationDTO> list(UUID registrationId) {
        OutpatientRegistration registration = registration(registrationId);
        ConsultationSession session = session(registration.sessionId());
        ActorIdentity actor = actors.current();
        if (actor != null && "DOCTOR".equals(actor.role())) {
            registrationAccess.requireAssignedDoctor(actor, session);
        } else if (actor != null && "NURSE".equals(actor.role())) {
            desk.requireDepartmentNurse(actor, session.configuration().departmentId());
        } else {
            throw new ForbiddenOperationException("CLINIC_DESK_SCOPE_DENIED", "Only clinic staff can read vital signs");
        }
        return vitals.listByRegistration(registrationId).stream().map(VitalSignApplicationService::dto).toList();
    }

    private static VitalSignObservation observation(OutpatientRegistration registration, UUID departmentId, ActorIdentity actor,
            VitalSignCreateRequest r, Instant now) {
        try {
            return VitalSignObservation.record(UUID.randomUUID(), registration.registrationId(), registration.patientId(), departmentId, now,
                    actor.accountId(), actor.staffId(), r.pulseBpm(), r.systolicMmhg(), r.diastolicMmhg(), r.temperatureC(), r.respiratoryRate(),
                    r.spo2Percent(), r.weightKg(), r.notMeasuredReason());
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleException(invalid.getMessage(), "Vital signs were not accepted: " + invalid.getMessage());
        }
    }

    private OutpatientRegistration registration(UUID id) {
        return registrations.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
    }

    private ConsultationSession session(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private static void requireKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120 || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    private static String fingerprint(String command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(command.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static VitalSignObservationDTO dto(VitalSignObservation o) {
        return new VitalSignObservationDTO(o.observationId(), o.registrationId(), o.observedAt(), o.recordedByAccountId(), o.recordedByStaffId(),
                o.pulseBpm(), o.systolicMmhg(), o.diastolicMmhg(), o.temperatureC(), o.respiratoryRate(), o.spo2Percent(), o.weightKg(),
                o.notMeasuredReason());
    }
}
