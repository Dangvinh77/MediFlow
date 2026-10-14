package com.mediflow.organization.application.dto.request;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record SurgicalCapabilityRequest(@NotNull @PositiveOrZero Long expectedRevision,
        @NotNull Boolean active, @NotNull Instant validFrom, @NotNull Instant validUntil,
        @NotBlank @Size(max=500) String reason) {}
