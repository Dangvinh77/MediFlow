-- Semantic singleton independent of delivery event IDs. No external clinical/contact payload.
CREATE TABLE care_notification_source (
    event_type VARCHAR(100) NOT NULL,
    source_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(event_type,source_id)
);
