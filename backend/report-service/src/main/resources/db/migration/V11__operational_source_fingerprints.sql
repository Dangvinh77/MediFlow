-- A new delivery ID must not hide changed producer evidence for the same business operation.
-- Keep hashes only: the replay journal deliberately does not retain clinical payloads.
CREATE TABLE operational_source_snapshot (
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    source_revision INTEGER NOT NULL CHECK (source_revision > 0),
    event_type VARCHAR(100) NOT NULL,
    first_event_id UUID NOT NULL,
    payload_fingerprint VARCHAR(64),
    evidence_state VARCHAR(24) NOT NULL,
    PRIMARY KEY(source_type,source_id,source_revision,event_type),
    CHECK ((evidence_state='VERIFIED_PAYLOAD' AND payload_fingerprint IS NOT NULL
            AND payload_fingerprint ~ '^[0-9a-f]{64}$')
        OR (evidence_state='LEGACY_UNVERIFIED' AND payload_fingerprint IS NULL))
);

-- Redacted old journals cannot reconstruct the original payload hash. Never fabricate one.
INSERT INTO operational_source_snapshot(source_type,source_id,source_revision,event_type,
    first_event_id,evidence_state)
SELECT DISTINCT ON (source_type,source_id,source_revision,event_type)
    source_type,source_id,source_revision,event_type,event_id,'LEGACY_UNVERIFIED'
FROM (
    SELECT source_type,source_id,source_revision,event_id,
        CASE metric_type
            WHEN 'COMPLETED_VISITS' THEN 'medicalrecord.completed'
            WHEN 'ADMISSIONS' THEN 'admission.started'
            WHEN 'DISCHARGES' THEN 'admission.closed'
            WHEN 'LAB_TESTS' THEN 'lab.result.created'
            WHEN 'DISPENSED_PRESCRIPTIONS' THEN 'prescription.filled'
            WHEN 'DISPENSED_UNITS' THEN 'prescription.filled'
            WHEN 'SURGERIES_COMPLETED' THEN 'surgery.completed'
            WHEN 'SURGERY_DURATION_MINUTES' THEN 'surgery.completed'
            WHEN 'SURGERIES_CANCELLED' THEN 'surgery.cancelled'
        END AS event_type
    FROM operational_contribution
) AS legacy
ORDER BY source_type,source_id,source_revision,event_type,event_id;
