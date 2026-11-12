-- v2.0 Lab specimens: one specimen may serve several tests of the same patient; a rejected specimen is
-- kept and a recollection points back to it. Collecting never creates a test or an event.
CREATE TABLE lab_specimen (
    specimen_id          UUID PRIMARY KEY,
    accession            VARCHAR(32) NOT NULL,
    patient_id           UUID NOT NULL,
    specimen_type        VARCHAR(40) NOT NULL,
    collected_by         UUID NOT NULL,
    collected_at         TIMESTAMPTZ NOT NULL,
    received_by          UUID,
    received_at          TIMESTAMPTZ,
    decision             VARCHAR(10) CHECK (decision IN ('ACCEPTED', 'REJECTED')),
    rejection_reason     TEXT,
    replaces_specimen_id UUID REFERENCES lab_specimen(specimen_id),
    CONSTRAINT uq_lab_specimen_accession UNIQUE (accession),
    CONSTRAINT ck_lab_specimen_rejection
        CHECK (decision IS DISTINCT FROM 'REJECTED' OR length(btrim(coalesce(rejection_reason, ''))) > 0),
    CONSTRAINT ck_lab_specimen_received
        CHECK ((decision IS NULL) = (received_by IS NULL AND received_at IS NULL))
);

CREATE TABLE lab_specimen_test (
    specimen_id UUID NOT NULL REFERENCES lab_specimen(specimen_id),
    test_id     UUID NOT NULL REFERENCES lab_test(test_id),
    position    INTEGER NOT NULL,
    PRIMARY KEY (specimen_id, test_id)
);
CREATE INDEX ix_lab_specimen_test_test ON lab_specimen_test (test_id);
