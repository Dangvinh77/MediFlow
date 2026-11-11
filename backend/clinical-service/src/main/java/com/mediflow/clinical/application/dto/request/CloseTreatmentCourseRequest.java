package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.TreatmentCourseDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloseTreatmentCourseRequest(@NotNull TreatmentCourseDecision decision, @NotBlank @Size(max = 1000) String note) {}
