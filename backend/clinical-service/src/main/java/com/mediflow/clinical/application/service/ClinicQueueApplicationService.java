package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.ClinicQueueTicketDTO;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/** The department nurse's desk: room numbers for patients who have paid and arrived. */
@Service
@ConditionalOnClinicIntake
public class ClinicQueueApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DESK_DENIED = "CLINIC_DESK_SCOPE_DENIED";
    private final ConsultationSessionRepositoryPort sessions;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final ClinicQueueRepositoryPort tickets;
    private final SchedulingAuthorityPort authority;
    private final CurrentClinicalActorPort actors;
    private final CorrelationIdProvider correlationIds;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    public ClinicQueueApplicationService(ConsultationSessionRepositoryPort sessions, OutpatientRegistrationRepositoryPort registrations,
            ClinicQueueRepositoryPort tickets, SchedulingAuthorityPort authority, CurrentClinicalActorPort actors,
            CorrelationIdProvider correlationIds, IdempotentCommandPort idempotency, @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.sessions = sessions;
        this.registrations = registrations;
        this.tickets = tickets;
        this.authority = authority;
        this.actors = actors;
        this.correlationIds = correlationIds;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<ClinicQueueTicketDTO> issue(UUID registrationId, String key) {
        OutpatientRegistration visible = registration(registrationId);
        ConsultationSession visibleSession = session(visible.sessionId());
        ActorIdentity actor = actors.current();
        Long scopeRevision = requireDepartmentNurse(actor, visibleSession.configuration().departmentId());
        requireKey(key);
        String path = "/api/v2/outpatient-registrations/" + registrationId + "/room-queue-ticket";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key, fingerprint(registrationId), ClinicQueueTicketDTO.class,
                201, data -> "/api/v2/clinic-queue-tickets/" + data.ticketId(), () -> {
                    ConsultationSession locked = lockSession(visible.sessionId());
                    OutpatientRegistration current = registrations.findByIdForUpdate(registrationId)
                            .orElseThrow(ClinicQueueApplicationService::registrationMissing);
                    if (tickets.findActiveByRegistration(registrationId).isPresent()) {
                        throw new DuplicateResourceException("ROOM_TICKET_EXISTS", "This registration already has a room queue number");
                    }
                    requireEligible(current, locked);
                    var config = locked.configuration();
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    int number = tickets.allocateRoomQueueNumber(config.roomId(), config.sessionDate());
                    return dto(tickets.insert(ClinicQueueTicket.issue(UUID.randomUUID(), registrationId, locked, number,
                            actor.accountId(), actor.staffId(), scopeRevision, now)));
                });
    }

    /** Core decides which department the nurse really belongs to; the answer is never taken from the request. */
    public Long requireDepartmentNurse(ActorIdentity actor, UUID departmentId) {
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !"NURSE".equals(actor.role())) throw denied();
        String correlationId = correlationIds.currentOrCreate().toString();
        var current = authority.currentDoctorAuthority(actor.accountId(), correlationId);
        if (!Boolean.TRUE.equals(current.accountExists()) || !Boolean.TRUE.equals(current.accountActive())
                || !"NURSE".equals(current.currentRole()) || !actor.staffId().equals(current.staffId())
                || !Boolean.TRUE.equals(current.staffActive()) || !departmentId.equals(current.departmentId())
                || !Boolean.TRUE.equals(current.departmentActive())) {
            throw denied();
        }
        return authority.authority(actor.accountId(), departmentId, correlationId).scopeRevision();
    }

    private void requireEligible(OutpatientRegistration registration, ConsultationSession session) {
        if (registration.status() != OutpatientRegistrationStatus.CONFIRMED || registration.checkedInAt() == null
                || registration.reservation().clearanceId() == null) {
            throw new BusinessRuleException("ROOM_TICKET_NOT_ELIGIBLE", "A room number needs a paid registration whose patient has arrived");
        }
        LocalDate today = clock.instant().atZone(CLINIC_ZONE).toLocalDate();
        if (!session.configuration().sessionDate().equals(today)) {
            throw new BusinessRuleException("ROOM_TICKET_WRONG_DATE", "A room number is issued only on the day of the session");
        }
    }

    private OutpatientRegistration registration(UUID id) {
        return registrations.findById(id).orElseThrow(ClinicQueueApplicationService::registrationMissing);
    }

    private ConsultationSession session(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private ConsultationSession lockSession(UUID id) {
        return sessions.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private static ResourceNotFoundException registrationMissing() {
        return new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Registration was not found");
    }

    private static ForbiddenOperationException denied() {
        return new ForbiddenOperationException(DESK_DENIED, "Only a nurse of this department can use its clinic desk");
    }

    private static void requireKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120 || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    private static String fingerprint(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(command.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static ClinicQueueTicketDTO dto(ClinicQueueTicket t) {
        return new ClinicQueueTicketDTO(t.ticketId(), t.revision(), t.registrationId(), t.roomId(), t.queueDate(), t.roomQueueNumber(),
                t.status(), t.priority(), t.issuedAt(), t.calledAt());
    }
}
