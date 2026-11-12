-- Amendments sit next to the finished visit; the visit row itself is never rewritten.
CREATE TABLE record_amendment (
    amendment_id         UUID PRIMARY KEY,
    record_id            UUID          NOT NULL REFERENCES medical_record (record_id),
    section              VARCHAR(32)   NOT NULL CHECK (section IN ('SYMPTOMS', 'CHIEF_COMPLAINT', 'PRESENT_ILLNESS',
                             'PAST_HISTORY', 'ALLERGY_NOTE', 'PHYSICAL_EXAM', 'ADVICE', 'DISPOSITION_NOTE', 'CLOSURE_NOTE',
                             'DIAGNOSIS')),
    original_text        TEXT          NOT NULL,
    amended_text         TEXT          NOT NULL CHECK (length(btrim(amended_text)) > 0 AND length(amended_text) <= 4000),
    reason               VARCHAR(1000) NOT NULL CHECK (length(btrim(reason)) > 0),
    author_staff_id      UUID          NOT NULL,
    assigned_by_staff_id UUID,
    status               VARCHAR(16)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_at         TIMESTAMPTZ   NOT NULL,
    decided_by_staff_id  UUID,
    decision_note        VARCHAR(1000),
    decided_at           TIMESTAMPTZ,
    content_hash         CHAR(64),
    CONSTRAINT ck_record_amendment_decision CHECK (
        (status = 'PENDING' AND decided_by_staff_id IS NULL AND decided_at IS NULL AND decision_note IS NULL)
        OR (status <> 'PENDING' AND decided_by_staff_id IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_record_amendment_hash CHECK ((status = 'APPROVED') = (content_hash IS NOT NULL)),
    CONSTRAINT ck_record_amendment_reject_note CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL),
    CONSTRAINT ck_record_amendment_self CHECK (decided_by_staff_id IS NULL OR decided_by_staff_id <> author_staff_id)
);

CREATE INDEX idx_record_amendment_record ON record_amendment (record_id, requested_at);
CREATE INDEX idx_record_amendment_pending ON record_amendment (requested_at) WHERE status = 'PENDING';

CREATE TABLE record_amendment_assignment (
    assignment_id        UUID PRIMARY KEY,
    record_id            UUID          NOT NULL REFERENCES medical_record (record_id),
    assignee_staff_id    UUID          NOT NULL,
    assigned_by_staff_id UUID          NOT NULL,
    reason               VARCHAR(1000) NOT NULL CHECK (length(btrim(reason)) > 0),
    created_at           TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_record_amendment_assignment ON record_amendment_assignment (record_id, assignee_staff_id);
