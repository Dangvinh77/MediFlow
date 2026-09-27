package com.mediflow.clinical.application.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record EmergencyOverrideRequest(
        @NotNull UUID overrideId,
        @NotNull UUID approvedBy,
        @NotBlank @Size(max = 32) String approverRole,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull @PastOrPresent Instant approvedAt
) {
}
