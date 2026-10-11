-- Minimal producer authority for future final recognition. Never reconstruct historical completion.
CREATE TABLE surgery_completion_source (
    surgery_case_id UUID PRIMARY KEY REFERENCES surgery_charge_source(surgery_case_id),
    surgery_request_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES billing_account(account_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(24) NOT NULL CHECK (care_episode_type IN ('ADMISSION','OUTPATIENT_VISIT')),
    care_episode_id UUID NOT NULL,
    result_id UUID NOT NULL UNIQUE,
    case_revision INTEGER NOT NULL CHECK (case_revision > 0),
    source_revision INTEGER NOT NULL CHECK (source_revision=1),
    completed_at_iso VARCHAR(40) NOT NULL,
    recorded_at_iso VARCHAR(40) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE TABLE surgery_completion_delivery (
    event_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_completion_source(surgery_case_id),
    delivery_fingerprint CHAR(64) NOT NULL CHECK (delivery_fingerprint ~ '^[0-9a-f]{64}$')
);
