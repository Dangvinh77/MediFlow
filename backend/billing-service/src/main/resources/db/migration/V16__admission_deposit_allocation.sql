-- Append-only audited allocation operation. It is not a final settlement or revenue source.
CREATE TABLE deposit_allocation_operation (
    allocation_operation_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES billing_account(account_id),
    original_transaction_id UUID NOT NULL REFERENCES payment_transaction(transaction_id),
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    intent_hash CHAR(64) NOT NULL CHECK (intent_hash ~ '^[0-9a-f]{64}$'),
    allocated_at TIMESTAMPTZ NOT NULL,
    payload TEXT NOT NULL
);
