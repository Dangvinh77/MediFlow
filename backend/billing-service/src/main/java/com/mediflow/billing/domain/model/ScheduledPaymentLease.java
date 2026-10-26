package com.mediflow.billing.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Clinical's durable lease receipt; Billing stores its fence before attempting the local cash commit. */
public record ScheduledPaymentLease(UUID paymentOperationId, UUID reservationId,
                                    long reservationRevision, long fenceToken,
                                    Instant lockedAt, Instant reconcileAfter,
                                    String basis, UUID quoteId) {
    public ScheduledPaymentLease(UUID paymentOperationId, UUID reservationId,
            long reservationRevision, long fenceToken, Instant lockedAt, Instant reconcileAfter) {
        this(paymentOperationId, reservationId, reservationRevision, fenceToken, lockedAt, reconcileAfter,
                "PATIENT_PAYMENT", null);
    }
}
