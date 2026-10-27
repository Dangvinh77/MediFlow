package com.mediflow.notification.application.dto.command;
import java.util.UUID;
/** A private availability notice, never the underlying clinical result. */
public record PatientResultNoticeCommand(UUID eventId, String eventType, UUID sourceId, UUID patientId,
        String correlationId, String fingerprint, String title, String content) {}
