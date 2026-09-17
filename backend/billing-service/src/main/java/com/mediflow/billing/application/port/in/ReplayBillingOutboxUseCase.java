package com.mediflow.billing.application.port.in;

import java.util.UUID;

/** Operator action for requeuing one quarantined Billing integration event. */
public interface ReplayBillingOutboxUseCase {

    /** Preserves event identity and payload while making the row retryable again. */
    boolean replay(UUID eventId);
}
