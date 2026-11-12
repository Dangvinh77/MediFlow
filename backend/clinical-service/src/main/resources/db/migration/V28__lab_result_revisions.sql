-- v2.0 Lab amendments: Clinical keeps every released Lab revision and shows the newest one.
-- Existing attachments keep result_version NULL (read as revision 1); no business backfill.
ALTER TABLE attached_result ADD COLUMN result_version INTEGER;
ALTER TABLE attached_result ADD CONSTRAINT ck_attached_result_version
    CHECK (result_version IS NULL OR (type = 'LAB' AND result_version >= 1));

CREATE TABLE attached_result_revision (
    record_id UUID NOT NULL REFERENCES medical_record(record_id) ON DELETE CASCADE,
    reference_id UUID NOT NULL,
    result_version INTEGER NOT NULL CHECK (result_version >= 1),
    summary TEXT,
    amendment_reason TEXT,
    source_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (record_id, reference_id, result_version),
    CONSTRAINT ck_attached_result_revision_reason
        CHECK ((result_version = 1 AND amendment_reason IS NULL)
            OR (result_version > 1 AND amendment_reason IS NOT NULL AND length(btrim(amendment_reason)) > 0))
);
