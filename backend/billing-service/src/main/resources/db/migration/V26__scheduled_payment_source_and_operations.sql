-- Billing-side source binding and durable operation receipts for the outpatient scheduling contract.
-- The scheduling gates stay off until the producer/consumer acceptance is complete.

CREATE TABLE outpatient_registration_event_receipt (
    event_id UUID PRIMARY KEY,
    registration_id UUID NOT NULL,
    registration_revision BIGINT NOT NULL CHECK (registration_revision > 0),
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    raw_envelope BYTEA NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outpatient_registration_receipt_source
    ON outpatient_registration_event_receipt(registration_id, registration_revision);

CREATE TABLE outpatient_registration_source (
    registration_id UUID PRIMARY KEY,
    registration_revision BIGINT NOT NULL CHECK (registration_revision > 0),
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    department_id UUID NOT NULL,
    session_id UUID NOT NULL,
    room_id UUID NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    visit_type VARCHAR(32) NOT NULL,
    reservation_id UUID NOT NULL,
    reservation_status VARCHAR(32) NOT NULL,
    registration_status VARCHAR(32) NOT NULL,
    checked_in_at TIMESTAMPTZ,
    queue_number INTEGER,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    price_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(16) NOT NULL CHECK (source_type = 'EXAM'),
    source_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT'),
    care_episode_id UUID NOT NULL,
    cancellation JSONB,
    changed_at TIMESTAMPTZ NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    payment_request_id UUID UNIQUE REFERENCES PAYMENT_REQUEST(payment_request_id),
    invoice_id UUID UNIQUE,
    account_id UUID REFERENCES BILLING_ACCOUNT(account_id),
    request_issue_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PRICE',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_outpatient_registration_source_ids CHECK
        (source_id = registration_id AND care_episode_id = registration_id),
    CONSTRAINT ck_outpatient_registration_status CHECK
        (registration_status IN ('AWAITING_PAYMENT','CONFIRMED','IN_EXAM','COMPLETED','CANCELLED','EXPIRED','RECONCILIATION_REQUIRED')),
    CONSTRAINT ck_outpatient_registration_reservation_status CHECK
        (reservation_status IN ('HELD','PAYMENT_LOCKED','CONFIRMED','CONSUMED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_outpatient_registration_visit_type CHECK
        (visit_type IN ('NEW_VISIT','FOLLOW_UP')),
    CONSTRAINT ck_outpatient_registration_request_binding CHECK
        ((payment_request_id IS NULL AND invoice_id IS NULL AND account_id IS NULL)
          OR (payment_request_id IS NOT NULL AND invoice_id IS NOT NULL AND account_id IS NOT NULL))
);

CREATE TABLE scheduled_payment_operation (
    payment_operation_id UUID PRIMARY KEY,
    payment_request_id UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id),
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    registration_id UUID NOT NULL REFERENCES outpatient_registration_source(registration_id),
    reservation_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    source_type VARCHAR(16) NOT NULL CHECK (source_type = 'EXAM'),
    source_id UUID NOT NULL,
    care_episode_id UUID NOT NULL,
    actor_account_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    command_fingerprint CHAR(64) NOT NULL CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    correlation_id VARCHAR(120) NOT NULL,
    expected_reservation_revision BIGINT NOT NULL CHECK (expected_reservation_revision > 0),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH','TRANSFER')),
    provider_reference VARCHAR(120),
    status VARCHAR(24) NOT NULL DEFAULT 'PREPARING',
    transaction_id UUID UNIQUE REFERENCES PAYMENT_TRANSACTION(transaction_id),
    clearance_id UUID UNIQUE REFERENCES FINANCIAL_CLEARANCE(clearance_id),
    fence_token BIGINT,
    lease_reservation_revision BIGINT,
    locked_at TIMESTAMPTZ,
    reconcile_after TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    resolution_applied_at TIMESTAMPTZ,
    close_reason VARCHAR(64),
    CONSTRAINT uq_scheduled_payment_request_key UNIQUE(payment_request_id, idempotency_key),
    CONSTRAINT ck_scheduled_payment_source_binding CHECK
        (source_id = registration_id AND care_episode_id = registration_id),
    CONSTRAINT ck_scheduled_payment_status CHECK
        (status IN ('PREPARING','COMPLETED','CLOSED_NOT_PAID')),
    CONSTRAINT ck_scheduled_payment_transaction_state CHECK
        ((status = 'COMPLETED') = (transaction_id IS NOT NULL)),
    CONSTRAINT ck_scheduled_payment_clearance_state CHECK
        ((status = 'COMPLETED') = (clearance_id IS NOT NULL)),
    CONSTRAINT ck_scheduled_payment_completed_state CHECK
        ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_scheduled_payment_close_reason CHECK
        ((status = 'CLOSED_NOT_PAID') = (close_reason IS NOT NULL)),
    CONSTRAINT ck_scheduled_payment_lease_shape CHECK
        ((fence_token IS NULL AND lease_reservation_revision IS NULL AND locked_at IS NULL AND reconcile_after IS NULL)
          OR (fence_token > 0 AND lease_reservation_revision > 0 AND locked_at IS NOT NULL AND reconcile_after IS NOT NULL))
);
CREATE UNIQUE INDEX uq_scheduled_payment_one_preparing_per_request
    ON scheduled_payment_operation(payment_request_id) WHERE status = 'PREPARING';
CREATE INDEX idx_scheduled_payment_recovery
    ON scheduled_payment_operation(status, updated_at, payment_operation_id);

CREATE FUNCTION protect_scheduled_payment_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.payment_operation_id, NEW.payment_request_id, NEW.account_id, NEW.registration_id, NEW.reservation_id,
           NEW.patient_id, NEW.source_type, NEW.source_id, NEW.care_episode_id, NEW.actor_account_id,
           NEW.idempotency_key, NEW.command_fingerprint, NEW.correlation_id, NEW.expected_reservation_revision,
           NEW.amount, NEW.currency, NEW.payment_method, NEW.provider_reference, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.payment_operation_id, OLD.payment_request_id, OLD.account_id, OLD.registration_id, OLD.reservation_id,
           OLD.patient_id, OLD.source_type, OLD.source_id, OLD.care_episode_id, OLD.actor_account_id,
           OLD.idempotency_key, OLD.command_fingerprint, OLD.correlation_id, OLD.expected_reservation_revision,
           OLD.amount, OLD.currency, OLD.payment_method, OLD.provider_reference, OLD.created_at) THEN
        RAISE EXCEPTION 'scheduled payment binding is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status <> 'PREPARING' AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION 'scheduled payment terminal status is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.fence_token IS NOT NULL AND ROW(NEW.fence_token, NEW.lease_reservation_revision, NEW.locked_at, NEW.reconcile_after)
       IS DISTINCT FROM ROW(OLD.fence_token, OLD.lease_reservation_revision, OLD.locked_at, OLD.reconcile_after) THEN
        RAISE EXCEPTION 'scheduled payment lease receipt is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'PREPARING' AND NEW.status = 'COMPLETED'
       AND (OLD.fence_token IS NULL OR NEW.fence_token IS NULL) THEN
        RAISE EXCEPTION 'scheduled payment completion requires persisted lease' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_protect_scheduled_payment_operation
    BEFORE UPDATE ON scheduled_payment_operation
    FOR EACH ROW EXECUTE FUNCTION protect_scheduled_payment_operation();
