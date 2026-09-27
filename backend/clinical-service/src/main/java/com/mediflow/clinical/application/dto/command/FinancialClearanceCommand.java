package com.mediflow.clinical.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClearancePurpose;

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
        UUID appointmentId,
        UUID recordId,
        List<UUID> labTestIds,
        UUID prescriptionId,
        UUID admissionId,
        UUID surgeryCaseId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        Instant expiresAt,
        boolean emergencyOverride
) {
    public FinancialClearanceCommand {
        labTestIds = labTestIds == null ? List.of() : List.copyOf(labTestIds);
    }
}
