package com.mediflow.surgery.application.dto;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

/** Stable local result persisted for idempotent Surgery commands. */
public record SurgeryCommandOutcome(
        String commandCode,
        UUID surgeryCaseId,
        long caseRevision,
        UUID subjectId,
        long subjectRevision,
        String state,
        Instant occurredAt,
        boolean replayed,
        List<String> blockingReasons) {

    public SurgeryCommandOutcome(String commandCode, UUID surgeryCaseId, long caseRevision, UUID subjectId,
            long subjectRevision, String state, Instant occurredAt, boolean replayed) {
        this(commandCode,surgeryCaseId,caseRevision,subjectId,subjectRevision,state,occurredAt,replayed,List.of());
    }

    public SurgeryCommandOutcome {
        blockingReasons = List.copyOf(blockingReasons);
        if (blockingReasons.size() > 32 || blockingReasons.stream().anyMatch(reason -> reason.isBlank() || reason.length() > 128)) {
            throw new IllegalArgumentException("Invalid blocking reasons");
        }
        if (commandCode == null || commandCode.isBlank() || commandCode.length() > 64
                || surgeryCaseId == null || caseRevision < 0 || subjectRevision < 0
                || state == null || state.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("Surgery command outcome is invalid");
        }
    }

    public SurgeryCommandOutcome asReplay() {
        return new SurgeryCommandOutcome(commandCode, surgeryCaseId, caseRevision,
                subjectId, subjectRevision, state, occurredAt, true, blockingReasons);
    }
}
