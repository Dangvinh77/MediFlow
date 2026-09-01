package com.mediflow.pharmacy.application.port.in;

/**
 * In-port — "trả lại chỗ tồn kho đã giữ nhưng không được thanh toán".
 *
 * <p>Kê đơn giữ chỗ tồn kho (STOCK_RESERVATION) với hạn hiệu lực (TTL). Khi hết hạn mà đơn
 * chưa được thanh toán, job định kỳ gọi use case này để chuyển các giữ chỗ {@code RESERVED}
 * quá hạn → {@code EXPIRED} — tức trả lại "chỗ" trong số tồn có thể bán cho các đơn khác.
 *
 * <p>Đây chỉ là hợp đồng — {@code PharmacyApplicationService} sẽ hiện thực. Driving adapter
 * là một scheduler (job) trong infrastructure.
 */
public interface ReleaseExpiredReservationsUseCase {

    /**
     * Giải phóng toàn bộ giữ chỗ đã quá hạn.
     *
     * @return số lượng giữ chỗ đã được giải phóng
     */
    int releaseExpiredReservations();
}
