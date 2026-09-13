package com.mediflow.pharmacy.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mediflow.pharmacy.application.port.in.ReleaseExpiredReservationsUseCase;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;

/**
 * Tìm các prescription có giữ chỗ quá TTL và xử lý theo batch giới hạn.
 *
 * <p>Query đầu tiên chỉ tìm mã ứng viên. Mỗi mã sau đó được khóa và kiểm tra lại trong
 * {@link ExpirePrescriptionTransaction}; vì vậy kết quả vẫn an toàn nếu có thao tác hủy hoặc
 * xuất thuốc xen vào giữa hai bước.</p>
 */
public class ReleaseExpiredReservationsService implements ReleaseExpiredReservationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseExpiredReservationsService.class);

    private final StockReservationRepositoryPort reservationRepository;
    private final ExpirePrescriptionTransaction expireTransaction;
    private final Clock clock;
    private final int batchSize;

    /**
     * Tạo batch service với đồng hồ và giới hạn có thể kiểm thử/cấu hình.
     *
     * @param reservationRepository port tìm reservation hết hạn
     * @param expireTransaction transaction xử lý từng đơn
     * @param clock đồng hồ nghiệp vụ
     * @param batchSize số đơn tối đa mỗi lần chạy, phải lớn hơn 0
     */
    public ReleaseExpiredReservationsService(
            StockReservationRepositoryPort reservationRepository,
            ExpirePrescriptionTransaction expireTransaction,
            Clock clock,
            int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Giới hạn batch phải lớn hơn 0");
        }
        this.reservationRepository = reservationRepository;
        this.expireTransaction = expireTransaction;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * Giải phóng các reservation hết TTL của tối đa số prescription đã cấu hình.
     *
     * @return tổng số dòng reservation đã chuyển sang {@code EXPIRED}
     */
    @Override
    public int releaseExpiredReservations() {
        Instant now = Instant.now(clock);
        int expiredReservations = 0;

        for (UUID prescriptionId : reservationRepository
                .findExpiredPrescriptionIds(now, batchSize)) {
            try {
                expiredReservations += expireTransaction.expire(prescriptionId, now);
            } catch (RuntimeException exception) {
                // Một aggregate lỗi không được chặn các prescription còn lại trong batch.
                log.warn("Không thể hết hạn reservation của prescription {}: {}",
                        prescriptionId, exception.getMessage());
            }
        }

        return expiredReservations;
    }
}
