package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ từ pharmacy khi một đơn thuốc bị hủy trước khi xuất.
 *
 * @param eventId khóa chống xử lý trùng
 * @param occurredAt thời điểm hủy đơn
 * @param correlationId mã truy vết xuyên saga
 * @param prescriptionId đơn thuốc đã hủy
 * @param patientId bệnh nhân sở hữu đơn
 * @param cancelledBy nhân viên thực hiện hủy
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
