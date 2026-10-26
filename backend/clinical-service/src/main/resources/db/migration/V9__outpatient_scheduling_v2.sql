ALTER TABLE appointment DROP CONSTRAINT ck_appointment_contract_version;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_contract_version CHECK (care_contract_version IN (0, 1, 2));
ALTER TABLE appointment DROP CONSTRAINT ck_appointment_v1_price;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_contract_price
    CHECK (care_contract_version = 0 OR exam_price_code IS NOT NULL);
ALTER TABLE appointment DROP CONSTRAINT ck_appointment_exam_gate;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_exam_gate
    CHECK (care_contract_version <> 1
        OR status NOT IN ('READY_FOR_EXAM', 'IN_EXAM', 'COMPLETED')
        OR exam_clearance_id IS NOT NULL OR emergency_override_id IS NOT NULL);

CREATE TABLE consultation_session (
    session_id UUID PRIMARY KEY,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    doctor_id UUID NOT NULL,
    department_id UUID NOT NULL,
    room_id UUID NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    breaks JSONB NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 200),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'STOPPED', 'CANCELLED')),
    held_count INTEGER NOT NULL DEFAULT 0 CHECK (held_count >= 0),
    payment_locked_count INTEGER NOT NULL DEFAULT 0 CHECK (payment_locked_count >= 0),
    confirmed_count INTEGER NOT NULL DEFAULT 0 CHECK (confirmed_count >= 0),
    consumed_count INTEGER NOT NULL DEFAULT 0 CHECK (consumed_count >= 0),
    available_count INTEGER NOT NULL CHECK (available_count >= 0),
    next_queue_number INTEGER NOT NULL DEFAULT 1 CHECK (next_queue_number >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_consultation_session_time CHECK (
        start_time >= TIME '07:00' AND start_time < end_time AND end_time <= TIME '17:00'),
    CONSTRAINT ck_consultation_session_capacity CHECK (
        held_count + payment_locked_count + confirmed_count + consumed_count + available_count = capacity)
);
CREATE INDEX idx_consultation_session_department_date
    ON consultation_session(department_id, session_date, start_time, session_id);
CREATE INDEX idx_consultation_session_doctor_date
    ON consultation_session(doctor_id, session_date, start_time, session_id);

CREATE TABLE consultation_session_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES consultation_session(session_id),
    revision INTEGER NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'STATUS_CHANGED')),
    actor_account_id UUID NOT NULL,
    before_state JSONB,
    after_state JSONB NOT NULL,
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_consultation_session_audit_revision UNIQUE(session_id, revision)
);

CREATE TABLE outpatient_registration (
    registration_id UUID PRIMARY KEY REFERENCES appointment(appointment_id),
    session_id UUID NOT NULL REFERENCES consultation_session(session_id),
    patient_id UUID NOT NULL,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    reception_mode VARCHAR(16) NOT NULL CHECK (reception_mode IN ('AT_COUNTER', 'PREBOOKED')),
    visit_type VARCHAR(16) NOT NULL CHECK (visit_type = 'NEW_VISIT'),
    status VARCHAR(32) NOT NULL CHECK (status IN ('AWAITING_PAYMENT','CONFIRMED','IN_EXAM','COMPLETED','EXPIRED','CANCELLED','RECONCILIATION_REQUIRED')),
    reservation_id UUID NOT NULL UNIQUE,
    reservation_revision INTEGER NOT NULL CHECK (reservation_revision >= 1),
    reservation_status VARCHAR(32) NOT NULL CHECK (reservation_status IN ('HELD','PAYMENT_LOCKED','CONFIRMED','CONSUMED','EXPIRED','CANCELLED','RECONCILIATION_REQUIRED')),
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    checked_in_at TIMESTAMPTZ,
    queue_number INTEGER CHECK (queue_number >= 1),
    next_fence_token BIGINT NOT NULL DEFAULT 0 CHECK (next_fence_token >= 0),
    active_payment_operation_id UUID,
    active_fence_token BIGINT,
    locked_at TIMESTAMPTZ,
    reconcile_after TIMESTAMPTZ,
    transaction_id UUID,
    clearance_id UUID,
    payment_completed_at TIMESTAMPTZ,
    cancellation_actor_account_id UUID,
    cancellation_actor_staff_id UUID,
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outpatient_queue_number UNIQUE(session_id, queue_number),
    CONSTRAINT ck_outpatient_registration_queue CHECK (
        status NOT IN ('CONFIRMED','IN_EXAM','COMPLETED') OR queue_number IS NOT NULL),
    CONSTRAINT ck_outpatient_registration_cancel_audit CHECK (
        (status = 'CANCELLED' AND cancellation_actor_account_id IS NOT NULL
            AND cancellation_reason IS NOT NULL AND length(btrim(cancellation_reason)) BETWEEN 1 AND 500
            AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancellation_actor_account_id IS NULL
            AND cancellation_actor_staff_id IS NULL AND cancellation_reason IS NULL AND cancelled_at IS NULL)),
    CONSTRAINT ck_outpatient_payment_lock CHECK (
        (reservation_status = 'PAYMENT_LOCKED' AND active_payment_operation_id IS NOT NULL
            AND active_fence_token IS NOT NULL AND locked_at IS NOT NULL AND reconcile_after IS NOT NULL)
        OR (reservation_status <> 'PAYMENT_LOCKED'))
);
CREATE INDEX idx_outpatient_registration_patient
    ON outpatient_registration(patient_id, created_at DESC, registration_id);
CREATE INDEX idx_outpatient_registration_session
    ON outpatient_registration(session_id, created_at, registration_id);
CREATE INDEX idx_outpatient_registration_reconcile
    ON outpatient_registration(reconcile_after, registration_id)
    WHERE reservation_status = 'PAYMENT_LOCKED';
CREATE UNIQUE INDEX uq_outpatient_active_patient_session
    ON outpatient_registration(patient_id, session_id)
    WHERE status IN ('AWAITING_PAYMENT','CONFIRMED','IN_EXAM','RECONCILIATION_REQUIRED');

CREATE TABLE outpatient_payment_operation_receipt (
    payment_operation_id UUID PRIMARY KEY,
    payment_request_id UUID NOT NULL,
    registration_id UUID NOT NULL REFERENCES outpatient_registration(registration_id),
    reservation_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    source_type VARCHAR(16) NOT NULL CHECK (source_type = 'EXAM'),
    source_id UUID NOT NULL,
    care_episode_id UUID NOT NULL,
    expected_reservation_revision INTEGER NOT NULL CHECK (expected_reservation_revision >= 1),
    lease_reservation_revision INTEGER,
    fence_token BIGINT,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PAYMENT_LOCKED','COMPLETED','CLOSED_NOT_PAID')),
    locked_at TIMESTAMPTZ,
    reconcile_after TIMESTAMPTZ,
    transaction_id UUID,
    clearance_id UUID,
    completed_at TIMESTAMPTZ,
    close_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_outpatient_payment_receipt_resolution CHECK (
        (state = 'PAYMENT_LOCKED' AND lease_reservation_revision IS NOT NULL AND fence_token IS NOT NULL
            AND locked_at IS NOT NULL AND reconcile_after IS NOT NULL)
        OR (state = 'COMPLETED' AND transaction_id IS NOT NULL AND clearance_id IS NOT NULL
            AND completed_at IS NOT NULL AND fence_token IS NOT NULL)
        OR (state = 'CLOSED_NOT_PAID' AND close_reason IS NOT NULL AND fence_token IS NOT NULL))
);
CREATE INDEX idx_outpatient_payment_receipt_registration
    ON outpatient_payment_operation_receipt(registration_id, created_at);

CREATE TABLE outpatient_payment_operation_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    payment_operation_id UUID NOT NULL REFERENCES outpatient_payment_operation_receipt(payment_operation_id),
    state VARCHAR(24) NOT NULL CHECK (state IN ('PAYMENT_LOCKED','COMPLETED','CLOSED_NOT_PAID')),
    fence_token BIGINT NOT NULL,
    evidence JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE clinical_scheduling_command_receipt (
    actor_key VARCHAR(100) NOT NULL,
    http_method VARCHAR(8) NOT NULL,
    canonical_path VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    response_status SMALLINT NOT NULL,
    location VARCHAR(512),
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(actor_key, http_method, canonical_path, idempotency_key)
);
CREATE INDEX idx_clinical_scheduling_receipt_created_at
    ON clinical_scheduling_command_receipt(created_at);

CREATE TABLE outpatient_registration_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    registration_id UUID NOT NULL REFERENCES outpatient_registration(registration_id),
    registration_revision INTEGER NOT NULL,
    reservation_revision INTEGER NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_account_id UUID,
    actor_staff_id UUID,
    before_state JSONB,
    after_state JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outpatient_registration_audit_revision UNIQUE(registration_id, registration_revision)
);
