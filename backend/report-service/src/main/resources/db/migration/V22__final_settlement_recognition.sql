-- Isolated final-only contributions; never automatically publish into V20 or legacy reports.
-- Account-bounded funding reads must not scan the entire cash history.
CREATE INDEX idx_cash_receipt_account_transaction ON report_cash_receipt(account_id,transaction_id);
CREATE INDEX idx_cash_refund_applied_account_transaction ON report_cash_refund(account_id,refund_transaction_id) WHERE state='APPLIED';
CREATE TABLE report_final_recognition (
    recognition_operation_id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE,
    settlement_id UUID NOT NULL UNIQUE,
    first_event_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','APPLIED','REJECTED')),
    reason_code VARCHAR(80),
    payload_snapshot JSONB NOT NULL CHECK(jsonb_typeof(payload_snapshot)='object'),
    payload_fingerprint VARCHAR(64) NOT NULL CHECK(payload_fingerprint ~ '^[a-f0-9]{64}$'),
    fact_fingerprint VARCHAR(64) NOT NULL CHECK(fact_fingerprint ~ '^[a-f0-9]{64}$'),
    report_zone VARCHAR(80) NOT NULL,
    business_date DATE NOT NULL,
    completed_at_iso VARCHAR(40) NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recovery_attempts BIGINT NOT NULL DEFAULT 0 CHECK(recovery_attempts>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    CHECK((state='APPLIED')=(applied_at IS NOT NULL)),
    CHECK((state='REJECTED')=(reason_code IS NOT NULL))
);
CREATE INDEX idx_final_recognition_pending ON report_final_recognition(next_attempt_at,recognition_operation_id) WHERE state='PENDING';
CREATE TABLE report_final_recognition_delivery (
    event_id UUID PRIMARY KEY,
    recognition_operation_id UUID NOT NULL REFERENCES report_final_recognition(recognition_operation_id),
    envelope_fingerprint VARCHAR(64) NOT NULL CHECK(envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE report_final_recognition_contribution (
    recognition_operation_id UUID NOT NULL REFERENCES report_final_recognition(recognition_operation_id),
    business_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
    report_zone VARCHAR(80) NOT NULL,
    department_id UUID,
    earned_revenue NUMERIC(19,2) NOT NULL CHECK(earned_revenue>=0),
    deposit_released NUMERIC(19,2) NOT NULL CHECK(deposit_released>=0),
    UNIQUE NULLS NOT DISTINCT(recognition_operation_id,department_id)
);
CREATE TABLE report_final_deposit_release (
    recognition_operation_id UUID NOT NULL REFERENCES report_final_recognition(recognition_operation_id),
    original_transaction_id UUID NOT NULL REFERENCES report_cash_receipt(transaction_id),
    amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
    PRIMARY KEY(recognition_operation_id,original_transaction_id)
);
CREATE INDEX idx_final_release_original ON report_final_deposit_release(original_transaction_id);
CREATE TABLE report_final_recognition_daily (
    business_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK(currency='VND'),
    report_zone VARCHAR(80) NOT NULL,
    department_id UUID,
    earned_revenue NUMERIC(19,2) NOT NULL CHECK(earned_revenue>=0),
    deposit_released NUMERIC(19,2) NOT NULL CHECK(deposit_released>=0),
    UNIQUE NULLS NOT DISTINCT(business_date,currency,report_zone,department_id)
);
