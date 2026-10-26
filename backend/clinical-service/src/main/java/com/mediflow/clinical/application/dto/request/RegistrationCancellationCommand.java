package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrationCancellationCommand(@Positive int expectedRevision,
        @NotBlank @Size(max = 500) String reason) {}
