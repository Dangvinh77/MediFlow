package com.mediflow.pharmacy.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable cursor and lease for the reservation-expiry scheduler.
 *
 * <p>The lease is acquired atomically by job name. A cursor is advanced only by the current
 * lease owner, so a second service instance cannot process the same scheduler batch while the
 * first instance is alive. The cursor is deliberately an opaque UUID; ordering is defined by
 * the persistence adapter and is not a business identifier contract.</p>
 */
public interface ReservationExpiryLeaseRepositoryPort {

    /**
     * Acquires or renews a scheduler lease and returns the durable cursor from its previous run.
     *
     * @return a claim containing the previous cursor, or empty when another instance still owns
     *         the lease
     */
    Optional<ReservationExpiryLeaseClaim> tryAcquire(
            String jobName, String owner, Instant now, Duration leaseDuration);

    /**
     * Advances the cursor only if the caller still owns a live lease.
     *
     * @return {@code true} when the cursor was persisted by this owner
     */
    boolean advance(String jobName, String owner, UUID leaseToken, UUID cursor, Instant now);

    /** Releases a lease only when it is still owned by the caller. */
    void release(String jobName, String owner, UUID leaseToken, Instant now);
}
