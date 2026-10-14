ALTER TABLE clinical_outbox_event
    ADD COLUMN payload_bytes BYTEA,
    ADD COLUMN held_at TIMESTAMPTZ,
    ADD COLUMN hold_reason VARCHAR(255),
    ADD COLUMN quarantined_at TIMESTAMPTZ,
    ADD COLUMN quarantine_reason VARCHAR(255);

CREATE INDEX idx_clinical_outbox_dispatchable
    ON clinical_outbox_event (occurred_at, event_id)
    WHERE published_at IS NULL AND held_at IS NULL AND quarantined_at IS NULL;
