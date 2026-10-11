-- Minimal immutable Inpatient proof, not settlement, recognition or a clinical summary.
CREATE TABLE admission_charge_close (
    admission_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    summary_id UUID NOT NULL UNIQUE,
    approved_by UUID NOT NULL,
    approved_at_iso VARCHAR(40) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    first_event_id UUID NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING_ACCOUNT', 'APPLIED')),
    account_id UUID UNIQUE REFERENCES billing_account(account_id),
    applied_at TIMESTAMPTZ,
    CHECK ((status = 'APPLIED' AND account_id IS NOT NULL AND applied_at IS NOT NULL)
        OR (status <> 'APPLIED' AND account_id IS NULL AND applied_at IS NULL))
);
CREATE TABLE admission_charge_close_delivery (
    event_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES admission_charge_close(admission_id),
    delivery_fingerprint CHAR(64) NOT NULL CHECK (delivery_fingerprint ~ '^[0-9a-f]{64}$')
);
