CREATE TABLE lab_test (
    test_id UUID PRIMARY KEY,
    record_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    requesting_department_id UUID NOT NULL,
    test_type VARCHAR(50) NOT NULL,
    requested_date DATE NOT NULL,
    performed_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    conclusion TEXT,
    is_paid BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_lab_test_patient ON lab_test (patient_id);
CREATE INDEX idx_lab_test_record ON lab_test (record_id);
CREATE INDEX idx_lab_test_department ON lab_test (requesting_department_id);

CREATE TABLE lab_result (
    result_id UUID PRIMARY KEY,
    test_id UUID NOT NULL REFERENCES lab_test(test_id) ON DELETE CASCADE,
    indicator VARCHAR(100) NOT NULL,
    value VARCHAR(50) NOT NULL,
    unit VARCHAR(20),
    reference_range VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lab_result_test ON lab_result (test_id);

CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
