package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.ClinicQueueAdjustmentRequest;
import com.mediflow.clinical.application.dto.request.ClinicQueueCallRequest;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicQueueCallTest {
    private final IntakeHarness h = new IntakeHarness();
    private final ClinicQueueRepositoryPort tickets = mock(ClinicQueueRepositoryPort.class);
    private ClinicQueueTicket waiting;
    private ActorIdentity nurse;

    private ClinicQueueApplicationService service() {
        return new ClinicQueueApplicationService(h.sessions, h.registrations, tickets, h.authority, h.actors, h.correlationIds,
                h.idempotency, h.clock);
    }

    private ClinicQueueTicket stub(ClinicQueueTicket ticket) {
        lenient().when(tickets.findById(ticket.ticketId())).thenReturn(Optional.of(ticket));
        lenient().when(tickets.findByIdForUpdate(ticket.ticketId())).thenReturn(Optional.of(ticket));
        return ticket;
    }

    @BeforeEach void setUp() {
        nurse = h.actAsNurse();
        waiting = stub(ClinicQueueTicket.issue(UUID.randomUUID(), UUID.randomUUID(), h.session(), 4, IntakeHarness.ACTOR, null, 7L, IntakeHarness.NOW));
    }

    private ClinicQueueAdjustmentRequest adjustment(int revision, ClinicQueueAdjustmentRequest.Type type, String reason) {
        return new ClinicQueueAdjustmentRequest(revision, type, reason);
    }

    @Test void nurseOfDepartmentCanCall() {
        assertThat(service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key").data().status()).isEqualTo(ClinicQueueTicketStatus.CALLED);
    }

    @Test void assignedDoctorCanCall() {
        h.actAsDoctor();
        assertThat(service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key").data().status()).isEqualTo(ClinicQueueTicketStatus.CALLED);
    }

    @Test void otherDoctorCannotCall() {
        h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        assertThatThrownBy(() -> service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key"))
                .isInstanceOfSatisfying(ForbiddenOperationException.class, e -> assertThat(e.getCode()).isEqualTo("CLINIC_DESK_SCOPE_DENIED"));
        verify(tickets, never()).update(any(), any());
    }

    @Test void nurseOfOtherDepartmentCannotCall() {
        h.actAs("NURSE", UUID.randomUUID(), h.otherDepartmentId);
        assertThatThrownBy(() -> service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key")).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void callRecordsCallerAndTimeAndAppendsAdjustment() {
        var dto = service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key").data();
        assertThat(dto.calledAt()).isEqualTo(IntakeHarness.NOW);
        verify(tickets).appendAdjustment(eq(waiting.ticketId()), eq("CALLED"), isNull(), eq(nurse.accountId()), eq(nurse.staffId()), eq(IntakeHarness.NOW));
    }

    @Test void recallKeepsNumberAndAppendsAnotherRow() {
        var called = stub(waiting.call(1, IntakeHarness.ACTOR, IntakeHarness.NOW));
        var dto = service().call(called.ticketId(), new ClinicQueueCallRequest(2), "key").data();
        assertThat(dto.roomQueueNumber()).isEqualTo(4);
        assertThat(dto.revision()).isEqualTo(3);
        verify(tickets, times(1)).appendAdjustment(any(), eq("CALLED"), any(), any(), any(), any());
    }

    @Test void noShowMarksAbsentWithoutReleasingSeat() {
        var dto = service().adjust(waiting.ticketId(), adjustment(1, ClinicQueueAdjustmentRequest.Type.NO_SHOW, "Gọi 3 lần không thấy"), "key").data();
        assertThat(dto.status()).isEqualTo(ClinicQueueTicketStatus.ABSENT);
        verifyNoInteractions(h.registrations);
        verify(h.sessions, never()).updateCounts(any(), any(), any());
        verify(tickets).appendAdjustment(eq(waiting.ticketId()), eq("NO_SHOW"), eq("Gọi 3 lần không thấy"), any(), any(), any());
    }

    @Test void returnedKeepsOriginalNumber() {
        var absent = stub(waiting.markAbsent(1));
        var dto = service().adjust(absent.ticketId(), adjustment(2, ClinicQueueAdjustmentRequest.Type.RETURNED, "Đã quay lại"), "key").data();
        assertThat(dto.status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
        assertThat(dto.roomQueueNumber()).isEqualTo(4);
    }

    @Test void lateKeepsStatusAndRecordsRow() {
        var dto = service().adjust(waiting.ticketId(), adjustment(1, ClinicQueueAdjustmentRequest.Type.LATE, "Đi vệ sinh"), "key").data();
        assertThat(dto.status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
        verify(tickets).appendAdjustment(any(), eq("LATE"), eq("Đi vệ sinh"), any(), any(), any());
    }

    @Test void priorityRequiresReasonAndRecordsActor() {
        assertThatThrownBy(() -> service().adjust(waiting.ticketId(), adjustment(1, ClinicQueueAdjustmentRequest.Type.PRIORITY, "   "), "key"))
                .isInstanceOf(BusinessRuleException.class);
        var dto = service().adjust(waiting.ticketId(), adjustment(1, ClinicQueueAdjustmentRequest.Type.PRIORITY, "Người cao tuổi"), "key").data();
        assertThat(dto.priority()).isTrue();
        verify(tickets).appendAdjustment(any(), eq("PRIORITY"), eq("Người cao tuổi"), eq(nurse.accountId()), eq(nurse.staffId()), any());
    }

    @Test void staleRevisionIsRejected() {
        assertThatThrownBy(() -> service().call(waiting.ticketId(), new ClinicQueueCallRequest(9), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("REVISION_CONFLICT"));
    }

    @Test void wrongStateReportsConflict() {
        var absent = stub(waiting.markAbsent(1));
        assertThatThrownBy(() -> service().call(absent.ticketId(), new ClinicQueueCallRequest(2), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("TICKET_STATE_CONFLICT"));
    }

    @Test void sameKeyReplaysSameResult() {
        Map<String, Object> results = new HashMap<>();
        doAnswer(call -> new IdempotentCommandPort.Result<>(
                results.computeIfAbsent(call.getArgument(3), ignored -> ((Supplier<?>) call.getArgument(8)).get()),
                call.getArgument(6), null, false))
                .when(h.idempotency).execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any());
        var first = service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key").data();
        var second = service().call(waiting.ticketId(), new ClinicQueueCallRequest(1), "key").data();
        assertThat(second).isEqualTo(first);
        verify(tickets, times(1)).update(any(), any());
    }
}
