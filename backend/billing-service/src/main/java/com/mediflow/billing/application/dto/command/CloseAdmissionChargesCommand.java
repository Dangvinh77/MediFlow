package com.mediflow.billing.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** Exact Inpatient proof; neither clinical narrative nor a final financial authorization. */
public record CloseAdmissionChargesCommand(UUID eventId, UUID admissionId, UUID patientId,
        UUID summaryId, UUID approvedBy, Instant approvedAt, String approvedAtIso,
        String correlationId, String sourceFingerprint, String deliveryFingerprint) {
    public CloseAdmissionChargesCommand {
        if (eventId == null || admissionId == null || patientId == null || summaryId == null
                || approvedBy == null || approvedAt == null || approvedAtIso == null
                || approvedAtIso.length() > 40 || !approvedAt.equals(Instant.parse(approvedAtIso))
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || correlationId.chars().anyMatch(Character::isISOControl)
                || sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid admission charge-close proof");
        }
    }
}
