package com.mediflow.billing.application.dto.command;

import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.time.Instant;
import java.util.UUID;

/** Validated original Surgery proof; clinical text is neither carried nor persisted. */
public record CaptureSurgeryCompletionCommand(SurgeryCompletedEvent event, UUID surgeryRequestId,
        String careEpisodeType, UUID careEpisodeId, int caseRevision, int sourceRevision,
        String completedAtIso, String recordedAtIso, String sourceFingerprint, String deliveryFingerprint) {
    public CaptureSurgeryCompletionCommand {
        if (event == null || event.payload() == null || event.eventId() == null || surgeryRequestId == null
                || careEpisodeId == null || !("ADMISSION".equals(careEpisodeType) || "OUTPATIENT_VISIT".equals(careEpisodeType))
                || caseRevision < 1 || sourceRevision != 1 || event.surgeryCaseId() == null
                || event.patientId() == null || event.departmentId() == null || event.resultId() == null
                || event.correlationId() == null || event.correlationId().isBlank() || event.correlationId().length() > 120
                || event.correlationId().chars().anyMatch(Character::isISOControl)
                || event.payload().startedAt() == null || event.payload().completedAt() == null || event.recordedAt() == null
                || event.occurredAt() == null || !event.occurredAt().equals(event.recordedAt())
                || event.payload().completedAt().isBefore(event.payload().startedAt())
                || event.recordedAt().isBefore(event.payload().completedAt())
                || completedAtIso == null || completedAtIso.length() > 40 || recordedAtIso == null || recordedAtIso.length() > 40
                || ("ADMISSION".equals(careEpisodeType) ? !careEpisodeId.equals(event.payload().admissionId()) : event.payload().admissionId() != null)
                || sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[0-9a-f]{64}")) throw invalid();
        try {
            if (!event.payload().completedAt().equals(Instant.parse(completedAtIso))
                    || !event.recordedAt().equals(Instant.parse(recordedAtIso))) throw invalid();
        } catch (RuntimeException failure) { throw invalid(); }
    }
    private static BillingRuleException invalid() {
        return new BillingRuleException("BILLING_SURGERY_COMPLETION_INVALID", "Invalid Surgery completion proof");
    }
}
