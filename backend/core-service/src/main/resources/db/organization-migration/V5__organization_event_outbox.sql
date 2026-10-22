CREATE TABLE organization_event_outbox (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    routing_key VARCHAR(150) NOT NULL,
    type_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    last_error VARCHAR(100),
    CONSTRAINT ck_organization_event_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'QUARANTINED')),
    CONSTRAINT ck_organization_event_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_organization_event_outbox_pending
    ON organization_event_outbox (next_attempt_at, occurred_at, event_id)
    WHERE status = 'PENDING';
