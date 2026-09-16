package com.mediflow.billing.infrastructure.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.BillingEventOutboxJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.BillingEventOutboxJpaRepository;

/** Claims outbox rows in short transactions and guards completion by dispatcher ownership. */
@Service
public class BillingOutboxClaimService {

    private final BillingEventOutboxJpaRepository repository;
    private final Clock clock;
    private final long leaseSeconds;

    public BillingOutboxClaimService(BillingEventOutboxJpaRepository repository,
                                     Clock clock,
                                     @Value("${mediflow.billing.outbox.lease-seconds:30}") long leaseSeconds) {
        if (leaseSeconds <= 0) {
            throw new IllegalArgumentException("Billing outbox lease duration must be positive");
        }
        this.repository = repository;
        this.clock = clock;
        this.leaseSeconds = leaseSeconds;
    }

    /** Atomically claims currently available rows with PostgreSQL SKIP LOCKED. */
    @Transactional
    public List<BillingEventOutboxJpaEntity> claim(int batchSize, String owner) {
        Instant now = Instant.now(clock);
        List<BillingEventOutboxJpaEntity> rows = repository.findClaimable(
                now, now.minusSeconds(leaseSeconds), batchSize);
        rows.forEach(row -> {
            row.setLockedAt(now);
            row.setLockedBy(owner);
        });
        repository.saveAll(rows);
        return rows;
    }

    @Transactional
    public boolean markPublished(UUID eventId, String owner, Instant publishedAt) {
        return repository.markPublishedIfOwned(eventId, owner, publishedAt) == 1;
    }

    @Transactional
    public boolean markFailure(UUID eventId, String owner, String error,
                               Instant availableAt, int maxAttempts) {
        return repository.markFailureIfOwned(eventId, owner, error, availableAt, maxAttempts) == 1;
    }

    @Transactional
    public boolean replay(UUID eventId) {
        return repository.replay(eventId, Instant.now(clock)) == 1;
    }

    @Transactional
    public int deletePublishedBefore(Instant cutoff) {
        return repository.deletePublishedBefore(cutoff);
    }
}
