package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort.BoardRow;
import com.mediflow.clinical.domain.model.ClinicQueueStage;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicQueueBoardTest {
    private final IntakeHarness h = new IntakeHarness();
    private final ClinicQueueRepositoryPort tickets = mock(ClinicQueueRepositoryPort.class);
    private ConsultationSession session;

    private ClinicQueueApplicationService service() {
        return new ClinicQueueApplicationService(h.sessions, h.registrations, tickets, h.authority, h.actors, h.correlationIds,
                h.idempotency, h.clock);
    }

    @BeforeEach void setUp() {
        h.actAsNurse();
        session = h.session();
        lenient().when(h.sessions.findOnDate(IntakeHarness.TODAY, h.departmentId, null, null)).thenReturn(List.of(session));
    }

    private void rows(BoardRow... rows) {
        when(tickets.boardRows(List.of(session.sessionId()))).thenReturn(List.of(rows));
    }

    private ClinicQueueTicket ticket(int number) {
        return ClinicQueueTicket.issue(UUID.randomUUID(), UUID.randomUUID(), session, number, IntakeHarness.ACTOR, null, 7L, IntakeHarness.NOW);
    }

    private BoardRow row(OutpatientRegistrationStatus status, boolean arrived, Integer queue, ClinicQueueTicket ticket) {
        UUID registration = ticket == null ? UUID.randomUUID() : ticket.registrationId();
        return new BoardRow(registration, UUID.randomUUID(), session.sessionId(), status, arrived ? IntakeHarness.NOW : null, queue,
                status != OutpatientRegistrationStatus.AWAITING_PAYMENT, ticket, false);
    }

    private ClinicQueueStage stageOf(BoardRow row) {
        rows(row);
        return service().board(IntakeHarness.TODAY, null).get(0).stage();
    }

    @Test void separatesRegisteredFromAllowedToExamine() {
        assertThat(stageOf(row(OutpatientRegistrationStatus.AWAITING_PAYMENT, true, null, null))).isEqualTo(ClinicQueueStage.AWAITING_PAYMENT);
        assertThat(stageOf(row(OutpatientRegistrationStatus.CONFIRMED, false, 3, null))).isEqualTo(ClinicQueueStage.NOT_ARRIVED);
        assertThat(stageOf(row(OutpatientRegistrationStatus.CONFIRMED, true, 3, null))).isEqualTo(ClinicQueueStage.WAITING_ROOM_TICKET);
        var waiting = ticket(1);
        assertThat(stageOf(row(OutpatientRegistrationStatus.CONFIRMED, true, 3, waiting))).isEqualTo(ClinicQueueStage.WAITING_CALL);
        assertThat(stageOf(row(OutpatientRegistrationStatus.CONFIRMED, true, 3, waiting.call(1, IntakeHarness.ACTOR, IntakeHarness.NOW)))).isEqualTo(ClinicQueueStage.CALLED);
        assertThat(stageOf(row(OutpatientRegistrationStatus.IN_EXAM, true, 3, waiting.startExam(IntakeHarness.NOW)))).isEqualTo(ClinicQueueStage.IN_EXAM);
        assertThat(stageOf(row(OutpatientRegistrationStatus.CONFIRMED, true, 3, waiting.markAbsent(1)))).isEqualTo(ClinicQueueStage.ABSENT);
    }

    @Test void onlyTicketedArrivedPatientIsReadyForExam() {
        rows(row(OutpatientRegistrationStatus.CONFIRMED, true, 3, null));
        assertThat(service().board(IntakeHarness.TODAY, null).get(0).readyForExam()).isFalse();
        rows(row(OutpatientRegistrationStatus.CONFIRMED, true, 3, ticket(1)));
        assertThat(service().board(IntakeHarness.TODAY, null).get(0).readyForExam()).isTrue();
    }

    @Test void nurseSeesOnlyHerDepartmentWhateverRoomIsRequested() {
        UUID foreignRoom = UUID.randomUUID();
        var foreign = ConsultationSession.open(UUID.randomUUID(), UUID.randomUUID(), h.otherDepartmentId, foreignRoom, IntakeHarness.TODAY,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(11, 0), List.of(), 10);
        when(h.sessions.findOnDate(IntakeHarness.TODAY, null, null, foreignRoom)).thenReturn(List.of(foreign));
        assertThatThrownBy(() -> service().board(IntakeHarness.TODAY, foreignRoom))
                .isInstanceOfSatisfying(ForbiddenOperationException.class, e -> assertThat(e.getCode()).isEqualTo("CLINIC_DESK_SCOPE_DENIED"));
    }

    @Test void nurseMayFilterOwnRoom() {
        when(h.sessions.findOnDate(IntakeHarness.TODAY, null, null, h.roomId)).thenReturn(List.of(session));
        rows(row(OutpatientRegistrationStatus.CONFIRMED, false, 1, null));
        assertThat(service().board(IntakeHarness.TODAY, h.roomId)).hasSize(1);
    }

    @Test void doctorSeesOnlyOwnSessions() {
        h.actAsDoctor();
        when(h.sessions.findOnDate(any(), isNull(), any(), isNull())).thenAnswer(call ->
                h.doctorId.equals(call.getArgument(2)) ? List.of(session) : List.of());
        rows(row(OutpatientRegistrationStatus.CONFIRMED, false, 1, null));
        assertThat(service().board(IntakeHarness.TODAY, null)).hasSize(1);

        h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        assertThat(service().board(IntakeHarness.TODAY, null)).isEmpty();
    }

    @Test void transferredDoctorSeesNothingFromOldDepartment() {
        h.actAs("DOCTOR", h.doctorId, h.otherDepartmentId);
        when(h.sessions.findOnDate(any(), isNull(), any(), isNull())).thenReturn(List.of(session));
        assertThat(service().board(IntakeHarness.TODAY, null)).isEmpty();
    }

    @Test void adminHasNoClinicBoard() {
        h.actAsManager();
        assertThatThrownBy(() -> service().board(IntakeHarness.TODAY, null)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void ordersPriorityThenRoomNumberAbsentLast() {
        var urgent = ticket(9).prioritize(1);
        var second = ticket(2);
        var third = ticket(3);
        var absent = ticket(1).markAbsent(1);
        rows(row(OutpatientRegistrationStatus.CONFIRMED, false, 7, null),
                row(OutpatientRegistrationStatus.CONFIRMED, true, 2, absent),
                row(OutpatientRegistrationStatus.CONFIRMED, true, 3, third),
                row(OutpatientRegistrationStatus.CONFIRMED, false, 5, null),
                row(OutpatientRegistrationStatus.CONFIRMED, true, 4, second),
                row(OutpatientRegistrationStatus.CONFIRMED, true, 6, urgent));
        var numbers = new ArrayList<Integer>();
        service().board(IntakeHarness.TODAY, null).forEach(e -> numbers.add(e.roomQueueNumber() != null ? e.roomQueueNumber() : -e.queueNumber()));
        assertThat(numbers).containsExactly(9, 2, 3, 1, -5, -7);
    }

    @Test void finishedSessionWithOpenExamIsMarkedForContinuation() {
        h.clock = Clock.fixed(Instant.parse("2026-11-10T05:00:00Z"), ZoneOffset.UTC);
        rows(row(OutpatientRegistrationStatus.IN_EXAM, true, 3, ticket(1).startExam(IntakeHarness.NOW)),
                row(OutpatientRegistrationStatus.CONFIRMED, true, 4, ticket(2)));
        var board = service().board(IntakeHarness.TODAY, null);
        assertThat(board).filteredOn(e -> e.stage() == ClinicQueueStage.IN_EXAM).singleElement()
                .satisfies(e -> assertThat(e.continuationRequired()).isTrue());
        assertThat(board).filteredOn(e -> e.stage() == ClinicQueueStage.WAITING_CALL).singleElement()
                .satisfies(e -> assertThat(e.continuationRequired()).isFalse());
    }

    @Test void runningSessionNeedsNoContinuation() {
        rows(row(OutpatientRegistrationStatus.IN_EXAM, true, 3, ticket(1).startExam(IntakeHarness.NOW)));
        assertThat(service().board(IntakeHarness.TODAY, null).get(0).continuationRequired()).isFalse();
    }
}
