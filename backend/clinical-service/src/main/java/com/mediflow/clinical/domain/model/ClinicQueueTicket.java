package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A patient's place in one consultation room's line for one day, issued at the department desk. */
public record ClinicQueueTicket(
        UUID ticketId,
        int revision,
        UUID registrationId,
        UUID sessionId,
        UUID roomId,
        UUID departmentId,
        UUID doctorId,
        LocalDate queueDate,
        int roomQueueNumber,
        ClinicQueueTicketStatus status,
        boolean priority,
        UUID issuedByAccountId,
        UUID issuedByStaffId,
        Instant issuedAt,
        Long authorityScopeRevision,
        UUID calledByAccountId,
        Instant calledAt,
        UUID cancelledByAccountId,
        String cancelReason,
        Instant cancelledAt) {
    private static final Set<ClinicQueueTicketStatus> IN_LINE = EnumSet.of(ClinicQueueTicketStatus.WAITING, ClinicQueueTicketStatus.CALLED);

    public ClinicQueueTicket {
        Objects.requireNonNull(ticketId, "ticket id is required");
        Objects.requireNonNull(registrationId, "registration id is required");
        Objects.requireNonNull(status, "ticket status is required");
        if (revision < 1 || roomQueueNumber < 1) throw new IllegalArgumentException("ticket revision and number must be positive");
    }

    public static ClinicQueueTicket issue(UUID ticketId, UUID registrationId, ConsultationSession session, int roomQueueNumber,
            UUID actorAccountId, UUID actorStaffId, Long scopeRevision, Instant at) {
        var config = session.configuration();
        return new ClinicQueueTicket(ticketId, 1, registrationId, session.sessionId(), config.roomId(), config.departmentId(),
                config.doctorId(), config.sessionDate(), roomQueueNumber, ClinicQueueTicketStatus.WAITING, false,
                actorAccountId, actorStaffId, at, scopeRevision, null, null, null, null, null);
    }

    public ClinicQueueTicket call(int expectedRevision, UUID actor, Instant at) {
        requireRevision(expectedRevision);
        requireIn(IN_LINE);
        return new ClinicQueueTicket(ticketId, revision + 1, registrationId, sessionId, roomId, departmentId, doctorId, queueDate,
                roomQueueNumber, ClinicQueueTicketStatus.CALLED, priority, issuedByAccountId, issuedByStaffId, issuedAt,
                authorityScopeRevision, actor, at, cancelledByAccountId, cancelReason, cancelledAt);
    }

    public ClinicQueueTicket markLate(int expectedRevision) {
        requireRevision(expectedRevision);
        requireIn(IN_LINE);
        return with(status, priority);
    }

    public ClinicQueueTicket markAbsent(int expectedRevision) {
        requireRevision(expectedRevision);
        requireIn(IN_LINE);
        return with(ClinicQueueTicketStatus.ABSENT, priority);
    }

    public ClinicQueueTicket markReturned(int expectedRevision) {
        requireRevision(expectedRevision);
        requireIn(EnumSet.of(ClinicQueueTicketStatus.ABSENT));
        return with(ClinicQueueTicketStatus.WAITING, priority);
    }

    public ClinicQueueTicket prioritize(int expectedRevision) {
        requireRevision(expectedRevision);
        requireIn(EnumSet.of(ClinicQueueTicketStatus.WAITING, ClinicQueueTicketStatus.CALLED, ClinicQueueTicketStatus.ABSENT));
        return with(status, true);
    }

    public ClinicQueueTicket startExam(Instant at) {
        requireIn(IN_LINE);
        return with(ClinicQueueTicketStatus.IN_EXAM, priority);
    }

    public ClinicQueueTicket complete(Instant at) {
        requireIn(EnumSet.of(ClinicQueueTicketStatus.IN_EXAM));
        return with(ClinicQueueTicketStatus.COMPLETED, priority);
    }

    public ClinicQueueTicket cancel(UUID actor, String reason, Instant at) {
        requireIn(EnumSet.of(ClinicQueueTicketStatus.WAITING, ClinicQueueTicketStatus.CALLED, ClinicQueueTicketStatus.ABSENT));
        return new ClinicQueueTicket(ticketId, revision + 1, registrationId, sessionId, roomId, departmentId, doctorId, queueDate,
                roomQueueNumber, ClinicQueueTicketStatus.CANCELLED, priority, issuedByAccountId, issuedByStaffId, issuedAt,
                authorityScopeRevision, calledByAccountId, calledAt, actor, reason, at);
    }

    private ClinicQueueTicket with(ClinicQueueTicketStatus nextStatus, boolean nextPriority) {
        return new ClinicQueueTicket(ticketId, revision + 1, registrationId, sessionId, roomId, departmentId, doctorId, queueDate,
                roomQueueNumber, nextStatus, nextPriority, issuedByAccountId, issuedByStaffId, issuedAt, authorityScopeRevision,
                calledByAccountId, calledAt, cancelledByAccountId, cancelReason, cancelledAt);
    }

    private void requireRevision(int expected) {
        if (expected != revision) throw new IllegalStateException("REVISION_CONFLICT");
    }

    private void requireIn(Set<ClinicQueueTicketStatus> allowed) {
        if (!allowed.contains(status)) throw new IllegalStateException("TICKET_STATE_CONFLICT");
    }
}
