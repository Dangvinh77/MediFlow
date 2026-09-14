package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseClaim;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;

/** Verifies durable cursor/lease semantics independently of PostgreSQL wiring. */
class ReleaseExpiredReservationsLeaseTest {

    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");
    private static final UUID TOKEN_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TOKEN_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    /** A second replica skips the batch while the first replica owns the lease. */
    @Test
    void twoSchedulers_onlyLeaseOwnerProcessesBatch() {
        StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
        ExpirePrescriptionTransaction expiry = mock(ExpirePrescriptionTransaction.class);
        ReservationExpiryLeaseRepositoryPort leases = mock(ReservationExpiryLeaseRepositoryPort.class);
        UUID prescriptionId = UUID.randomUUID();
        when(leases.tryAcquire(
                ReleaseExpiredReservationsService.EXPIRY_JOB_NAME, "replica-a", NOW, Duration.ofMinutes(5)))
                .thenReturn(Optional.of(new ReservationExpiryLeaseClaim(null, TOKEN_A)));
        when(leases.tryAcquire(
                ReleaseExpiredReservationsService.EXPIRY_JOB_NAME, "replica-b", NOW, Duration.ofMinutes(5)))
                .thenReturn(Optional.empty());
        when(reservations.findExpiredPrescriptionIdsAfter(NOW, null, 10))
                .thenReturn(List.of(prescriptionId));
        when(expiry.expire(prescriptionId, NOW)).thenReturn(1);

        ReleaseExpiredReservationsService first = service(reservations, expiry, leases, "replica-a");
        ReleaseExpiredReservationsService second = service(reservations, expiry, leases, "replica-b");

        assertThat(first.releaseExpiredReservations()).isEqualTo(1);
        assertThat(second.releaseExpiredReservations()).isZero();
        verify(expiry).expire(prescriptionId, NOW);
        verify(leases).advance(
                ReleaseExpiredReservationsService.EXPIRY_JOB_NAME, "replica-a", TOKEN_A, prescriptionId, NOW);
    }

    /** A cursor survives a new service instance and wraps to the first page after the tail. */
    @Test
    void cursorSurvivesRestart_andWrapsAfterTail() {
        StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
        ExpirePrescriptionTransaction expiry = mock(ExpirePrescriptionTransaction.class);
        ReservationExpiryLeaseRepositoryPort leases = mock(ReservationExpiryLeaseRepositoryPort.class);
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(leases.tryAcquire(
                ReleaseExpiredReservationsService.EXPIRY_JOB_NAME, "replica", NOW, Duration.ofMinutes(5)))
                .thenReturn(Optional.of(new ReservationExpiryLeaseClaim(previous, TOKEN_A)))
                .thenReturn(Optional.of(new ReservationExpiryLeaseClaim(next, TOKEN_B)));
        when(reservations.findExpiredPrescriptionIdsAfter(NOW, previous, 10)).thenReturn(List.of(next));
        when(reservations.findExpiredPrescriptionIdsAfter(NOW, next, 10)).thenReturn(List.of());
        when(reservations.findExpiredPrescriptionIdsAfter(NOW, null, 10)).thenReturn(List.of(previous));
        when(expiry.expire(next, NOW)).thenReturn(1);
        when(expiry.expire(previous, NOW)).thenReturn(1);

        ReleaseExpiredReservationsService service = service(reservations, expiry, leases, "replica");

        assertThat(service.releaseExpiredReservations()).isEqualTo(1);
        assertThat(service.releaseExpiredReservations()).isEqualTo(1);
        verify(reservations).findExpiredPrescriptionIdsAfter(NOW, null, 10);
    }

    private ReleaseExpiredReservationsService service(
            StockReservationRepositoryPort reservations,
            ExpirePrescriptionTransaction expiry,
            ReservationExpiryLeaseRepositoryPort leases,
            String owner) {
        return new ReleaseExpiredReservationsService(
                reservations, expiry, Clock.fixed(NOW, ZoneOffset.UTC), 10, leases, owner,
                Duration.ofMinutes(5));
    }
}
