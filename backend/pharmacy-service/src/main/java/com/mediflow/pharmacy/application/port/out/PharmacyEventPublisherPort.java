package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionCancelledEvent;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;

/**
 * Out-port — "tôi cần ai đó biết cách báo tin cho các service khác".
 * Application phải thông báo các sự kiện nghiệp vụ (kê đơn, xuất thành công, xuất thất bại,
 * hết ngưỡng kho) nhưng KHÔNG được đụng RabbitMQ. Adapter thật là
 * {@code PharmacyEventPublisherAdapter} trong {@code infrastructure/messaging}, publish
 * SAU KHI transaction commit (docs/ai/06-events-rabbitmq.md).
 */
public interface PharmacyEventPublisherPort {

    /** Publish {@code prescription.cancelled} sau khi đơn và các giữ chỗ được hủy thành công. */
    void publishPrescriptionCancelled(PrescriptionCancelledEvent event);

    /** Publish {@code prescription.created} sau khi kê đơn commit — billing tạo hóa đơn (khởi đầu saga). */
    void publishPrescriptionCreated(PrescriptionCreatedEvent event);

    /** Publish {@code prescription.filled} sau khi xuất thuốc thành công. */
    void publishPrescriptionFilled(PrescriptionFilledEvent event);

    /** Publish {@code prescription.dispense.failed} khi xuất thất bại — kích hoạt bù trừ saga (BR-D6). */
    void publishPrescriptionDispenseFailed(PrescriptionDispenseFailedEvent event);

    /** Publish {@code prescription.expired} sau khi toàn bộ giữ chỗ của đơn hết TTL. */
    void publishPrescriptionExpired(PrescriptionExpiredEvent event);

    /**
     * Publish {@code stock.low} khi tồn kho chạm/dưới ngưỡng (BR-D11).
     * Kiểu "bắn-rồi-quên": lỗi khi gửi tin này không được làm hỏng một lần xuất đã thành công.
     */
    void publishStockLow(StockLowEvent event);
}
