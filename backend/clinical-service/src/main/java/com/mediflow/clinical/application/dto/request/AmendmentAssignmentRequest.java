package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

public record AmendmentAssignmentRequest(UUID assigneeStaffId, String reason) {
}
