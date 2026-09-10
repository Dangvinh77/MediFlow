package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.*;

public record AddDiagnosisRequest(
        @NotBlank @Size(max = 255) String diagnosisName,
        @Size(max = 2000) String description,
        @Pattern(regexp = "^[A-Z]\\d{2}(\\.\\d{1,2})?$") String icdCode
) {}
