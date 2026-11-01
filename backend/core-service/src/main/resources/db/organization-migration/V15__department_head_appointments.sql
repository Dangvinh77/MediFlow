-- No legacy department_head_id or MANAGER scope grants are promoted into appointments.
-- Public organization commands accept printable correlation IDs up to 120 characters.
ALTER TABLE organization_event_outbox ALTER COLUMN correlation_id TYPE VARCHAR(120);

CREATE TABLE department_head_state (
 department_id UUID PRIMARY KEY REFERENCES department(department_id),
 revision BIGINT NOT NULL CHECK (revision > 0),
 updated_at TIMESTAMPTZ NOT NULL,
 updated_by_account_id UUID NOT NULL REFERENCES account(account_id)
);
CREATE TABLE department_head_appointment (
 appointment_id UUID PRIMARY KEY,
 position_type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT_HEAD' CHECK(position_type='DEPARTMENT_HEAD'),
 department_id UUID NOT NULL REFERENCES department(department_id),
 staff_id UUID NOT NULL REFERENCES staff(staff_id),
 account_id UUID NOT NULL REFERENCES account(account_id),
 started_at TIMESTAMPTZ NOT NULL,
 ended_at TIMESTAMPTZ,
 CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE UNIQUE INDEX uk_head_active_department ON department_head_appointment(department_id) WHERE ended_at IS NULL;
CREATE UNIQUE INDEX uk_head_active_staff ON department_head_appointment(staff_id) WHERE ended_at IS NULL;
CREATE UNIQUE INDEX uk_head_active_account ON department_head_appointment(account_id) WHERE ended_at IS NULL;
CREATE TABLE department_head_audit (
 audit_id UUID PRIMARY KEY,
 department_id UUID NOT NULL REFERENCES department(department_id),
 revision BIGINT NOT NULL,
 previous_revision BIGINT NOT NULL,
 previous_appointment_id UUID REFERENCES department_head_appointment(appointment_id),
 appointment_id UUID REFERENCES department_head_appointment(appointment_id),
 actor_account_id UUID NOT NULL REFERENCES account(account_id),
 reason VARCHAR(500) NOT NULL CHECK(length(reason) BETWEEN 1 AND 500),
 occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(department_id,revision)
);
CREATE TABLE department_head_receipt (
 actor_account_id UUID NOT NULL REFERENCES account(account_id),
 department_id UUID NOT NULL REFERENCES department(department_id),
 idempotency_key VARCHAR(120) NOT NULL,
 command TEXT NOT NULL,
 response TEXT NOT NULL,
 PRIMARY KEY(actor_account_id,department_id,idempotency_key)
);
