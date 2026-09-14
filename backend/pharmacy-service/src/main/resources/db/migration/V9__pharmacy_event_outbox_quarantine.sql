-- Failed outbox rows are quarantined after bounded retries so one poison event
-- cannot block delivery of unrelated saga events forever.
ALTER TABLE PHARMACY_EVENT_OUTBOX
    ADD COLUMN quarantined_at TIMESTAMPTZ;

CREATE INDEX idx_pharmacy_outbox_quarantined
    ON PHARMACY_EVENT_OUTBOX (created_at)
    WHERE published_at IS NULL AND quarantined_at IS NULL;
