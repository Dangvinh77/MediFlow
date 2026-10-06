-- Immutable Organization hints, not master data or permission grants.
CREATE TABLE surgery_authority_change (
    event_id UUID PRIMARY KEY REFERENCES surgery_inbox(event_id),
    reference_key VARCHAR(120) NOT NULL,
    reference_kind VARCHAR(24) NOT NULL CHECK (reference_kind IN ('ROOM', 'STAFF_CAPABILITY')),
    reference_id UUID NOT NULL,
    team_role VARCHAR(24),
    source_revision BIGINT NOT NULL CHECK (source_revision > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_account_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    correlation_id VARCHAR(128) NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    UNIQUE (reference_key, source_revision),
    CHECK ((reference_kind = 'ROOM' AND team_role IS NULL)
        OR (reference_kind = 'STAFF_CAPABILITY' AND team_role IS NOT NULL AND team_role IN
            ('PRIMARY_SURGEON', 'ASSISTANT_SURGEON', 'ANESTHESIOLOGIST', 'OR_NURSE')))
);
CREATE TABLE surgery_authority_invalidation (
    event_id UUID NOT NULL REFERENCES surgery_authority_change(event_id),
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    readiness_snapshot_id UUID NOT NULL REFERENCES surgery_readiness_snapshot(readiness_snapshot_id),
    schedule_id UUID NOT NULL REFERENCES surgery_schedule(schedule_id),
    schedule_revision BIGINT NOT NULL CHECK (schedule_revision > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'SUPERSEDED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 1000000),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, surgery_case_id, readiness_snapshot_id)
);
CREATE INDEX idx_authority_invalidation_due ON surgery_authority_invalidation (next_attempt_at, surgery_case_id)
    WHERE status = 'PENDING';
