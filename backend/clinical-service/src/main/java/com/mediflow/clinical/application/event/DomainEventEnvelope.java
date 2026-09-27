package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        T payload
) {
    public static <T> DomainEventEnvelope<T> versionOne(String eventType, Instant occurredAt,
                                                        String correlationId, T payload) {
        return new DomainEventEnvelope<>(UUID.randomUUID(), eventType, 1, occurredAt,
                correlationId, "clinical-service", payload);
    }
}
