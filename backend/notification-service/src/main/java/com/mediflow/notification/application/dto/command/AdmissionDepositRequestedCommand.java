package com.mediflow.notification.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code admission.deposit.requested} (inpatient-service) — CONTRACT-CARE-PROJECTIONS-01. */
public record AdmissionDepositRequestedCommand(UUID eventId, String fingerprint, String correlationId,
        UUID admissionId, UUID patientId, BigDecimal suggestedAmount, String reason) {
    public AdmissionDepositRequestedCommand {
        if (eventId == null || admissionId == null || patientId == null || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120 || suggestedAmount == null || suggestedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid admission deposit requested projection");
        }
    }
}
