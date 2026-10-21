-- Durable one-time admission receipts and terminal cancellation evidence for the owned
-- EXAM/LAB_TEST sources. All writers serialize on the BILLING_ACCOUNT row after the source lock.

ALTER TABLE lab_test_charge_source
    ADD COLUMN patient_id UUID,
    ADD COLUMN department_id UUID,
    ADD COLUMN record_id UUID,
    ADD COLUMN care_episode_type VARCHAR(32),
    ADD COLUMN care_episode_id UUID,
    ADD COLUMN price_code VARCHAR(64),
    ADD COLUMN lab_type VARCHAR(64),
    ADD COLUMN requested_at_iso VARCHAR(40);

ALTER TABLE exam_charge_source
    ADD COLUMN patient_id UUID,
    ADD COLUMN department_id UUID,
    ADD COLUMN care_episode_type VARCHAR(32),
    ADD COLUMN care_episode_id UUID,
    ADD COLUMN price_code VARCHAR(64),
    ADD COLUMN requested_at_iso VARCHAR(40);

-- Keep valid historical clearances readable by rebuilding only identity already fixed in Billing's
-- immutable request, account, target, charge, and source rows. Older Lab rows did not retain labType;
-- that value remains NULL and cancellation reconciliation must reject rather than guess it.
UPDATE exam_charge_source s SET patient_id=a.patient_id, department_id=a.department_id,
    care_episode_type=a.care_episode_type, care_episode_id=a.care_episode_id, price_code=c.price_code
FROM PAYMENT_REQUEST r
JOIN BILLING_ACCOUNT a ON a.account_id=r.account_id
JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
JOIN PAYMENT_REQUEST_CHARGE rc ON rc.payment_request_id=r.payment_request_id
JOIN CHARGE c ON c.charge_id=rc.charge_id
WHERE s.payment_request_id=r.payment_request_id AND r.purpose='EXAM'
  AND t.appointment_id=s.appointment_id AND t.record_id IS NULL AND t.prescription_id IS NULL
  AND t.admission_id IS NULL AND t.surgery_case_id IS NULL AND cardinality(t.lab_test_ids)=0
  AND c.source_type='EXAM' AND c.source_id=s.appointment_id;

UPDATE lab_test_charge_source s SET patient_id=a.patient_id, department_id=a.department_id,
    record_id=t.record_id, care_episode_type=a.care_episode_type, care_episode_id=a.care_episode_id,
    price_code=c.price_code
FROM PAYMENT_REQUEST r
JOIN BILLING_ACCOUNT a ON a.account_id=r.account_id
JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
JOIN PAYMENT_REQUEST_CHARGE rc ON rc.payment_request_id=r.payment_request_id
JOIN CHARGE c ON c.charge_id=rc.charge_id
WHERE s.payment_request_id=r.payment_request_id AND r.purpose='LAB_TEST'
  AND cardinality(t.lab_test_ids)=1 AND t.lab_test_ids[1]=s.lab_id
  AND t.appointment_id IS NULL AND t.prescription_id IS NULL AND t.admission_id IS NULL AND t.surgery_case_id IS NULL
  AND c.source_type='LAB_TEST' AND c.source_id=s.lab_id;

CREATE TABLE outpatient_execution_start (
    operation_id UUID PRIMARY KEY,
    clearance_id UUID NOT NULL REFERENCES FINANCIAL_CLEARANCE(clearance_id),
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('EXAM','LAB_TEST')),
    source_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    state VARCHAR(12) NOT NULL DEFAULT 'STARTED' CHECK (state='STARTED'),
    started_at TIMESTAMPTZ NOT NULL,
    UNIQUE(source_type, source_id),
    UNIQUE(clearance_id, source_type, source_id)
);
CREATE INDEX idx_outpatient_execution_start_account ON outpatient_execution_start(account_id, started_at);

CREATE TABLE outpatient_cancellation_source (
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('EXAM','LAB_TEST')),
    source_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL CHECK (event_type IN ('appointment.exam.cancelled','lab.request.cancelled')),
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    first_event_id UUID NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    source_order_id UUID,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    record_id UUID,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    price_code VARCHAR(64),
    lab_type VARCHAR(64),
    cancelled_by UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    cancelled_at_iso VARCHAR(40) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPLIED','REJECTED')),
    rejection_code VARCHAR(80),
    applied_at TIMESTAMPTZ,
    PRIMARY KEY(source_type, source_id),
    CHECK ((source_type='LAB_TEST' AND event_type='lab.request.cancelled' AND source_order_id IS NOT NULL AND lab_type IS NOT NULL AND price_code IS NOT NULL)
        OR (source_type='EXAM' AND event_type='appointment.exam.cancelled' AND source_order_id IS NULL AND record_id IS NULL AND lab_type IS NULL AND price_code IS NULL)),
    CHECK ((status='REJECTED') = (rejection_code IS NOT NULL)),
    CHECK ((status='APPLIED') = (applied_at IS NOT NULL))
);
CREATE TABLE outpatient_cancellation_delivery (
    event_id UUID PRIMARY KEY,
    source_type VARCHAR(16) NOT NULL,
    source_id UUID NOT NULL,
    delivery_fingerprint CHAR(64) NOT NULL CHECK (delivery_fingerprint ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(source_type, source_id) REFERENCES outpatient_cancellation_source(source_type, source_id)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_outpatient_cancellation_delivery_source ON outpatient_cancellation_delivery(source_type, source_id);

CREATE TABLE outpatient_cancellation_refund_due (
    source_type VARCHAR(16) NOT NULL,
    source_id UUID NOT NULL,
    original_transaction_id UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    PRIMARY KEY(source_type, source_id, original_transaction_id, charge_id),
    FOREIGN KEY(source_type, source_id) REFERENCES outpatient_cancellation_source(source_type, source_id)
);
CREATE INDEX idx_outpatient_cancellation_refund_original ON outpatient_cancellation_refund_due(original_transaction_id);
