package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegistrationIntakeRegressionTest {
    private final IntakeHarness h = new IntakeHarness();
    private ConsultationSession session;

    @BeforeEach void signIn() {
        h.actAsNurse();
        session = h.session();
    }

    private RegistrationCreateRequest request(UUID patient, ConsultationSession target, OutpatientReceptionMode mode) {
        return new RegistrationCreateRequest(patient, target.sessionId(), target.revision(), mode,
                OutpatientVisitType.NEW_VISIT, null, "Khám");
    }

    /** The real receipt store's contract: one key replays one result and refuses a different request. */
    private void replayingIdempotency() {
        Map<String, String> fingerprints = new HashMap<>();
        Map<String, Object> results = new HashMap<>();
        org.mockito.Mockito.doAnswer(call -> {
                    String key = call.getArgument(3);
                    String fingerprint = call.getArgument(4);
                    if (fingerprints.containsKey(key) && !fingerprints.get(key).equals(fingerprint)) {
                        throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Key reused for another request");
                    }
                    fingerprints.put(key, fingerprint);
                    Object result = results.computeIfAbsent(key, ignored -> ((Supplier<?>) call.getArgument(8)).get());
                    return new IdempotentCommandPort.Result<>(result, call.getArgument(6), null, false);
                }).when(h.idempotency).execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any());
    }

    private void makeActorDepartmentHead(ActorIdentity actor) {
        when(h.authority.authority(any(), any(), any())).thenAnswer(call -> new SchedulingAuthoritySnapshot(
                actor.accountId(), true, true, "DOCTOR", actor.staffId(), true, call.getArgument(1), true, true, 7L, IntakeHarness.NOW));
    }

    private void afterHoldExpired() {
        h.clock = Clock.fixed(IntakeHarness.NOW.plusSeconds(15 * 60), ZoneOffset.UTC);
    }

    @Test void eligibilityIsRecheckedAtSubmission() {
        when(h.authority.consultationEligibility(any(), any(), any(), any()))
                .thenReturn(h.eligibility(h.doctorId, h.departmentId, h.roomId, false));
        assertThatThrownBy(() -> h.registrationService(true).create(request(UUID.randomUUID(), session, OutpatientReceptionMode.AT_COUNTER), "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("SCHEDULE_INELIGIBLE"));
        verify(h.registrations, never()).insert(any(), any(), any(), any(), any());
    }

    @Test void counterRegistrationIsArrivedButPrebookedIsNot() {
        var counter = h.registrationService(true).create(request(UUID.randomUUID(), session, OutpatientReceptionMode.AT_COUNTER), "a").data();
        var prebooked = h.registrationService(true).create(request(UUID.randomUUID(), session, OutpatientReceptionMode.PREBOOKED), "b").data();
        assertThat(counter.checkedInAt()).isNotNull();
        assertThat(prebooked.checkedInAt()).isNull();
    }

    @Test void paidPrebookedIsNotTreatedAsArrived() {
        var paid = h.stub(h.confirmed(session, OutpatientReceptionMode.PREBOOKED));
        var dto = h.registrationService(true).get(paid.registrationId());
        assertThat(dto.status()).isEqualTo(OutpatientRegistrationStatus.CONFIRMED);
        assertThat(dto.readyForExam()).isFalse();
    }

    @Test void heldReservationExpiresAndReleasesSeat() {
        var occupied = h.withCounts(session, 1, 0, 0, 0);
        var held = h.stub(h.held(occupied, OutpatientReceptionMode.PREBOOKED));
        afterHoldExpired();
        h.registrationService(true).expireHeldReservation(held.registrationId());

        var saved = ArgumentCaptor.forClass(com.mediflow.clinical.domain.model.OutpatientRegistration.class);
        verify(h.registrations).update(eq(held), saved.capture(), any(), any(), eq("EXPIRED"), any());
        assertThat(saved.getValue().status()).isEqualTo(OutpatientRegistrationStatus.EXPIRED);
        var counts = ArgumentCaptor.forClass(ConsultationSession.class);
        verify(h.sessions).updateCounts(eq(occupied), counts.capture(), any());
        assertThat(counts.getValue().counts().heldCount()).isZero();
        assertThat(counts.getValue().counts().availableCount()).isEqualTo(occupied.counts().availableCount() + 1);
        verify(h.outbox).appendRawOutpatientEvent(any());
    }

    private com.mediflow.clinical.domain.model.OutpatientRegistration heldThenMoved(ConsultationSession occupied) {
        var held = h.stub(h.held(occupied, OutpatientReceptionMode.PREBOOKED));
        var target = h.session(UUID.randomUUID(), UUID.randomUUID(), IntakeHarness.TODAY, 20);
        var moved = held.reschedule(held.revision(), target, null, false, IntakeHarness.NOW);
        when(h.registrations.findByIdForUpdate(held.registrationId())).thenReturn(java.util.Optional.of(moved));
        return held;
    }

    @Test void holdMovedBeforeItsLockGivesNoSeatBackToTheOldSession() {
        var held = heldThenMoved(h.withCounts(session, 1, 0, 0, 0));
        afterHoldExpired();
        assertThatThrownBy(() -> h.registrationService(true).expireHeldReservation(held.registrationId()))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("REVISION_CONFLICT"));
        verify(h.sessions, never()).updateCounts(any(), any(), any());
    }

    @Test void paymentAgainstAHoldMovedBeforeItsLockIsRefused() {
        var held = heldThenMoved(h.withCounts(session, 1, 0, 0, 0));
        var command = new com.mediflow.clinical.application.dto.request.PaymentLockCommand(UUID.randomUUID(), UUID.randomUUID(),
                held.registrationId(), held.patientId(), "EXAM", held.registrationId(), held.registrationId(),
                held.reservation().revision(), "PATIENT_PAYMENT", null);
        assertThatThrownBy(() -> h.registrationService(true).acquirePayment(held.reservation().reservationId(), command))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("REVISION_CONFLICT"));
        verify(h.sessions, never()).updateCounts(any(), any(), any());
    }

    @Test void holdStillValidIsNotExpired() {
        var occupied = h.withCounts(session, 1, 0, 0, 0);
        var held = h.stub(h.held(occupied, OutpatientReceptionMode.PREBOOKED));
        h.registrationService(true).expireHeldReservation(held.registrationId());
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test void paymentLockedReservationIsNotReleasedByExpiry() {
        var occupied = h.withCounts(session, 0, 1, 0, 0);
        var locked = h.stub(h.paymentLocked(occupied, UUID.randomUUID()));
        afterHoldExpired();
        h.registrationService(true).expireHeldReservation(locked.registrationId());
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
        verify(h.sessions, never()).updateCounts(any(), any(), any());
    }

    @Test void expiryWorkerSweepsDueHoldsAndSurvivesAFailure() {
        var service = org.mockito.Mockito.mock(OutpatientRegistrationApplicationService.class);
        UUID broken = UUID.randomUUID();
        UUID due = UUID.randomUUID();
        when(h.registrations.findExpiredHeld(IntakeHarness.NOW, 50)).thenReturn(List.of(broken, due));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(service).expireHeldReservation(broken);
        new OutpatientHoldExpiryWorker(h.registrations, service, h.clock).runOnce();
        verify(service).expireHeldReservation(due);
    }

    @Test void sameKeyReturnsSameRegistration() {
        replayingIdempotency();
        UUID patient = UUID.randomUUID();
        var service = h.registrationService(true);
        var first = service.create(request(patient, session, OutpatientReceptionMode.AT_COUNTER), "key").data();
        var second = service.create(request(patient, session, OutpatientReceptionMode.AT_COUNTER), "key").data();
        assertThat(second.registrationId()).isEqualTo(first.registrationId());
        verify(h.registrations, org.mockito.Mockito.times(1)).insert(any(), any(), any(), any(), any());
    }

    @Test void sameKeyDifferentPatientIsRejected() {
        replayingIdempotency();
        var service = h.registrationService(true);
        service.create(request(UUID.randomUUID(), session, OutpatientReceptionMode.AT_COUNTER), "key");
        assertThatThrownBy(() -> service.create(request(UUID.randomUUID(), session, OutpatientReceptionMode.AT_COUNTER), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test void sameKeyDifferentSessionIsRejected() {
        replayingIdempotency();
        UUID patient = UUID.randomUUID();
        var service = h.registrationService(true);
        service.create(request(patient, session, OutpatientReceptionMode.AT_COUNTER), "key");
        var other = h.session();
        assertThatThrownBy(() -> service.create(request(patient, other, OutpatientReceptionMode.AT_COUNTER), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test void samePatientMayRegisterAnotherSessionSameDay() {
        UUID patient = UUID.randomUUID();
        var other = h.session();
        when(h.registrations.existsActivePatientSession(patient, session.sessionId())).thenReturn(true);
        var dto = h.registrationService(true).create(request(patient, other, OutpatientReceptionMode.AT_COUNTER), "key");
        assertThat(dto.data().patientId()).isEqualTo(patient);
    }

    @Test void samePatientSameSessionIsDuplicate() {
        UUID patient = UUID.randomUUID();
        when(h.registrations.existsActivePatientSession(patient, session.sessionId())).thenReturn(true);
        assertThatThrownBy(() -> h.registrationService(true).create(request(patient, session, OutpatientReceptionMode.AT_COUNTER), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("ACTIVE_REGISTRATION_EXISTS"));
    }

    @Test void departmentHeadCanViewButCannotStartAnotherDoctorsExam() {
        var head = h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        makeActorDepartmentHead(head);
        when(h.registrations.findBySession(eq(session.sessionId()), any())).thenReturn(PageResult.of(List.of(), 0, 0, 20));
        var service = h.registrationService(true);
        assertThat(service.sessionRegistrations(session.sessionId(), PageQuery.of(0, 20)).content()).isEmpty();

        var registration = h.stub(h.arrivedConfirmed(session));
        var appointment = h.stubAppointment(registration, session);
        assertThatThrownBy(() -> service.startExamination(appointment, IntakeHarness.NOW)).isInstanceOf(ResourceNotFoundException.class);
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test void transferredDoctorLosesStartExamImmediately() {
        h.actAs("DOCTOR", h.doctorId, h.otherDepartmentId);
        var registration = h.stub(h.arrivedConfirmed(session));
        var appointment = h.stubAppointment(registration, session);
        assertThatThrownBy(() -> h.registrationService(true).startExamination(appointment, IntakeHarness.NOW))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }
}
