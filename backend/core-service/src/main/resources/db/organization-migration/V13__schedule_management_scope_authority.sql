CREATE TABLE schedule_management_scope (
    account_id UUID PRIMARY KEY REFERENCES account(account_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_account_id UUID NOT NULL REFERENCES account(account_id)
);

CREATE TABLE schedule_management_scope_department (
    account_id UUID NOT NULL REFERENCES schedule_management_scope(account_id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES department(department_id),
    PRIMARY KEY (account_id, department_id)
);

CREATE INDEX idx_schedule_management_scope_department_department
    ON schedule_management_scope_department(department_id, account_id);

CREATE TABLE schedule_management_scope_audit (
    audit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    previous_revision BIGINT NOT NULL CHECK (previous_revision >= 0),
    actor_account_id UUID NOT NULL REFERENCES account(account_id),
    reason VARCHAR(500) NOT NULL CHECK (length(reason) BETWEEN 1 AND 500),
    previous_department_ids TEXT NOT NULL,
    department_ids TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_schedule_scope_audit_revision UNIQUE (account_id, revision)
);

CREATE TABLE schedule_management_scope_command_receipt (
    actor_account_id UUID NOT NULL REFERENCES account(account_id),
    account_id UUID NOT NULL REFERENCES account(account_id),
    method VARCHAR(10) NOT NULL CHECK (method = 'PUT'),
    canonical_path VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 120),
    expected_revision BIGINT NOT NULL CHECK (expected_revision >= 0),
    department_ids TEXT NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (length(reason) BETWEEN 1 AND 500),
    response_revision BIGINT NOT NULL CHECK (response_revision >= 1),
    response_department_ids TEXT NOT NULL,
    response_updated_at TIMESTAMPTZ NOT NULL,
    response_updated_by_account_id UUID NOT NULL REFERENCES account(account_id),
    response_status INTEGER NOT NULL CHECK (response_status = 200),
    response_location VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_schedule_scope_command_idempotency
        UNIQUE (actor_account_id, method, canonical_path, idempotency_key)
);

CREATE INDEX idx_schedule_scope_command_receipt_created_at
    ON schedule_management_scope_command_receipt(created_at);
