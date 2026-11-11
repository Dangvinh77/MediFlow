-- A department head can end an amendment assignment; the row stays as history.
ALTER TABLE record_amendment_assignment
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by_staff_id UUID,
    ADD CONSTRAINT ck_record_amendment_assignment_revocation
        CHECK ((revoked_at IS NULL) = (revoked_by_staff_id IS NULL));
