package com.mediflow.pharmacy.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện cho biết toàn bộ giữ chỗ của một đơn thuốc đã hết hạn.
 *
 * @param eventId mã duy nhất của sự kiện
 * @param occurredAt thời điểm hệ thống kết thúc đơn
 * @param correlationId mã tương quan; có thể {@code null} đối với scheduled job
 * @param prescriptionId mã đơn thuốc hết hạn
 * @param patientId mã bệnh nhân của đơn
 * @param expiredReservations số dòng giữ chỗ được chuyển sang {@code EXPIRED}
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
