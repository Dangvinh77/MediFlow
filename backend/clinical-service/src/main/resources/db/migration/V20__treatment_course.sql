-- A doctor-chosen grouping of visits for one problem. Visits are linked, never copied; nothing is backfilled.
CREATE TABLE treatment_course (
    course_id             UUID PRIMARY KEY,
    patient_id            UUID          NOT NULL,
    department_id         UUID          NOT NULL,
    problem               VARCHAR(500)  NOT NULL CHECK (length(btrim(problem)) > 0),
    opening_reason        VARCHAR(1000) NOT NULL CHECK (length(btrim(opening_reason)) > 0),
    responsible_doctor_id UUID          NOT NULL,
    status                VARCHAR(16)   NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    opened_at             TIMESTAMPTZ   NOT NULL,
    closure_decision      VARCHAR(32)   CHECK (closure_decision IN ('RESOLVED', 'TRANSFERRED', 'PATIENT_STOPPED', 'OTHER')),
    closure_note          VARCHAR(1000),
    closed_by_staff_id    UUID,
    closed_at             TIMESTAMPTZ,
    CONSTRAINT ck_treatment_course_closure CHECK (
        (status = 'OPEN' AND closure_decision IS NULL AND closure_note IS NULL AND closed_by_staff_id IS NULL
            AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closure_decision IS NOT NULL AND closure_note IS NOT NULL
            AND closed_by_staff_id IS NOT NULL AND closed_at IS NOT NULL))
);

CREATE INDEX idx_treatment_course_patient ON treatment_course (patient_id, opened_at DESC);

CREATE TABLE treatment_course_visit (
    course_id         UUID        NOT NULL REFERENCES treatment_course (course_id),
    record_id         UUID        NOT NULL REFERENCES medical_record (record_id),
    added_by_staff_id UUID        NOT NULL,
    added_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (course_id, record_id)
);

CREATE INDEX idx_treatment_course_visit_record ON treatment_course_visit (record_id);
