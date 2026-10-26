CREATE TABLE outpatient_registration_notice_cursor (
    registration_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    status VARCHAR(40)
);
CREATE TABLE outpatient_registration_notice_revision (
    registration_id UUID NOT NULL REFERENCES outpatient_registration_notice_cursor(registration_id),
    revision BIGINT NOT NULL CHECK (revision > 0),
    snapshot_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY(registration_id, revision)
);
