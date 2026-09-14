package com.mediflow.pharmacy.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mediflow.pharmacy.application.port.in.ReleaseExpiredReservationsUseCase;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseClaim;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseRepositoryPort;
import com.mediflow.pharmacy.application.port.out.TransientFailureClassifierPort;

/**
 * Tìm các prescription có giữ chỗ quá TTL và xử lý theo batch giới hạn.
 *
 * <p>Query đầu tiên chỉ tìm mã ứng viên. Mỗi mã sau đó được khóa và kiểm tra lại trong
 * {@link ExpirePrescriptionTransaction}; vì vậy kết quả vẫn an toàn nếu có thao tác hủy hoặc
 * xuất thuốc xen vào giữa hai bước.</p>
 */
public class ReleaseExpiredReservationsService implements ReleaseExpiredReservationsUseCase {

    /** Stable key so every pharmacy replica shares one cursor/lease row. */
    public static final String EXPIRY_JOB_NAME = "reservation-expiry";

    private static final Logger log = LoggerFactory.getLogger(ReleaseExpiredReservationsService.class);

    private final StockReservationRepositoryPort reservationRepository;
    private final ExpirePrescriptionTransaction expireTransaction;
    private final Clock clock;
    private final int batchSize;
    private final ReservationExpiryLeaseRepositoryPort leaseRepository;
    private final TransientFailureClassifierPort transientFailureClassifier;
    private final String leaseOwner;
    private final Duration leaseDuration;
    private UUID cursor;

    /**
     * Creates a scheduler service backed by a durable multi-instance cursor and lease.
     *
     * @param reservationRepository port finding expired candidates
     * @param expireTransaction transaction boundary for one aggregate
     * @param clock business clock
     * @param batchSize maximum candidates per run
     * @param leaseRepository durable lease adapter
     * @param leaseOwner unique instance identity
     * @param leaseDuration maximum time another instance waits before reclaiming
     * @param transientFailureClassifier classifier for retryable infrastructure failures
     */
    public ReleaseExpiredReservationsService(
            StockReservationRepositoryPort reservationRepository,
            ExpirePrescriptionTransaction expireTransaction,
            Clock clock,
            int batchSize,
            ReservationExpiryLeaseRepositoryPort leaseRepository,
            String leaseOwner,
            Duration leaseDuration,
            TransientFailureClassifierPort transientFailureClassifier) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Giới hạn batch phải lớn hơn 0");
        }
        if (clock == null || leaseOwner == null || leaseOwner.isBlank()
                || leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Cấu hình scheduler không hợp lệ");
        }
        this.reservationRepository = reservationRepository;
        this.expireTransaction = expireTransaction;
        this.clock = clock;
        this.batchSize = batchSize;
        if (leaseRepository == null) {
            throw new IllegalArgumentException("Durable scheduler lease repository is required");
        }
        this.leaseRepository = leaseRepository;
        if (transientFailureClassifier == null) {
            throw new IllegalArgumentException("Transient failure classifier is required");
        }
        this.transientFailureClassifier = transientFailureClassifier;
        this.leaseOwner = leaseOwner;
        this.leaseDuration = leaseDuration;
    }

    /**
     * Giải phóng các reservation hết TTL của tối đa số prescription đã cấu hình.
     *
     * @return tổng số dòng reservation đã chuyển sang {@code EXPIRED}
     */
    @Override
    public synchronized int releaseExpiredReservations() {
        Instant now = Instant.now(clock);
        UUID runCursor = cursor;
        ReservationExpiryLeaseClaim claim = leaseRepository
                .tryAcquire(EXPIRY_JOB_NAME, leaseOwner, now, leaseDuration)
                .orElse(null);
        if (claim == null) {
            return 0;
        }
        runCursor = claim.cursor();
        UUID leaseToken = claim.leaseToken();

        int expiredReservations = 0;
        try {
            java.util.List<UUID> candidates = reservationRepository
                    .findExpiredPrescriptionIdsAfter(now, runCursor, batchSize);
            if (candidates.isEmpty() && runCursor != null) {
                runCursor = null;
                candidates = reservationRepository
                        .findExpiredPrescriptionIdsAfter(now, null, batchSize);
            }
            if (!candidates.isEmpty()) {
                runCursor = candidates.get(candidates.size() - 1);
            }

            for (UUID prescriptionId : candidates) {
                try {
                    expiredReservations += expireTransaction.expire(prescriptionId, now);
                } catch (RuntimeException exception) {
                    if (transientFailureClassifier.isTransient(exception)) {
                        // Không được bỏ qua lỗi hạ tầng: giữ nguyên cursor để lần chạy sau retry.
                        throw exception;
                    }
                    // Dữ liệu poison cô lập một aggregate nhưng không chặn các đơn còn lại.
                    log.warn("Không thể hết hạn reservation của prescription {}: {}",
                            prescriptionId, exception.getMessage());
                }
            }
            if (runCursor != null) {
                leaseRepository.advance(EXPIRY_JOB_NAME, leaseOwner, leaseToken, runCursor, now);
            }
            cursor = runCursor;
            return expiredReservations;
        } finally {
            if (leaseToken != null) {
                leaseRepository.release(EXPIRY_JOB_NAME, leaseOwner, leaseToken, Instant.now(clock));
            }
        }
    }

}
