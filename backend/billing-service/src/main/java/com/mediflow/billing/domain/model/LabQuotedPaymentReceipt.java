package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable historical evidence joining a Lab cash receipt to the exact quote and actor. */
public record LabQuotedPaymentReceipt(UUID paymentRequestId, UUID transactionId, UUID quoteId,
        UUID actorAccountId, String idempotencyKey, BigDecimal amount, String currency,
        PaymentMethod paymentMethod, String quoteFingerprint, String commandFingerprint,
        UUID clearanceId, Instant completedAt) {
    public LabQuotedPaymentReceipt {
        if (paymentRequestId == null || transactionId == null || quoteId == null || actorAccountId == null
                || idempotencyKey == null || idempotencyKey.isBlank() || amount == null || amount.signum() <= 0
                || currency == null || paymentMethod == null || paymentMethod == PaymentMethod.INSURANCE
                || !fingerprint(quoteFingerprint) || !fingerprint(commandFingerprint)
                || clearanceId == null || completedAt == null)
            throw new IllegalArgumentException("Invalid Lab quoted-payment receipt");
    }

    private static boolean fingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
