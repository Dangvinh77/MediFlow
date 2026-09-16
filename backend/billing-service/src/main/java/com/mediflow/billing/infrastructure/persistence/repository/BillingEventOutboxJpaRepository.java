package com.mediflow.billing.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.BillingEventOutboxJpaEntity;

/** PostgreSQL-backed queue for durable Billing integration events. */
public interface BillingEventOutboxJpaRepository
        extends JpaRepository<BillingEventOutboxJpaEntity, UUID> {

    long countByPublishedAtIsNullAndQuarantinedAtIsNull();

    long countByPublishedAtIsNullAndQuarantinedAtIsNotNull();

    Optional<BillingEventOutboxJpaEntity>
            findFirstByPublishedAtIsNullAndQuarantinedAtIsNullOrderByCreatedAtAsc();

    Optional<BillingEventOutboxJpaEntity>
            findFirstByPublishedAtIsNullAndQuarantinedAtIsNotNullOrderByQuarantinedAtAsc();

    /** Claims only the oldest pending event per invoice so saga events cannot overtake each other. */
    @Query(value = """
            SELECT candidate.*
              FROM BILLING_EVENT_OUTBOX candidate
             WHERE candidate.published_at IS NULL
               AND candidate.quarantined_at IS NULL
               AND candidate.available_at <= :now
               AND (candidate.locked_at IS NULL OR candidate.locked_at < :leaseCutoff)
               AND NOT EXISTS (
                    SELECT 1
                      FROM BILLING_EVENT_OUTBOX predecessor
                     WHERE predecessor.aggregate_id = candidate.aggregate_id
                       AND predecessor.published_at IS NULL
                       AND (
                            predecessor.created_at < candidate.created_at
                            OR (predecessor.created_at = candidate.created_at
                                AND predecessor.event_id < candidate.event_id)
                       )
               )
            ORDER BY candidate.created_at, candidate.event_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BillingEventOutboxJpaEntity> findClaimable(
            @Param("now") Instant now,
            @Param("leaseCutoff") Instant leaseCutoff,
            @Param("batchSize") int batchSize);

    @Modifying
    @Query("""
            update BillingEventOutboxJpaEntity e
               set e.publishedAt = :publishedAt, e.lastError = null,
                   e.lockedAt = null, e.lockedBy = null
             where e.eventId = :eventId and e.lockedBy = :owner and e.publishedAt is null
            """)
    int markPublishedIfOwned(@Param("eventId") UUID eventId,
                             @Param("owner") String owner,
                             @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query(value = """
            UPDATE BILLING_EVENT_OUTBOX
               SET attempts = attempts + 1, last_error = :error,
                   locked_at = NULL, locked_by = NULL, available_at = :availableAt,
                   quarantined_at = CASE WHEN attempts + 1 >= :maxAttempts
                                         THEN CURRENT_TIMESTAMP ELSE NULL END
             WHERE event_id = :eventId AND locked_by = :owner AND published_at IS NULL
            """, nativeQuery = true)
    int markFailureIfOwned(@Param("eventId") UUID eventId,
                           @Param("owner") String owner,
                           @Param("error") String error,
                           @Param("availableAt") Instant availableAt,
                           @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query("""
            update BillingEventOutboxJpaEntity e
               set e.publishedAt = null, e.availableAt = :availableAt,
                   e.lockedAt = null, e.lockedBy = null, e.lastError = null,
                   e.quarantinedAt = null, e.attempts = 0
             where e.eventId = :eventId
               and e.publishedAt is null
               and e.quarantinedAt is not null
            """)
    int replay(@Param("eventId") UUID eventId, @Param("availableAt") Instant availableAt);

    @Modifying
    @Query("""
            delete from BillingEventOutboxJpaEntity e
             where e.publishedAt is not null and e.publishedAt < :cutoff
            """)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
