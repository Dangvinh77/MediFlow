package com.mediflow.notification.application.dto.command;

import java.util.UUID;

public record RegistrationNoticeCommand(UUID eventId, String rawHash, String snapshotHash,
        String correlationId, UUID registrationId, long revision, UUID patientId, String status) {
    public static final String EVENT_TYPE = "outpatient.registration.changed";
}
