-- Wave 3 fix: a doctor may withdraw an order Lab never received, so it stops blocking the end of the visit.
ALTER TABLE clinical_lab_order
    ADD COLUMN withdrawn_at TIMESTAMPTZ,
    ADD COLUMN withdrawn_by_staff_id UUID,
    ADD COLUMN withdrawal_reason VARCHAR(500);

ALTER TABLE clinical_lab_order
    ADD CONSTRAINT ck_clinical_lab_order_withdrawal
        CHECK ((withdrawn_at IS NULL AND withdrawn_by_staff_id IS NULL AND withdrawal_reason IS NULL)
            OR (withdrawn_at IS NOT NULL AND withdrawn_by_staff_id IS NOT NULL
                AND length(btrim(withdrawal_reason)) BETWEEN 1 AND 500));
