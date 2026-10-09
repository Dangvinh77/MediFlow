-- Unreleased local issuance slice follows master's immutable V7 reconciliation and V8 refunds.
-- Additive precision widening: preserve actual producer quantities, never round existing rows.
ALTER TABLE CHARGE ALTER COLUMN quantity TYPE NUMERIC(19,4);

CREATE TABLE surgery_charge_delivery (
    event_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE surgery_charge_source (
    surgery_case_id UUID PRIMARY KEY,
    surgery_request_id UUID NOT NULL UNIQUE,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    payment_request_id UUID NOT NULL UNIQUE REFERENCES PAYMENT_REQUEST(payment_request_id),
    record_id UUID,
    requested_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE surgery_charge_delivery ADD CONSTRAINT fk_surgery_charge_delivery_case
    FOREIGN KEY(surgery_case_id) REFERENCES surgery_charge_source(surgery_case_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_surgery_charge_delivery_case ON surgery_charge_delivery(surgery_case_id);
CREATE TABLE surgery_charge_item (
    surgery_case_id UUID NOT NULL REFERENCES surgery_charge_source(surgery_case_id),
    item_code VARCHAR(64) NOT NULL,
    price_code VARCHAR(64) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    PRIMARY KEY(surgery_case_id,item_code)
);
