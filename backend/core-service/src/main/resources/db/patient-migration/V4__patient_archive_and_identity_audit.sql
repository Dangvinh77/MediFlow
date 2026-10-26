ALTER TABLE patient ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE patient_identity_audit (
    audit_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patient(patient_id),
    actor_account_id UUID NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_identity_number VARCHAR(20) NOT NULL,
    current_identity_number VARCHAR(20) NOT NULL,
    previous_date_of_birth DATE NOT NULL,
    current_date_of_birth DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL
);
CREATE INDEX ix_patient_identity_audit_patient ON patient_identity_audit(patient_id, occurred_at);
