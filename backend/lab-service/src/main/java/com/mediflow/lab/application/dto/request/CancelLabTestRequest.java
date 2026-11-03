package com.mediflow.lab.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Actor comes from the verified session; an optional legacy actor must match it. */
public record CancelLabTestRequest(
        @NotBlank @Size(max = 1000) String reason,
        UUID cancelledBy
) {}
