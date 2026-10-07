package com.mediflow.surgery.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Only optimistic concurrency values; never caller-supplied readiness or actor evidence. */
public record SurgeryLifecycleRequest(@NotNull @PositiveOrZero Long expectedCaseRevision,
        @NotNull @Positive Long expectedScheduleRevision) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected lifecycle field");
    }
}
