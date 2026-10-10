package com.mediflow.billing.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Exact immutable creation fact from {@code admission.deposit.requested}; the deposit amount is
 * Inpatient's own suggestion, never inferred or re-priced by Billing. */
public record AdmissionDepositRequestCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID admissionId, UUID patientId, UUID departmentId, String careEpisodeType,
        UUID careEpisodeId, String priceCode, BigDecimal suggestedAmount, String reason, Instant occurredAt) {
    public AdmissionDepositRequestCommand {
        if (eventId == null || admissionId == null || patientId == null || departmentId == null
                || careEpisodeId == null || occurredAt == null
                || !fingerprint(deliveryFingerprint) || !fingerprint(sourceFingerprint)
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || !"ADMISSION".equals(careEpisodeType) || !careEpisodeId.equals(admissionId)
                || !code(priceCode) || !money(suggestedAmount) || suggestedAmount.signum() <= 0
                || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("Invalid Admission deposit request context");
    }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean code(String value) { return value != null && value.matches("[A-Za-z0-9._-]{1,64}"); }
    private static boolean money(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 2 && value.precision() - value.scale() <= 17;
    }
}
