package com.mediflow.lab.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One result submitted with an add-results request. */
public record LabResultItem(
        @NotBlank @Size(max = 100) String indicator,
        @NotBlank @Size(max = 50) String value,
        @Size(max = 20) String unit,
        @Size(max = 50) String referenceRange
) {}
