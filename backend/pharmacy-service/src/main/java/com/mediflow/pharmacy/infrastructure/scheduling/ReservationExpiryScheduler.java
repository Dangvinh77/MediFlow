package com.mediflow.pharmacy.infrastructure.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.in.ReleaseExpiredReservationsUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job định kỳ giải phóng các giữ chỗ tồn kho đã hết hạn TTL.
 *
 * <p>Kê đơn giữ chỗ với hạn hiệu lực (24h mặc định). Nếu đơn không được thanh toán trước hạn,
 * job này trả lại "chỗ" trong số tồn có thể bán cho các đơn khác (RESERVED → EXPIRED).
 *
 * <p>Là một driving adapter: chỉ gọi in-port, không chứa logic nghiệp vụ.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    /** Chạy mỗi giờ. */
    @Scheduled(cron = "${mediflow.pharmacy.reservation.release-cron:0 0 * * * *}")
    public void releaseExpired() {
        int released = releaseExpiredReservationsUseCase.releaseExpiredReservations();
        if (released > 0) {
            log.info("Đã giải phóng {} giữ chỗ tồn kho hết hạn", released);
        }
    }
}
