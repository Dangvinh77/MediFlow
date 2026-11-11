package com.mediflow.lab.application.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Wire shapes of the v2.0 Lab report workflow. */
public final class LabReportRequests {
    private LabReportRequests() {
    }

    public record LabValueRequest(@NotBlank @Size(max = 32) String fieldCode, @Size(max = 2) String operator,
            @Size(max = 4000) String value, @Size(max = 500) String noResultReason) {
    }

    public record SaveLabReportDraftRequest(@NotNull Integer draftRevision,
            @Size(max = 100) List<@Valid @NotNull LabValueRequest> values, @Size(max = 4000) String conclusion,
            LocalDate performedDate) {
    }

    public record SubmitLabReportRequest(@NotNull Integer draftRevision) {
    }

    public record ReturnLabReportRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record OpenAmendmentRequest(@Size(max = 1000) String reason) {
    }
}
