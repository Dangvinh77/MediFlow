package com.mediflow.notification.application.dto.command;

import java.time.Instant;
import java.util.UUID;

/** {@code surgery.cancelled} (surgery-service) — CONTRACT-CARE-PROJECTIONS-01. Reminder/notice
 * only; never authorizes a care or financial transition. */
public record SurgeryCancelledNoticeCommand(UUID eventId, String fingerprint, String correlationId,
        UUID surgeryCaseId, UUID patientId, String cancellationStage, String reason, Instant cancelledAt) {
    public SurgeryCancelledNoticeCommand {
        if (eventId == null || surgeryCaseId == null || patientId == null || cancellationStage == null
                || cancellationStage.isBlank() || cancelledAt == null || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}") || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120) {
            throw new IllegalArgumentException("Invalid surgery cancelled projection");
        }
    }
}
