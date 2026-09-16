package com.mediflow.billing.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event tiêu thụ từ pharmacy khi toàn bộ giữ chỗ của đơn thuốc đã hết hạn.
 *
 * @param eventId khóa chống xử lý trùng
 * @param occurredAt thời điểm đơn hết hạn
 * @param correlationId mã truy vết của tác vụ hết hạn
 * @param prescriptionId đơn thuốc đã hết hạn
 * @param patientId bệnh nhân sở hữu đơn
 * @param expiredReservations số giữ chỗ đã hết hạn
 */
public record PrescriptionExpiredEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId,
        int expiredReservations
) {
}
