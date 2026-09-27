package com.mediflow.lab.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;

/** Application projection of Billing's versioned financial-clearance envelope. */
public record FinancialClearanceCommand(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        UUID clearanceId,
        UUID invoiceId,
        UUID accountId,
        UUID patientId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        ClearancePurpose purpose,
        List<UUID> labTestIds,
        BigDecimal amount,
        String currency,
        Instant expiresAt,
        boolean emergencyOverride
) {
    public FinancialClearanceCommand {
        labTestIds = labTestIds == null ? null : List.copyOf(labTestIds);
    }
}
