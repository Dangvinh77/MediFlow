-- Historical cancellations deliberately retain NULL audit values; do not invent an actor or reason.
ALTER TABLE lab_test ADD COLUMN cancelled_by UUID;
ALTER TABLE lab_test ADD COLUMN cancellation_reason VARCHAR(1000);
ALTER TABLE lab_test ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE lab_test ADD CONSTRAINT ck_lab_cancellation_audit CHECK (
    (cancelled_by IS NULL AND cancellation_reason IS NULL AND cancelled_at IS NULL)
    OR (status = 'CANCELLED' AND cancelled_by IS NOT NULL
        AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason)) > 0 AND cancelled_at IS NOT NULL)
);
