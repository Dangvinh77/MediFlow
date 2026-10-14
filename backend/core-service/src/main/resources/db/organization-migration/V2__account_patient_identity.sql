ALTER TABLE account
    ADD COLUMN patient_id UUID;

ALTER TABLE account
    ADD CONSTRAINT ck_account_identity_owner
    CHECK (
        (role = 'PATIENT' AND patient_id IS NOT NULL AND staff_id IS NULL)
        OR (role <> 'PATIENT' AND patient_id IS NULL)
    );
