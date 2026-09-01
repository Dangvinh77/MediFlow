package com.mediflow.pharmacy.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event domain: "xuất thuốc thất bại". Publish sau khi dispense thất bại, routing key
 * {@code prescription.dispense.failed} — đây là tín hiệu BÙ TRỪ saga: billing lắng nghe
 * để hoàn/hủy hóa đơn đã tạo (BR-D6).
 *
 * <p>Ba trường đầu (eventId, occurredAt, correlationId) là envelope chuẩn. {@code invoiceId}
 * được bù trừ; {@code reason} là lý do thất bại. Với mô hình reservation, đây là lỗi hệ thống
 * hiếm gặp (giữ chỗ thất lạc / mất dữ liệu) — {@code failedItems[]} cho billing/notification
 * báo chính xác thuốc nào thiếu bao nhiêu thay vì một lý do chung chung.
 */
public record PrescriptionDispenseFailedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID invoiceId,
        UUID patientId,
        String reason,
        List<FailedItem> failedItems
) {
    /** Một thuốc không xuất được — kèm số lượng cần và số thực tế có thể bán. */
    public record FailedItem(UUID drugId, String drugName, int requestedQty, int availableQty) {}
}
