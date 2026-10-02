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
    public CareFinanceEventMetadata {
        if (eventId == null || eventType == null || eventType.isBlank() || version != 1
                || occurredAt == null || correlationId == null || correlationId.isBlank()
                || producer == null || producer.isBlank() || sourceField == null || sourceField.isBlank()
                || sourceId == null) {
            throw new IllegalArgumentException("Complete version-1 care-finance event metadata is required");
        }
    }
}
