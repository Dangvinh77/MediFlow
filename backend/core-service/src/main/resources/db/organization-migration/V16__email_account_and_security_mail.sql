-- Widen the existing unique login identifier so human accounts can use email.
ALTER TABLE account ALTER COLUMN username TYPE VARCHAR(254);
ALTER TABLE account ADD COLUMN email_migrated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE account ADD COLUMN temporary_password_expires_at TIMESTAMPTZ;

WITH candidates AS (
    SELECT account_id, lower(btrim(username)) AS email,
           count(*) OVER (PARTITION BY lower(btrim(username))) AS address_count
    FROM account
    WHERE role <> 'SYSTEM'
      AND username ~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
)
UPDATE account a SET username = c.email, email_migrated = TRUE
FROM candidates c
WHERE a.account_id = c.account_id
  AND c.address_count = 1
  AND NOT EXISTS (
      SELECT 1 FROM account other
      WHERE other.account_id <> a.account_id
        AND lower(btrim(other.username)) = c.email
  );

-- Migrate only unambiguous staff contacts. Missing or duplicate addresses remain untouched
-- and cannot authenticate through the email-only verifier until an ADMIN repairs them.
WITH candidates AS (
    SELECT a.account_id, lower(btrim(s.email)) AS email,
           count(*) OVER (PARTITION BY lower(btrim(s.email))) AS address_count
    FROM account a
    JOIN staff s ON s.staff_id = a.staff_id
    WHERE a.role NOT IN ('PATIENT', 'SYSTEM')
      AND s.email IS NOT NULL
      AND s.email ~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
)
UPDATE account a
SET username = c.email, email_migrated = TRUE
FROM candidates c
WHERE a.account_id = c.account_id
  AND c.address_count = 1
  AND NOT EXISTS (SELECT 1 FROM account other WHERE lower(btrim(other.username)) = c.email AND other.account_id <> a.account_id);

CREATE UNIQUE INDEX ux_account_migrated_email ON account (lower(username)) WHERE email_migrated = TRUE;

-- Explicit account-to-seed mapping prevents an edited real address from inheriting mailbox access.
CREATE TABLE local_demo_mailbox_account (
    account_id UUID PRIMARY KEY REFERENCES account(account_id),
    seed_key VARCHAR(100) NOT NULL UNIQUE REFERENCES local_demo_seed_receipt(seed_key),
    synthetic_email VARCHAR(254) NOT NULL UNIQUE
);

CREATE TABLE account_recovery_token (
    token_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    credential_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_recovery_account_created ON account_recovery_token(account_id, created_at DESC);

CREATE TABLE security_mail_message (
    message_id UUID PRIMARY KEY,
    operation_key VARCHAR(220) NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES account(account_id),
    sender_email VARCHAR(254) NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    message_kind VARCHAR(32) NOT NULL,
    subject VARCHAR(180) NOT NULL,
    encrypted_content BYTEA,
    content_expires_at TIMESTAMPTZ NOT NULL,
    demo_mailbox BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    last_failure_code VARCHAR(80),
    CONSTRAINT ck_security_mail_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED')),
    CONSTRAINT ck_security_mail_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_security_mail_dispatch ON security_mail_message(status, next_attempt_at, created_at);
CREATE INDEX ix_security_mail_account ON security_mail_message(account_id, created_at DESC);

CREATE TABLE security_mail_rate_limit (
    email_hash CHAR(64) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (email_hash, window_start),
    CONSTRAINT ck_security_mail_rate_count CHECK (request_count >= 0)
);

CREATE TABLE account_email_audit (
    audit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    changed_by_account_id UUID NOT NULL REFERENCES account(account_id),
    old_email VARCHAR(254),
    new_email VARCHAR(254) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_account_email_audit_account ON account_email_audit(account_id, changed_at DESC);

CREATE TABLE account_patient_email_sync (
    sync_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    patient_id UUID NOT NULL,
    target_email VARCHAR(254) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_failure_code VARCHAR(80),
    CONSTRAINT ck_account_patient_email_sync_status CHECK (status IN ('PENDING','SENDING','SENT')),
    UNIQUE(account_id,target_email)
);
CREATE INDEX ix_account_patient_email_sync_dispatch ON account_patient_email_sync(status,next_attempt_at,created_at);

CREATE TABLE staff_identity_audit (
    audit_id UUID PRIMARY KEY,
    staff_id UUID NOT NULL REFERENCES staff(staff_id),
    changed_by_account_id UUID NOT NULL REFERENCES account(account_id),
    reason VARCHAR(500) NOT NULL,
    old_identity_number VARCHAR(12) NOT NULL,
    new_identity_number VARCHAR(12) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_staff_identity_audit_staff ON staff_identity_audit(staff_id, changed_at DESC);
ALTER TABLE staff ALTER COLUMN email TYPE VARCHAR(254);
