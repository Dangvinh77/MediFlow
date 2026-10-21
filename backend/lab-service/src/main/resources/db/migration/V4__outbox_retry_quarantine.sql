-- Preserve existing identities, payloads and delivery history during upgrade.
ALTER TABLE lab_outbox_event
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN quarantined_at TIMESTAMPTZ,
    ADD COLUMN last_failure_code VARCHAR(32);

CREATE INDEX idx_lab_outbox_dispatchable
    ON lab_outbox_event(occurred_at, event_id)
    WHERE published_at IS NULL AND quarantined_at IS NULL;
