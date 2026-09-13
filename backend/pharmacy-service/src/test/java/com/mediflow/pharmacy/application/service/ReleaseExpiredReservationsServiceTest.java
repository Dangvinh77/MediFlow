package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;

/** Kiểm tra boundary thời gian và khả năng phục hồi từng phần của job hết TTL. */
class ReleaseExpiredReservationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");

    private final StockReservationRepositoryPort reservationRepository = mock(StockReservationRepositoryPort.class);
    private final ExpirePrescriptionTransaction expireTransaction = mock(ExpirePrescriptionTransaction.class);

    private ReleaseExpiredReservationsService service;

    /** Khởi tạo service với clock cố định để test không phụ thuộc giờ hệ thống. */
    @BeforeEach
    void setUp() {
        service = new ReleaseExpiredReservationsService(
                reservationRepository,
                expireTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3);
    }

    /** Một transaction lỗi không được làm mất cơ hội xử lý các đơn còn lại trong batch. */
    @Test
    void releaseExpired_oneFailure_continuesRemainingCandidates() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(reservationRepository.findExpiredPrescriptionIds(NOW, 3))
                .thenReturn(List.of(first, second, third));
        when(expireTransaction.expire(first, NOW)).thenReturn(2);
        when(expireTransaction.expire(second, NOW))
                .thenThrow(new IllegalStateException("deadlock"));
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
        when(reservationRepository.findExpiredPrescriptionIds(NOW, 3)).thenReturn(List.of());

        assertThat(service.releaseExpiredReservations()).isZero();
        verify(reservationRepository).findExpiredPrescriptionIds(NOW, 3);
    }

    /** Không cho phép cấu hình batch bằng không hoặc âm vì sẽ làm scheduler không tiến triển. */
    @Test
    void constructor_nonPositiveBatch_rejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReleaseExpiredReservationsService(
                reservationRepository,
                expireTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC),
                0));
    }
}
