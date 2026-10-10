package com.mediflow.billing.application.dto.request;

import java.math.BigDecimal;

import com.mediflow.billing.domain.model.SettlementOutcome;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

/** {@code approvedInsuranceAmount} and {@code insuranceDecisionReference} must be given together or
 * not at all. {@code approvedExceptionalOutcome}, when given, must be DEBT_APPROVED or WAIVED —
 * computed outcomes (PAID_IN_FULL/ADDITIONAL_PAYMENT_REQUIRED/REFUND_DUE) are never an input. */
public record SettleAdmissionRequest(
        @DecimalMin(value = "0", inclusive = true) @Digits(integer = 17, fraction = 2) BigDecimal approvedInsuranceAmount,
        @Size(max = 120) String insuranceDecisionReference,
        SettlementOutcome approvedExceptionalOutcome) { }
