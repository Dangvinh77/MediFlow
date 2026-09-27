package com.mediflow.clinical.messaging.consumer.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinancialClearancePayload(
        UUID clearanceId,
        UUID invoiceId,
        UUID accountId,
        UUID patientId,
        String careEpisodeType,
        UUID careEpisodeId,
        String purpose,
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
}
