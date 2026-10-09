-- CURRENT V0 prescription has at most one fill. Delivery IDs alone are not source identity.
-- No patient/narrative/raw event retention and no inferred backfill from existing totals.
CREATE TABLE prescription_fill_receipt (
    prescription_id UUID PRIMARY KEY,
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[0-9a-f]{64}$'),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
