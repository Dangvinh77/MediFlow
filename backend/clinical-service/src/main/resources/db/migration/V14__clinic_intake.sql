-- Department desk intake: per-room numbers, per-department counter numbers, queue corrections,
-- vital signs and reschedule evidence. Everything here is dormant until mediflow.v2.clinic-intake is on.
CREATE TABLE clinic_queue_ticket (
    ticket_id UUID PRIMARY KEY,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    registration_id UUID NOT NULL REFERENCES outpatient_registration(registration_id),
    session_id UUID NOT NULL,
    room_id UUID NOT NULL,
    department_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    queue_date DATE NOT NULL,
    room_queue_number INTEGER NOT NULL CHECK (room_queue_number >= 1),
    status VARCHAR(16) NOT NULL CHECK (status IN ('WAITING','CALLED','IN_EXAM','COMPLETED','ABSENT','CANCELLED')),
    priority BOOLEAN NOT NULL DEFAULT FALSE,
    issued_by_account_id UUID NOT NULL,
    issued_by_staff_id UUID,
    issued_at TIMESTAMPTZ NOT NULL,
    authority_scope_revision BIGINT,
    called_by_account_id UUID,
    called_at TIMESTAMPTZ,
    cancelled_by_account_id UUID,
    cancel_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT uq_clinic_queue_room_number UNIQUE (room_id, queue_date, room_queue_number),
    CONSTRAINT ck_clinic_queue_cancel_audit CHECK (
        status <> 'CANCELLED' OR cancel_reason IS NULL OR length(btrim(cancel_reason)) BETWEEN 1 AND 500)
);
CREATE UNIQUE INDEX uq_clinic_queue_active_registration
    ON clinic_queue_ticket(registration_id) WHERE status <> 'CANCELLED';
CREATE INDEX idx_clinic_queue_session ON clinic_queue_ticket(session_id, room_queue_number);

CREATE TABLE clinic_room_queue_counter (
    room_id UUID NOT NULL,
    queue_date DATE NOT NULL,
    next_number INTEGER NOT NULL CHECK (next_number >= 1),
    PRIMARY KEY (room_id, queue_date)
);

CREATE TABLE department_queue_counter (
    department_id UUID NOT NULL,
    queue_date DATE NOT NULL,
    next_number INTEGER NOT NULL CHECK (next_number >= 1),
    PRIMARY KEY (department_id, queue_date)
);

CREATE TABLE queue_adjustment (
    adjustment_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES clinic_queue_ticket(ticket_id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('CALLED','LATE','NO_SHOW','RETURNED','PRIORITY')),
    reason VARCHAR(500),
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_queue_adjustment_ticket ON queue_adjustment(ticket_id, created_at);

CREATE TABLE vital_sign_observation (
    observation_id UUID PRIMARY KEY,
    registration_id UUID NOT NULL REFERENCES outpatient_registration(registration_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    recorded_by_account_id UUID NOT NULL,
    recorded_by_staff_id UUID,
    pulse_bpm INTEGER,
    systolic_mmhg INTEGER,
    diastolic_mmhg INTEGER,
    temperature_c NUMERIC(4,1),
    respiratory_rate INTEGER,
    spo2_percent INTEGER,
    weight_kg NUMERIC(5,2),
    not_measured_reason VARCHAR(500)
);
CREATE INDEX idx_vital_sign_registration ON vital_sign_observation(registration_id, observed_at DESC);

CREATE TABLE appointment_reschedule_log (
    log_id UUID PRIMARY KEY,
    registration_id UUID NOT NULL REFERENCES outpatient_registration(registration_id),
    old_session_id UUID NOT NULL,
    new_session_id UUID NOT NULL,
    old_doctor_id UUID NOT NULL,
    new_doctor_id UUID NOT NULL,
    old_room_id UUID NOT NULL,
    new_room_id UUID NOT NULL,
    old_session_date DATE NOT NULL,
    new_session_date DATE NOT NULL,
    old_start_time TIME NOT NULL,
    new_start_time TIME NOT NULL,
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID,
    authority_scope_revision BIGINT,
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    patient_confirmed BOOLEAN NOT NULL CHECK (patient_confirmed),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reschedule_log_registration ON appointment_reschedule_log(registration_id, created_at);

CREATE FUNCTION reject_clinic_intake_history_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Clinic intake history is append-only' USING ERRCODE = '23514'; END $$;
CREATE TRIGGER immutable_queue_adjustment BEFORE UPDATE OR DELETE ON queue_adjustment
    FOR EACH ROW EXECUTE FUNCTION reject_clinic_intake_history_mutation();
CREATE TRIGGER immutable_vital_sign_observation BEFORE UPDATE OR DELETE ON vital_sign_observation
    FOR EACH ROW EXECUTE FUNCTION reject_clinic_intake_history_mutation();
CREATE TRIGGER immutable_appointment_reschedule_log BEFORE UPDATE OR DELETE ON appointment_reschedule_log
    FOR EACH ROW EXECUTE FUNCTION reject_clinic_intake_history_mutation();
