-- Internal command receipt only; public V1 routes/listeners remain disabled.
CREATE TABLE care_prescription_creation (
    command_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    intent_fingerprint CHAR(64) NOT NULL CHECK (intent_fingerprint ~ '^[a-f0-9]{64}$'),
    prescription_id UUID UNIQUE REFERENCES PRESCRIPTION(prescription_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
