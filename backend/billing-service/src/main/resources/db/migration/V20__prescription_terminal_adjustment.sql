-- Exact creation snapshot proof; old receipts are not silently granted reconstructed proof.
ALTER TABLE prescription_charge_source ADD COLUMN snapshot_fingerprint VARCHAR(64)
    CHECK (snapshot_fingerprint ~ '^[0-9a-f]{64}$');

CREATE TABLE prescription_terminal_source (
    prescription_id UUID PRIMARY KEY,
    event_type VARCHAR(48) NOT NULL CHECK (event_type IN ('prescription.filled','prescription.cancelled',
        'prescription.expired','prescription.dispense.failed')),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    snapshot_fingerprint VARCHAR(64) NOT NULL CHECK (snapshot_fingerprint ~ '^[0-9a-f]{64}$'),
    terminal_at_iso VARCHAR(40) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPLIED','REJECTED')),
    rejection_code VARCHAR(80),
    applied_at TIMESTAMPTZ,
    CHECK ((status='REJECTED') = (rejection_code IS NOT NULL)),
    CHECK ((status='APPLIED') = (applied_at IS NOT NULL))
);
CREATE TABLE prescription_terminal_delivery (
    event_id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES prescription_terminal_source(prescription_id) DEFERRABLE INITIALLY DEFERRED,
    delivery_fingerprint VARCHAR(64) NOT NULL CHECK (delivery_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_prescription_terminal_delivery_source ON prescription_terminal_delivery(prescription_id);
CREATE TABLE prescription_terminal_refund_due (
    prescription_id UUID NOT NULL REFERENCES prescription_terminal_source(prescription_id),
    original_transaction_id UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    PRIMARY KEY(prescription_id, original_transaction_id, charge_id)
);
CREATE INDEX idx_prescription_terminal_refund_original ON prescription_terminal_refund_due(original_transaction_id);
