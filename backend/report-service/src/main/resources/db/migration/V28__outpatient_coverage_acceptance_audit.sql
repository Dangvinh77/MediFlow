-- No backfill or inferred coverage. One immutable request receipt per reviewed acceptance.
CREATE TABLE report_outpatient_coverage_acceptance (
    request_id UUID PRIMARY KEY,
    coverage_id UUID NOT NULL UNIQUE REFERENCES report_outpatient_coverage(coverage_id),
    actor_id UUID NOT NULL,
    request_payload JSONB NOT NULL CHECK (jsonb_typeof(request_payload) = 'object'),
    coverage_payload JSONB NOT NULL CHECK (jsonb_typeof(coverage_payload) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
