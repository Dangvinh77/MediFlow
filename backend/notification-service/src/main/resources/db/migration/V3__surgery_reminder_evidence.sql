-- Local notification evidence only; never room booking, clinical state or a copy of Surgery tables.
CREATE TABLE surgery_notice_case (
    surgery_case_id UUID PRIMARY KEY,
    surgery_request_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type IN ('ADMISSION','OUTPATIENT_VISIT')),
    care_episode_id UUID NOT NULL,
    admission_id UUID,
    record_id UUID,
    terminal_event_type VARCHAR(32) CHECK (terminal_event_type IN ('surgery.cancelled','surgery.completed')),
    terminal_source_id UUID,
    CHECK ((terminal_event_type IS NULL) = (terminal_source_id IS NULL)),
    CHECK ((care_episode_type='ADMISSION' AND admission_id IS NOT NULL AND admission_id=care_episode_id)
        OR (care_episode_type='OUTPATIENT_VISIT' AND admission_id IS NULL))
);
CREATE TABLE surgery_notice_source (
    event_type VARCHAR(40) NOT NULL CHECK (event_type IN
        ('surgery.ready','surgery.readiness.invalidated','surgery.cancelled','surgery.completed')),
    source_id UUID NOT NULL,
    surgery_case_id UUID NOT NULL REFERENCES surgery_notice_case(surgery_case_id),
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY (event_type,source_id)
);
CREATE TABLE surgery_notice_snapshot (
    snapshot_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_notice_case(surgery_case_id),
    schedule_id UUID NOT NULL,
    schedule_revision BIGINT NOT NULL CHECK (schedule_revision>0),
    invalidated BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_surgery_notice_snapshot_case ON surgery_notice_snapshot(surgery_case_id);
