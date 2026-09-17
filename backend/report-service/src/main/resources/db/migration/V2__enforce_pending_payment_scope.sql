ALTER TABLE PAYMENT_CONTRIBUTION
    DROP CONSTRAINT ck_payment_state_data;

ALTER TABLE PAYMENT_CONTRIBUTION
    ADD CONSTRAINT ck_payment_state_data CHECK (
        (status = 'PENDING_REVERSAL' AND failed_event_id IS NOT NULL AND completed_event_id IS NULL
            AND payment_date IS NULL AND department_id IS NULL AND amount IS NULL)
        OR (status = 'APPLIED' AND completed_event_id IS NOT NULL AND failed_event_id IS NULL
            AND payment_date IS NOT NULL AND amount IS NOT NULL)
        OR (status = 'REVERSED' AND completed_event_id IS NOT NULL AND failed_event_id IS NOT NULL
            AND payment_date IS NOT NULL AND amount IS NOT NULL)
    );
