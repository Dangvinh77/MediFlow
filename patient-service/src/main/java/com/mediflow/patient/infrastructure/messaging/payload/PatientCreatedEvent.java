package com.mediflow.patient.infrastructure.messaging.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Routing key {@code patient.created}. Consumed by notification-service (welcome message).
 *
 * <p>The first three fields are the envelope every MediFlow event carries
 * (docs/ai/06-events-rabbitmq.md). {@code eventId} is what consumers dedupe on — redelivery is
 * normal, not exceptional.
 *
 * <p>Carries {@code email} and {@code sdt} so notification-service can pick a channel without
 * calling back into this service — an async path must not create a sync dependency.
 */
public record PatientCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        String hoTen,
        String email,
        String sdt
) {
}
