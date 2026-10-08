package com.mediflow.notification.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** {@code admission.started} (inpatient-service) — CONTRACT-CARE-PROJECTIONS-01. */
public record AdmissionStartedCommand(UUID eventId, String fingerprint, String correlationId,
        UUID admissionId, UUID patientId, Instant admittedAt) {
    public AdmissionStartedCommand {
        if (eventId == null || admissionId == null || patientId == null || admittedAt == null
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 120) {
            throw new IllegalArgumentException("Invalid admission started projection");
        }
    }
}
