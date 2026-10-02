-- Finite MVCC-visible journal manifest; never truncates live projections or switches their readers.
CREATE TABLE operational_replay_generation (
    generation_id UUID PRIMARY KEY,
    status VARCHAR(12) NOT NULL CHECK (status IN ('BUILDING', 'VERIFIED', 'FAILED')),
    projector_version INTEGER NOT NULL DEFAULT 1 CHECK (projector_version = 1),
    source_events BIGINT NOT NULL DEFAULT 0 CHECK (source_events >= 0),
    applied_events BIGINT NOT NULL DEFAULT 0 CHECK (applied_events >= 0 AND applied_events <= source_events),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CHECK ((status = 'BUILDING') = (completed_at IS NULL))
);

CREATE TABLE operational_replay_input (
    generation_id UUID NOT NULL REFERENCES operational_replay_generation(generation_id),
    event_id UUID NOT NULL,
    event_snapshot JSONB NOT NULL CHECK (jsonb_typeof(event_snapshot) = 'object'),
    contribution_snapshot JSONB NOT NULL CHECK (jsonb_typeof(contribution_snapshot) = 'array'),
    envelope_fingerprint CHAR(64) NOT NULL,
    projection_fingerprint CHAR(64) NOT NULL,
    projector_version INTEGER NOT NULL CHECK (projector_version > 0),
    applied BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (generation_id, event_id)
);
CREATE INDEX idx_operational_replay_pending ON operational_replay_input(generation_id, event_id) WHERE NOT applied;

CREATE TABLE operational_replay_contribution (
    generation_id UUID NOT NULL REFERENCES operational_replay_generation(generation_id),
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    source_revision INTEGER NOT NULL CHECK (source_revision = 1),
    metric_type VARCHAR(40) NOT NULL,
    fact_fingerprint CHAR(64) NOT NULL,
    fact_snapshot JSONB NOT NULL CHECK (jsonb_typeof(fact_snapshot) = 'object'),
    PRIMARY KEY (generation_id, source_type, source_id, source_revision, metric_type)
);

CREATE TABLE operational_replay_scope (
    generation_id UUID NOT NULL REFERENCES operational_replay_generation(generation_id),
    metric_date DATE NOT NULL,
    department_id UUID,
    metric_type VARCHAR(40) NOT NULL,
    numeric_value BIGINT NOT NULL CHECK (numeric_value >= 0),
    UNIQUE NULLS NOT DISTINCT (generation_id, metric_date, department_id, metric_type)
);
