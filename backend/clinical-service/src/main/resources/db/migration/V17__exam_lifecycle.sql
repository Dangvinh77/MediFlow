-- Wave 3: a visit can end without a conclusion, and a new visit can point at the one before it.
ALTER TABLE medical_record ALTER COLUMN status TYPE VARCHAR(32);

ALTER TABLE medical_record
    DROP CONSTRAINT ck_medical_record_status,
    DROP CONSTRAINT ck_medical_record_completion;

ALTER TABLE medical_record
    ADD COLUMN previous_record_id UUID REFERENCES medical_record(record_id),
    ADD COLUMN closure_reason VARCHAR(32),
    ADD COLUMN closure_note TEXT,
    ADD COLUMN closed_by_staff_id UUID,
    ADD COLUMN closed_at TIMESTAMPTZ;

ALTER TABLE medical_record
    ADD CONSTRAINT ck_medical_record_status
        CHECK (status IN ('OPEN', 'COMPLETED', 'CLOSED_WITHOUT_CONCLUSION')),
    ADD CONSTRAINT ck_medical_record_completion
        CHECK ((status = 'OPEN' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND disposition IS NOT NULL AND completed_at IS NOT NULL)
            OR (status = 'CLOSED_WITHOUT_CONCLUSION' AND disposition IS NULL AND completed_at IS NULL)),
    ADD CONSTRAINT ck_medical_record_closure_reason
        CHECK (closure_reason IS NULL
            OR closure_reason IN ('PATIENT_LEFT', 'PATIENT_REFUSED_EXAM', 'PATIENT_REFUSED_ORDER', 'OTHER')),
    ADD CONSTRAINT ck_medical_record_closure
        CHECK ((status = 'CLOSED_WITHOUT_CONCLUSION' AND closure_reason IS NOT NULL
                AND length(btrim(closure_note)) BETWEEN 1 AND 1000
                AND closed_by_staff_id IS NOT NULL AND closed_at IS NOT NULL)
            OR (status <> 'CLOSED_WITHOUT_CONCLUSION' AND closure_reason IS NULL AND closure_note IS NULL
                AND closed_by_staff_id IS NULL AND closed_at IS NULL));

CREATE INDEX idx_medical_record_open_patient ON medical_record(patient_id) WHERE status = 'OPEN';
CREATE INDEX idx_medical_record_open_doctor ON medical_record(doctor_id) WHERE status = 'OPEN';

ALTER TABLE outpatient_registration
    ADD COLUMN return_source_record_id UUID REFERENCES medical_record(record_id),
    ADD COLUMN return_reason VARCHAR(500),
    ADD COLUMN independent_visit_reason VARCHAR(500);

ALTER TABLE outpatient_registration
    ADD CONSTRAINT ck_outpatient_registration_return_link
        CHECK ((return_source_record_id IS NULL AND return_reason IS NULL)
            OR (return_source_record_id IS NOT NULL AND length(btrim(return_reason)) > 0));
