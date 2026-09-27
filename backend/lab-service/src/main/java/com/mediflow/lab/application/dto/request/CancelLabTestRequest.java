package com.mediflow.lab.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Audited reason for cancelling a Lab test. */
public record CancelLabTestRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotNull UUID cancelledBy
) {}
