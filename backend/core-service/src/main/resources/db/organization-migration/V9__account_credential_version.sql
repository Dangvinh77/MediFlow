-- Password changes revoke previously issued refresh credentials without storing tokens.
ALTER TABLE account ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE account ADD CONSTRAINT ck_account_credential_version CHECK (credential_version >= 0);
