package com.mediflow.surgery.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Expected aggregate revision for the idempotent transition into pre-operative work. */
public record BeginPreopRequest(
        @NotNull @PositiveOrZero Long expectedCaseRevision) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected Surgery command field");
    }
}
