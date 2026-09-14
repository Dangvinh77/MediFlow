package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA row for the stock adjustment audit aggregate. */
@Entity
@Table(name = "STOCK_ADJUSTMENT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentJpaEntity {

    @Id
    @Column(name = "adjustment_id", nullable = false, updatable = false)
    private UUID adjustmentId;
    @Column(name = "drug_id", nullable = false, updatable = false)
    private UUID drugId;
    @Column(name = "before_stock", nullable = false, updatable = false)
    private int beforeStock;
    @Column(name = "delta", nullable = false, updatable = false)
    private int delta;
    @Column(name = "after_stock", nullable = false, updatable = false)
    private int afterStock;
    @Column(name = "reason", nullable = false, length = 255, updatable = false)
    private String reason;
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
