package com.mediflow.lab.application.dto.request;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request that records observations and completes a lab test. */
public record AddResultRequest(
        @NotEmpty @Valid List<@NotNull LabResultItem> results,
        @Size(max = 4000) String conclusion,
        @NotNull LocalDate performedDate
) {

    public AddResultRequest {
        // Preserve invalid null items so validation returns a field error at the boundary.
        results = results == null ? null : Collections.unmodifiableList(new ArrayList<>(results));
    }
}
