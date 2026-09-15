package com.mediflow.pharmacy.infrastructure.persistence.jpaentity;

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

    @Column(name = "aggregate_id")
    private UUID aggregateId;

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

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "quarantined_at")
    private Instant quarantinedAt;

    /**
     * Creates a new pending outbox row tied to the aggregate whose causal order it must preserve.
     *
     * @param eventId immutable event identity
     * @param routingKey RabbitMQ routing key
     * @param aggregateId prescription or drug identity used for ordered delivery
     * @param payload serialized event envelope
     */
    public PharmacyEventOutboxJpaEntity(
            UUID eventId,
            String routingKey,
            UUID aggregateId,
            String payload) {
        this.eventId = eventId;
        this.routingKey = routingKey;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.availableAt = Instant.now();
    }
}
