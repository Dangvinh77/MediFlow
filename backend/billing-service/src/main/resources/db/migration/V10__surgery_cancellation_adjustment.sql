-- Cancellation is an adjustment/refund obligation, NEVER evidence that cash was returned.
-- Do not backfill unverifiable master/V7 processed markers or change existing migration checksums.
CREATE TABLE surgery_cancellation_source (
    surgery_case_id UUID PRIMARY KEY,
    cancellation_id UUID NOT NULL UNIQUE,
    surgery_request_id UUID NOT NULL,
    first_event_id UUID NOT NULL UNIQUE,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    delivery_fingerprint CHAR(64) NOT NULL CHECK (delivery_fingerprint ~ '^[0-9a-f]{64}$'),
    correlation_id VARCHAR(120) NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type IN ('ADMISSION','OUTPATIENT_VISIT')),
    care_episode_id UUID NOT NULL,
    admission_id UUID,
    record_id UUID,
    case_revision BIGINT NOT NULL CHECK (case_revision > 0),
    cancellation_stage VARCHAR(24) NOT NULL CHECK (cancellation_stage IN ('BEFORE_PREOP','AFTER_PREOP','BEFORE_START')),
    cancelled_by UUID NOT NULL,
    cancelled_by_staff_id UUID,
    cancelled_at_iso VARCHAR(40) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPLIED','REJECTED')),
    rejection_code VARCHAR(80),
    retry_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    CHECK (length(btrim(correlation_id)) > 0),
    CHECK ((care_episode_type='ADMISSION' AND admission_id IS NOT NULL AND admission_id=care_episode_id)
        OR (care_episode_type='OUTPATIENT_VISIT' AND admission_id IS NULL AND record_id IS NOT NULL)),
    CHECK ((status='REJECTED') = (rejection_code IS NOT NULL)),
    CHECK ((status='APPLIED') = (applied_at IS NOT NULL))
);
CREATE INDEX idx_surgery_cancellation_due ON surgery_cancellation_source(retry_at,surgery_case_id) WHERE status='PENDING';
CREATE TABLE surgery_cancellation_delivery (
    event_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_cancellation_source(surgery_case_id) DEFERRABLE INITIALLY DEFERRED,
    delivery_fingerprint CHAR(64) NOT NULL CHECK (delivery_fingerprint ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_surgery_cancellation_delivery_case ON surgery_cancellation_delivery(surgery_case_id);
CREATE TABLE surgery_cancellation_refund_due (
    cancellation_id UUID NOT NULL REFERENCES surgery_cancellation_source(cancellation_id),
    original_transaction_id UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(cancellation_id,original_transaction_id,charge_id)
);
CREATE INDEX idx_surgery_cancellation_refund_original ON surgery_cancellation_refund_due(original_transaction_id);
