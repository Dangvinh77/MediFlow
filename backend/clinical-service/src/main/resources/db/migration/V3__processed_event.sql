CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
