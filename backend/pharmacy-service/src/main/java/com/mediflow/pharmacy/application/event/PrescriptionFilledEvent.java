package com.mediflow.pharmacy.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event domain: "đơn thuốc đã được xuất thành công". Publish sau khi dispense thành công,
 * routing key {@code prescription.filled} — báo cho hệ thống rằng chuỗi saga đã kết thúc.
 *
 * <p>Ba trường đầu (eventId, occurredAt, correlationId) là envelope chuẩn; {@code departmentId}
 * bắt buộc vì report group theo khoa. Các service khác có thể dùng để cập nhật trạng thái,
 * in biên nhận, hoặc báo cho bệnh nhân.
 */
public record PrescriptionFilledEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId,
        UUID departmentId,
        BigDecimal totalAmount,
        java.util.List<DispensedItem> dispensedItems
) {
    /** Một dòng thuốc đã thực sự xuất ra khỏi kho. */
    public record DispensedItem(UUID drugId, String drugName, int quantity) {}
}
