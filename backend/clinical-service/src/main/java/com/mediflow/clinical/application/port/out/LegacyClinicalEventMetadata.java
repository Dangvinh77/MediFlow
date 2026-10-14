package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Persistence metadata derived from the existing flat Clinical event DTO. */
public record LegacyClinicalEventMetadata(
        UUID eventId,
        String eventType,
        String correlationId,
        UUID aggregateId,
        Instant occurredAt) {
}
