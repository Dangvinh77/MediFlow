package com.mediflow.notification.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** {@code admission.closed} (inpatient-service) — CONTRACT-CARE-PROJECTIONS-01. Administrative
 * close only; not a claim about medical discharge time. */
public record AdmissionClosedCommand(UUID eventId, String fingerprint, String correlationId,
        UUID admissionId, UUID patientId, Instant closedAt) {
    public AdmissionClosedCommand {
        if (eventId == null || admissionId == null || patientId == null || closedAt == null
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 120) {
            throw new IllegalArgumentException("Invalid admission closed projection");
        }
    }
}
