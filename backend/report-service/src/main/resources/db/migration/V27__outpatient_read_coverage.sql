-- Empty by default. No data backfill, automatic publication or inferred coverage from zero events.
-- Owner-reviewed source-export/reconciliation acceptance must populate this in an audited rollout.
CREATE TABLE report_outpatient_coverage (
    coverage_id UUID PRIMARY KEY,
    covered_from DATE NOT NULL,
    covered_to DATE NOT NULL CHECK (covered_to >= covered_from AND covered_to-covered_from < 366),
    department_id UUID,
    zone_id VARCHAR(80) NOT NULL CHECK (btrim(zone_id) <> ''),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'),
    source_references JSONB NOT NULL CHECK (jsonb_typeof(source_references) = 'object'),
    source_observed_at TIMESTAMPTZ NOT NULL,
    reconciled_at TIMESTAMPTZ NOT NULL CHECK (reconciled_at >= source_observed_at),
    accepted_at TIMESTAMPTZ NOT NULL CHECK (accepted_at >= reconciled_at),
    accepted_by UUID NOT NULL,
    acceptance_reference VARCHAR(255) NOT NULL CHECK (btrim(acceptance_reference) <> ''),
    local_evidence_fingerprint VARCHAR(64) NOT NULL CHECK (local_evidence_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_outpatient_coverage_scope ON report_outpatient_coverage
    (department_id,zone_id,currency,covered_from,covered_to,accepted_at);
