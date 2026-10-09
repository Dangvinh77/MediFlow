package com.mediflow.billing.application.dto.command;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Minimal immutable cancellation fact. Narrative is hashed by the wire adapter, never retained here. */
public record SurgeryCancellationCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID surgeryCaseId, UUID surgeryRequestId, UUID cancellationId,
        UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId,
        UUID admissionId, UUID recordId, long caseRevision, String cancellationStage,
        UUID cancelledBy, UUID cancelledByStaffId, Instant cancelledAt) {
    public SurgeryCancellationCommand {
        if (eventId == null || surgeryCaseId == null || surgeryRequestId == null || cancellationId == null
                || patientId == null || departmentId == null || careEpisodeId == null || cancelledBy == null
                || cancelledAt == null || caseRevision < 1
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[0-9a-f]{64}")
                || sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || cancellationStage == null || !Set.of("BEFORE_PREOP", "AFTER_PREOP", "BEFORE_START").contains(cancellationStage)
                || !("ADMISSION".equals(careEpisodeType) && careEpisodeId.equals(admissionId)
                    || "OUTPATIENT_VISIT".equals(careEpisodeType) && admissionId == null && recordId != null)) {
            throw new IllegalArgumentException("Invalid Surgery cancellation context");
        }
    }
}
