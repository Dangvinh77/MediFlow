package com.mediflow.surgery.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Stable local result persisted for idempotent Surgery commands. */
public record SurgeryCommandOutcome(
        String commandCode,
        UUID surgeryCaseId,
        long caseRevision,
        UUID subjectId,
        long subjectRevision,
        String state,
        Instant occurredAt,
        boolean replayed) {

    public SurgeryCommandOutcome {
        if (commandCode == null || commandCode.isBlank() || commandCode.length() > 64
                || surgeryCaseId == null || caseRevision < 0 || subjectRevision < 0
                || state == null || state.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("Surgery command outcome is invalid");
        }
    }

    public SurgeryCommandOutcome asReplay() {
        return new SurgeryCommandOutcome(commandCode, surgeryCaseId, caseRevision,
                subjectId, subjectRevision, state, occurredAt, true);
    }
}
