package com.mediflow.surgery.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** HTTP input for cancelling a case before the operation starts. */
public record CancelSurgeryRequest(
        @NotNull @PositiveOrZero Long expectedCaseRevision,
        @NotBlank @Size(max = 1_000) String reason) {
}
