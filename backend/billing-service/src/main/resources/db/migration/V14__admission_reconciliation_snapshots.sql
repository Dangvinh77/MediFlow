-- Non-final reconciliation namespace; do not insert these rows into final SETTLEMENT.
CREATE TABLE admission_reconciliation (
    settlement_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES billing_account(account_id),
    settlement_version INTEGER NOT NULL CHECK (settlement_version > 0),
    supersedes_settlement_id UUID REFERENCES admission_reconciliation(settlement_id),
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    reconciled_at TIMESTAMPTZ NOT NULL,
    payload TEXT NOT NULL,
    CONSTRAINT uq_admission_reconciliation_version UNIQUE(account_id, settlement_version),
    CONSTRAINT ck_admission_reconciliation_predecessor CHECK (
        (settlement_version = 1 AND supersedes_settlement_id IS NULL)
        OR (settlement_version > 1 AND supersedes_settlement_id IS NOT NULL))
);
