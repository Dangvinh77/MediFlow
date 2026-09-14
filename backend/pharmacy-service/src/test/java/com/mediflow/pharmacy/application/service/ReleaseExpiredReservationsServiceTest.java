package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseClaim;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseRepositoryPort;
import com.mediflow.pharmacy.application.port.out.TransientFailureClassifierPort;

/** Kiểm tra boundary thời gian và khả năng phục hồi từng phần của job hết TTL. */
class ReleaseExpiredReservationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");

    private final StockReservationRepositoryPort reservationRepository = mock(StockReservationRepositoryPort.class);
    private final ExpirePrescriptionTransaction expireTransaction = mock(ExpirePrescriptionTransaction.class);
    private final ReservationExpiryLeaseRepositoryPort leaseRepository = mock(ReservationExpiryLeaseRepositoryPort.class);
    private final TransientFailureClassifierPort transientFailureClassifier = mock(TransientFailureClassifierPort.class);
    private static final UUID LEASE_TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private ReleaseExpiredReservationsService service;

    /** Khởi tạo service với clock cố định để test không phụ thuộc giờ hệ thống. */
    @BeforeEach
    void setUp() {
        service = new ReleaseExpiredReservationsService(
                reservationRepository,
                expireTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3, leaseRepository, "test", Duration.ofMinutes(5), transientFailureClassifier);
        when(leaseRepository.tryAcquire(any(), any(), any(), any())).thenReturn(
                java.util.Optional.of(new ReservationExpiryLeaseClaim(null, LEASE_TOKEN)));
    }

    /** Một transaction lỗi không được làm mất cơ hội xử lý các đơn còn lại trong batch. */
    @Test
    void releaseExpired_oneFailure_continuesRemainingCandidates() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(reservationRepository.findExpiredPrescriptionIdsAfter(NOW, null, 3))
                .thenReturn(List.of(first, second, third));
        when(expireTransaction.expire(first, NOW)).thenReturn(2);
        when(expireTransaction.expire(second, NOW))
                .thenThrow(new IllegalStateException("poison-data"));
        when(expireTransaction.expire(third, NOW)).thenReturn(1);

        int released = service.releaseExpiredReservations();

        assertThat(released).isEqualTo(3);
        verify(expireTransaction).expire(first, NOW);
        verify(expireTransaction).expire(second, NOW);
        verify(expireTransaction).expire(third, NOW);
    }

    /** Batch rỗng phải trả nhanh và vẫn truyền đúng mốc thời gian cùng giới hạn query. */
    @Test
    void releaseExpired_emptyBatch_returnsZero() {
        when(reservationRepository.findExpiredPrescriptionIdsAfter(NOW, null, 3)).thenReturn(List.of());

        assertThat(service.releaseExpiredReservations()).isZero();
        verify(reservationRepository).findExpiredPrescriptionIdsAfter(NOW, null, 3);
    }

    /** Cursor advances even after a poison aggregate and wraps after reaching the final page. */
    @Test
    void releaseExpired_poisonAggregate_nextRunContinuesThenWraps() {
        UUID poison = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        when(leaseRepository.tryAcquire(any(), any(), any(), any()))
                .thenReturn(java.util.Optional.of(new ReservationExpiryLeaseClaim(null, LEASE_TOKEN)))
                .thenReturn(java.util.Optional.of(new ReservationExpiryLeaseClaim(poison, LEASE_TOKEN)))
                .thenReturn(java.util.Optional.of(new ReservationExpiryLeaseClaim(later, LEASE_TOKEN)));
        when(reservationRepository.findExpiredPrescriptionIdsAfter(NOW, null, 3))
                .thenReturn(List.of(poison));
        when(reservationRepository.findExpiredPrescriptionIdsAfter(NOW, poison, 3))
                .thenReturn(List.of(later));
        when(reservationRepository.findExpiredPrescriptionIdsAfter(NOW, later, 3))
                .thenReturn(List.of());
        when(expireTransaction.expire(poison, NOW)).thenThrow(new IllegalStateException("poison"));
        when(expireTransaction.expire(later, NOW)).thenReturn(1);

        assertThat(service.releaseExpiredReservations()).isZero();
        assertThat(service.releaseExpiredReservations()).isEqualTo(1);
        assertThat(service.releaseExpiredReservations()).isZero();

        verify(reservationRepository).findExpiredPrescriptionIdsAfter(NOW, poison, 3);
        verify(reservationRepository).findExpiredPrescriptionIdsAfter(NOW, later, 3);
    }

    /** Infrastructure lock failures abort the batch so the durable cursor remains retryable. */
    @Test
    void releaseExpired_transientLockFailure_doesNotAdvanceCursor() {
        UUID candidate = UUID.randomUUID();
        when(reservationRepository.findExpiredPrescriptionIdsAfter(NOW, null, 3))
                .thenReturn(List.of(candidate));
        when(expireTransaction.expire(candidate, NOW))
                .thenThrow(new CannotAcquireLockException("deadlock"));
        when(transientFailureClassifier.isTransient(any(CannotAcquireLockException.class))).thenReturn(true);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                service::releaseExpiredReservations)).isInstanceOf(CannotAcquireLockException.class);
        org.mockito.Mockito.verify(leaseRepository, org.mockito.Mockito.never())
                .advance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    /** Không cho phép cấu hình batch bằng không hoặc âm vì sẽ làm scheduler không tiến triển. */
    @Test
    void constructor_nonPositiveBatch_rejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReleaseExpiredReservationsService(
                reservationRepository,
                expireTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC),
                0, leaseRepository, "test", Duration.ofMinutes(5), transientFailureClassifier));
    }
}
