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
 *
 * @param eventId event identifier
 * @param occurredAt failure timestamp
 * @param correlationId saga correlation identifier
 * @param prescriptionId prescription identifier
 * @param invoiceId invoice to compensate
 * @param patientId patient identifier
 * @param reason stable failure reason
 * @param failedItems item-level failure snapshot
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
    /** Một thuốc không xuất được — kèm số lượng cần và số thực tế có thể bán.
     * @param drugId drug identifier
     * @param drugName drug name snapshot
     * @param requestedQty requested quantity
     * @param availableQty available quantity
     */
    public record FailedItem(UUID drugId, String drugName, int requestedQty, int availableQty) {}
}
