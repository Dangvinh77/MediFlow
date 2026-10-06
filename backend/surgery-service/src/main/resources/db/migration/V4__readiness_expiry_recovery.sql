-- Operational retry only; neither a clinical TTL nor permission to release started cases.
CREATE TABLE surgery_readiness_expiry_retry (
    surgery_case_id UUID PRIMARY KEY REFERENCES surgery_case(surgery_case_id),
    readiness_snapshot_id UUID NOT NULL REFERENCES surgery_readiness_snapshot(readiness_snapshot_id),
    attempt_count INTEGER NOT NULL CHECK (attempt_count BETWEEN 1 AND 1000000),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_readiness_expiry_due ON surgery_readiness_snapshot
    (valid_until, readiness_snapshot_id) WHERE valid_until IS NOT NULL;
CREATE INDEX idx_case_readiness_expiry_active ON surgery_case
    (readiness_snapshot_id, surgery_case_id) WHERE status IN ('READY', 'SCHEDULED');
