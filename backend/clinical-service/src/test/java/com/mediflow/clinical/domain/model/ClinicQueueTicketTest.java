package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClinicQueueTicketTest {
    private static final Instant AT = Instant.parse("2026-11-10T01:00:00Z");
    private static final UUID ACTOR = UUID.randomUUID();
    private final ConsultationSession session = ConsultationSession.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), LocalDate.of(2026, 11, 10), LocalTime.of(8, 0), LocalTime.of(11, 0), List.of(), 20);
    private final ClinicQueueTicket waiting = ClinicQueueTicket.issue(UUID.randomUUID(), UUID.randomUUID(), session, 2, ACTOR, null, 7L, AT);

    private static void conflict(Runnable command) {
        assertThatThrownBy(command::run).isInstanceOf(IllegalStateException.class).hasMessage("TICKET_STATE_CONFLICT");
    }

    @Test void issueCopiesPlaceFromSession() {
        assertThat(waiting.status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
        assertThat(waiting.revision()).isEqualTo(1);
        assertThat(waiting.roomId()).isEqualTo(session.configuration().roomId());
        assertThat(waiting.queueDate()).isEqualTo(session.configuration().sessionDate());
        assertThat(waiting.authorityScopeRevision()).isEqualTo(7L);
    }

    @Test void callRecordsCallerAndMayRepeat() {
        var called = waiting.call(1, ACTOR, AT);
        assertThat(called.status()).isEqualTo(ClinicQueueTicketStatus.CALLED);
        assertThat(called.calledByAccountId()).isEqualTo(ACTOR);
        assertThat(called.calledAt()).isEqualTo(AT);
        var recalled = called.call(2, ACTOR, AT.plusSeconds(60));
        assertThat(recalled.roomQueueNumber()).isEqualTo(2);
        assertThat(recalled.calledAt()).isEqualTo(AT.plusSeconds(60));
        assertThat(recalled.revision()).isEqualTo(3);
    }

    @Test void staleRevisionIsRejected() {
        assertThatThrownBy(() -> waiting.call(9, ACTOR, AT)).isInstanceOf(IllegalStateException.class).hasMessage("REVISION_CONFLICT");
    }

    @Test void absentReturnsToWaitingWithSameNumber() {
        var absent = waiting.markAbsent(1);
        assertThat(absent.status()).isEqualTo(ClinicQueueTicketStatus.ABSENT);
        var back = absent.markReturned(2);
        assertThat(back.status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
        assertThat(back.roomQueueNumber()).isEqualTo(2);
    }

    @Test void absentCannotBeCalledOrStarted() {
        var absent = waiting.markAbsent(1);
        conflict(() -> absent.call(2, ACTOR, AT));
        conflict(() -> absent.startExam(AT));
    }

    @Test void onlyAbsentCanReturn() {
        conflict(() -> waiting.markReturned(1));
    }

    @Test void prioritySticks() {
        var urgent = waiting.prioritize(1);
        assertThat(urgent.priority()).isTrue();
        assertThat(urgent.status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
        assertThat(urgent.call(2, ACTOR, AT).priority()).isTrue();
    }

    @Test void examLifecycle() {
        var inExam = waiting.call(1, ACTOR, AT).startExam(AT);
        assertThat(inExam.status()).isEqualTo(ClinicQueueTicketStatus.IN_EXAM);
        assertThat(waiting.startExam(AT).status()).isEqualTo(ClinicQueueTicketStatus.IN_EXAM);
        assertThat(inExam.complete(AT).status()).isEqualTo(ClinicQueueTicketStatus.COMPLETED);
        conflict(() -> waiting.complete(AT));
        conflict(() -> inExam.call(3, ACTOR, AT));
        conflict(() -> inExam.markAbsent(3));
    }

    @Test void cancelKeepsAuditAndIsFinal() {
        var cancelled = waiting.cancel(ACTOR, "Đổi lịch", AT);
        assertThat(cancelled.status()).isEqualTo(ClinicQueueTicketStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Đổi lịch");
        assertThat(cancelled.cancelledByAccountId()).isEqualTo(ACTOR);
        conflict(() -> cancelled.cancel(ACTOR, "Lần nữa", AT));
        conflict(() -> waiting.startExam(AT).cancel(ACTOR, "Đang khám", AT));
    }

    @Test void lateNoteKeepsStatus() {
        assertThat(waiting.markLate(1).status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
        assertThat(waiting.markLate(1).revision()).isEqualTo(2);
        conflict(() -> waiting.markAbsent(1).markLate(2));
    }
}
