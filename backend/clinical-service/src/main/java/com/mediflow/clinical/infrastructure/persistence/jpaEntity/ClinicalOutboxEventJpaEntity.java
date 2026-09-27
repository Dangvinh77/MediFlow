package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clinical_outbox_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalOutboxEventJpaEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
}
