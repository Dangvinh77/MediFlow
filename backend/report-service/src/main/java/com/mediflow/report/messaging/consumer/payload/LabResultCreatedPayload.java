package com.mediflow.report.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Wire projection of lab-service's lab.result.created event. */
public record LabResultCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID labId,
        UUID recordId,
        UUID departmentId,
        LocalDate performedDate
) {
}
