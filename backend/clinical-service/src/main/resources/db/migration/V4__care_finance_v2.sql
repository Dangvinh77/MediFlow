ALTER TABLE appointment
    ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN exam_clearance_id UUID,
    ADD COLUMN exam_clearance_at TIMESTAMPTZ,
    ADD COLUMN emergency_override_id UUID,
    ADD COLUMN exam_price_code VARCHAR(64),
    ADD COLUMN checked_in_at TIMESTAMPTZ,
    ADD COLUMN examination_started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE appointment
    ADD CONSTRAINT ck_appointment_contract_version
        CHECK (care_contract_version IN (0, 1)),
    ADD CONSTRAINT ck_appointment_v1_price
        CHECK (care_contract_version = 0 OR exam_price_code IS NOT NULL),
    ADD CONSTRAINT ck_appointment_exam_gate
        CHECK (care_contract_version = 0
            OR status NOT IN ('READY_FOR_EXAM', 'IN_EXAM', 'COMPLETED')
            OR exam_clearance_id IS NOT NULL OR emergency_override_id IS NOT NULL);

ALTER TABLE medical_record
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN disposition VARCHAR(32),
    ADD COLUMN disposition_note TEXT,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE medical_record
    ADD CONSTRAINT ck_medical_record_status
        CHECK (status IN ('OPEN', 'COMPLETED')),
    ADD CONSTRAINT ck_medical_record_completion
        CHECK ((status = 'OPEN' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND disposition IS NOT NULL AND completed_at IS NOT NULL));

CREATE TABLE exam_clearance (
    clearance_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    appointment_id UUID,
    record_id UUID,
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT'),
    care_episode_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exam_clearance_target CHECK (appointment_id IS NOT NULL OR record_id IS NOT NULL)
);
CREATE INDEX idx_exam_clearance_appointment
    ON exam_clearance(appointment_id) WHERE appointment_id IS NOT NULL;
CREATE INDEX idx_exam_clearance_record
    ON exam_clearance(record_id) WHERE appointment_id IS NULL AND record_id IS NOT NULL;

CREATE TABLE clinical_emergency_override (
    override_id UUID PRIMARY KEY,
    appointment_id UUID REFERENCES appointment(appointment_id),
    record_id UUID REFERENCES medical_record(record_id),
    patient_id UUID NOT NULL,
    care_episode_id UUID NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinical_override_target CHECK (appointment_id IS NOT NULL OR record_id IS NOT NULL)
);

CREATE TABLE admission_referral (
    admission_request_id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record(record_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    diagnosis_summary TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    emergency BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_admission_referral_record UNIQUE(record_id)
);

CREATE TABLE clinical_outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_clinical_outbox_unpublished
    ON clinical_outbox_event(occurred_at) WHERE published_at IS NULL;
