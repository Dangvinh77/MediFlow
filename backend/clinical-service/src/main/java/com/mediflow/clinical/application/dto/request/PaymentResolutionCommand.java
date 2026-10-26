package com.mediflow.clinical.application.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.OutpatientPaymentOutcome;
import jakarta.validation.constraints.NotNull;

public record PaymentResolutionCommand(@NotNull UUID paymentOperationId, @NotNull UUID paymentRequestId,
        @NotNull UUID registrationId, @NotNull UUID reservationId, @NotNull Long fenceToken,
        @NotNull OutpatientPaymentOutcome outcome, UUID transactionId, UUID clearanceId, Instant completedAt, String basis, UUID quoteId) {
    public PaymentResolutionCommand(@NotNull UUID paymentOperationId, @NotNull UUID paymentRequestId,
        @NotNull UUID registrationId, @NotNull UUID reservationId, @NotNull Long fenceToken,
        @NotNull OutpatientPaymentOutcome outcome, UUID transactionId, UUID clearanceId, Instant completedAt) { this(paymentOperationId, paymentRequestId, registrationId, reservationId, fenceToken, outcome, transactionId, clearanceId, completedAt, "PATIENT_PAYMENT", null); }
}
