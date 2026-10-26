package com.mediflow.billing.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Immutable evidence that Billing granted a zero-liability LAB_TEST clearance. */
public record LabZeroPayableEligibilityReceipt(UUID eligibilityOperationId, UUID paymentRequestId,
        UUID quoteId, String idempotencyKey, UUID actorAccountId, String correlationId,
        UUID clearanceId, Instant completedAt) {
    public LabZeroPayableEligibilityReceipt {
        if (eligibilityOperationId == null || paymentRequestId == null || quoteId == null
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120
                || actorAccountId == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120 || clearanceId == null || completedAt == null)
            throw new IllegalArgumentException("Invalid Lab zero-payable eligibility receipt");
    }
}
