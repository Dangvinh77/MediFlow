CREATE TABLE PATIENT (
    patient_id UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(1) NOT NULL,
    identity_number VARCHAR(20) NOT NULL,
    address VARCHAR(255),
    phone_number VARCHAR(15),
    email VARCHAR(100),
    health_insurance_number VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_patient_identity_number UNIQUE (identity_number),
    CONSTRAINT ck_patient_gender CHECK (gender IN ('M', 'F'))
);

CREATE INDEX idx_patient_full_name ON PATIENT (full_name);
