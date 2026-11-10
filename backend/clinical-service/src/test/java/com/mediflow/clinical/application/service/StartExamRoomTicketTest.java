package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.request.RegistrationCancellationCommand;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class StartExamRoomTicketTest {
    private final IntakeHarness h = new IntakeHarness();
    private final ClinicQueueRepositoryPort tickets = mock(ClinicQueueRepositoryPort.class);
    private ConsultationSession session;
    private OutpatientRegistration registration;
    private Appointment appointment;

    @SuppressWarnings("unchecked")
    private OutpatientRegistrationApplicationService service(boolean flag) {
        ObjectProvider<ClinicQueueRepositoryPort> provider = mock(ObjectProvider.class);
        lenient().when(provider.getIfAvailable()).thenReturn(flag ? tickets : null);
        return new OutpatientRegistrationApplicationService(h.sessions, h.registrations, h.appointments, h.patients, h.authority, h.actors, h.outbox,
                h.correlationIds, h.idempotency, h.clock, h.records, h.recommendations, new ClinicIntakeSettings(flag), provider);
    }

    @BeforeEach void setUp() {
        h.actAsDoctor();
        session = h.withCounts(h.session(), 0, 0, 1, 0);
        registration = h.stub(h.arrivedConfirmed(session));
        appointment = h.stubAppointment(registration, session);
        appointment.confirmScheduledPayment(registration.reservation().clearanceId(), IntakeHarness.NOW);
        lenient().when(h.appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(tickets.findActiveByRegistration(any())).thenReturn(Optional.empty());
    }

    private ClinicQueueTicket ticket() {
        var ticket = ClinicQueueTicket.issue(UUID.randomUUID(), registration.registrationId(), session, 2, IntakeHarness.ACTOR, null, 7L, IntakeHarness.NOW);
        return stub(ticket);
    }

    private ClinicQueueTicket stub(ClinicQueueTicket ticket) {
        lenient().when(tickets.findActiveByRegistration(registration.registrationId())).thenReturn(Optional.of(ticket));
        lenient().when(tickets.findByIdForUpdate(ticket.ticketId())).thenReturn(Optional.of(ticket));
        return ticket;
    }

    private ClinicQueueTicket savedTicket() {
        var saved = ArgumentCaptor.forClass(ClinicQueueTicket.class);
        verify(tickets).update(any(), saved.capture());
        return saved.getValue();
    }

    @Test void flagOnRequiresRoomTicket() {
        assertThatThrownBy(() -> service(true).startExamination(appointment, IntakeHarness.NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("CLINICAL_ROOM_TICKET_REQUIRED"));
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
        verify(h.sessions, never()).updateCounts(any(), any(), any());
    }

    @Test void absentTicketCannotStartExam() {
        stub(ticket().markAbsent(1));
        assertThatThrownBy(() -> service(true).startExamination(appointment, IntakeHarness.NOW))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("TICKET_STATE_CONFLICT"));
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void waitingAndCalledTicketsCanStart(boolean called) {
        var ticket = ticket();
        if (called) stub(ticket.call(1, IntakeHarness.ACTOR, IntakeHarness.NOW));
        service(true).startExamination(appointment, IntakeHarness.NOW);
        assertThat(savedTicket().status()).isEqualTo(ClinicQueueTicketStatus.IN_EXAM);
        verify(h.registrations).update(any(), any(), any(), any(), eq("EXAM_STARTED"), any());
    }

    @Test void missingVitalSignsDoesNotBlockExam() {
        ticket();
        service(true).startExamination(appointment, IntakeHarness.NOW);
        verify(h.registrations).update(any(), any(), any(), any(), eq("EXAM_STARTED"), any());
    }

    @Test void flagOffStartsWithoutTicket() {
        service(false).startExamination(appointment, IntakeHarness.NOW);
        verify(h.registrations).update(any(), any(), any(), any(), eq("EXAM_STARTED"), any());
        verifyNoInteractions(tickets);
    }

    @Test void replayedStartDoesNotFail() {
        var inExam = h.stub(registration.startExam(IntakeHarness.NOW));
        var started = h.stubAppointment(inExam, session);
        started.confirmScheduledPayment(inExam.reservation().clearanceId(), IntakeHarness.NOW);
        started.startScheduledExam(inExam.reservation().clearanceId(), IntakeHarness.NOW);
        service(true).startExamination(started, IntakeHarness.NOW);
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
        verify(tickets, never()).update(any(), any());
    }

    @Test void completingExamCompletesTicket() {
        var inExam = h.stub(registration.startExam(IntakeHarness.NOW));
        stub(ticket().startExam(IntakeHarness.NOW));
        var started = h.stubAppointment(inExam, session);
        service(true).completeExamination(started, IntakeHarness.NOW);
        assertThat(savedTicket().status()).isEqualTo(ClinicQueueTicketStatus.COMPLETED);
    }

    @Test void completingExamStartedBeforeTheFlagNeedsNoTicket() {
        var inExam = h.stub(registration.startExam(IntakeHarness.NOW));
        var started = h.stubAppointment(inExam, session);
        service(true).completeExamination(started, IntakeHarness.NOW);
        verify(h.registrations).update(any(), any(), any(), any(), eq("COMPLETED"), any());
        verify(tickets, never()).update(any(), any());
    }

    @Test void cancellingRegistrationCancelsTicketWithReason() {
        h.actAsNurse();
        ticket();
        service(true).cancel(registration.registrationId(), new RegistrationCancellationCommand(registration.revision(), "Bệnh nhân đổi ý"), "key");
        var saved = savedTicket();
        assertThat(saved.status()).isEqualTo(ClinicQueueTicketStatus.CANCELLED);
        assertThat(saved.cancelReason()).isEqualTo("Bệnh nhân đổi ý");
    }

    @Test void flagOffCancelLeavesTicketsAlone() {
        h.actAsNurse();
        service(false).cancel(registration.registrationId(), new RegistrationCancellationCommand(registration.revision(), "Đổi ý"), "key");
        verifyNoInteractions(tickets);
    }
}
