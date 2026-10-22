-- Independent receipt for the one-time enrichment of tracked demo masters.
CREATE TABLE local_demo_profile_receipt (
    seed_key VARCHAR(100) PRIMARY KEY,
    entity_id UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
