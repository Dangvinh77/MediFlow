package com.mediflow.notification.application.dto.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Exact immutable source evidence, not a workflow command or external contact payload. */
public record SurgeryNoticeCommand(UUID eventId, String eventType, String deliveryFingerprint,
        String sourceFingerprint, String correlationId, Context context, UUID sourceId,
        UUID scheduleId, Long scheduleRevision, Instant plannedStartAt, Instant businessAt) {
    public SurgeryNoticeCommand {
        Objects.requireNonNull(eventId); Objects.requireNonNull(context); Objects.requireNonNull(sourceId);
        Objects.requireNonNull(businessAt);
        if (!java.util.Set.of("surgery.ready", "surgery.readiness.invalidated", "surgery.cancelled", "surgery.completed").contains(eventType)
                || deliveryFingerprint == null || !deliveryFingerprint.matches("[a-f0-9]{64}")
                || sourceFingerprint == null || !sourceFingerprint.matches("[a-f0-9]{64}")
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120)
            throw new IllegalArgumentException("Invalid Surgery notification evidence");
        if (eventType.equals("surgery.ready") || eventType.equals("surgery.readiness.invalidated")) {
            if (scheduleId == null || scheduleRevision == null || scheduleRevision < 1)
                throw new IllegalArgumentException("Exact snapshot schedule is required");
        }
        if (eventType.equals("surgery.ready") && plannedStartAt == null)
            throw new IllegalArgumentException("Provisional time is required");
    }

    public record Context(UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
            String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId) {
        public Context {
            Objects.requireNonNull(surgeryCaseId); Objects.requireNonNull(surgeryRequestId);
            Objects.requireNonNull(patientId); Objects.requireNonNull(departmentId); Objects.requireNonNull(careEpisodeId);
            if (!java.util.Set.of("ADMISSION", "OUTPATIENT_VISIT").contains(careEpisodeType)
                    || (careEpisodeType.equals("ADMISSION") && !careEpisodeId.equals(admissionId))
                    || (careEpisodeType.equals("OUTPATIENT_VISIT") && admissionId != null))
                throw new IllegalArgumentException("Invalid exact Surgery episode");
        }
    }
}
