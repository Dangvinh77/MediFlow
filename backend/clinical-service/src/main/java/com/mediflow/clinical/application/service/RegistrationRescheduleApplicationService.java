package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.request.RegistrationRescheduleRequest;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.application.event.OutpatientRegistrationFact.RescheduleFact;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort.RescheduleLog;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;

/** Moves a registration to another session of the same department before the exam; payment and identity stay. */
@Service
@ConditionalOnClinicIntake
public class RegistrationRescheduleApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ConsultationSessionRepositoryPort sessions;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final AppointmentRepositoryPort appointments;
    private final ClinicQueueRepositoryPort tickets;
    private final OutpatientRegistrationApplicationService access;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    public RegistrationRescheduleApplicationService(ConsultationSessionRepositoryPort sessions, OutpatientRegistrationRepositoryPort registrations,
            AppointmentRepositoryPort appointments, ClinicQueueRepositoryPort tickets, OutpatientRegistrationApplicationService access,
            IdempotentCommandPort idempotency, @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.sessions = sessions;
        this.registrations = registrations;
        this.appointments = appointments;
        this.tickets = tickets;
        this.access = access;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<OutpatientRegistrationDTO> reschedule(UUID registrationId, RegistrationRescheduleRequest request, String key) {
        OutpatientRegistration visible = registrations.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
        ConsultationSession visibleSource = sessions.findById(visible.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
        var actor = access.requireHumanActor("ADMIN", "NURSE");
        UUID departmentId = visibleSource.configuration().departmentId();
        Long scopeRevision = access.requireActiveActor(actor, departmentId).scopeRevision();
        if (!request.patientConfirmed()) {
            throw new BusinessRuleException("RESCHEDULE_CONFIRMATION_REQUIRED", "The patient must agree before the appointment is moved");
        }
        ConsultationSession visibleTarget = sessions.findById(request.targetSessionId()).orElseThrow(RegistrationRescheduleApplicationService::targetInvalid);
        // A retry of a move that succeeded finds the registration already in the target and must reach its stored result.
        if (!visibleTarget.sessionId().equals(visibleSource.sessionId())) {
            if (!departmentId.equals(visibleTarget.configuration().departmentId())) throw targetInvalid();
            try {
                access.requireEligible(visibleTarget.configuration());
            } catch (BusinessRuleException ineligible) {
                throw targetInvalid();
            }
        }
        requireKey(key);
        String path = "/api/v2/outpatient-registrations/" + registrationId + "/reschedule";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key, fingerprint(registrationId + ":" + request),
                OutpatientRegistrationDTO.class, 200, ignored -> null, () -> {
                    UUID sourceId = visibleSource.sessionId();
                    UUID targetId = visibleTarget.sessionId();
                    if (sourceId.equals(targetId)) throw targetInvalid();
                    boolean sourceFirst = sourceId.compareTo(targetId) < 0;
                    ConsultationSession firstLocked = lock(sourceFirst ? sourceId : targetId);
                    ConsultationSession secondLocked = lock(sourceFirst ? targetId : sourceId);
                    ConsultationSession source = sourceFirst ? firstLocked : secondLocked;
                    ConsultationSession target = sourceFirst ? secondLocked : firstLocked;
                    OutpatientRegistration before = registrations.findByIdForUpdate(registrationId)
                            .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
                    if (!before.sessionId().equals(sourceId)) throw new DuplicateResourceException("REVISION_CONFLICT", "Registration moved meanwhile");
                    step(() -> before.requireReschedulable(request.expectedRevision()));
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    requireTargetOpen(target, now);
                    if (target.counts().availableCount() < 1) {
                        throw new DuplicateResourceException("SESSION_CAPACITY_EXHAUSTED", "Session has no available seats");
                    }
                    if (registrations.existsActivePatientSession(before.patientId(), targetId)) {
                        throw new DuplicateResourceException("ACTIVE_REGISTRATION_EXISTS", "Patient already has an active registration for that session");
                    }
                    var oldConfig = source.configuration();
                    var newConfig = target.configuration();
                    boolean otherDay = !oldConfig.sessionDate().equals(newConfig.sessionDate());
                    Integer newNumber = otherDay && before.status() == OutpatientRegistrationStatus.CONFIRMED
                            ? registrations.allocateDepartmentQueueNumber(departmentId, newConfig.sessionDate()) : null;
                    OutpatientRegistration after = before.reschedule(request.expectedRevision(), target, newNumber, otherDay, now);
                    tickets.findActiveByRegistration(registrationId)
                            .map(found -> tickets.findByIdForUpdate(found.ticketId()).orElse(found))
                            .ifPresent(ticket -> tickets.update(ticket, ticket.cancel(actor.accountId(), request.reason(), now)));
                    Appointment appointment = appointments.findByIdForUpdate(registrationId)
                            .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found"));
                    appointment.rescheduleScheduled(newConfig.doctorId(), newConfig.sessionDate(), newConfig.startTime(), otherDay, now);
                    appointments.save(appointment);
                    registrations.update(before, after, actor.accountId(), actor.staffId(), "RESCHEDULED", now);
                    boolean held = before.reservation().status() == OutpatientReservationStatus.HELD;
                    sessions.updateCounts(source, OutpatientRegistrationApplicationService.adjustCounts(source, held ? -1 : 0, 0, held ? 0 : -1, 0, 1), now);
                    sessions.updateCounts(target, OutpatientRegistrationApplicationService.adjustCounts(target, held ? 1 : 0, 0, held ? 0 : 1, 0, -1), now);
                    registrations.appendRescheduleLog(new RescheduleLog(registrationId, sourceId, targetId, oldConfig.doctorId(), newConfig.doctorId(),
                            oldConfig.roomId(), newConfig.roomId(), oldConfig.sessionDate(), newConfig.sessionDate(), oldConfig.startTime(),
                            newConfig.startTime(), actor.accountId(), actor.staffId(), scopeRevision, request.reason(), now));
                    access.appendFact(after, target, now, new RescheduleFact(actor.accountId(), actor.staffId(), request.reason(), sourceId,
                            oldConfig.doctorId(), oldConfig.roomId(), oldConfig.sessionDate(), oldConfig.startTime(), now));
                    return access.toDto(after, target);
                });
    }

    private void requireTargetOpen(ConsultationSession target, Instant now) {
        var config = target.configuration();
        var clinicNow = now.atZone(CLINIC_ZONE);
        boolean ended = config.sessionDate().isBefore(clinicNow.toLocalDate())
                || config.sessionDate().equals(clinicNow.toLocalDate()) && !clinicNow.toLocalTime().isBefore(config.endTime());
        if (target.status() != ConsultationSessionStatus.OPEN || ended) throw targetInvalid();
    }

    private ConsultationSession lock(UUID id) {
        return sessions.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private static void step(Runnable check) {
        try {
            check.run();
        } catch (IllegalStateException conflict) {
            throw new DuplicateResourceException(conflict.getMessage(), conflict.getMessage());
        }
    }

    private static BusinessRuleException targetInvalid() {
        return new BusinessRuleException("RESCHEDULE_TARGET_INVALID", "The target session cannot take this registration");
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
}
