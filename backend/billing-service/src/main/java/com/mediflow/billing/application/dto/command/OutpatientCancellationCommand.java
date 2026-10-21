package com.mediflow.billing.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** Immutable cancellation fact for one Billing-owned EXAM or LAB_TEST source. */
public record OutpatientCancellationCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, String sourceType, UUID sourceId, UUID sourceOrderId, UUID patientId,
        UUID departmentId, UUID recordId, String careEpisodeType, UUID careEpisodeId, String priceCode,
        String labType, UUID cancelledBy, String reason, Instant cancelledAt) {
    public OutpatientCancellationCommand {
        boolean lab = "LAB_TEST".equals(sourceType);
        boolean exam = "EXAM".equals(sourceType);
        if (!(lab || exam) || eventId == null || sourceId == null || patientId == null || departmentId == null
                || careEpisodeId == null || cancelledBy == null || cancelledAt == null
                || !fingerprint(deliveryFingerprint) || !fingerprint(sourceFingerprint)
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || lab && (priceCode == null || !priceCode.matches("[A-Za-z0-9._-]{1,64}"))
                || reason == null || reason.isBlank() || reason.length() > (lab ? 1000 : 500)
                || !("OUTPATIENT_VISIT".equals(careEpisodeType) || lab && "ADMISSION".equals(careEpisodeType))
                || lab && (sourceOrderId == null || labType == null || labType.isBlank() || labType.length() > 64)
                || exam && (sourceOrderId != null || recordId != null || labType != null
                    || !"OUTPATIENT_VISIT".equals(careEpisodeType) || !sourceId.equals(careEpisodeId))) {
            throw new IllegalArgumentException("Invalid outpatient cancellation context");
        }
    }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
