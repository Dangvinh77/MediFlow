ALTER TABLE scheduled_payment_operation
    ADD COLUMN recovery_attempt_count INTEGER NOT NULL DEFAULT 0
        CONSTRAINT ck_scheduled_payment_recovery_attempts CHECK (recovery_attempt_count >= 0),
    ADD COLUMN next_recovery_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_scheduled_payment_recovery_due
    ON scheduled_payment_operation(next_recovery_attempt_at, created_at, payment_operation_id)
    WHERE status = 'PREPARING' OR (status = 'COMPLETED' AND resolution_applied_at IS NULL);
