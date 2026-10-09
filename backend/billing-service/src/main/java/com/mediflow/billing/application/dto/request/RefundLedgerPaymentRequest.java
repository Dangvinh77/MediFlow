package com.mediflow.billing.application.dto.request;

import java.math.BigDecimal;
import com.mediflow.billing.domain.model.PaymentMethod;
import jakarta.validation.constraints.*;

/** A cashier-recorded completed refund, not an instruction to a bank/provider. */
public record RefundLedgerPaymentRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 500) String reason,
        @NotNull PaymentMethod paymentMethod) { }
