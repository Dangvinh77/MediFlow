-- Keep payload_hash unchanged: it is also read by pre-upgrade Notification binaries.
-- Existing receipt raw identity is unknown until original producer bytes are verified offline.
ALTER TABLE PROCESSED_EVENT ADD COLUMN receipt_raw_hash CHAR(64)
    CHECK (receipt_raw_hash IS NULL OR receipt_raw_hash ~ '^[0-9a-f]{64}$');

CREATE TABLE care_receipt_byte_backfill (
    event_id UUID PRIMARY KEY REFERENCES PROCESSED_EVENT(event_id),
    legacy_hash CHAR(64) NOT NULL CHECK (legacy_hash ~ '^[0-9a-f]{64}$'),
    raw_hash CHAR(64) NOT NULL CHECK (raw_hash ~ '^[0-9a-f]{64}$'),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_by TEXT NOT NULL DEFAULT current_user
);
