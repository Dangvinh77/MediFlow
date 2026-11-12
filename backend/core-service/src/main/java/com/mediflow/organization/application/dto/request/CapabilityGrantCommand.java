package com.mediflow.organization.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CapabilityGrantCommand(
        @NotBlank @Size(max = 40) String capability, @NotBlank @Size(max = 500) String reason) {}
