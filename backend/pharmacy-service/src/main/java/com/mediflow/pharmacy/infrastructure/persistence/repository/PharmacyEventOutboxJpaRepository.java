package com.mediflow.pharmacy.infrastructure.persistence.repository;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
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
    long countByPublishedAtIsNull();

    /** Reads the oldest pending creation timestamp for age monitoring. */
    Optional<PharmacyEventOutboxJpaEntity> findFirstByPublishedAtIsNullOrderByCreatedAtAsc();

    /** Selects claimable rows while preventing another replica from selecting the same rows. */
    @Query(value = """
            SELECT * FROM PHARMACY_EVENT_OUTBOX
            WHERE published_at IS NULL
              AND available_at <= :now
              AND (locked_at IS NULL OR locked_at < :now - (:leaseSeconds * interval '1 second'))
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PharmacyEventOutboxJpaEntity> findClaimable(
            @Param("now") Instant now,
            @Param("leaseSeconds") long leaseSeconds,
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
    @org.springframework.data.jpa.repository.Query("""
            update PharmacyEventOutboxJpaEntity e
               set e.attempts = e.attempts + 1, e.lastError = :error,
                   e.lockedAt = null, e.lockedBy = null, e.availableAt = :availableAt
             where e.eventId = :eventId and e.lockedBy = :owner and e.publishedAt is null
            """)
    int markFailureIfOwned(@Param("eventId") UUID eventId, @Param("owner") String owner,
            @Param("error") String error, @Param("availableAt") Instant availableAt);

    /** Makes a failed row available for operator replay without changing event id or payload. */
    @Modifying
    @org.springframework.data.jpa.repository.Query("""
            update PharmacyEventOutboxJpaEntity e
               set e.publishedAt = null, e.availableAt = :availableAt,
                   e.lockedAt = null, e.lockedBy = null, e.lastError = null
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

    /** Marks an event delivered and records the delivery timestamp. */
    default void markPublished(PharmacyEventOutboxJpaEntity event, Instant publishedAt) {
        event.setPublishedAt(publishedAt);
        event.setLastError(null);
        save(event);
    }

    /** Records a failed delivery attempt without losing the event. */
    default void markFailure(PharmacyEventOutboxJpaEntity event, String error) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(error == null ? "Unknown publish failure"
                : error.substring(0, Math.min(error.length(), 500)));
        save(event);
    }
}
