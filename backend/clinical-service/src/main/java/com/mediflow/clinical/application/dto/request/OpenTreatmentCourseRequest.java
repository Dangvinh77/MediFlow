package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenTreatmentCourseRequest(
        @NotNull UUID patientId,
        @NotBlank @Size(max = 500) String problem,
        @NotBlank @Size(max = 1000) String openingReason,
        @NotNull UUID recordId) {}
