package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.ClinicQueueAdjustmentRequest;
import com.mediflow.clinical.application.dto.request.ClinicQueueCallRequest;
import com.mediflow.clinical.application.dto.response.ClinicQueueEntryDTO;
import com.mediflow.clinical.application.dto.response.ClinicQueueTicketDTO;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.ClinicQueueStage;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
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
    private static final Comparator<ClinicQueueEntryDTO> BOARD_ORDER = Comparator
            .comparing((ClinicQueueEntryDTO e) -> e.ticketId() == null)
            .thenComparing(e -> e.stage() == ClinicQueueStage.ABSENT)
            .thenComparing(e -> !e.priority())
            .thenComparing(ClinicQueueEntryDTO::roomQueueNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ClinicQueueEntryDTO::queueNumber, Comparator.nullsLast(Comparator.naturalOrder()));
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

    /** Who is waiting today: nurses see their department, doctors their own sessions. */
    public List<ClinicQueueEntryDTO> board(LocalDate date, UUID roomId) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.role() == null) throw denied();
        List<ConsultationSession> visible = switch (actor.role()) {
            case "NURSE" -> nurseSessions(actor, date, roomId);
            case "DOCTOR" -> doctorSessions(actor, date, roomId);
            default -> throw denied();
        };
        if (visible.isEmpty()) return List.of();
        Map<UUID, ConsultationSession> byId = new HashMap<>();
        visible.forEach(s -> byId.put(s.sessionId(), s));
        Instant now = clock.instant();
        return tickets.boardRows(visible.stream().map(ConsultationSession::sessionId).toList()).stream()
                .map(row -> entry(row, byId.get(row.sessionId()), now)).sorted(BOARD_ORDER).toList();
    }

    public IdempotentCommandPort.Result<ClinicQueueTicketDTO> call(UUID ticketId, ClinicQueueCallRequest request, String key) {
        ActorIdentity actor = actors.current();
        requireTicketScope(actor, ticket(ticketId));
        requireKey(key);
        return idempotency.execute(actor.accountId().toString(), "PUT", "/api/v2/clinic-queue-tickets/" + ticketId + "/call", key,
                fingerprint(ticketId + ":" + request), ClinicQueueTicketDTO.class, 200, ignored -> null, () -> {
                    ClinicQueueTicket before = lockTicket(ticketId);
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    ClinicQueueTicket after = domain(() -> before.call(request.expectedRevision(), actor.accountId(), now));
                    tickets.update(before, after);
                    tickets.appendAdjustment(ticketId, "CALLED", null, actor.accountId(), actor.staffId(), now);
                    return dto(after);
                });
    }

    public IdempotentCommandPort.Result<ClinicQueueTicketDTO> adjust(UUID ticketId, ClinicQueueAdjustmentRequest request, String key) {
        ActorIdentity actor = actors.current();
        requireTicketScope(actor, ticket(ticketId));
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw new BusinessRuleException("INVALID_REQUEST", "A queue adjustment needs a reason of 1-500 characters");
        }
        requireKey(key);
        return idempotency.execute(actor.accountId().toString(), "POST", "/api/v2/clinic-queue-tickets/" + ticketId + "/adjustments", key,
                fingerprint(ticketId + ":" + request), ClinicQueueTicketDTO.class, 201, ignored -> null, () -> {
                    ClinicQueueTicket before = lockTicket(ticketId);
                    int expected = request.expectedRevision();
                    ClinicQueueTicket after = domain(() -> switch (request.type()) {
                        case LATE -> before.markLate(expected);
                        case NO_SHOW -> before.markAbsent(expected);
                        case RETURNED -> before.markReturned(expected);
                        case PRIORITY -> before.prioritize(expected);
                    });
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    tickets.update(before, after);
                    tickets.appendAdjustment(ticketId, request.type().name(), request.reason(), actor.accountId(), actor.staffId(), now);
                    return dto(after);
                });
    }

    private List<ConsultationSession> nurseSessions(ActorIdentity actor, LocalDate date, UUID roomId) {
        UUID department = currentStaff(actor, "NURSE").departmentId();
        if (roomId == null) return sessions.findOnDate(date, department, null, null);
        var inRoom = sessions.findOnDate(date, null, null, roomId);
        if (inRoom.stream().anyMatch(s -> !department.equals(s.configuration().departmentId()))) throw denied();
        return inRoom;
    }

    private List<ConsultationSession> doctorSessions(ActorIdentity actor, LocalDate date, UUID roomId) {
        UUID department = currentStaff(actor, "DOCTOR").departmentId();
        return sessions.findOnDate(date, null, actor.staffId(), roomId).stream()
                .filter(s -> department.equals(s.configuration().departmentId())).toList();
    }

    private ClinicQueueEntryDTO entry(ClinicQueueRepositoryPort.BoardRow row, ConsultationSession session, Instant now) {
        var config = session.configuration();
        ClinicQueueTicket t = row.ticket();
        ClinicQueueStage stage = stage(row);
        boolean inLine = t != null && (t.status() == ClinicQueueTicketStatus.WAITING || t.status() == ClinicQueueTicketStatus.CALLED);
        boolean ready = row.status() == OutpatientRegistrationStatus.CONFIRMED && row.checkedInAt() != null && row.cleared() && inLine;
        var clinicNow = now.atZone(CLINIC_ZONE);
        boolean ended = config.sessionDate().isBefore(clinicNow.toLocalDate())
                || config.sessionDate().equals(clinicNow.toLocalDate()) && !clinicNow.toLocalTime().isBefore(config.endTime());
        return new ClinicQueueEntryDTO(row.registrationId(), row.patientId(), row.sessionId(), config.doctorId(), config.departmentId(),
                config.roomId(), config.sessionDate(), row.queueNumber(), t == null ? null : t.ticketId(), t == null ? null : t.revision(),
                t == null ? null : t.roomQueueNumber(), stage, t != null && t.priority(), row.checkedInAt(), t == null ? null : t.calledAt(),
                row.vitalSignsRecorded(), ready, ended && stage == ClinicQueueStage.IN_EXAM);
    }

    private static ClinicQueueStage stage(ClinicQueueRepositoryPort.BoardRow row) {
        if (row.ticket() != null) {
            return switch (row.ticket().status()) {
                case CALLED -> ClinicQueueStage.CALLED;
                case IN_EXAM, COMPLETED -> ClinicQueueStage.IN_EXAM;
                case ABSENT -> ClinicQueueStage.ABSENT;
                default -> ClinicQueueStage.WAITING_CALL;
            };
        }
        if (row.status() == OutpatientRegistrationStatus.AWAITING_PAYMENT) return ClinicQueueStage.AWAITING_PAYMENT;
        if (row.status() == OutpatientRegistrationStatus.IN_EXAM) return ClinicQueueStage.IN_EXAM;
        return row.checkedInAt() == null ? ClinicQueueStage.NOT_ARRIVED : ClinicQueueStage.WAITING_ROOM_TICKET;
    }

    /** Core decides which department the nurse really belongs to; the answer is never taken from the request. */
    public Long requireDepartmentNurse(ActorIdentity actor, UUID departmentId) {
        var current = currentStaff(actor, "NURSE");
        if (!departmentId.equals(current.departmentId())) throw denied();
        return authority.authority(actor.accountId(), departmentId, correlationIds.currentOrCreate().toString()).scopeRevision();
    }

    private void requireTicketScope(ActorIdentity actor, ClinicQueueTicket ticket) {
        if (actor != null && "DOCTOR".equals(actor.role())) {
            var current = currentStaff(actor, "DOCTOR");
            if (!ticket.doctorId().equals(actor.staffId()) || !ticket.departmentId().equals(current.departmentId())) throw denied();
            return;
        }
        requireDepartmentNurse(actor, ticket.departmentId());
    }

    private CurrentDoctorAuthoritySnapshot currentStaff(ActorIdentity actor, String role) {
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !role.equals(actor.role())) throw denied();
        var current = authority.currentDoctorAuthority(actor.accountId(), correlationIds.currentOrCreate().toString());
        if (!Boolean.TRUE.equals(current.accountExists()) || !Boolean.TRUE.equals(current.accountActive())
                || !role.equals(current.currentRole()) || !actor.staffId().equals(current.staffId())
                || !Boolean.TRUE.equals(current.staffActive()) || current.departmentId() == null
                || !Boolean.TRUE.equals(current.departmentActive())) {
            throw denied();
        }
        return current;
    }

    private ClinicQueueTicket ticket(UUID id) {
        return tickets.findById(id).orElseThrow(ClinicQueueApplicationService::ticketMissing);
    }

    private ClinicQueueTicket lockTicket(UUID id) {
        return tickets.findByIdForUpdate(id).orElseThrow(ClinicQueueApplicationService::ticketMissing);
    }

    private static ResourceNotFoundException ticketMissing() {
        return new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Queue ticket was not found");
    }

    private static <T> T domain(Supplier<T> command) {
        try {
            return command.get();
        } catch (IllegalStateException conflict) {
            throw new DuplicateResourceException(conflict.getMessage(), conflict.getMessage());
        }
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
