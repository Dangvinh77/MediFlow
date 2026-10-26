package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutpatientRegistrationTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");

    @Test
    void counterRegistrationStartsCheckedInWithFifteenMinuteHold() {
        OutpatientRegistration registration = registration(OutpatientReceptionMode.AT_COUNTER);

        assertThat(registration.status()).isEqualTo(OutpatientRegistrationStatus.AWAITING_PAYMENT);
        assertThat(registration.reservation().status()).isEqualTo(OutpatientReservationStatus.HELD);
        assertThat(registration.checkedInAt()).isEqualTo(NOW);
        assertThat(registration.reservation().expiresAt()).isEqualTo(NOW.plusSeconds(15 * 60));
    }

    @Test
    void prebookedRegistrationRequiresArrivalAndArrivalIsRecordedOnlyOnce() {
        OutpatientRegistration registration = registration(OutpatientReceptionMode.PREBOOKED);

        assertThat(registration.checkedInAt()).isNull();
        OutpatientRegistration arrived = registration.arrive(1, NOW.plusSeconds(10));
        assertThat(arrived.checkedInAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(arrived.revision()).isEqualTo(2);
        assertThatThrownBy(() -> arrived.arrive(2, NOW.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paymentResolutionConfirmsOnceAndClosedHoldNeverReopens() {
        OutpatientRegistration locked = registration(OutpatientReceptionMode.AT_COUNTER)
                .acquirePayment(1, id(5), 1, NOW.plusSeconds(5));
        OutpatientRegistration confirmed = locked.resolveCompleted(
                id(5), 1, 1, id(7), id(8), NOW.plusSeconds(7), false);

        assertThat(confirmed.status()).isEqualTo(OutpatientRegistrationStatus.CONFIRMED);
        assertThat(confirmed.reservation().status()).isEqualTo(OutpatientReservationStatus.CONFIRMED);
        assertThat(confirmed.queueNumber()).isEqualTo(1);
        assertThat(confirmed.revision()).isEqualTo(3);
        assertThat(confirmed.resolveCompleted(
                id(5), 1, 1, id(7), id(8), NOW.plusSeconds(7), false)).isEqualTo(confirmed);

        OutpatientRegistration closed = locked.resolveClosedNotPaid(id(5), 1, NOW.plusSeconds(20), true);
        assertThat(closed.status()).isEqualTo(OutpatientRegistrationStatus.EXPIRED);
        assertThat(closed.reservation().status()).isEqualTo(OutpatientReservationStatus.EXPIRED);
        assertThatThrownBy(() -> closed.acquirePayment(2, id(5), 1, NOW.plusSeconds(21)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void latePaymentOutcomeKeepsEvidenceForReconciliationWithoutQueueNumber() {
        OutpatientRegistration locked = registration(OutpatientReceptionMode.PREBOOKED)
                .acquirePayment(1, id(5), 1, NOW.plusSeconds(5));
        OutpatientRegistration late = locked.resolveCompleted(
                id(5), 1, 1, id(7), id(8), NOW.plusSeconds(10), true);

        assertThat(late.status()).isEqualTo(OutpatientRegistrationStatus.RECONCILIATION_REQUIRED);
        assertThat(late.reservation().status()).isEqualTo(OutpatientReservationStatus.RECONCILIATION_REQUIRED);
        assertThat(late.queueNumber()).isNull();
        assertThat(late.reservation().transactionId()).isEqualTo(id(7));
    }

    @Test
    void staleRevisionAndWrongFenceCannotChangeRegistration() {
        OutpatientRegistration locked = registration(OutpatientReceptionMode.AT_COUNTER)
                .acquirePayment(1, id(5), 2, NOW.plusSeconds(5));

        assertThatThrownBy(() -> locked.arrive(1, NOW.plusSeconds(6)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> locked.resolveCompleted(
                id(5), 1, 1, id(7), id(8), NOW.plusSeconds(7), false))
                .isInstanceOf(IllegalStateException.class);
    }

    private static OutpatientRegistration registration(OutpatientReceptionMode mode) {
        ConsultationSession session = ConsultationSession.open(id(4), id(1), id(2), id(3),
                LocalDate.of(2026, 10, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(), 5);
        return OutpatientRegistration.create(id(10), id(11), id(12), session, mode, NOW);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
