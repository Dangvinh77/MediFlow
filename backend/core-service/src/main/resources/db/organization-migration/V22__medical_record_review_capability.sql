ALTER TABLE staff_capability_grant
    DROP CONSTRAINT staff_capability_grant_capability_check;

ALTER TABLE staff_capability_grant
    ADD CONSTRAINT ck_staff_capability_grant_capability
        CHECK (capability IN ('LAB_RESULT_RELEASE', 'MEDICAL_RECORD_REVIEW'));
