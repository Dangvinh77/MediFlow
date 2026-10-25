-- Receipts survive edits/deletion of a demo drug: restarting must never restore old stock.
-- No FK intentionally: deleting a demo drug must not delete its completed seed receipt.
CREATE TABLE local_demo_drug_seed_receipt (
    seed_key VARCHAR(100) PRIMARY KEY,
    drug_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
