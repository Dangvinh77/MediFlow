package com.mediflow.notification.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** {@code surgery.ready} (surgery-service) — CONTRACT-CARE-PROJECTIONS-01. */
public record SurgeryReadyCommand(UUID eventId, String fingerprint, String correlationId,
        UUID surgeryCaseId, UUID patientId, Instant plannedStartAt) {
    public SurgeryReadyCommand {
        if (eventId == null || surgeryCaseId == null || patientId == null || plannedStartAt == null
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 120) {
            throw new IllegalArgumentException("Invalid surgery ready projection");
        }
    }
}
