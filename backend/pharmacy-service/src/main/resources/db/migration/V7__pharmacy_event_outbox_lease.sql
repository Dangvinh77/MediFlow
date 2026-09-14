-- Multi-instance outbox leasing. Existing rows remain pending and immediately available.
ALTER TABLE PHARMACY_EVENT_OUTBOX
    ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(100);

UPDATE PHARMACY_EVENT_OUTBOX
SET available_at = created_at
WHERE available_at IS NULL;

CREATE INDEX idx_pharmacy_outbox_claimable
    ON PHARMACY_EVENT_OUTBOX (available_at, created_at)
    WHERE published_at IS NULL;
