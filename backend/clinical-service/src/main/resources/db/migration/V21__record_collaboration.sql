-- Handover moves write responsibility for an open visit; doctor_id keeps the opening doctor for events and reports.
ALTER TABLE medical_record ADD COLUMN responsible_doctor_id UUID;

CREATE TABLE record_handover (
    handover_id    UUID PRIMARY KEY,
    record_id      UUID          NOT NULL REFERENCES medical_record (record_id),
    from_doctor_id UUID          NOT NULL,
    to_doctor_id   UUID          NOT NULL,
    reason         VARCHAR(500)  NOT NULL CHECK (length(btrim(reason)) > 0),
    pending_items  VARCHAR(2000) NOT NULL CHECK (length(btrim(pending_items)) > 0),
    status         VARCHAR(16)   NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    requested_at   TIMESTAMPTZ   NOT NULL,
    accepted_at    TIMESTAMPTZ,
    CONSTRAINT ck_record_handover_parties CHECK (from_doctor_id <> to_doctor_id),
    CONSTRAINT ck_record_handover_acceptance CHECK ((status = 'PENDING') = (accepted_at IS NULL))
);

CREATE UNIQUE INDEX uq_record_handover_pending ON record_handover (record_id) WHERE status = 'PENDING';
CREATE INDEX idx_record_handover_record ON record_handover (record_id, requested_at);

-- Consultation opinions are appended per author and never edited.
CREATE TABLE consultation_note (
    note_id         UUID PRIMARY KEY,
    record_id       UUID        NOT NULL REFERENCES medical_record (record_id),
    author_staff_id UUID        NOT NULL,
    opinion         TEXT        NOT NULL CHECK (length(btrim(opinion)) > 0 AND length(opinion) <= 4000),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_consultation_note_record ON consultation_note (record_id, created_at);
