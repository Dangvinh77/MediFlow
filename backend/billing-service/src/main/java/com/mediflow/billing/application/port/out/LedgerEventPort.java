package com.mediflow.billing.application.port.out;

import java.util.UUID;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;

public interface LedgerEventPort {
    void appendHeld(UUID accountId, LedgerIntegrationEvent event);

    /**
     * Writes an outpatient event eligible to publish if {@code paymentRequestId} is enrolled in the
     * V1 publication cohort and {@code accountId}/the event shape match that enrollment exactly;
     * writes it HELD (same as {@link #appendHeld}) if the request is simply not enrolled — the
     * default, expected case while the global flag is off. A mismatch on an enrolled request is
     * conflict, not a silent fallback to HELD.
     * Refunds additionally require the separate new-refund publication approval; otherwise they
     * remain HELD even on an enrolled request. No retry or gate toggle releases historical facts.
     */
    void appendForOutpatientRequest(UUID accountId, UUID paymentRequestId, LedgerIntegrationEvent event);
}
