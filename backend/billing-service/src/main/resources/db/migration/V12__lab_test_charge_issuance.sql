-- Opt-in LAB_TEST planned-request issuance (CONTRACT-CARE-BILLING-01, HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE).
-- Mirrors the Surgery delivery/source dedupe pattern from V9: one lab.request.created event,
-- keyed by its exact labId, issues at most one charge and one PAYMENT_REQUEST.
CREATE TABLE lab_test_charge_delivery (
    event_id UUID PRIMARY KEY,
    lab_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE lab_test_charge_source (
    lab_id UUID PRIMARY KEY,
    source_order_id UUID NOT NULL,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    payment_request_id UUID NOT NULL UNIQUE REFERENCES PAYMENT_REQUEST(payment_request_id),
    requested_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE lab_test_charge_delivery ADD CONSTRAINT fk_lab_test_charge_delivery_lab
    FOREIGN KEY(lab_id) REFERENCES lab_test_charge_source(lab_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_lab_test_charge_delivery_lab ON lab_test_charge_delivery(lab_id);
