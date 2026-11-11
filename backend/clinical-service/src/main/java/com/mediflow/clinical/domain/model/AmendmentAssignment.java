package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

import lombok.Getter;

/** A department head names who may amend a finished visit whose responsible doctor has left the department. */
@Getter
public final class AmendmentAssignment {
    private final UUID assignmentId;
    private final UUID recordId;
    private final UUID assigneeStaffId;
    private final UUID assignedByStaffId;
    private final String reason;
    private final Instant createdAt;

    private AmendmentAssignment(UUID assignmentId, UUID recordId, UUID assigneeStaffId, UUID assignedByStaffId,
            String reason, Instant createdAt) {
        this.assignmentId = assignmentId;
        this.recordId = recordId;
        this.assigneeStaffId = assigneeStaffId;
        this.assignedByStaffId = assignedByStaffId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static AmendmentAssignment create(UUID recordId, UUID assigneeStaffId, UUID assignedByStaffId, String reason,
            Instant at) {
        if (recordId == null || assigneeStaffId == null || assignedByStaffId == null || at == null
                || reason == null || reason.isBlank() || reason.strip().length() > 1000) {
            throw new InvalidClinicalDataException("AMENDMENT_INVALID",
                    "An assignment needs the assignee, the assigning head and a reason up to 1000 characters");
        }
        return new AmendmentAssignment(UUID.randomUUID(), recordId, assigneeStaffId, assignedByStaffId, reason.strip(), at);
    }

    public static AmendmentAssignment restore(UUID assignmentId, UUID recordId, UUID assigneeStaffId,
            UUID assignedByStaffId, String reason, Instant createdAt) {
        return new AmendmentAssignment(Objects.requireNonNull(assignmentId), recordId, assigneeStaffId, assignedByStaffId,
                reason, createdAt);
    }
}
