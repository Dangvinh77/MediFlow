package com.mediflow.organization.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CapabilityRevocationCommand(@NotBlank @Size(max = 500) String reason) {}
