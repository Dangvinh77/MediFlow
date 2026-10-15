CREATE TABLE consumer_inbox_delivery (
    consumer_name VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    operation_key VARCHAR(512) NOT NULL,
    raw_body BYTEA NOT NULL CHECK (octet_length(raw_body) BETWEEN 1 AND 1048576),
    raw_sha256 VARCHAR(64) NOT NULL CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    canonical_business_bytes BYTEA NOT NULL
        CHECK (octet_length(canonical_business_bytes) BETWEEN 1 AND 1048576),
    canonical_sha256 VARCHAR(64) NOT NULL CHECK (canonical_sha256 ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id),
    CONSTRAINT uq_consumer_inbox_delivery_global_event UNIQUE (event_id)
);

CREATE TABLE consumer_inbox_operation (
    consumer_name VARCHAR(64) NOT NULL,
    operation_key VARCHAR(512) NOT NULL,
    first_event_id UUID NOT NULL,
    canonical_business_bytes BYTEA NOT NULL
        CHECK (octet_length(canonical_business_bytes) BETWEEN 1 AND 1048576),
    canonical_sha256 VARCHAR(64) NOT NULL CHECK (canonical_sha256 ~ '^[0-9a-f]{64}$'),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, operation_key),
    CONSTRAINT fk_consumer_inbox_operation_delivery
        FOREIGN KEY (consumer_name, first_event_id)
        REFERENCES consumer_inbox_delivery (consumer_name, event_id)
);
