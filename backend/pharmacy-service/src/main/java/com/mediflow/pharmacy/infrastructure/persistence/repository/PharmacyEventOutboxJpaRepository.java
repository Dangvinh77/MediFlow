package com.mediflow.pharmacy.infrastructure.persistence.repository;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for durable pharmacy event delivery. */
public interface PharmacyEventOutboxJpaRepository
        extends JpaRepository<PharmacyEventOutboxJpaEntity, UUID> {

    /** Returns the oldest events that still need delivery. */
    List<PharmacyEventOutboxJpaEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    /** Counts pending rows for operational gauges. */
    long countByPublishedAtIsNullAndQuarantinedAtIsNull();

    /** Reads the oldest pending creation timestamp for age monitoring. */
    Optional<PharmacyEventOutboxJpaEntity> findFirstByPublishedAtIsNullAndQuarantinedAtIsNullOrderByCreatedAtAsc();

    /** Counts poison events that require operator intervention. */
    long countByPublishedAtIsNullAndQuarantinedAtIsNotNull();

    /** Reads the oldest quarantined row for incident-age monitoring. */
    Optional<PharmacyEventOutboxJpaEntity>
            findFirstByPublishedAtIsNullAndQuarantinedAtIsNotNullOrderByQuarantinedAtAsc();

    /**
     * Selects claimable rows while preserving causal order for every critical aggregate.
     *
     * <p>A retrying or leased critical predecessor blocks later events of the same aggregate.
     * Stock notifications are intentionally non-blocking so they cannot stall prescription
     * completion. A quarantined predecessor continues to block later critical events until an
     * operator replays or otherwise resolves it, preventing consumers from observing an invalid
     * aggregate sequence.</p>
     */
    @Query(value = """
            SELECT candidate.*
              FROM PHARMACY_EVENT_OUTBOX candidate
             WHERE candidate.published_at IS NULL
               AND candidate.quarantined_at IS NULL
               AND candidate.available_at <= :now
               AND (candidate.locked_at IS NULL OR candidate.locked_at < :leaseCutoff)
               AND (
                    candidate.routing_key IN ('stock.low', 'stock.adjusted')
                    OR NOT EXISTS (
                        SELECT 1
                          FROM PHARMACY_EVENT_OUTBOX predecessor
                         WHERE predecessor.published_at IS NULL
                           AND predecessor.routing_key NOT IN ('stock.low', 'stock.adjusted')
                           AND (
                                predecessor.aggregate_id = candidate.aggregate_id
                                OR (predecessor.aggregate_id IS NULL
                                    AND candidate.aggregate_id IS NULL)
                           )
                           AND (
                                predecessor.created_at < candidate.created_at
                                OR (predecessor.created_at = candidate.created_at
                                    AND predecessor.event_id < candidate.event_id)
                           )
                    )
               )
            ORDER BY candidate.created_at, candidate.event_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PharmacyEventOutboxJpaEntity> findClaimable(
            @Param("now") Instant now,
            @Param("leaseCutoff") Instant leaseCutoff,
            @Param("batchSize") int batchSize);

    /** Marks a row published only when this replica still owns its lease. */
    @Modifying
    @org.springframework.data.jpa.repository.Query("""
            update PharmacyEventOutboxJpaEntity e
               set e.publishedAt = :publishedAt, e.lastError = null,
                   e.lockedAt = null, e.lockedBy = null
             where e.eventId = :eventId and e.lockedBy = :owner and e.publishedAt is null
            """)
    int markPublishedIfOwned(
            @Param("eventId") UUID eventId,
            @Param("owner") String owner,
            @Param("publishedAt") Instant publishedAt);

    /** Records a failed attempt only when this replica still owns its lease. */
    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE PHARMACY_EVENT_OUTBOX
               SET attempts = attempts + 1, last_error = :error,
                   locked_at = NULL, locked_by = NULL, available_at = :availableAt,
                   quarantined_at = CASE WHEN attempts + 1 >= :maxAttempts
                                         THEN CURRENT_TIMESTAMP ELSE NULL END
             WHERE event_id = :eventId AND locked_by = :owner AND published_at IS NULL
            """, nativeQuery = true)
    int markFailureIfOwned(@Param("eventId") UUID eventId, @Param("owner") String owner,
            @Param("error") String error, @Param("availableAt") Instant availableAt,
            @Param("maxAttempts") int maxAttempts);

    /** Makes a failed row available for operator replay without changing event id or payload. */
    @Modifying
    @org.springframework.data.jpa.repository.Query("""
            update PharmacyEventOutboxJpaEntity e
               set e.publishedAt = null, e.availableAt = :availableAt,
                   e.lockedAt = null, e.lockedBy = null, e.lastError = null,
                   e.quarantinedAt = null, e.attempts = 0
             where e.eventId = :eventId
            """)
    int replay(@Param("eventId") UUID eventId, @Param("availableAt") Instant availableAt);

    /** Deletes only published rows older than the retention cutoff. */
    @Modifying
    @org.springframework.data.jpa.repository.Query("""
            delete from PharmacyEventOutboxJpaEntity e
             where e.publishedAt is not null and e.publishedAt < :cutoff
            """)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

}
