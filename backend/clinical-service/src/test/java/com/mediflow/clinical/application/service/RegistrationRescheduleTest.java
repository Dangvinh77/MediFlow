package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RegistrationRescheduleRequest;
import com.mediflow.clinical.application.dto.request.RevisionCommand;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.OutpatientRegistrationFact;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort.RescheduleLog;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ClinicQueueTicket;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegistrationRescheduleTest {
    private final IntakeHarness h = new IntakeHarness();
    private final ClinicQueueRepositoryPort tickets = mock(ClinicQueueRepositoryPort.class);
    private final UUID otherDoctor = UUID.randomUUID();
    private final UUID otherRoom = UUID.randomUUID();
    private ActorIdentity nurse;
    private ConsultationSession source;
    private ConsultationSession target;
    private OutpatientRegistration confirmed;

    private RegistrationRescheduleApplicationService service() {
        return new RegistrationRescheduleApplicationService(h.sessions, h.registrations, h.appointments, tickets, h.registrationService(true),
                h.idempotency, h.clock);
    }

    private RegistrationRescheduleRequest request(OutpatientRegistration r, ConsultationSession to) {
        return new RegistrationRescheduleRequest(r.revision(), to.sessionId(), "Bác sĩ bận đột xuất", true);
    }

    @BeforeEach void setUp() {
        nurse = h.actAsNurse();
        source = h.withCounts(h.session(), 0, 0, 1, 0);
        target = h.session(otherDoctor, otherRoom, IntakeHarness.TODAY, 20);
        confirmed = h.stub(h.arrivedConfirmed(source));
        stubAppointment(confirmed, source);
        lenient().when(tickets.findActiveByRegistration(any())).thenReturn(Optional.empty());
    }

    private Appointment stubAppointment(OutpatientRegistration r, ConsultationSession s) {
        var appointment = h.stubAppointment(r, s);
        if (r.reservation().clearanceId() != null) appointment.confirmScheduledPayment(r.reservation().clearanceId(), IntakeHarness.NOW);
        return appointment;
    }

    private OutpatientRegistration saved() {
        var captor = ArgumentCaptor.forClass(OutpatientRegistration.class);
        verify(h.registrations).update(any(), captor.capture(), any(), any(), eq("RESCHEDULED"), any());
        return captor.getValue();
    }

    private ConsultationSession counts(ConsultationSession session) {
        var captor = ArgumentCaptor.forClass(ConsultationSession.class);
        verify(h.sessions).updateCounts(eq(session), captor.capture(), any());
        return captor.getValue();
    }

    private OutpatientRegistrationFact fact() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEventEnvelope<?>> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(h.outbox).appendRawOutpatientEvent(captor.capture());
        return (OutpatientRegistrationFact) captor.getValue().payload();
    }

    private void assertNothingMoved() {
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
        verify(h.sessions, never()).updateCounts(any(), any(), any());
        verify(h.registrations, never()).allocateDepartmentQueueNumber(any(), any());
    }

    @Test void movesSeatBetweenSessions() {
        var dto = service().reschedule(confirmed.registrationId(), request(confirmed, target), "key").data();
        assertThat(dto.sessionId()).isEqualTo(target.sessionId());
        assertThat(dto.doctorId()).isEqualTo(otherDoctor);
        assertThat(dto.roomId()).isEqualTo(otherRoom);
        assertThat(counts(source).counts().confirmedCount()).isZero();
        assertThat(counts(source).counts().availableCount()).isEqualTo(20);
        assertThat(counts(target).counts().confirmedCount()).isEqualTo(1);
        assertThat(counts(target).counts().availableCount()).isEqualTo(19);
    }

    @Test void heldRegistrationMovesHeldSeat() {
        var occupied = h.withCounts(source, 1, 0, 1, 0);
        var held = h.stub(h.held(occupied, OutpatientReceptionMode.AT_COUNTER));
        stubAppointment(held, occupied);
        service().reschedule(held.registrationId(), request(held, target), "key");
        assertThat(counts(occupied).counts().heldCount()).isZero();
        assertThat(counts(target).counts().heldCount()).isEqualTo(1);
    }

    @Test void targetWithoutSeatLeavesEverythingUnchanged() {
        var full = h.withCounts(h.session(otherDoctor, otherRoom, IntakeHarness.TODAY, 1), 0, 0, 1, 0);
        assertThatThrownBy(() -> service().reschedule(confirmed.registrationId(), request(confirmed, full), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_CAPACITY_EXHAUSTED"));
        assertNothingMoved();
    }

    @Test void retryAfterSuccessReturnsTheStoredResult() {
        var first = service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        h.stub(confirmed.reschedule(confirmed.revision(), target, null, false, IntakeHarness.NOW));
        org.mockito.Mockito.doAnswer(call -> new com.mediflow.clinical.application.port.out.IdempotentCommandPort.Result<>(first.data(), 200, null, true))
                .when(h.idempotency).execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
        var retry = service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.data()).isEqualTo(first.data());
    }

    @Test void requiresPatientConfirmation() {
        var request = new RegistrationRescheduleRequest(confirmed.revision(), target.sessionId(), "Đổi", false);
        assertThatThrownBy(() -> service().reschedule(confirmed.registrationId(), request, "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("RESCHEDULE_CONFIRMATION_REQUIRED"));
        assertNothingMoved();
    }

    private void assertTargetInvalid(ConsultationSession to) {
        assertThatThrownBy(() -> service().reschedule(confirmed.registrationId(), request(confirmed, to), "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("RESCHEDULE_TARGET_INVALID"));
        assertNothingMoved();
    }

    @Test void targetMustBeOpenSameDepartmentAndEligible() {
        assertTargetInvalid(h.stub(h.session(otherDoctor, otherRoom, IntakeHarness.TODAY, 20).changeStatus(1, ConsultationSessionStatus.STOPPED, "Dừng")));
        var foreign = h.stub(ConsultationSession.open(UUID.randomUUID(), otherDoctor, h.otherDepartmentId, otherRoom, IntakeHarness.TODAY,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(11, 0), java.util.List.of(), 20));
        assertTargetInvalid(foreign);
        assertTargetInvalid(source);
        assertTargetInvalid(h.session(otherDoctor, otherRoom, IntakeHarness.TODAY.minusDays(1), 20));
        when(h.authority.consultationEligibility(any(), any(), any(), any())).thenReturn(h.eligibility(otherDoctor, h.departmentId, otherRoom, false));
        assertTargetInvalid(target);
    }

    @Test void rescheduleDuringPaymentLockIsRefused() {
        var occupied = h.withCounts(source, 0, 1, 1, 0);
        var locked = h.stub(h.paymentLocked(occupied, UUID.randomUUID()));
        stubAppointment(locked, occupied);
        assertThatThrownBy(() -> service().reschedule(locked.registrationId(), request(locked, target), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("REGISTRATION_PAYMENT_IN_PROGRESS"));
        assertNothingMoved();
    }

    @Test void examStartedCannotBeRescheduled() {
        var inExam = h.stub(confirmed.startExam(IntakeHarness.NOW));
        assertThatThrownBy(() -> service().reschedule(inExam.registrationId(), request(inExam, target), "key"))
                .isInstanceOf(DuplicateResourceException.class);
        assertNothingMoved();
    }

    @Test void patientAlreadyInTargetIsRefused() {
        when(h.registrations.existsActivePatientSession(confirmed.patientId(), target.sessionId())).thenReturn(true);
        assertThatThrownBy(() -> service().reschedule(confirmed.registrationId(), request(confirmed, target), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("ACTIVE_REGISTRATION_EXISTS"));
        assertNothingMoved();
    }

    @Test void sameDayKeepsCounterNumberAndArrival() {
        service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        assertThat(saved().queueNumber()).isEqualTo(confirmed.queueNumber());
        assertThat(saved().checkedInAt()).isEqualTo(confirmed.checkedInAt());
        verify(h.registrations, never()).allocateDepartmentQueueNumber(any(), any());
    }

    @Test void otherDayGetsNewCounterNumberAndNeedsArrival() {
        LocalDate tomorrow = IntakeHarness.TODAY.plusDays(1);
        var next = h.session(otherDoctor, otherRoom, tomorrow, 20);
        when(h.registrations.allocateDepartmentQueueNumber(h.departmentId, tomorrow)).thenReturn(12);
        service().reschedule(confirmed.registrationId(), request(confirmed, next), "key");
        assertThat(saved().queueNumber()).isEqualTo(12);
        assertThat(saved().checkedInAt()).isNull();
    }

    @Test void unpaidRegistrationGetsNoCounterNumberOnOtherDay() {
        var occupied = h.withCounts(source, 1, 0, 1, 0);
        var held = h.stub(h.held(occupied, OutpatientReceptionMode.PREBOOKED));
        stubAppointment(held, occupied);
        var next = h.session(otherDoctor, otherRoom, IntakeHarness.TODAY.plusDays(1), 20);
        service().reschedule(held.registrationId(), request(held, next), "key");
        assertThat(saved().queueNumber()).isNull();
        verify(h.registrations, never()).allocateDepartmentQueueNumber(any(), any());
    }

    @Test void activeRoomTicketIsCancelledWithReason() {
        var ticket = ClinicQueueTicket.issue(UUID.randomUUID(), confirmed.registrationId(), source, 3, IntakeHarness.ACTOR, null, 7L, IntakeHarness.NOW);
        when(tickets.findActiveByRegistration(confirmed.registrationId())).thenReturn(Optional.of(ticket));
        when(tickets.findByIdForUpdate(ticket.ticketId())).thenReturn(Optional.of(ticket));
        service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        var captor = ArgumentCaptor.forClass(ClinicQueueTicket.class);
        verify(tickets).update(eq(ticket), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ClinicQueueTicketStatus.CANCELLED);
        assertThat(captor.getValue().cancelReason()).isEqualTo("Bác sĩ bận đột xuất");
        assertThat(captor.getValue().cancelledByAccountId()).isEqualTo(nurse.accountId());
    }

    @Test void writesOldNewActorReasonAndConfirmationToLog() {
        service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        var captor = ArgumentCaptor.forClass(RescheduleLog.class);
        verify(h.registrations).appendRescheduleLog(captor.capture());
        var log = captor.getValue();
        assertThat(log.oldSessionId()).isEqualTo(source.sessionId());
        assertThat(log.newSessionId()).isEqualTo(target.sessionId());
        assertThat(log.oldDoctorId()).isEqualTo(h.doctorId);
        assertThat(log.newDoctorId()).isEqualTo(otherDoctor);
        assertThat(log.oldRoomId()).isEqualTo(h.roomId);
        assertThat(log.newRoomId()).isEqualTo(otherRoom);
        assertThat(log.actorAccountId()).isEqualTo(nurse.accountId());
        assertThat(log.actorStaffId()).isEqualTo(nurse.staffId());
        assertThat(log.authorityScopeRevision()).isEqualTo(7L);
        assertThat(log.reason()).isEqualTo("Bác sĩ bận đột xuất");
    }

    @Test void appointmentFollowsNewDoctorAndDate() {
        var appointment = h.appointments.findByIdForUpdate(confirmed.registrationId()).orElseThrow();
        LocalDate tomorrow = IntakeHarness.TODAY.plusDays(1);
        service().reschedule(confirmed.registrationId(), request(confirmed, h.session(otherDoctor, otherRoom, tomorrow, 20)), "key");
        assertThat(appointment.getDoctorId()).isEqualTo(otherDoctor);
        assertThat(appointment.getAppointmentDate()).isEqualTo(tomorrow);
        assertThat(appointment.getCheckedInAt()).isNull();
        verify(h.appointments).save(appointment);
    }

    @Test void doesNotTouchMedicalRecordAuthor() {
        service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        verifyNoInteractions(h.records);
    }

    @Test void clearanceStaysBoundToSameRegistration() {
        service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        assertThat(saved().registrationId()).isEqualTo(confirmed.registrationId());
        assertThat(saved().reservation().clearanceId()).isEqualTo(confirmed.reservation().clearanceId());
        assertThat(saved().reservation().transactionId()).isEqualTo(confirmed.reservation().transactionId());
    }

    @Test void factCarriesRescheduleOnlyOnThisRevision() {
        var prebooked = h.stub(h.confirmed(source, OutpatientReceptionMode.PREBOOKED));
        stubAppointment(prebooked, source);
        service().reschedule(prebooked.registrationId(), request(prebooked, target), "key");
        var moved = fact();
        assertThat(moved.reschedule().previousSessionId()).isEqualTo(source.sessionId());
        assertThat(moved.reschedule().previousDoctorId()).isEqualTo(h.doctorId);
        assertThat(moved.reschedule().reason()).isEqualTo("Bác sĩ bận đột xuất");
        assertThat(moved.doctorId()).isEqualTo(otherDoctor);

        var after = h.stub(saved());
        stubAppointment(after, target);
        clearInvocations(h.outbox);
        h.registrationService(true).arrive(after.registrationId(), new RevisionCommand(after.revision()), "arrive");
        assertThat(fact().reschedule()).isNull();
    }

    @Test void locksSessionsInUuidOrder() {
        service().reschedule(confirmed.registrationId(), request(confirmed, target), "key");
        boolean sourceFirst = source.sessionId().compareTo(target.sessionId()) < 0;
        var order = inOrder(h.sessions);
        order.verify(h.sessions).findByIdForUpdate(sourceFirst ? source.sessionId() : target.sessionId());
        order.verify(h.sessions).findByIdForUpdate(sourceFirst ? target.sessionId() : source.sessionId());
    }

    @Test void flagOffEndpointIsAbsent() {
        new ApplicationContextRunner()
                .withBean(RegistrationRescheduleApplicationService.class, () -> mock(RegistrationRescheduleApplicationService.class))
                .withBean(com.mediflow.clinical.application.port.out.CorrelationIdProvider.class, () -> mock(com.mediflow.clinical.application.port.out.CorrelationIdProvider.class))
                .withUserConfiguration(com.mediflow.clinical.web.RegistrationRescheduleController.class)
                .withPropertyValues("mediflow.clinical.scheduling.enabled=true", "mediflow.v2.clinic-intake=false")
                .run(context -> assertThat(context).doesNotHaveBean(com.mediflow.clinical.web.RegistrationRescheduleController.class));
    }
}
