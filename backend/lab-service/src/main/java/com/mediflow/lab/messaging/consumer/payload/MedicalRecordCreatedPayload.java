package com.mediflow.lab.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Local wire projection matching Clinical's medicalrecord.created payload. */
public record MedicalRecordCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID recordId,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        String diagnosis,
        LocalDate examinationDate
) {
}
