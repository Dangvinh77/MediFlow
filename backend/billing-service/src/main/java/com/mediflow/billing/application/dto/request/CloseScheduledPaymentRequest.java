package com.mediflow.billing.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CloseScheduledPaymentRequest(@NotNull UUID registrationId, @NotNull UUID reservationId,
        @Positive long fenceToken, @NotBlank @Pattern(regexp = "RESERVATION_RECONCILIATION") String reason) { }
