package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrationRescheduleRequest(@Min(1) int expectedRevision, @NotNull UUID targetSessionId,
        @NotBlank @Size(max = 500) String reason, boolean patientConfirmed) {}
