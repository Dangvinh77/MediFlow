-- A legacy dispatcher/admin replay can never release held ledger contracts.
-- A reviewed activation migration is required to relax this constraint after every consumer passes.
ALTER TABLE BILLING_EVENT_OUTBOX ADD COLUMN contract_version INTEGER NOT NULL DEFAULT 0 CHECK (contract_version >= 0);
ALTER TABLE BILLING_EVENT_OUTBOX ADD CONSTRAINT ck_ledger_event_delivery_hold
    CHECK (contract_version = 0 OR publication_enabled = FALSE);
