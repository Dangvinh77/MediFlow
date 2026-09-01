package com.mediflow.pharmacy.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event domain: "một đơn thuốc vừa được kê xong". Publish sau khi {@code createPrescription}
 * commit, với routing key {@code prescription.created} — billing lắng nghe để tạo hóa đơn
 * (bước khởi đầu của saga kê đơn → hóa đơn → thanh toán → xuất thuốc).
 *
 * <p>Ba trường đầu (eventId, occurredAt, correlationId) là envelope chuẩn của mọi event
 * (docs/ai/06-events-rabbitmq.md). {@code departmentId} bắt buộc vì report group theo khoa.
 */
public record PrescriptionCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        BigDecimal totalAmount,
        java.util.List<Item> items
) {
    /** Một dòng thuốc trong đơn — price là giá chụp tại thời điểm kê (BR-D7). */
    public record Item(UUID drugId, String drugName, int quantity, BigDecimal price) {}
}
