-- LOCAL durable facts, not wire messages. No dispatcher reads this table.
-- Approval/translation to canonical outbox bytes requires a separately reviewed rollout.
CREATE TABLE surgery_lifecycle_intent (
    operation_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    case_revision BIGINT NOT NULL CHECK (case_revision > 0),
    intent_kind VARCHAR(16) NOT NULL CHECK (intent_kind IN ('READY', 'COMPLETED')),
    protocol_version INTEGER NOT NULL CHECK (protocol_version = 1),
    correlation_id VARCHAR(128) NOT NULL,
    payload BYTEA NOT NULL,
    delivery_status VARCHAR(8) NOT NULL DEFAULT 'HELD' CHECK (delivery_status = 'HELD'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (surgery_case_id, case_revision, intent_kind)
);

-- Preserve source instants exactly for new decisions without rewriting any historic snapshot.
-- PostgreSQL timestamps remain query/index columns; domain authorization reads the ISO value.
CREATE TABLE surgery_readiness_precision (
    readiness_snapshot_id UUID PRIMARY KEY REFERENCES surgery_readiness_snapshot(readiness_snapshot_id),
    evaluated_at_iso VARCHAR(40) NOT NULL,
    valid_until_iso VARCHAR(40)
);
