CREATE TABLE appointment (
    appointment_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    department_id UUID NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_appointment_patient ON appointment (patient_id);
CREATE INDEX idx_appointment_department_date ON appointment (department_id, appointment_date);
CREATE INDEX idx_appointment_patient_date_status ON appointment (patient_id, appointment_date, status);
CREATE UNIQUE INDEX uq_appointment_pending_patient_date
    ON appointment (patient_id, appointment_date) WHERE status = 'PENDING';

CREATE TABLE medical_record (
    record_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    department_id UUID NOT NULL,
    examination_date DATE NOT NULL,
    symptoms TEXT,
    appointment_id UUID REFERENCES appointment(appointment_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_medical_record_patient ON medical_record (patient_id);
CREATE INDEX idx_medical_record_department_date ON medical_record (department_id, examination_date);
CREATE UNIQUE INDEX uq_medical_record_appointment
    ON medical_record (appointment_id) WHERE appointment_id IS NOT NULL;

CREATE TABLE diagnosis (
    diagnosis_id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record(record_id) ON DELETE CASCADE,
    diagnosis_name VARCHAR(255) NOT NULL,
    description TEXT,
    icd_code VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_diagnosis_record ON diagnosis (record_id);
