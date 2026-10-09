-- Additive internal accepted cash rebuild. No live state rewrite, publication or guessed history.
-- Paired V13 receipt and V17 refund manifests are frozen in a single REPEATABLE READ transaction.
CREATE TABLE refund_replay_generation (
    generation_id UUID PRIMARY KEY REFERENCES cash_replay_generation(generation_id),
    status VARCHAR(12) NOT NULL CHECK (status IN ('BUILDING','VERIFIED','FAILED')),
    format_version INTEGER NOT NULL DEFAULT 1 CHECK (format_version > 0),
    source_refunds BIGINT NOT NULL DEFAULT 0 CHECK (source_refunds >= 0),
    processed_refunds BIGINT NOT NULL DEFAULT 0 CHECK (processed_refunds BETWEEN 0 AND source_refunds),
    pending_refunds BIGINT NOT NULL DEFAULT 0 CHECK (pending_refunds >= 0),
    rejected_refunds BIGINT NOT NULL DEFAULT 0 CHECK (rejected_refunds >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CHECK (pending_refunds + rejected_refunds <= source_refunds),
    CHECK ((status='BUILDING') = (completed_at IS NULL)),
    CHECK (status<>'VERIFIED' OR processed_refunds=source_refunds)
);
CREATE TABLE refund_replay_input (
    generation_id UUID NOT NULL REFERENCES refund_replay_generation(generation_id),
    refund_transaction_id UUID NOT NULL,
    original_transaction_id UUID NOT NULL,
    first_event_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING','APPLIED','REJECTED')),
    fact_snapshot JSONB NOT NULL CHECK (jsonb_typeof(fact_snapshot)='object'),
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[a-f0-9]{64}$'),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    first_envelope_fingerprint VARCHAR(64) NOT NULL CHECK (first_envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    processed BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (generation_id,refund_transaction_id)
);
CREATE INDEX idx_refund_replay_pending ON refund_replay_input(generation_id,refund_transaction_id) WHERE NOT processed;
CREATE INDEX idx_refund_replay_original ON refund_replay_input(generation_id,original_transaction_id,state);
CREATE TABLE refund_replay_fact (
    generation_id UUID NOT NULL REFERENCES refund_replay_generation(generation_id),
    refund_transaction_id UUID NOT NULL,
    original_transaction_id UUID NOT NULL,
    first_event_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING','APPLIED','REJECTED')),
    fact_snapshot JSONB NOT NULL CHECK (jsonb_typeof(fact_snapshot)='object'),
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[a-f0-9]{64}$'),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    first_envelope_fingerprint VARCHAR(64) NOT NULL CHECK (first_envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY (generation_id,refund_transaction_id)
);
CREATE TABLE refund_replay_scope (
    generation_id UUID NOT NULL REFERENCES refund_replay_generation(generation_id),
    business_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency='VND'),
    report_zone VARCHAR(80) NOT NULL,
    department_id UUID,
    classification VARCHAR(32) NOT NULL CHECK (classification IN ('SERVICE_PAYMENT','ADMISSION_DEPOSIT')),
    completed_refunds NUMERIC(19,2) NOT NULL CHECK (completed_refunds > 0),
    refund_count BIGINT NOT NULL CHECK (refund_count > 0),
    UNIQUE NULLS NOT DISTINCT (generation_id,business_date,currency,report_zone,department_id,classification)
);
