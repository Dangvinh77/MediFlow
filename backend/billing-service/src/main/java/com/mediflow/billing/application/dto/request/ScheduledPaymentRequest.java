package com.mediflow.billing.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** V2 wire command; monetary values stay decimal strings at the HTTP boundary. */
public record ScheduledPaymentRequest(@NotNull UUID reservationId, @Positive int expectedReservationRevision,
        @NotBlank @Pattern(regexp = "(?:0|[1-9][0-9]{0,16})(?:\\.[0-9]{1,2})?") String amount,
        @NotBlank @Pattern(regexp = "VND") String currency,
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER") String paymentMethod,
        @Size(max = 120) String providerReference, UUID quoteId) {
    public ScheduledPaymentRequest(@NotNull UUID reservationId, @Positive int expectedReservationRevision,
        @NotBlank @Pattern(regexp = "(?:0|[1-9][0-9]{0,16})(?:\\.[0-9]{1,2})?") String amount,
        @NotBlank @Pattern(regexp = "VND") String currency,
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER") String paymentMethod,
        @Size(max = 120) String providerReference) {
        this(reservationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference, null);
    }
 }
