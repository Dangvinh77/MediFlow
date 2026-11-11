-- Critical Lab values addressed to the requesting department's doctors (never to the patient).
-- notice_id is Lab's notice id, so a redelivered event records nothing new.
CREATE TABLE staff_critical_notice (
    notice_id               UUID PRIMARY KEY,
    source_event_id         UUID NOT NULL,
    lab_id                  UUID NOT NULL,
    recipient_department_id UUID NOT NULL,
    patient_id              UUID NOT NULL,
    result_version          INTEGER NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    content                 TEXT NOT NULL,
    received_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_staff_critical_notice_department ON staff_critical_notice (recipient_department_id, received_at);
