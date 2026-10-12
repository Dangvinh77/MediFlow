-- Opt-in EXAM charge issuance (CONTRACT-CARE-BILLING-01, HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE).
-- One appointment.status.changed PENDING->AWAITING_PAYMENT check-in fact issues at most one EXAM
-- CHARGE + PAYMENT_REQUEST, keyed by its exact appointmentId. Mirrors V12's lab-test dedupe shape;
-- unlike V12's labId/sourceOrderId pair, an appointmentId already identifies exactly one outpatient
-- episode, so no separate order-id column is needed.
CREATE TABLE exam_charge_delivery (
    event_id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE exam_charge_source (
    appointment_id UUID PRIMARY KEY,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    payment_request_id UUID NOT NULL UNIQUE REFERENCES PAYMENT_REQUEST(payment_request_id),
    requested_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE exam_charge_delivery ADD CONSTRAINT fk_exam_charge_delivery_appointment
    FOREIGN KEY(appointment_id) REFERENCES exam_charge_source(appointment_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_exam_charge_delivery_appointment ON exam_charge_delivery(appointment_id);
