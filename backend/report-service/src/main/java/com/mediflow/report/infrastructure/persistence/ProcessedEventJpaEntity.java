package com.mediflow.report.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence-only insert marker for atomic Rabbit event claims. */
@Entity
@Table(name = "PROCESSED_EVENT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "routing_key", length = 100, nullable = false)
    private String routingKey;

    @CreationTimestamp
    @Column(name = "processed_at", updatable = false, nullable = false)
    private Instant processedAt;
}
