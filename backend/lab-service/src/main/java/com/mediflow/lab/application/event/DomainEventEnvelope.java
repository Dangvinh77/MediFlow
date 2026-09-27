package com.mediflow.lab.application.event;

import java.time.Instant;
import java.util.UUID;

/** Common versioned event envelope used by V2 cross-service contracts. */
public record DomainEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        T payload
) {}
