package com.mediflow.pharmacy.application.dto.command;

import java.util.UUID;

import com.mediflow.pharmacy.domain.model.PrescriptionClearance;

public record PrescriptionClearanceCommand(UUID eventId, String eventFingerprint, PrescriptionClearance clearance) {
    public PrescriptionClearanceCommand {
        if (eventId == null || eventFingerprint == null || !eventFingerprint.matches("[0-9a-f]{64}")
                || clearance == null) {
            throw new IllegalArgumentException("Clearance command requires event identity and snapshot");
        }
    }
}
