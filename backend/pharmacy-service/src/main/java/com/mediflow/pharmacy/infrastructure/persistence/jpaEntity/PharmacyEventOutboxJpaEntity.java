package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Durable representation of a published-or-pending pharmacy event. */
@Entity
@Table(name = "PHARMACY_EVENT_OUTBOX")
@Getter
@Setter
@NoArgsConstructor
public class PharmacyEventOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Creates a new pending outbox row. */
    public PharmacyEventOutboxJpaEntity(UUID eventId, String routingKey, String payload) {
        this.eventId = eventId;
        this.routingKey = routingKey;
        this.payload = payload;
    }
}
