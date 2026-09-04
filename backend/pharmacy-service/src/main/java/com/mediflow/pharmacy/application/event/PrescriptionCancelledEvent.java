package com.mediflow.pharmacy.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện cho biết một đơn thuốc đã bị hủy chủ động và các giữ chỗ đã được giải phóng.
 *
 * @param eventId mã duy nhất của sự kiện
 * @param occurredAt thời điểm nghiệp vụ xảy ra
 * @param correlationId mã tương quan xuyên suốt request
 * @param prescriptionId mã đơn thuốc đã hủy
 * @param patientId mã bệnh nhân của đơn
 * @param cancelledBy mã người thực hiện hủy
 * @param reason lý do hủy
 */
public record PrescriptionCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId,
        UUID cancelledBy,
        String reason
) {
}
