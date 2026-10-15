CREATE TABLE clinical_lab_order (
    source_order_id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record(record_id),
    patient_id UUID NOT NULL,
    requesting_doctor_id UUID NOT NULL,
    requesting_department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT'),
    care_episode_id UUID NOT NULL REFERENCES appointment(appointment_id),
    lab_type VARCHAR(50) NOT NULL CHECK (length(btrim(lab_type)) > 0),
    requested_date DATE NOT NULL,
    price_code VARCHAR(64) NOT NULL CHECK (length(btrim(price_code)) > 0),
    ordered_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_clinical_lab_order_record ON clinical_lab_order(record_id, ordered_at);
