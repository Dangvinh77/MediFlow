-- Capabilities an administrator grants to one staff member. Revocation keeps the row as the audit trail.
CREATE TABLE staff_capability_grant (
    grant_id          UUID         PRIMARY KEY,
    staff_id          UUID         NOT NULL REFERENCES staff (staff_id),
    capability        VARCHAR(40)  NOT NULL CHECK (capability IN ('LAB_RESULT_RELEASE')),
    granted_by        UUID         NOT NULL,
    grant_reason      VARCHAR(500) NOT NULL CHECK (btrim(grant_reason) <> ''),
    granted_at        TIMESTAMPTZ  NOT NULL,
    revoked_by        UUID,
    revocation_reason VARCHAR(500),
    revoked_at        TIMESTAMPTZ,
    CONSTRAINT staff_capability_grant_revocation_complete CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL AND revocation_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND btrim(revocation_reason) <> '')
    )
);

CREATE UNIQUE INDEX ux_staff_capability_grant_active
    ON staff_capability_grant (staff_id, capability) WHERE revoked_at IS NULL;
