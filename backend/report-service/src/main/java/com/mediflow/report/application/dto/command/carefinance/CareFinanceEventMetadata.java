package com.mediflow.report.application.dto.command.carefinance;

import java.time.Instant;
import java.util.UUID;

/** Validated envelope metadata plus the canonical source key for one Care-Finance V2 fact. */
public record CareFinanceEventMetadata(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        String sourceField,
        UUID sourceId) {
}
