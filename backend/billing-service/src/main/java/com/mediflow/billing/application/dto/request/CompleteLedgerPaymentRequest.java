package com.mediflow.billing.application.dto.request;

import java.math.BigDecimal;

import com.mediflow.billing.domain.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteLedgerPaymentRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 120) String providerReference) { }
