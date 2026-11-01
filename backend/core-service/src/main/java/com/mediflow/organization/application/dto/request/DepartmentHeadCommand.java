package com.mediflow.organization.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DepartmentHeadCommand(
        @NotNull @Min(0) Long expectedRevision,
        @JsonProperty(required = true) UUID staffId,
        @NotBlank @Size(max = 500) String reason) {}
