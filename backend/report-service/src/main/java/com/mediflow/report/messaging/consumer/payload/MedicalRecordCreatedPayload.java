package com.mediflow.report.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Wire projection of clinical-service's medicalrecord.created event. */
public record MedicalRecordCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID recordId,
        UUID departmentId,
        LocalDate examinationDate
) {
}
