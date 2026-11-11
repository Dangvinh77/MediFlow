package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A result or prescription linked to a visit; {@code afterConclusion} marks what arrived after the visit ended.
 * A Lab result shows its current revision; earlier released revisions are listed and labelled as amended.
 */
public record RecordDocumentDTO(String type, UUID referenceId, String summary, Instant attachedAt,
        boolean afterConclusion, Integer resultVersion, String amendmentReason, List<PreviousRevision> previousRevisions) {

    public RecordDocumentDTO {
        previousRevisions = previousRevisions == null ? List.of() : List.copyOf(previousRevisions);
    }

    public RecordDocumentDTO(String type, UUID referenceId, String summary, Instant attachedAt,
            boolean afterConclusion) {
        this(type, referenceId, summary, attachedAt, afterConclusion, null, null, List.of());
    }

    public record PreviousRevision(int resultVersion, String summary, String amendmentReason, Instant sourceAt,
            String label) {}
}
