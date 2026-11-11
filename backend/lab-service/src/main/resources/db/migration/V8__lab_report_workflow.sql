-- v2.0 Lab review workflow (mediflow.v2.lab-review). One lab_report row per released revision of a test:
-- revision 1 is the original, 2+ are amendments. Tests created before stay on the old path
-- (result_workflow_version = 0); no business backfill.
ALTER TABLE lab_test ADD COLUMN result_workflow_version SMALLINT NOT NULL DEFAULT 0
    CHECK (result_workflow_version IN (0, 1));

CREATE TABLE lab_report (
    report_id        UUID PRIMARY KEY,
    test_id          UUID NOT NULL REFERENCES lab_test(test_id),
    result_version   INTEGER NOT NULL CHECK (result_version >= 1),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'RELEASED')),
    draft_revision   INTEGER NOT NULL DEFAULT 0 CHECK (draft_revision >= 0),
    template_code    VARCHAR(32) NOT NULL,
    template_version INTEGER NOT NULL,
    conclusion       TEXT,
    performed_date   DATE,
    amendment_reason TEXT,
    entered_by       UUID,
    submitted_by     UUID,
    submitted_at     TIMESTAMPTZ,
    returned_by      UUID,
    return_reason    TEXT,
    released_by      UUID,
    released_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_lab_report_revision UNIQUE (test_id, result_version),
    CONSTRAINT ck_lab_report_amendment
        CHECK ((result_version = 1 AND amendment_reason IS NULL)
            OR (result_version > 1 AND amendment_reason IS NOT NULL AND length(btrim(amendment_reason)) > 0)),
    CONSTRAINT ck_lab_report_released
        CHECK ((status = 'RELEASED') = (released_by IS NOT NULL AND released_at IS NOT NULL))
);
-- At most one revision is being written or reviewed at a time.
CREATE UNIQUE INDEX uq_lab_report_open ON lab_report (test_id) WHERE status <> 'RELEASED';

CREATE TABLE lab_report_value (
    report_id        UUID NOT NULL REFERENCES lab_report(report_id) ON DELETE CASCADE,
    field_code       VARCHAR(32) NOT NULL,
    position         INTEGER NOT NULL,
    operator         VARCHAR(2),
    value            VARCHAR(4000),
    no_result_reason VARCHAR(500),
    flag             VARCHAR(20),
    PRIMARY KEY (report_id, field_code)
);
