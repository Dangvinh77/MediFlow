package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClinicQueueAdjustmentRequest(@Min(1) int expectedRevision, @NotNull Type type, @NotBlank @Size(max = 500) String reason) {
    public enum Type { LATE, NO_SHOW, RETURNED, PRIORITY }
}
