CREATE TABLE attached_result (
    record_id UUID NOT NULL REFERENCES medical_record(record_id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('LAB', 'PRESCRIPTION')),
    reference_id UUID NOT NULL,
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (record_id, type, reference_id)
);

CREATE INDEX idx_attached_result_reference ON attached_result (type, reference_id);
