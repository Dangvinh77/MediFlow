package com.mediflow.clinical.application.dto.authority;

import java.time.Instant;
import java.util.UUID;

/** Billing's exact scheduled-payment operation and terminal receipt read by Clinical recovery. */
public record ScheduledPaymentSnapshot(Operation operation, Payment payment, UUID clearanceId) {
    public record Operation(
            UUID paymentOperationId,
            UUID paymentRequestId,
            UUID registrationId,
            UUID reservationId,
            String status,
            UUID transactionId,
            String amount,
            String currency,
            Instant createdAt,
            Instant updatedAt,
            String basis,
            UUID quoteId,
            Instant completedAt) {
    }

    public record Payment(UUID transactionId, String status, String amount, String currency,
            String paymentMethod, Instant completedAt) {
    }
}
