-- Payment proof local to pharmacy. No foreign keys cross bounded contexts.
CREATE TABLE PAYMENT_RECEIPT (
    receipt_id            UUID PRIMARY KEY,
    event_id              UUID NOT NULL UNIQUE,
    invoice_id            UUID NOT NULL,
    prescription_id      UUID NOT NULL,
    patient_id            UUID NOT NULL,
    department_id         UUID NOT NULL,
    total_amount          DECIMAL(15,2) NOT NULL,
    payment_method        VARCHAR(50),
    payment_occurred_at   TIMESTAMPTZ NOT NULL,
    correlation_id        VARCHAR(200) NOT NULL,
    payload_fingerprint   VARCHAR(128),
    status                VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    failure_code          VARCHAR(100),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    CONSTRAINT ck_payment_receipt_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT ck_payment_receipt_status CHECK (status IN ('RECEIVED', 'DISPENSED', 'COMPENSATED'))
);

CREATE INDEX idx_payment_receipt_prescription_status
    ON PAYMENT_RECEIPT (prescription_id, status);

-- Business-key uniqueness is intentionally deferred until Billing confirms whether the key is
-- invoiceId, paymentId, or an attempt tuple. event_id remains the only contract-safe unique key.
