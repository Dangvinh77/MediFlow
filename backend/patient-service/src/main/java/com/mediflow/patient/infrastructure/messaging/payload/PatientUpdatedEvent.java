package com.mediflow.patient.infrastructure.messaging.payload;

import java.time.Instant;
import java.util.UUID;

/** Routing key {@code patient.updated}. No subscriber today — published for future consumers. */
public record PatientUpdatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        String hoTen,
        String email,
        String sdt,
        String diaChi
) {
}
