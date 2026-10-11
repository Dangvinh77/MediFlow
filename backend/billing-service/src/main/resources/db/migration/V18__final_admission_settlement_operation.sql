-- Initial paid-in-full final operation; estimates retain their independent V14 namespace.
CREATE TABLE final_admission_settlement_operation (
    recognition_operation_id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL UNIQUE REFERENCES settlement(settlement_id),
    account_id UUID NOT NULL UNIQUE REFERENCES billing_account(account_id),
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    completed_at TIMESTAMPTZ NOT NULL,
    payload TEXT NOT NULL
);
