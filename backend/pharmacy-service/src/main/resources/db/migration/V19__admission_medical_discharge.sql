-- Keep medical discharge distinct from administrative close. Either fact may arrive before STARTED.
ALTER TABLE admission_medication_context
    ADD COLUMN medically_discharged_at TIMESTAMPTZ,
    ADD COLUMN source_medically_discharged_at VARCHAR(40),
    ADD COLUMN medical_discharge_fingerprint VARCHAR(64),
    ADD CONSTRAINT ck_admission_medication_medical_discharge CHECK (
        (medically_discharged_at IS NULL AND source_medically_discharged_at IS NULL
            AND medical_discharge_fingerprint IS NULL)
        OR (medically_discharged_at IS NOT NULL AND source_medically_discharged_at IS NOT NULL
            AND medical_discharge_fingerprint IS NOT NULL)
    ),
    ADD CONSTRAINT ck_admission_medication_medical_discharge_hash CHECK (
        medical_discharge_fingerprint IS NULL
        OR medical_discharge_fingerprint ~ '^[a-f0-9]{64}$'
    ),
    ADD CONSTRAINT ck_admission_medication_medical_discharge_source_time CHECK (
        CAST(source_medically_discharged_at AS TIMESTAMPTZ)
            IS NOT DISTINCT FROM medically_discharged_at
    ),
    ADD CONSTRAINT ck_admission_medication_medical_discharge_order CHECK (
        (started_at IS NULL OR medically_discharged_at IS NULL
            OR medically_discharged_at >= started_at)
        AND (medically_discharged_at IS NULL OR closed_at IS NULL
            OR closed_at >= medically_discharged_at)
    );
