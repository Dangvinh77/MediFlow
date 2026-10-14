ALTER TABLE dot_noi_tru
    ADD COLUMN placement_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE giuong_benh
    ADD COLUMN capacity_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE lich_su_cong_suat_giuong (
    capacity_history_id UUID PRIMARY KEY,
    bed_id UUID NOT NULL REFERENCES giuong_benh(bed_id),
    capacity_revision BIGINT NOT NULL CHECK (capacity_revision > 0),
    department_id UUID NOT NULL,
    ward_code VARCHAR(32) NOT NULL,
    bed_type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    operational_status VARCHAR(20) NOT NULL,
    capacity_eligible BOOLEAN NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    actor_id UUID,
    correlation_id VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    CONSTRAINT uq_lich_su_cong_suat_revision UNIQUE (bed_id, capacity_revision),
    CONSTRAINT ck_lich_su_cong_suat_status
        CHECK (operational_status IN ('AVAILABLE', 'OCCUPIED', 'OUT_OF_SERVICE'))
);

CREATE INDEX idx_lich_su_cong_suat_scope_time
    ON lich_su_cong_suat_giuong(department_id, ward_code, effective_at, bed_id);

UPDATE giuong_benh
SET capacity_revision = 1
WHERE capacity_revision = 0;

INSERT INTO lich_su_cong_suat_giuong (
    capacity_history_id, bed_id, capacity_revision, department_id, ward_code, bed_type,
    active, operational_status, capacity_eligible, effective_at, actor_id,
    correlation_id, event_id
)
SELECT md5('bed-capacity-history:' || bed_id::text || ':1')::uuid,
       bed_id, 1, department_id, ward_code, bed_type, active, status,
       active AND status <> 'OUT_OF_SERVICE', updated_at, NULL,
       'migration-v3-' || bed_id::text,
       md5('bed.capacity.changed:' || bed_id::text || ':1')::uuid
FROM giuong_benh;

INSERT INTO su_kien_outbox_noi_tru (
    event_id, aggregate_type, aggregate_id, event_type, event_version,
    correlation_id, payload, occurred_at
)
SELECT h.event_id, 'BED', h.bed_id, 'bed.capacity.changed', 1,
       h.correlation_id,
       jsonb_build_object(
           'eventId', h.event_id,
           'eventType', 'bed.capacity.changed',
           'version', 1,
           'occurredAt', h.effective_at,
           'correlationId', h.correlation_id,
           'producer', 'inpatient-service',
           'payload', jsonb_build_object(
               'bedId', h.bed_id,
               'capacityRevision', h.capacity_revision,
               'departmentId', h.department_id,
               'wardCode', h.ward_code,
               'bedType', h.bed_type,
               'active', h.active,
               'operationalStatus', h.operational_status,
               'capacityEligible', h.capacity_eligible,
               'effectiveAt', h.effective_at,
               'actorId', h.actor_id
           )
       ),
       h.effective_at
FROM lich_su_cong_suat_giuong h
ON CONFLICT (event_id) DO NOTHING;
