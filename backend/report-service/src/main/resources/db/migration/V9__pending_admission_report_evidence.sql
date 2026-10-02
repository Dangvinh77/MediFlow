-- Offline exact-admission evidence. No source_revision default, contribution or metric effect.
CREATE TABLE report_admission_target (
    admission_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE report_admission_delivery (
    event_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN ('admission.started', 'admission.closed')),
    envelope_fingerprint VARCHAR(64) NOT NULL CHECK (envelope_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_report_admission_delivery_target ON report_admission_delivery(admission_id);

CREATE TABLE report_admission_fact (
    admission_id UUID NOT NULL REFERENCES report_admission_target(admission_id),
    fact_type VARCHAR(16) NOT NULL CHECK (fact_type IN ('STARTED', 'CLOSED')),
    patient_id UUID NOT NULL,
    department_id UUID,
    bed_id UUID,
    business_at TIMESTAMPTZ NOT NULL,
    business_at_iso VARCHAR(35) NOT NULL,
    emergency BOOLEAN NOT NULL,
    emergency_override_id UUID,
    settlement_id UUID,
    approved_override_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (admission_id, fact_type),
    CONSTRAINT ck_report_admission_exact_time CHECK (business_at = CAST(business_at_iso AS TIMESTAMPTZ)),
    CONSTRAINT ck_report_admission_fact_shape CHECK (
        (fact_type = 'STARTED' AND department_id IS NOT NULL AND bed_id IS NOT NULL
            AND emergency = (emergency_override_id IS NOT NULL)
            AND settlement_id IS NULL AND approved_override_id IS NULL)
        OR (fact_type = 'CLOSED' AND department_id IS NULL AND bed_id IS NULL AND NOT emergency
            AND emergency_override_id IS NULL
            AND ((settlement_id IS NOT NULL) <> (approved_override_id IS NOT NULL)))
    )
);
CREATE INDEX ix_report_admission_fact_pending_close ON report_admission_fact(admission_id) WHERE fact_type = 'CLOSED';
