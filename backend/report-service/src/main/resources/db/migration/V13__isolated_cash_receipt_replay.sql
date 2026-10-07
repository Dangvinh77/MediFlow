-- Finite accepted receipt rebuild only. Does not publish financial reads or change live tables.
CREATE TABLE cash_replay_generation (
    generation_id UUID PRIMARY KEY,
    status VARCHAR(12) NOT NULL CHECK (status IN ('BUILDING','VERIFIED','FAILED')),
    snapshot_version INTEGER NOT NULL DEFAULT 1 CHECK (snapshot_version > 0),
    projector_version INTEGER NOT NULL DEFAULT 1 CHECK (projector_version > 0),
    source_receipts BIGINT NOT NULL DEFAULT 0 CHECK (source_receipts >= 0),
    applied_receipts BIGINT NOT NULL DEFAULT 0 CHECK (applied_receipts >= 0 AND applied_receipts <= source_receipts),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CHECK ((status='BUILDING') = (completed_at IS NULL)),
    CHECK (status<>'VERIFIED' OR applied_receipts=source_receipts)
);

CREATE TABLE cash_replay_input (
    generation_id UUID NOT NULL REFERENCES cash_replay_generation(generation_id),
    transaction_id UUID NOT NULL,
    first_event_id UUID NOT NULL,
    fact_snapshot JSONB NOT NULL CHECK (jsonb_typeof(fact_snapshot)='object'),
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[a-f0-9]{64}$'),
    payload_fingerprint VARCHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),
    first_envelope_fingerprint VARCHAR(64) NOT NULL CHECK (first_envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    snapshot_version INTEGER NOT NULL CHECK (snapshot_version > 0),
    projector_version INTEGER NOT NULL CHECK (projector_version > 0),
    applied BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (generation_id,transaction_id)
);
CREATE INDEX idx_cash_replay_pending ON cash_replay_input(generation_id,transaction_id) WHERE NOT applied;

CREATE TABLE cash_replay_receipt (
    generation_id UUID NOT NULL REFERENCES cash_replay_generation(generation_id),
    transaction_id UUID NOT NULL,
    first_event_id UUID NOT NULL,
    fact_snapshot JSONB NOT NULL CHECK (jsonb_typeof(fact_snapshot)='object'),
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[a-f0-9]{64}$'),
    payload_fingerprint VARCHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),
    first_envelope_fingerprint VARCHAR(64) NOT NULL CHECK (first_envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY (generation_id,transaction_id)
);

CREATE TABLE cash_replay_scope (
    generation_id UUID NOT NULL REFERENCES cash_replay_generation(generation_id),
    business_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    report_zone VARCHAR(80) NOT NULL CHECK (btrim(report_zone) <> ''),
    department_id UUID,
    classification VARCHAR(32) NOT NULL CHECK (classification IN ('SERVICE_PAYMENT','ADMISSION_DEPOSIT')),
    gross_receipts NUMERIC(19,2) NOT NULL CHECK (gross_receipts > 0),
    receipt_count BIGINT NOT NULL CHECK (receipt_count > 0),
    UNIQUE NULLS NOT DISTINCT (generation_id,business_date,currency,report_zone,department_id,classification)
);
