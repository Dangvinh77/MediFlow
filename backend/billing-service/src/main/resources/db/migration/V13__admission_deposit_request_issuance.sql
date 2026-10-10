-- Opt-in ADMISSION_DEPOSIT initial request issuance (CONTRACT-CARE-BILLING-01,
-- HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT). One admission.deposit.requested event issues at most one
-- PAYMENT_REQUEST, keyed by its exact admissionId. No CHARGE row is ever created for a deposit
-- (invariant: a deposit is cash/liability, never an earned charge), so dedupe cannot reuse
-- uq_charge_source and needs its own delivery/source tables, mirroring V9/V12.
CREATE TABLE admission_deposit_delivery (
    event_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE admission_deposit_source (
    admission_id UUID PRIMARY KEY,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    payment_request_id UUID NOT NULL UNIQUE REFERENCES PAYMENT_REQUEST(payment_request_id),
    requested_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE admission_deposit_delivery ADD CONSTRAINT fk_admission_deposit_delivery_admission
    FOREIGN KEY(admission_id) REFERENCES admission_deposit_source(admission_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_admission_deposit_delivery_admission ON admission_deposit_delivery(admission_id);
