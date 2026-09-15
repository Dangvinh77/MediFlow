package com.mediflow.pharmacy.infrastructure.persistence.jpaentity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Durable idempotency claim for one consumed external event. */
@Entity
@Table(name = "PROCESSED_EVENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "routing_key", length = 100, nullable = false)
    private String routingKey;

    @CreationTimestamp
    @Column(name = "processed_at", updatable = false, nullable = false)
    private Instant processedAt;
}
