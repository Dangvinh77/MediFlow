package com.mediflow.lab.messaging.consumer.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;

/** Lab projection of the canonical financial.clearance.granted v1 payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinancialClearancePayload(
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
    public FinancialClearancePayload {
        labTestIds = labTestIds == null ? null : List.copyOf(labTestIds);
    }
}
