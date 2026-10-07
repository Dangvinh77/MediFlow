-- Internal request fence only. No clinical templates/policy defaults or outbox release.
CREATE TABLE surgery_creation_receipt (
    surgery_request_id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL UNIQUE,
    intent_fingerprint CHAR(64) NOT NULL CHECK (intent_fingerprint ~ '^[a-f0-9]{64}$'),
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING', 'COMPLETED')),
    surgery_case_id UUID UNIQUE REFERENCES surgery_case(surgery_case_id),
    checklist_snapshot_id UUID UNIQUE REFERENCES preop_checklist_snapshot(checklist_snapshot_id),
    completed_at_iso VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_creation_receipt_result CHECK (
        (state = 'PENDING' AND surgery_case_id IS NULL AND checklist_snapshot_id IS NULL AND completed_at_iso IS NULL)
        OR (state = 'COMPLETED' AND surgery_case_id IS NOT NULL AND checklist_snapshot_id IS NOT NULL AND completed_at_iso IS NOT NULL))
);
