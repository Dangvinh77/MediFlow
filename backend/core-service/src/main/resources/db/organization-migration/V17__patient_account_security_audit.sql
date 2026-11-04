CREATE TABLE patient_account_security_audit (
    audit_id UUID PRIMARY KEY,
    actor_account_id UUID NOT NULL REFERENCES account(account_id),
    patient_id UUID NOT NULL,
    target_account_id UUID NOT NULL REFERENCES account(account_id),
    operation VARCHAR(40) NOT NULL,
    correlation_id UUID NOT NULL,
    operation_key VARCHAR(220) UNIQUE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_patient_account_security_operation
        CHECK (operation IN ('PATIENT_PASSWORD_RESET', 'PATIENT_MAIL_RETRY'))
);

CREATE INDEX ix_patient_account_security_audit_patient
    ON patient_account_security_audit(patient_id, occurred_at DESC);
CREATE INDEX ix_patient_account_security_audit_actor
    ON patient_account_security_audit(actor_account_id, occurred_at DESC);
