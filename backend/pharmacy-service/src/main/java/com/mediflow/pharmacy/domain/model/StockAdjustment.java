package com.mediflow.pharmacy.domain.model;

import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Immutable audit record for one stock mutation. */
@Getter
public final class StockAdjustment {

    private static final int MAX_REASON_LENGTH = 255;
    private final UUID adjustmentId;
    private final UUID drugId;
    private final int beforeStock;
    private final int delta;
    private final int afterStock;
    private final String reason;
    private final UUID actorId;
    private final String correlationId;
    private final Instant occurredAt;

    private StockAdjustment(UUID adjustmentId, UUID drugId, int beforeStock, int delta,
            int afterStock, String reason, UUID actorId, String correlationId, Instant occurredAt) {
        this.adjustmentId = adjustmentId;
        this.drugId = drugId;
        this.beforeStock = beforeStock;
        this.delta = delta;
        this.afterStock = afterStock;
        this.reason = reason;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    /** Creates and validates an audit record before persistence. */
    public static StockAdjustment create(UUID drugId, int beforeStock, int delta, int afterStock,
            String reason, UUID actorId, String correlationId, Instant occurredAt) {
        if (drugId == null || beforeStock < 0 || afterStock < 0 || afterStock - beforeStock != delta) {
            throw new DrugRuleException("DRUG_ADJUSTMENT_INVALID", "Snapshot điều chỉnh tồn kho không hợp lệ");
        }
        if (delta < 0 && (reason == null || reason.isBlank())) {
            throw new DrugRuleException("DRUG_ADJUSTMENT_REASON_REQUIRED", "Lý do là bắt buộc khi giảm tồn kho");
        }
        if (occurredAt == null) {
            throw new DrugRuleException("DRUG_ADJUSTMENT_TIME_REQUIRED", "Thời điểm điều chỉnh là bắt buộc");
        }
        String normalized = reason == null || reason.isBlank() ? "UNSPECIFIED" : reason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            normalized = normalized.substring(0, MAX_REASON_LENGTH);
        }
        return new StockAdjustment(UUID.randomUUID(), drugId, beforeStock, delta, afterStock,
                normalized, actorId, correlationId, occurredAt);
    }
}
