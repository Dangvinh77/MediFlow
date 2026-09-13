package com.mediflow.pharmacy.infrastructure.persistence.repository;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for durable pharmacy event delivery. */
public interface PharmacyEventOutboxJpaRepository
        extends JpaRepository<PharmacyEventOutboxJpaEntity, UUID> {

    /** Returns the oldest events that still need delivery. */
    List<PharmacyEventOutboxJpaEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

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
