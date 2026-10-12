package com.mediflow.notification.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code settlement.completed} (billing-service) — CONTRACT-CARE-PROJECTIONS-01. A final
 * discharge outcome notice only; Notification never infers or re-derives money from this. */
public record SettlementCompletedCommand(UUID eventId, String fingerprint, String correlationId,
        UUID settlementId, UUID admissionId, UUID patientId, BigDecimal balance, String outcome,
        Instant completedAt) {
    public SettlementCompletedCommand {
        if (eventId == null || settlementId == null || admissionId == null || patientId == null
                || balance == null || completedAt == null
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 120
                || outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("Invalid settlement completed projection");
        }
    }
}
