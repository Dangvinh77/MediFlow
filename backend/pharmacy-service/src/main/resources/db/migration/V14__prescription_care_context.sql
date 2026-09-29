-- Add explicit care context without assigning an episode to historical prescriptions.
ALTER TABLE PRESCRIPTION
    ALTER COLUMN record_id DROP NOT NULL,
    ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN care_context VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT',
    ADD COLUMN care_episode_type VARCHAR(32),
    ADD COLUMN care_episode_id UUID,
    ADD COLUMN admission_id UUID,
    ADD COLUMN price_code VARCHAR(64);

ALTER TABLE PRESCRIPTION
    ADD CONSTRAINT ck_prescription_contract_version
        CHECK (care_contract_version IN (0, 1)),
    ADD CONSTRAINT ck_prescription_care_context
        CHECK (care_context IN ('OUTPATIENT', 'ADMISSION')),
    ADD CONSTRAINT ck_prescription_care_metadata
        CHECK (
            (care_contract_version = 0
                AND record_id IS NOT NULL
                AND care_context = 'OUTPATIENT'
                AND care_episode_type IS NULL
                AND care_episode_id IS NULL
                AND admission_id IS NULL
                AND price_code IS NULL)
            OR
            (care_contract_version = 1
                AND care_episode_type IS NOT NULL
                AND care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')
                AND care_episode_id IS NOT NULL
                AND price_code IS NOT NULL
                AND btrim(price_code) <> ''
                AND (
                    (care_context = 'OUTPATIENT'
                        AND care_episode_type = 'OUTPATIENT_VISIT'
                        AND admission_id IS NULL)
                    OR
                    (care_context = 'ADMISSION'
                        AND care_episode_type = 'ADMISSION'
                        AND admission_id IS NOT NULL
                        AND admission_id = care_episode_id)
                ))
        );

CREATE INDEX idx_prescription_episode
    ON PRESCRIPTION(care_episode_type, care_episode_id);

CREATE INDEX idx_prescription_admission
    ON PRESCRIPTION(admission_id)
    WHERE admission_id IS NOT NULL;
