package com.mediflow.pharmacy.application.port.out;

import java.util.UUID;

/** A successfully acquired scheduler lease, its cursor and opaque fencing token.
 *
 * @param cursor last processed prescription id, or {@code null} on first run
 * @param leaseToken opaque fencing token
 */
public record ReservationExpiryLeaseClaim(UUID cursor, UUID leaseToken) {

    /** Rejects a claim without a fencing token; the cursor may be null on first run. */
    public ReservationExpiryLeaseClaim {
        if (leaseToken == null) {
            throw new IllegalArgumentException("leaseToken is required");
        }
    }
}
