-- Durable delivery for pharmacy saga events.
CREATE TABLE PHARMACY_EVENT_OUTBOX (
    event_id       UUID PRIMARY KEY,
    routing_key    VARCHAR(100) NOT NULL,
    payload        TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

CREATE INDEX idx_pharmacy_outbox_pending
    ON PHARMACY_EVENT_OUTBOX (created_at)
    WHERE published_at IS NULL;
