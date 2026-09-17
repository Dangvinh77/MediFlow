package com.mediflow.billing.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable integration event written in the same transaction as Billing business state. */
@Entity
@Table(name = "BILLING_EVENT_OUTBOX")
@Getter
@Setter
@NoArgsConstructor
public class BillingEventOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "aggregate_id", nullable = false)
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

    /** Creates a pending event whose order is scoped to one invoice aggregate. */
    public BillingEventOutboxJpaEntity(UUID eventId, String routingKey,
                                        UUID aggregateId, String payload) {
        this.eventId = eventId;
        this.routingKey = routingKey;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.availableAt = Instant.now();
    }
}
