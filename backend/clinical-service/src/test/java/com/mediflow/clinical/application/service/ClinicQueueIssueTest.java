package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClinicQueueIssueTest {
    private final IntakeHarness h = new IntakeHarness();
    private final ClinicQueueRepositoryPort tickets = mock(ClinicQueueRepositoryPort.class);
    private ConsultationSession session;
    private OutpatientRegistration registration;

    private ClinicQueueApplicationService service() {
        return new ClinicQueueApplicationService(h.sessions, h.registrations, tickets, h.authority, h.actors, h.correlationIds,
                h.idempotency, h.clock);
    }

    @BeforeEach void setUp() {
        h.actAsNurse();
        session = h.session();
        registration = h.stub(h.arrivedConfirmed(session));
        lenient().when(tickets.findActiveByRegistration(any())).thenReturn(Optional.empty());
        lenient().when(tickets.allocateRoomQueueNumber(any(), any())).thenReturn(1);
        lenient().when(tickets.insert(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void assertDenied(Runnable command, String code) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(ForbiddenOperationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
        verify(tickets, never()).allocateRoomQueueNumber(any(), any());
    }

    private ClinicQueueTicket existing(ClinicQueueTicketStatus status) {
        var ticket = ClinicQueueTicket.issue(UUID.randomUUID(), registration.registrationId(), session, 3, IntakeHarness.ACTOR, null, 7L, IntakeHarness.NOW);
        return status == ClinicQueueTicketStatus.IN_EXAM ? ticket.startExam(IntakeHarness.NOW) : ticket;
    }

    @Test void issuesNextNumberOfDoctorsRoom() {
        when(tickets.allocateRoomQueueNumber(h.roomId, IntakeHarness.TODAY)).thenReturn(4);
        var dto = service().issue(registration.registrationId(), "key").data();
        assertThat(dto.roomId()).isEqualTo(h.roomId);
        assertThat(dto.roomQueueNumber()).isEqualTo(4);
        assertThat(dto.status()).isEqualTo(ClinicQueueTicketStatus.WAITING);
    }

    @Test void resendingSameKeyReturnsSameNumber() {
        Map<String, Object> results = new HashMap<>();
        doAnswer(call -> new IdempotentCommandPort.Result<>(
                results.computeIfAbsent(call.getArgument(3), ignored -> ((Supplier<?>) call.getArgument(8)).get()),
                call.getArgument(6), null, false))
                .when(h.idempotency).execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any());
        var first = service().issue(registration.registrationId(), "key").data();
        var second = service().issue(registration.registrationId(), "key").data();
        assertThat(second).isEqualTo(first);
        verify(tickets, times(1)).allocateRoomQueueNumber(any(), any());
    }

    @Test void differentKeyOnTicketedRegistrationIsRejected() {
        when(tickets.findActiveByRegistration(registration.registrationId())).thenReturn(Optional.of(existing(ClinicQueueTicketStatus.WAITING)));
        assertThatThrownBy(() -> service().issue(registration.registrationId(), "other"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("ROOM_TICKET_EXISTS"));
        verify(tickets, never()).allocateRoomQueueNumber(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"AWAITING_PAYMENT", "NOT_ARRIVED", "NO_CLEARANCE"})
    void requiresConfirmedArrivedAndCleared(String situation) {
        OutpatientRegistration target = switch (situation) {
            case "AWAITING_PAYMENT" -> h.held(session, OutpatientReceptionMode.AT_COUNTER);
            case "NOT_ARRIVED" -> h.confirmed(session, OutpatientReceptionMode.PREBOOKED);
            default -> withoutClearance(h.arrivedConfirmed(session));
        };
        h.stub(target);
        assertThatThrownBy(() -> service().issue(target.registrationId(), "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("ROOM_TICKET_NOT_ELIGIBLE"));
        verify(tickets, never()).allocateRoomQueueNumber(any(), any());
    }

    private OutpatientRegistration withoutClearance(OutpatientRegistration r) {
        var v = r.reservation();
        var noClearance = new OutpatientRegistration.Reservation(v.reservationId(), v.revision(), v.status(), v.expiresAt(), v.paymentOperationId(),
                v.fenceToken(), v.lockedAt(), v.reconcileAfter(), v.transactionId(), null, v.completedAt());
        return new OutpatientRegistration(r.registrationId(), r.revision(), r.patientId(), r.sessionId(), r.receptionMode(), r.visitType(),
                r.status(), r.checkedInAt(), r.queueNumber(), r.createdAt(), r.updatedAt(), noClearance, r.cancellation());
    }

    @Test void onlyOnTheSessionDate() {
        var tomorrow = h.session(h.doctorId, h.roomId, IntakeHarness.TODAY.plusDays(1), 20);
        var future = h.stub(h.arrivedConfirmed(tomorrow));
        assertThatThrownBy(() -> service().issue(future.registrationId(), "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("ROOM_TICKET_WRONG_DATE"));
        verify(tickets, never()).allocateRoomQueueNumber(any(), any());
    }

    @Test void queueDateComesFromSessionNotServerClock() {
        LocalDate nextDay = LocalDate.of(2026, 11, 11);
        h.clock = Clock.fixed(Instant.parse("2026-11-10T23:30:00Z"), ZoneOffset.UTC);
        var morning = h.session(h.doctorId, h.roomId, nextDay, 20);
        var target = h.stub(h.arrivedConfirmed(morning));
        when(tickets.allocateRoomQueueNumber(h.roomId, nextDay)).thenReturn(1);
        assertThat(service().issue(target.registrationId(), "key").data().queueDate()).isEqualTo(nextDay);
    }

    @Test void receptionNurseCannotIssue() {
        h.actAs("NURSE", UUID.randomUUID(), UUID.randomUUID());
        assertDenied(() -> service().issue(registration.registrationId(), "key"), "CLINIC_DESK_SCOPE_DENIED");
    }

    @Test void otherDepartmentNurseCannotIssue() {
        h.actAs("NURSE", UUID.randomUUID(), h.otherDepartmentId);
        assertDenied(() -> service().issue(registration.registrationId(), "key"), "CLINIC_DESK_SCOPE_DENIED");
    }

    @Test void adminCannotIssue() {
        h.actAsManager();
        assertDenied(() -> service().issue(registration.registrationId(), "key"), "CLINIC_DESK_SCOPE_DENIED");
    }

    @Test void nurseTransferredMidShiftIsDeniedImmediately() {
        var service = service();
        assertThat(service.issue(registration.registrationId(), "first").data()).isNotNull();
        h.actAs("NURSE", UUID.randomUUID(), h.otherDepartmentId);
        org.mockito.Mockito.clearInvocations(tickets);
        assertDenied(() -> service.issue(registration.registrationId(), "second"), "CLINIC_DESK_SCOPE_DENIED");
    }

    @Test void coreOutageDeniesTicket() {
        when(h.authority.currentDoctorAuthority(any(), any()))
                .thenThrow(new UpstreamUnavailableException("SCHEDULING_AUTHORITY_UNAVAILABLE", "Core is unavailable"));
        assertThatThrownBy(() -> service().issue(registration.registrationId(), "key"))
                .isInstanceOfSatisfying(UpstreamUnavailableException.class, e -> assertThat(e.getCode()).isEqualTo("SCHEDULING_AUTHORITY_UNAVAILABLE"));
        verify(tickets, never()).allocateRoomQueueNumber(any(), any());
    }

    @Test void returningForResultsDoesNotIssueNewNumber() {
        var inExam = h.stub(registration.startExam(IntakeHarness.NOW));
        when(tickets.findActiveByRegistration(inExam.registrationId())).thenReturn(Optional.of(existing(ClinicQueueTicketStatus.IN_EXAM)));
        assertThatThrownBy(() -> service().issue(inExam.registrationId(), "again"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("ROOM_TICKET_EXISTS"));
        verify(tickets, never()).allocateRoomQueueNumber(any(), any());
    }
}
