package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConsultationSessionStatusRequest(@Positive int expectedRevision,
        @NotNull ConsultationSessionStatus status, @NotBlank String reason) {}
