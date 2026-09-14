package com.mediflow.notification.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Local wire projection khớp hợp đồng của patient-service (backend-spec/02-patient.md §... —
 * {@code {eventId, occurredAt, correlationId, patientId, hoTen, email, sdt}}). Đây là event duy
 * nhất trong 6 event notification subscribe mang sẵn địa chỉ liên hệ (§10) — dùng để chọn kênh
 * EMAIL/SMS ngay khi chào mừng bệnh nhân mới.
 */
public record PatientCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        String hoTen,
        String email,
        String sdt
) {}
