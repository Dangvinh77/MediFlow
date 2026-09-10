package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event tiêu thụ: "xuất thuốc thất bại" (routing key {@code prescription.dispense.failed},
 * do pharmacy-service phát). Đây là tín hiệu <b>bù trừ saga</b>: billing đảo thanh toán của
 * hóa đơn đã tạo, chuyển sang {@code REFUNDED} và publish {@code payment.failed}
 * (backend-spec/06-billing.md §7 "onDispenseFailed", BR-B4/BR-B5).
 *
 * <p>Khai lại cùng tên/field với record pharmacy publish (06-billing.md §12.1).
 *
 * @param eventId        khóa chống xử lý trùng
 * @param occurredAt     thời điểm xác định xuất thất bại
 * @param correlationId  mã truy vết xuyên suốt saga
 * @param prescriptionId đơn thuốc — billing tra hóa đơn theo trường này
 * @param invoiceId      hóa đơn cần bù trừ (nếu pharmacy biết)
 * @param patientId      bệnh nhân được đảo tiền trong sổ sách
 * @param reason         lý do thất bại — đưa vào {@code payment.failed} để notification báo bệnh nhân
 * @param failedItems    chi tiết thuốc thiếu (số cần / số còn) để báo cáo chính xác
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
