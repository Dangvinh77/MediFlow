-- Transactional outbox for reliable Billing -> RabbitMQ delivery.
CREATE TABLE BILLING_EVENT_OUTBOX (
    event_id        UUID PRIMARY KEY,
    routing_key     VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    available_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at       TIMESTAMPTZ,
    locked_by       VARCHAR(100),
    quarantined_at  TIMESTAMPTZ
);

CREATE INDEX idx_billing_outbox_claimable
    ON BILLING_EVENT_OUTBOX (available_at, created_at, event_id)
    WHERE published_at IS NULL AND quarantined_at IS NULL;

CREATE INDEX idx_billing_outbox_aggregate_order
    ON BILLING_EVENT_OUTBOX (aggregate_id, created_at, event_id)
    WHERE published_at IS NULL;

CREATE INDEX idx_billing_outbox_quarantined
    ON BILLING_EVENT_OUTBOX (quarantined_at)
    WHERE published_at IS NULL AND quarantined_at IS NOT NULL;
