-- Who read which medical record and on what basis. Denied attempts must survive, so no foreign key
-- ties a row to medical_record. Clinical content and tokens are never stored here.
CREATE TABLE record_access_log (
    log_id BIGSERIAL PRIMARY KEY,
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID,
    actor_role VARCHAR(32) NOT NULL,
    patient_id UUID NOT NULL,
    record_id UUID,
    action VARCHAR(16) NOT NULL CHECK (action IN ('READ', 'LIST', 'DECLARE')),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('ALLOWED', 'DENIED')),
    basis VARCHAR(32),
    purpose VARCHAR(32),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_record_access_log_patient ON record_access_log(patient_id, occurred_at);

CREATE TABLE record_access_declaration (
    declaration_id UUID PRIMARY KEY,
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID,
    actor_role VARCHAR(32) NOT NULL,
    patient_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    declared_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > declared_at)
);
CREATE INDEX idx_record_access_declaration_active
    ON record_access_declaration(actor_account_id, patient_id, expires_at);
