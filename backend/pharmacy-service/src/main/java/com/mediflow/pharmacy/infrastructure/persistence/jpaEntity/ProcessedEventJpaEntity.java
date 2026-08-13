package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "PROCESSED_EVENT")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;          // eventId của message — KHÔNG auto-gen

    @Column(name = "routing_key", length = 100, nullable = false)
    private String routingKey;

    @CreationTimestamp
    @Column(name = "processed_at", updatable = false, nullable = false)
    private Instant processedAt;
}