ALTER TABLE appointment
    ADD COLUMN cancelled_by UUID,
    ADD COLUMN cancellation_reason TEXT,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE appointment
    ADD CONSTRAINT ck_appointment_v1_cancellation_audit
        CHECK (
            (care_contract_version = 1 AND status = 'CANCELLED'
                AND cancelled_by IS NOT NULL
                AND cancellation_reason IS NOT NULL
                AND btrim(cancellation_reason) <> ''
                AND char_length(cancellation_reason) <= 500
                AND cancelled_at IS NOT NULL
                AND cancelled_at >= created_at)
            OR
            ((care_contract_version <> 1 OR status <> 'CANCELLED')
                AND cancelled_by IS NULL
                AND cancellation_reason IS NULL
                AND cancelled_at IS NULL)
        );
