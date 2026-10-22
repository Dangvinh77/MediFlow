-- Local demo bookkeeping only; never modify business rows on startup replay.
CREATE TABLE local_demo_seed_receipt (
    seed_key VARCHAR(100) PRIMARY KEY,
    entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
