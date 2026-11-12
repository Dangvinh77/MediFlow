-- v2.0 critical result notices: one per (test, released revision, field) whose value is critical.
-- Addressed to the requesting department's doctors; acknowledged once.
CREATE TABLE lab_critical_notice (
    notice_id       UUID PRIMARY KEY,
    test_id         UUID NOT NULL REFERENCES lab_test(test_id),
    result_version  INTEGER NOT NULL CHECK (result_version >= 1),
    field_code      VARCHAR(32) NOT NULL,
    label           VARCHAR(200) NOT NULL,
    value           VARCHAR(4000),
    unit            VARCHAR(40),
    flag            VARCHAR(20) NOT NULL CHECK (flag IN ('CRITICAL_LOW', 'CRITICAL_HIGH')),
    patient_id      UUID NOT NULL,
    department_id   UUID NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED')),
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_lab_critical_notice UNIQUE (test_id, result_version, field_code),
    CONSTRAINT ck_lab_critical_notice_ack
        CHECK ((status = 'ACKNOWLEDGED') = (acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL))
);
CREATE INDEX ix_lab_critical_notice_open ON lab_critical_notice (department_id, created_at) WHERE status = 'OPEN';
