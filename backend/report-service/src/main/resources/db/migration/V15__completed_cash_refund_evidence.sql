-- No original FK: broker ordering allows a refund fact to arrive before its receipt.
CREATE TABLE report_cash_refund (
    refund_transaction_id UUID PRIMARY KEY,
    original_transaction_id UUID NOT NULL,
    first_event_id UUID NOT NULL,
    account_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type IN ('ADMISSION','OUTPATIENT_VISIT')),
    care_episode_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency='VND'),
    completed_at_iso VARCHAR(40) NOT NULL,
    business_date DATE NOT NULL,
    report_zone VARCHAR(80) NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','APPLIED','REJECTED')),
    reason_code VARCHAR(80),
    original_business_date DATE,
    classification VARCHAR(32) CHECK (classification IN ('SERVICE_PAYMENT','ADMISSION_DEPOSIT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recovery_attempts BIGINT NOT NULL DEFAULT 0 CHECK (recovery_attempts >= 0),
    CHECK (refund_transaction_id <> original_transaction_id),
    CHECK (state <> 'APPLIED' OR (original_business_date IS NOT NULL AND classification IS NOT NULL AND applied_at IS NOT NULL)),
    CHECK (state <> 'REJECTED' OR reason_code IS NOT NULL)
);
CREATE INDEX idx_cash_refund_original ON report_cash_refund(original_transaction_id, state);
CREATE INDEX idx_cash_refund_pending ON report_cash_refund(next_attempt_at,created_at) WHERE state='PENDING';
CREATE TABLE report_cash_refund_delivery (
    event_id UUID PRIMARY KEY,
    refund_transaction_id UUID NOT NULL REFERENCES report_cash_refund(refund_transaction_id),
    envelope_fingerprint VARCHAR(64) NOT NULL CHECK (envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cash_refund_delivery_source ON report_cash_refund_delivery(refund_transaction_id);
CREATE TABLE report_refund_cash_daily (
    scope_id UUID PRIMARY KEY,
    business_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    report_zone VARCHAR(80) NOT NULL,
    department_id UUID,
    classification VARCHAR(32) NOT NULL CHECK (classification IN ('SERVICE_PAYMENT','ADMISSION_DEPOSIT')),
    completed_refunds NUMERIC(19,2) NOT NULL CHECK (completed_refunds > 0),
    refund_count BIGINT NOT NULL CHECK (refund_count > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_refund_cash_daily_scope ON report_refund_cash_daily
    (business_date,currency,report_zone,department_id,classification) NULLS NOT DISTINCT;
