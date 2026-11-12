package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AmendmentAssignmentDTO(UUID assignmentId, UUID recordId, UUID assigneeStaffId, UUID assignedByStaffId,
        String reason, Instant createdAt) {
}
