package com.mediflow.billing.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** V2 positive patient-payable Lab payment. Amount is intentionally a JSON decimal string. */
public record LabQuotedPaymentRequest(
        @NotNull UUID quoteId,
        @NotBlank @Pattern(regexp = "(?:0|[1-9][0-9]{0,16})(?:\\.[0-9]{1,2})?") String amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER") String paymentMethod) { }
