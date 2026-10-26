package com.mediflow.clinical.application.dto.request;

import java.time.Instant;
import java.util.UUID;

public record PaymentLease(UUID paymentOperationId, UUID reservationId, int reservationRevision,
        long fenceToken, Instant lockedAt, Instant reconcileAfter, String basis, UUID quoteId) {
    public PaymentLease(UUID paymentOperationId, UUID reservationId, int reservationRevision,
            long fenceToken, Instant lockedAt, Instant reconcileAfter) {
        this(paymentOperationId, reservationId, reservationRevision, fenceToken, lockedAt, reconcileAfter,
                "PATIENT_PAYMENT", null);
    }
}
