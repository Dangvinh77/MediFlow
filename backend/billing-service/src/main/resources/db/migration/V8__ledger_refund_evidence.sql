-- Cashier reason is retained locally, never forwarded as clinical/free-text notification content.
CREATE TABLE ledger_refund_evidence (
    refund_transaction_id UUID PRIMARY KEY REFERENCES payment_transaction(transaction_id),
    original_transaction_id UUID NOT NULL REFERENCES payment_transaction(transaction_id),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ledger_refund_original ON ledger_refund_evidence(original_transaction_id);
CREATE INDEX idx_payment_transaction_original_completed
    ON payment_transaction(original_transaction_id, status, transaction_type);
