CREATE TABLE tiep_nhan_su_kien_phau_thuat (
    receipt_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    operation_id UUID NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL CHECK (char_length(payload_fingerprint) = 64),
    surgery_case_id UUID NOT NULL,
    surgery_request_id UUID NOT NULL,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    case_revision INTEGER NOT NULL CHECK (case_revision >= 0),
    source_revision INTEGER CHECK (source_revision IS NULL OR source_revision >= 0),
    target_status VARCHAR(20) NOT NULL,
    summary TEXT,
    timeline_content TEXT,
    timeline_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    applied_at TIMESTAMPTZ,
    CONSTRAINT uq_surgery_business_operation UNIQUE(event_type, operation_id),
    CONSTRAINT ck_surgery_receipt_event_type CHECK (event_type IN
        ('surgery.case.created', 'surgery.ready', 'surgery.completed', 'surgery.cancelled')),
    CONSTRAINT ck_surgery_receipt_target_status CHECK (target_status IN
        ('REQUESTED', 'READY', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_surgery_receipt_timeline CHECK (
        (target_status IN ('COMPLETED', 'CANCELLED')
            AND timeline_content IS NOT NULL AND timeline_at IS NOT NULL)
        OR (target_status IN ('REQUESTED', 'READY') AND timeline_content IS NULL)
    )
);

CREATE INDEX idx_surgery_receipt_case_received
    ON tiep_nhan_su_kien_phau_thuat(surgery_case_id, received_at);
CREATE INDEX idx_surgery_receipt_pending
    ON tiep_nhan_su_kien_phau_thuat(surgery_case_id, received_at)
    WHERE applied_at IS NULL;
