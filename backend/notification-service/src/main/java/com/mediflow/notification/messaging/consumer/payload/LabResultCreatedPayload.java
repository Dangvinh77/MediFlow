package com.mediflow.notification.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local wire projection khớp hợp đồng của lab-service (backend-spec/04-lab.md §8). Notification
 * chỉ cần {@code labType} cho biến {@code loaiXn} của {@code NotificationTemplates} — các trường
 * còn lại giữ lại để hợp đồng rõ ràng, không dùng tới.
 */
public record LabResultCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        String labType,
        LocalDate performedDate
) {}
