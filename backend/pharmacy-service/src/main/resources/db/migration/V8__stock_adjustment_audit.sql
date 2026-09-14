-- Immutable stock mutation audit, committed with DRUG and its outbox intent.
CREATE TABLE STOCK_ADJUSTMENT (
    adjustment_id UUID PRIMARY KEY,
    drug_id UUID NOT NULL,
    before_stock INT NOT NULL CHECK (before_stock >= 0),
    delta INT NOT NULL,
    after_stock INT NOT NULL CHECK (after_stock >= 0),
    reason VARCHAR(255) NOT NULL,
    actor_id UUID,
    correlation_id VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_stock_adjustment_snapshot CHECK (after_stock - before_stock = delta),
    CONSTRAINT ck_stock_adjustment_reason CHECK (delta >= 0 OR length(trim(reason)) > 0)
);

CREATE INDEX idx_stock_adjustment_drug_time
    ON STOCK_ADJUSTMENT (drug_id, occurred_at DESC);
