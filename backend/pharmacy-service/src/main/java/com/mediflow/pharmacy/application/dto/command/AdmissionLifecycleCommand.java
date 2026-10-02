package com.mediflow.pharmacy.application.dto.command;

import java.util.UUID;

import com.mediflow.pharmacy.domain.model.AdmissionLifecycleFact;

public record AdmissionLifecycleCommand(UUID eventId, String eventFingerprint, AdmissionLifecycleFact fact) {
    public AdmissionLifecycleCommand {
        if (eventId == null || eventFingerprint == null || !eventFingerprint.matches("[a-f0-9]{64}")
                || fact == null) {
            throw new IllegalArgumentException("Admission event identity, fingerprint and fact are required");
        }
    }
}
