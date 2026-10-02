-- Only newly accepted V1 envelopes are journalled; this does not reconstruct pre-activation history.
-- Shadow writer only. Generation replay/read cutover needs a separate migration and implementation.
CREATE TABLE operational_event_journal (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_snapshot JSONB NOT NULL,
    contribution_snapshot JSONB NOT NULL,
    envelope_fingerprint VARCHAR(64) NOT NULL CHECK (envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    projection_fingerprint VARCHAR(64) NOT NULL CHECK (projection_fingerprint ~ '^[a-f0-9]{64}$'),
    projector_version INTEGER NOT NULL DEFAULT 1 CHECK (projector_version > 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE operational_delivery (
    event_id UUID NOT NULL REFERENCES operational_event_journal(event_id),
    metric_type VARCHAR(40) NOT NULL,
    fact_fingerprint VARCHAR(64) NOT NULL CHECK (fact_fingerprint ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY (event_id, metric_type)
);
-- NULL historical fingerprints are not silently interpreted as verified facts by the V2 adapter.
ALTER TABLE operational_contribution ADD COLUMN fact_fingerprint VARCHAR(64);
ALTER TABLE operational_contribution ADD CONSTRAINT ck_operational_fact_fingerprint CHECK (
    fact_fingerprint IS NULL OR fact_fingerprint ~ '^[a-f0-9]{64}$'
);
-- An operational source/metric has exactly one department. A changed department is a conflict,
-- not an allocation that may be counted again. Financial allocation keys remain unchanged.
CREATE UNIQUE INDEX uq_operational_source_metric ON operational_contribution
    (source_type, source_id, source_revision, metric_type);
ALTER TABLE daily_operational_report ADD COLUMN lab_tests BIGINT NOT NULL DEFAULT 0;
ALTER TABLE daily_operational_report ADD COLUMN dispensed_prescriptions BIGINT NOT NULL DEFAULT 0;
ALTER TABLE daily_operational_report ADD COLUMN dispensed_units BIGINT NOT NULL DEFAULT 0;
