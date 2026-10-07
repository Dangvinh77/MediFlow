package com.mediflow.surgery.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Original creation receipt, not a current status/readiness view. */
public record SurgeryCreationOutcome(UUID requestId, UUID surgeryCaseId, UUID checklistSnapshotId,
                                     Instant createdAt, boolean replayed) {
    public SurgeryCreationOutcome {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(surgeryCaseId);
        Objects.requireNonNull(checklistSnapshotId);
        Objects.requireNonNull(createdAt);
    }
    public SurgeryCreationOutcome asReplay() {
        return new SurgeryCreationOutcome(requestId, surgeryCaseId, checklistSnapshotId, createdAt, true);
    }
}
