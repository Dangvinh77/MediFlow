-- Patient-level allergy list. Rows are never deleted; a mistaken entry is marked ENTERED_IN_ERROR with its reason.
CREATE TABLE patient_allergy (
    allergy_id                 UUID PRIMARY KEY,
    patient_id                 UUID         NOT NULL,
    substance                  VARCHAR(200) NOT NULL CHECK (length(btrim(substance)) > 0),
    substance_key              VARCHAR(200) NOT NULL CHECK (length(substance_key) > 0),
    reaction                   VARCHAR(500),
    severity                   VARCHAR(16)  NOT NULL CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE', 'UNKNOWN')),
    status                     VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'RESOLVED', 'ENTERED_IN_ERROR')),
    recorded_by_staff_id       UUID         NOT NULL,
    recorded_at                TIMESTAMPTZ  NOT NULL,
    source_record_id           UUID REFERENCES medical_record (record_id),
    status_reason              VARCHAR(500),
    status_changed_by_staff_id UUID,
    status_changed_at          TIMESTAMPTZ,
    CONSTRAINT ck_patient_allergy_status CHECK (
        (status = 'ACTIVE' AND status_reason IS NULL AND status_changed_by_staff_id IS NULL AND status_changed_at IS NULL)
        OR (status <> 'ACTIVE' AND status_reason IS NOT NULL AND status_changed_by_staff_id IS NOT NULL
            AND status_changed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_patient_allergy_active_substance ON patient_allergy (patient_id, substance_key) WHERE status = 'ACTIVE';
CREATE INDEX idx_patient_allergy_patient ON patient_allergy (patient_id, recorded_at DESC);
