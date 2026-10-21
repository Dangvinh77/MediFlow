CREATE TABLE patient_event_outbox (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'QUARANTINED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(100)
);

CREATE INDEX idx_patient_event_outbox_pending ON patient_event_outbox (next_attempt_at, occurred_at, event_id)
    WHERE status = 'PENDING';
