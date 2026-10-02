package com.mediflow.surgery.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Expected aggregate revision for the idempotent transition into pre-operative work. */
public record BeginPreopRequest(
        @NotNull @PositiveOrZero Long expectedCaseRevision) {
}
