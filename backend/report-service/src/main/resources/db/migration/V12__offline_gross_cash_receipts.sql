-- Isolated gross-receipt evidence: NOT net cash, earned revenue, liability or read publication.
-- Immutable Billing PAYMENT transactions use transaction_id, never invoice_id, as semantic identity.
CREATE TABLE report_cash_receipt (
    transaction_id UUID PRIMARY KEY,
    first_event_id UUID NOT NULL,
    invoice_id UUID,
    payment_request_id UUID NOT NULL,
    account_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type IN ('OUTPATIENT_VISIT','ADMISSION')),
    care_episode_id UUID NOT NULL,
    classification VARCHAR(32) NOT NULL CHECK (classification IN ('SERVICE_PAYMENT','ADMISSION_DEPOSIT')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    payment_method VARCHAR(16) NOT NULL CHECK (payment_method IN ('CASH','TRANSFER')),
    completed_at TIMESTAMPTZ NOT NULL,
    completed_at_iso VARCHAR(40) NOT NULL,
    business_date DATE NOT NULL,
    report_zone VARCHAR(80) NOT NULL CHECK (btrim(report_zone) <> ''),
    payload_fingerprint VARCHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_cash_receipt_deposit_episode CHECK (classification <> 'ADMISSION_DEPOSIT' OR care_episode_type='ADMISSION')
);
CREATE INDEX idx_cash_receipt_invoice ON report_cash_receipt(invoice_id);
CREATE INDEX idx_cash_receipt_period ON report_cash_receipt(business_date, currency, department_id);

CREATE TABLE report_cash_delivery (
    event_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES report_cash_receipt(transaction_id),
    envelope_fingerprint VARCHAR(64) NOT NULL CHECK (envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE report_gross_cash_daily (
    scope_id UUID PRIMARY KEY,
    business_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    report_zone VARCHAR(80) NOT NULL CHECK (btrim(report_zone) <> ''),
    department_id UUID,
    classification VARCHAR(32) NOT NULL CHECK (classification IN ('SERVICE_PAYMENT','ADMISSION_DEPOSIT')),
    gross_receipts NUMERIC(19,2) NOT NULL CHECK (gross_receipts > 0),
    receipt_count BIGINT NOT NULL CHECK (receipt_count > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_gross_cash_daily_scope ON report_gross_cash_daily
    (business_date, currency, report_zone, department_id, classification) NULLS NOT DISTINCT;
