package com.mediflow.billing.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** Exact immutable creation fact from the check-in {@code appointment.status.changed} transition
 * (PENDING -> AWAITING_PAYMENT); no inferred price or episode. */
public record ExamChargeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID appointmentId, UUID patientId, UUID departmentId,
        String careEpisodeType, UUID careEpisodeId, String priceCode, Instant changedAt) {
    public ExamChargeCommand {
        if (eventId == null || appointmentId == null || patientId == null || departmentId == null
                || careEpisodeId == null || changedAt == null
                || !fingerprint(deliveryFingerprint) || !fingerprint(sourceFingerprint)
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || !"OUTPATIENT_VISIT".equals(careEpisodeType) || !careEpisodeId.equals(appointmentId)
                || !code(priceCode))
            throw new IllegalArgumentException("Invalid Exam charge context");
    }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean code(String value) { return value != null && value.matches("[A-Za-z0-9._-]{1,64}"); }
}
