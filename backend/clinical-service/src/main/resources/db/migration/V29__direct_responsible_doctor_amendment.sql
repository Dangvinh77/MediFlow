-- A responsible doctor may directly apply an audited correction. The original medical_record row remains immutable.
ALTER TABLE record_amendment DROP CONSTRAINT record_amendment_status_check;
ALTER TABLE record_amendment ADD CONSTRAINT record_amendment_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED'));

ALTER TABLE record_amendment DROP CONSTRAINT ck_record_amendment_decision;
ALTER TABLE record_amendment ADD CONSTRAINT ck_record_amendment_decision CHECK (
    (status = 'PENDING' AND decided_by_staff_id IS NULL AND decided_at IS NULL AND decision_note IS NULL)
    OR (status = 'APPROVED' AND decided_by_staff_id IS NOT NULL AND decided_at IS NOT NULL
        AND decided_by_staff_id <> author_staff_id)
    OR (status = 'REJECTED' AND decided_by_staff_id IS NOT NULL AND decided_at IS NOT NULL
        AND decision_note IS NOT NULL AND decided_by_staff_id <> author_staff_id)
    OR (status = 'APPLIED' AND decided_by_staff_id = author_staff_id AND decided_at IS NOT NULL
        AND decision_note IS NOT NULL)
);

ALTER TABLE record_amendment DROP CONSTRAINT ck_record_amendment_hash;
ALTER TABLE record_amendment ADD CONSTRAINT ck_record_amendment_hash
    CHECK ((status IN ('APPROVED', 'APPLIED')) = (content_hash IS NOT NULL));

ALTER TABLE record_amendment DROP CONSTRAINT ck_record_amendment_self;
ALTER TABLE record_amendment ADD CONSTRAINT ck_record_amendment_self CHECK (
    decided_by_staff_id IS NULL OR decided_by_staff_id <> author_staff_id OR status = 'APPLIED'
);
