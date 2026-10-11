-- Additive immutable Rx receipts. No changes to released migrations or V1 delivery holds.
CREATE TABLE prescription_charge_source (
    prescription_id UUID PRIMARY KEY,
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    charge_id UUID NOT NULL UNIQUE REFERENCES CHARGE(charge_id),
    payment_request_id UUID UNIQUE REFERENCES PAYMENT_REQUEST(payment_request_id),
    invoice_id UUID UNIQUE,
    care_context VARCHAR(20) NOT NULL CHECK (care_context IN ('OUTPATIENT','ADMISSION')),
    record_id UUID,
    created_at_iso VARCHAR(40) NOT NULL,
    CONSTRAINT ck_prescription_charge_request CHECK (
        (care_context='OUTPATIENT' AND payment_request_id IS NOT NULL AND invoice_id IS NOT NULL)
        OR (care_context='ADMISSION' AND payment_request_id IS NULL AND invoice_id IS NULL))
);
CREATE TABLE prescription_charge_item (
    prescription_id UUID NOT NULL REFERENCES prescription_charge_source(prescription_id),
    drug_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY(prescription_id,drug_id)
);
CREATE TABLE prescription_charge_delivery (
    event_id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES prescription_charge_source(prescription_id) DEFERRABLE INITIALLY DEFERRED,
    payload_fingerprint VARCHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$')
);
