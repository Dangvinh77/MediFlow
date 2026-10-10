package com.mediflow.billing.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** Exact immutable creation fact from {@code lab.request.created}; no inferred price or episode. */
public record LabTestChargeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID labId, UUID sourceOrderId, UUID patientId, UUID departmentId,
        String careEpisodeType, UUID careEpisodeId, UUID recordId, String priceCode, String labType,
        Instant requestedAt, UUID emergencyOverrideId) {
    public LabTestChargeCommand {
        if (eventId == null || labId == null || sourceOrderId == null || patientId == null
                || departmentId == null || careEpisodeId == null || requestedAt == null
                || !fingerprint(deliveryFingerprint) || !fingerprint(sourceFingerprint)
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || !("OUTPATIENT_VISIT".equals(careEpisodeType) || "ADMISSION".equals(careEpisodeType))
                || !code(priceCode) || labType == null || labType.isBlank() || labType.length() > 64)
            throw new IllegalArgumentException("Invalid Lab test charge context");
    }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean code(String value) { return value != null && value.matches("[A-Za-z0-9._-]{1,64}"); }
}
