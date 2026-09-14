package com.mediflow.notification.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Local wire projection khớp hợp đồng của pharmacy-service ({@code prescription.filled}).
 * Nội dung template cho event này là văn bản tĩnh (backend-spec/07-notification.md §8) nên
 * notification chỉ cần {@code patientId}.
 */
public record PrescriptionFilledPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID patientId
) {}
