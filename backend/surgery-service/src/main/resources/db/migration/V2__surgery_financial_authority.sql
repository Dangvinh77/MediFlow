CREATE TABLE surgery_financial_clearance (
    clearance_id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    episode_type VARCHAR(32) NOT NULL CHECK (episode_type IN ('OUTPATIENT_VISIT','ADMISSION')),
    episode_id UUID NOT NULL,
    admission_id UUID,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    granted_at_iso VARCHAR(40) NOT NULL,
    expires_at_iso VARCHAR(40),
    fingerprint CHAR(64) NOT NULL,
    CHECK ((episode_type='ADMISSION' AND admission_id IS NOT NULL AND admission_id=episode_id)
        OR (episode_type='OUTPATIENT_VISIT' AND admission_id IS NULL))
);
CREATE INDEX idx_surgery_clearance_case ON surgery_financial_clearance(surgery_case_id,granted_at);
