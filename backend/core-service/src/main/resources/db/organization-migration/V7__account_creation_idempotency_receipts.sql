CREATE TABLE account_creation_receipt (
    actor_account_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_account_creation_receipt PRIMARY KEY (actor_account_id, idempotency_key),
    CONSTRAINT fk_account_creation_receipt_account FOREIGN KEY (account_id) REFERENCES account(account_id)
);
