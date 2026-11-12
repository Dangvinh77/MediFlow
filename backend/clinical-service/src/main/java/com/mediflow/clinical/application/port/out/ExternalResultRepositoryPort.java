package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ExternalResultType;

/** Stores external references separately from the medical record's clinical text. */
public interface ExternalResultRepositoryPort {

    /** Repeating the same record, type and reference must have no additional effect. */
    default boolean attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary) {
        return attach(recordId, type, referenceId, summary, null);
    }

    /** {@code sourceAt} is when the source produced the reference; {@code null} means the arrival time. */
    boolean attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary, Instant sourceAt);

    /** Every reference attached to a record, oldest first. */
    List<AttachedDocument> findByRecord(UUID recordId);

    /**
     * Records released Lab revision {@code resultVersion} (2 and above) and makes it the attached summary
     * unless a newer revision is already shown. Returns false when this revision was already recorded.
     */
    boolean attachLabRevision(UUID recordId, UUID labId, int resultVersion, String summary, String amendmentReason,
            Instant sourceAt);

    /** Every recorded revision of one Lab test on a record, oldest first. */
    List<LabResultRevision> labResultRevisions(UUID recordId, UUID labId);

    record AttachedDocument(String type, UUID referenceId, String summary, Instant attachedAt) {}

    record LabResultRevision(int resultVersion, String summary, String amendmentReason, Instant sourceAt) {}
}
