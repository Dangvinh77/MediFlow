-- Empty by default. VERIFIED finite replay alone must NEVER insert publication/coverage.
-- A future owner-approved activation workflow must verify source exports, metric semantics,
-- completeness, timezone and retained history before publishing; there is no write/admin API here.
CREATE TABLE operational_report_publication (
    report_kind VARCHAR(12) PRIMARY KEY CHECK (report_kind IN ('DAILY', 'SURGERY')),
    generation_id UUID NOT NULL REFERENCES operational_replay_generation(generation_id),
    covered_from DATE NOT NULL,
    covered_to DATE NOT NULL CHECK (covered_to >= covered_from),
    zone_id VARCHAR(80) NOT NULL CHECK (length(btrim(zone_id)) > 0),
    metrics VARCHAR(40)[] NOT NULL CHECK (cardinality(metrics) > 0 AND array_position(metrics, NULL) IS NULL),
    acceptance_reference VARCHAR(255) NOT NULL CHECK (length(btrim(acceptance_reference)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
