CREATE TABLE staff_creation_receipt (
    actor_account_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    staff_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_staff_creation_receipt PRIMARY KEY (actor_account_id, idempotency_key),
    CONSTRAINT fk_staff_creation_receipt_staff FOREIGN KEY (staff_id) REFERENCES staff(staff_id)
);
