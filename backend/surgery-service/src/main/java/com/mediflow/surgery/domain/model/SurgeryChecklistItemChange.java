package com.mediflow.surgery.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit fact for one checklist item revision. */
public record SurgeryChecklistItemChange(
        UUID changeId,
        UUID checklistItemId,
        long revision,
        SurgeryChecklistStatus previousStatus,
        SurgeryChecklistStatus newStatus,
        UUID evidenceReferenceId,
        Long evidenceRevision,
        SurgeryAuditActor actor,
        Instant occurredAt,
        String correlationId) {

    public SurgeryChecklistItemChange {
        if (changeId == null || checklistItemId == null || revision < 1
                || previousStatus == null || newStatus == null || actor == null
                || occurredAt == null || correlationId == null || correlationId.isBlank()
                || (evidenceRevision != null && (evidenceRevision < 0 || evidenceReferenceId == null))) {
            throw new IllegalArgumentException("Checklist item change is invalid");
        }
    }
}
