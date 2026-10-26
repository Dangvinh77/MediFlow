ALTER TABLE outpatient_payment_operation_receipt
    ADD COLUMN recovery_owner UUID,
    ADD COLUMN recovery_lease_until TIMESTAMPTZ,
    ADD COLUMN recovery_first_attempt_at TIMESTAMPTZ,
    ADD COLUMN recovery_last_attempt_at TIMESTAMPTZ,
    ADD COLUMN recovery_next_attempt_at TIMESTAMPTZ,
    ADD COLUMN recovery_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN recovery_last_error_code VARCHAR(120),
    ADD COLUMN recovery_alerted_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_outpatient_payment_recovery_attempts CHECK (recovery_attempt_count >= 0),
    ADD CONSTRAINT ck_outpatient_payment_recovery_lease CHECK (
        (recovery_owner IS NULL AND recovery_lease_until IS NULL)
        OR (recovery_owner IS NOT NULL AND recovery_lease_until IS NOT NULL)
    );

CREATE INDEX idx_outpatient_payment_recovery_due
    ON outpatient_payment_operation_receipt(reconcile_after, recovery_next_attempt_at, payment_operation_id)
    WHERE state = 'PAYMENT_LOCKED';
