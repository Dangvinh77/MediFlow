package com.mediflow.surgery.application.port.out;

import java.time.Instant;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;

/** Billing is the only authority for current financial effectiveness. */
public interface FinancialClearanceLookupPort {
    Observation observe(SurgeryFinancialClearance expected, String correlationId);
    record Observation(boolean eligible, Instant observedAt, Instant validUntil) {
        public Observation {
            // Business expiry is independent of the observation's freshness window.
            if (observedAt == null || eligible && validUntil != null && !validUntil.isAfter(observedAt))
                throw new IllegalArgumentException("Valid financial observation required");
        }
    }
}
