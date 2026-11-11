-- A newer request from the responsible doctor replaces a pending one; accepted_at stays only on accepted rows.
ALTER TABLE record_handover DROP CONSTRAINT record_handover_status_check;
ALTER TABLE record_handover ADD CONSTRAINT record_handover_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'SUPERSEDED'));
ALTER TABLE record_handover DROP CONSTRAINT ck_record_handover_acceptance;
ALTER TABLE record_handover ADD CONSTRAINT ck_record_handover_acceptance
    CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL));
