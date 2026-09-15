package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims outbox rows in a short transaction before any broker call is made. */
@Service
public class PharmacyOutboxClaimService {

    private final PharmacyEventOutboxJpaRepository repository;
    private final Clock clock;
    private final long leaseSeconds;

    /** Creates the claim service with the configured lease duration. */
    public PharmacyOutboxClaimService(
            PharmacyEventOutboxJpaRepository repository,
            Clock clock,
            @Value("${mediflow.pharmacy.outbox.lease-seconds:30}") long leaseSeconds) {
        if (leaseSeconds <= 0) {
            throw new IllegalArgumentException("Outbox lease duration must be positive");
        }
        this.repository = repository;
        this.clock = clock;
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * Atomically locks claimable rows with PostgreSQL row locks and returns them after commit.
     *
     * @param batchSize maximum rows to claim
     * @param owner stable dispatcher instance id
     * @return rows owned by this dispatcher
     */
    @Transactional
    public List<PharmacyEventOutboxJpaEntity> claim(int batchSize, String owner) {
        Instant now = Instant.now(clock);
        List<PharmacyEventOutboxJpaEntity> rows = repository.findClaimable(
                now, now.minusSeconds(leaseSeconds), batchSize);
        rows.forEach(row -> {
            row.setLockedAt(now);
            row.setLockedBy(owner);
        });
        repository.saveAll(rows);
        return rows;
    }

    /** Completes a publish in a separate short transaction with an owner guard. */
    @Transactional
    public boolean markPublished(java.util.UUID eventId, String owner, Instant publishedAt) {
        return repository.markPublishedIfOwned(eventId, owner, publishedAt) == 1;
    }

    /** Records a failed attempt in a separate short transaction with an owner guard. */
    @Transactional
    public boolean markFailure(
            java.util.UUID eventId,
            String owner,
            String error,
            Instant availableAt,
            int maxAttempts) {
        return repository.markFailureIfOwned(eventId, owner, error, availableAt, maxAttempts) == 1;
    }

    /** Requeues a row for operator replay while retaining its immutable event payload. */
    @Transactional
    public boolean replay(java.util.UUID eventId) {
        return repository.replay(eventId, Instant.now(clock)) == 1;
    }

    /** Removes published rows older than retention; pending rows are never deleted. */
    @Transactional
    public int deletePublishedBefore(Instant cutoff) {
        return repository.deletePublishedBefore(cutoff);
    }
}
