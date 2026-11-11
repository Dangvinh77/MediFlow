package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record RecordAmendmentDTO(
        UUID amendmentId,
        UUID recordId,
        String section,
        String originalText,
        String amendedText,
        String reason,
        UUID authorStaffId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID assignedByStaffId,
        String status,
        Instant requestedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID decidedByStaffId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String decisionNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant decidedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String contentHash
) {
}
