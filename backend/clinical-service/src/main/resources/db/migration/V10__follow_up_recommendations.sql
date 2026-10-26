ALTER TABLE outpatient_registration
    ADD CONSTRAINT uq_outpatient_registration_patient_binding
        UNIQUE (registration_id, patient_id);

ALTER TABLE medical_record
    ADD CONSTRAINT uq_medical_record_follow_up_binding
        UNIQUE (record_id, patient_id, doctor_id, department_id, appointment_id);

CREATE TABLE follow_up_recommendation (
    recommendation_id UUID PRIMARY KEY,
    source_record_id UUID NOT NULL,
    source_registration_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    preferred_doctor_id UUID NOT NULL,
    recommended_from DATE NOT NULL,
    recommended_through DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    preparation_instructions VARCHAR(2000),
    created_by_account_id UUID NOT NULL,
    created_by_staff_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_follow_up_recommendation_source_record UNIQUE (source_record_id),
    CONSTRAINT uq_follow_up_recommendation_source_registration UNIQUE (source_registration_id),
    CONSTRAINT uq_follow_up_recommendation_registration_binding
        UNIQUE (recommendation_id, source_registration_id, patient_id),
    CONSTRAINT ck_follow_up_recommendation_window
        CHECK (recommended_through >= recommended_from),
    CONSTRAINT ck_follow_up_recommendation_reason
        CHECK (length(btrim(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_follow_up_recommendation_instructions
        CHECK (preparation_instructions IS NULL OR length(preparation_instructions) <= 2000),
    CONSTRAINT fk_follow_up_recommendation_record_binding
        FOREIGN KEY (source_record_id, patient_id, preferred_doctor_id, department_id, source_registration_id)
        REFERENCES medical_record (record_id, patient_id, doctor_id, department_id, appointment_id),
    CONSTRAINT fk_follow_up_recommendation_registration_binding
        FOREIGN KEY (source_registration_id, patient_id)
        REFERENCES outpatient_registration (registration_id, patient_id)
);

CREATE TABLE follow_up_recommendation_audit (
    audit_id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES follow_up_recommendation(recommendation_id),
    action VARCHAR(24) NOT NULL CHECK (action = 'CREATED'),
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL UNIQUE REFERENCES clinical_outbox_event(event_id),
    after_state JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_follow_up_recommendation_audit_action UNIQUE (recommendation_id, action)
);
CREATE INDEX idx_follow_up_recommendation_audit_occurred
    ON follow_up_recommendation_audit(occurred_at, audit_id);

ALTER TABLE outpatient_registration
    DROP CONSTRAINT outpatient_registration_visit_type_check,
    ADD COLUMN follow_up_reason VARCHAR(1000),
    ADD COLUMN previous_registration_id UUID,
    ADD COLUMN follow_up_recommendation_id UUID,
    ADD COLUMN doctor_replacement_reason VARCHAR(1000),
    ADD COLUMN patient_agreed_to_doctor_replacement BOOLEAN,
    ADD CONSTRAINT fk_outpatient_previous_registration
        FOREIGN KEY (previous_registration_id, patient_id)
        REFERENCES outpatient_registration (registration_id, patient_id),
    ADD CONSTRAINT fk_outpatient_follow_up_recommendation
        FOREIGN KEY (follow_up_recommendation_id, previous_registration_id, patient_id)
        REFERENCES follow_up_recommendation (recommendation_id, source_registration_id, patient_id),
    ADD CONSTRAINT ck_outpatient_registration_visit_type
        CHECK (
            (visit_type = 'NEW_VISIT'
                AND previous_registration_id IS NULL
                AND follow_up_recommendation_id IS NULL
                AND doctor_replacement_reason IS NULL
                AND patient_agreed_to_doctor_replacement IS NULL)
            OR
            (visit_type = 'FOLLOW_UP'
                AND previous_registration_id IS NOT NULL
                AND (
                    (doctor_replacement_reason IS NULL AND patient_agreed_to_doctor_replacement IS NULL)
                    OR
                    (doctor_replacement_reason IS NOT NULL
                        AND length(btrim(doctor_replacement_reason)) BETWEEN 1 AND 1000
                        AND patient_agreed_to_doctor_replacement IS TRUE)
                ))
        );

ALTER TABLE outpatient_payment_operation_receipt
    ADD COLUMN basis VARCHAR(24) NOT NULL DEFAULT 'PATIENT_PAYMENT',
    ADD COLUMN quote_id UUID,
    DROP CONSTRAINT ck_outpatient_payment_receipt_resolution,
    ADD CONSTRAINT ck_outpatient_payment_receipt_basis CHECK (
        basis = 'PATIENT_PAYMENT' OR (basis = 'ZERO_LIABILITY' AND quote_id IS NOT NULL)),
    ADD CONSTRAINT ck_outpatient_payment_receipt_resolution CHECK (
        (state = 'PAYMENT_LOCKED' AND lease_reservation_revision IS NOT NULL AND fence_token IS NOT NULL
            AND locked_at IS NOT NULL AND reconcile_after IS NOT NULL)
        OR (state = 'COMPLETED' AND clearance_id IS NOT NULL AND completed_at IS NOT NULL AND fence_token IS NOT NULL
            AND ((basis = 'PATIENT_PAYMENT' AND transaction_id IS NOT NULL) OR (basis = 'ZERO_LIABILITY' AND transaction_id IS NULL)))
        OR (state = 'CLOSED_NOT_PAID' AND close_reason IS NOT NULL AND fence_token IS NOT NULL
            AND transaction_id IS NULL AND clearance_id IS NULL AND completed_at IS NULL));

CREATE FUNCTION reject_follow_up_snapshot_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Follow-up recommendation snapshots and audit rows are immutable'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_follow_up_recommendation_immutable
    BEFORE UPDATE OR DELETE ON follow_up_recommendation
    FOR EACH ROW EXECUTE FUNCTION reject_follow_up_snapshot_mutation();

CREATE TRIGGER trg_follow_up_recommendation_audit_immutable
    BEFORE UPDATE OR DELETE ON follow_up_recommendation_audit
    FOR EACH ROW EXECUTE FUNCTION reject_follow_up_snapshot_mutation();

CREATE FUNCTION reject_outpatient_follow_up_link_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.visit_type IS DISTINCT FROM NEW.visit_type
        OR OLD.follow_up_reason IS DISTINCT FROM NEW.follow_up_reason
        OR OLD.previous_registration_id IS DISTINCT FROM NEW.previous_registration_id
        OR OLD.follow_up_recommendation_id IS DISTINCT FROM NEW.follow_up_recommendation_id
        OR OLD.doctor_replacement_reason IS DISTINCT FROM NEW.doctor_replacement_reason
        OR OLD.patient_agreed_to_doctor_replacement IS DISTINCT FROM NEW.patient_agreed_to_doctor_replacement THEN
        RAISE EXCEPTION 'Outpatient follow-up linkage is immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outpatient_follow_up_link_immutable
    BEFORE UPDATE OF visit_type, follow_up_reason, previous_registration_id, follow_up_recommendation_id,
        doctor_replacement_reason, patient_agreed_to_doctor_replacement ON outpatient_registration
    FOR EACH ROW EXECUTE FUNCTION reject_outpatient_follow_up_link_mutation();
