package com.mediflow.pharmacy.application.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mediflow.pharmacy.application.port.in.ReleaseExpiredReservationsUseCase;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;

import lombok.RequiredArgsConstructor;

/**
 * Tìm các prescription có giữ chỗ quá TTL và xử lý theo batch giới hạn.
 *
 * <p>Query đầu tiên chỉ tìm mã ứng viên. Mỗi mã sau đó được khóa và kiểm tra lại trong
 * {@link ExpirePrescriptionTransaction}; vì vậy kết quả vẫn an toàn nếu có thao tác hủy hoặc
 * xuất thuốc xen vào giữa hai bước.</p>
 */
@Service
@RequiredArgsConstructor
public class ReleaseExpiredReservationsService implements ReleaseExpiredReservationsUseCase {

    /** Số prescription tối đa được xem xét trong một lần scheduler chạy. */
    static final int BATCH_SIZE = 100;

    private final StockReservationRepositoryPort reservationRepository;
    private final ExpirePrescriptionTransaction expireTransaction;

    /**
     * Giải phóng các reservation hết TTL của tối đa {@value #BATCH_SIZE} prescription.
     *
     * @return tổng số dòng reservation đã chuyển sang {@code EXPIRED}
     */
    @Override
    public int releaseExpiredReservations() {
        Instant now = Instant.now();
        int expiredReservations = 0;

        for (UUID prescriptionId : reservationRepository
                .findExpiredPrescriptionIds(now, BATCH_SIZE)) {
            expiredReservations += expireTransaction.expire(prescriptionId, now);
        }

        return expiredReservations;
    }
}
