-- Completed-stay statistics, not bed occupancy, staffed census or billable bed days.
ALTER TABLE daily_operational_report ADD COLUMN medical_los_minutes BIGINT NOT NULL DEFAULT 0 CHECK (medical_los_minutes >= 0);
ALTER TABLE daily_operational_report ADD COLUMN medical_discharges BIGINT NOT NULL DEFAULT 0 CHECK (medical_discharges >= 0);
ALTER TABLE daily_operational_report ADD COLUMN completed_stay_days BIGINT NOT NULL DEFAULT 0 CHECK (completed_stay_days >= 0);
CREATE TABLE report_medical_discharge (
    admission_id UUID PRIMARY KEY REFERENCES report_admission_target(admission_id),
    patient_id UUID NOT NULL,
    summary_id UUID NOT NULL UNIQUE,
    approved_by UUID NOT NULL,
    approved_at_iso VARCHAR(40) NOT NULL,
    first_event_id UUID NOT NULL UNIQUE,
    occurred_at_iso VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    payload_hash CHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_START' CHECK (status IN ('PENDING_START','APPLIED','REJECTED'))
);
CREATE TABLE report_medical_discharge_delivery (
    event_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES report_admission_target(admission_id),
    envelope_hash CHAR(64) NOT NULL CHECK (envelope_hash ~ '^[0-9a-f]{64}$')
);
