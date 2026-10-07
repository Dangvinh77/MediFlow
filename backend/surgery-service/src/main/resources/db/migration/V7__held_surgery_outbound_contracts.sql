-- Approved producer wire is distinct from both private V6 intents and generic transport tests.
-- No dispatcher or feature flag can release these rows without a reviewed cutover migration.
CREATE TABLE surgery_care_event_outbox (
    event_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    case_revision BIGINT NOT NULL CHECK (case_revision >= 0),
    event_type VARCHAR(100) NOT NULL CHECK (event_type IN
        ('surgery.case.created','surgery.ready','surgery.readiness.invalidated','surgery.completed','surgery.cancelled')),
    event_version INTEGER NOT NULL CHECK (event_version=1),
    correlation_id VARCHAR(128) NOT NULL,
    payload BYTEA NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(8) NOT NULL DEFAULT 'HELD' CHECK (delivery_status='HELD'),
    UNIQUE(surgery_case_id,case_revision,event_type)
);
