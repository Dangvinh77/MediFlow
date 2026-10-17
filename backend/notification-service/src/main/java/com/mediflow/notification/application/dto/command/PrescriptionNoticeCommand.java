package com.mediflow.notification.application.dto.command;

import java.util.Set;
import java.util.UUID;

/** A private notice of a Pharmacy fact, never a payment or treatment permission. */
public record PrescriptionNoticeCommand(UUID eventId, String eventType, String deliveryFingerprint,
        String sourceFingerprint, String correlationId, UUID prescriptionId, UUID patientId) {
    public static final Set<String> EVENT_TYPES = Set.of("prescription.created", "prescription.filled",
            "prescription.cancelled", "prescription.expired", "prescription.dispense.failed");

    public PrescriptionNoticeCommand {
        if (eventId == null || prescriptionId == null || patientId == null || eventType == null || !EVENT_TYPES.contains(eventType)
                || !fingerprint(deliveryFingerprint) || !fingerprint(sourceFingerprint)
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || correlationId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid outpatient prescription notice");
        }
    }

    private static boolean fingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
