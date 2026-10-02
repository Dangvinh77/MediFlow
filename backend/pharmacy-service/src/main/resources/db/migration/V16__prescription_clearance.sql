-- Consumer-local authorization storage. No binding or V1 writer is activated by this migration.
-- No prescription FK: authoritative clearance may arrive before its prescription is committed.
CREATE TABLE prescription_clearance_target (
    prescription_id UUID PRIMARY KEY
);

CREATE TABLE prescription_clearance (
    clearance_id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    prescription_id UUID NOT NULL REFERENCES prescription_clearance_target(prescription_id),
    patient_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL CHECK (purpose = 'PRESCRIPTION'),
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT'),
    care_episode_id UUID NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'),
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    granted_at TIMESTAMPTZ NOT NULL,
    source_granted_at VARCHAR(40) NOT NULL,
    expires_at TIMESTAMPTZ,
    source_expires_at VARCHAR(40),
    payload_fingerprint VARCHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    target_status VARCHAR(16) NOT NULL CHECK (target_status IN ('PENDING', 'VERIFIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clearance_source_times CHECK (
        source_granted_at::timestamptz = granted_at AND (
            (expires_at IS NULL AND source_expires_at IS NULL) OR
            (expires_at IS NOT NULL AND source_expires_at IS NOT NULL
                AND source_expires_at::timestamptz = expires_at AND expires_at >= granted_at)
        )
    )
);
CREATE INDEX idx_clearance_target ON prescription_clearance(prescription_id, clearance_id);
CREATE INDEX idx_clearance_pending ON prescription_clearance(prescription_id) WHERE target_status = 'PENDING';

CREATE TABLE prescription_clearance_event (
    event_id UUID PRIMARY KEY,
    event_fingerprint VARCHAR(64) NOT NULL CHECK (event_fingerprint ~ '^[0-9a-f]{64}$'),
    snapshot_fingerprint VARCHAR(64) NOT NULL CHECK (snapshot_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
