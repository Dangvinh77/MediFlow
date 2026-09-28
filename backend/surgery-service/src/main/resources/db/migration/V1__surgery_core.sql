-- Surgery-owned data only. Cross-service identities are bare UUID references.
-- Clinical catalogue, clearance wire fields and emergency overrides are intentionally absent.
CREATE TABLE surgery_case (
    surgery_case_id UUID PRIMARY KEY,
    surgery_request_id UUID NOT NULL UNIQUE,
    episode_type VARCHAR(24) NOT NULL,
    episode_id UUID NOT NULL,
    admission_id UUID,
    medical_record_id UUID,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    procedure_code VARCHAR(64) NOT NULL,
    indication TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    readiness_snapshot_id UUID,
    requested_at TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_actor_type VARCHAR(8),
    cancellation_account_id UUID,
    cancellation_staff_id UUID,
    cancellation_system_producer VARCHAR(128),
    cancellation_reason VARCHAR(1000),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    technical_version BIGINT NOT NULL DEFAULT 0 CHECK (technical_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_surgery_case_episode CHECK (
        (episode_type = 'ADMISSION' AND admission_id IS NOT NULL
            AND episode_id = admission_id)
        OR (episode_type = 'OUTPATIENT_VISIT' AND admission_id IS NULL)),
    CONSTRAINT ck_surgery_case_priority CHECK (priority IN ('ROUTINE', 'URGENT', 'EMERGENCY')),
    CONSTRAINT ck_surgery_case_status CHECK (status IN ('REQUESTED', 'PREOP_IN_PROGRESS',
        'READY', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_surgery_case_cancelled CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL
            AND cancellation_actor_type IS NOT NULL AND cancellation_reason IS NOT NULL
            AND length(trim(cancellation_reason)) > 0)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL
            AND cancellation_actor_type IS NULL AND cancellation_account_id IS NULL
            AND cancellation_staff_id IS NULL AND cancellation_system_producer IS NULL
            AND cancellation_reason IS NULL)),
    CONSTRAINT ck_surgery_case_cancellation_actor CHECK (
        cancellation_actor_type IS NULL
        OR (cancellation_actor_type = 'HUMAN' AND cancellation_account_id IS NOT NULL
            AND cancellation_system_producer IS NULL)
        OR (cancellation_actor_type = 'SYSTEM' AND cancellation_account_id IS NULL
            AND cancellation_staff_id IS NULL AND cancellation_system_producer IS NOT NULL))
);
CREATE INDEX idx_case_department_status_requested ON surgery_case
    (department_id, status, requested_at, surgery_case_id);
CREATE INDEX idx_case_patient_episode ON surgery_case (patient_id, episode_id);

CREATE TABLE surgery_status_history (
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    sequence_no INTEGER NOT NULL CHECK (sequence_no >= 0),
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    actor_type VARCHAR(8) NOT NULL,
    account_id UUID,
    staff_id UUID,
    system_producer VARCHAR(128),
    reason VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (surgery_case_id, sequence_no),
    CONSTRAINT ck_status_history_new CHECK (new_status IN
        ('REQUESTED', 'PREOP_IN_PROGRESS', 'READY', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_status_history_previous CHECK (previous_status IS NULL OR previous_status IN
        ('REQUESTED', 'PREOP_IN_PROGRESS', 'READY', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_status_history_actor CHECK (
        (actor_type = 'HUMAN' AND account_id IS NOT NULL AND system_producer IS NULL)
        OR (actor_type = 'SYSTEM' AND account_id IS NULL
            AND staff_id IS NULL AND system_producer IS NOT NULL))
);

CREATE TABLE surgery_revision_history (
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    change_code VARCHAR(64) NOT NULL,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    actor_type VARCHAR(8) NOT NULL,
    account_id UUID,
    staff_id UUID,
    system_producer VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (surgery_case_id, revision),
    CONSTRAINT ck_revision_history_previous CHECK (previous_status IS NULL OR previous_status IN
        ('REQUESTED', 'PREOP_IN_PROGRESS', 'READY', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_revision_history_new CHECK (new_status IN
        ('REQUESTED', 'PREOP_IN_PROGRESS', 'READY', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_revision_history_actor CHECK (
        (actor_type = 'HUMAN' AND account_id IS NOT NULL AND system_producer IS NULL)
        OR (actor_type = 'SYSTEM' AND account_id IS NULL
            AND staff_id IS NULL AND system_producer IS NOT NULL))
);

CREATE TABLE preop_checklist_template (
    template_id UUID PRIMARY KEY,
    procedure_code VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (procedure_code, revision)
);
CREATE TABLE preop_checklist_definition (
    definition_id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES preop_checklist_template(template_id),
    item_code VARCHAR(64) NOT NULL,
    mandatory BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 1),
    UNIQUE (template_id, item_code),
    UNIQUE (template_id, display_order)
);
CREATE TABLE preop_checklist_snapshot (
    checklist_snapshot_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES surgery_case(surgery_case_id),
    template_id UUID NOT NULL REFERENCES preop_checklist_template(template_id),
    template_revision BIGINT NOT NULL CHECK (template_revision >= 1),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
CREATE TABLE preop_checklist_item (
    checklist_item_id UUID PRIMARY KEY,
    checklist_snapshot_id UUID NOT NULL REFERENCES preop_checklist_snapshot(checklist_snapshot_id),
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    template_definition_id UUID NOT NULL REFERENCES preop_checklist_definition(definition_id),
    item_code VARCHAR(64) NOT NULL,
    mandatory BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 1),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    evidence_reference_id UUID,
    evidence_revision BIGINT CHECK (evidence_revision >= 0),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CONSTRAINT ck_checklist_status CHECK (status IN
        ('PENDING', 'SATISFIED', 'NOT_APPLICABLE', 'FAILED')),
    CONSTRAINT ck_checklist_evidence CHECK
        (evidence_revision IS NULL OR evidence_reference_id IS NOT NULL),
    UNIQUE (surgery_case_id, item_code),
    UNIQUE (checklist_snapshot_id, display_order)
);
CREATE TABLE preop_checklist_item_history (
    change_code UUID PRIMARY KEY,
    checklist_item_id UUID NOT NULL REFERENCES preop_checklist_item(checklist_item_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    evidence_reference_id UUID,
    evidence_revision BIGINT CHECK (evidence_revision >= 0),
    actor_type VARCHAR(8) NOT NULL,
    account_id UUID,
    staff_id UUID,
    system_producer VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    UNIQUE (checklist_item_id, revision),
    CONSTRAINT ck_checklist_history_actor CHECK (
        (actor_type = 'HUMAN' AND account_id IS NOT NULL AND system_producer IS NULL)
        OR (actor_type = 'SYSTEM' AND account_id IS NULL
            AND staff_id IS NULL AND system_producer IS NOT NULL))
);

CREATE TABLE surgery_consent (
    consent_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    consent_type VARCHAR(16) NOT NULL CHECK (consent_type IN ('SURGERY', 'ANESTHESIA')),
    signer_id UUID NOT NULL,
    signer_type VARCHAR(32) NOT NULL CHECK (signer_type IN
        ('PATIENT', 'GUARDIAN', 'AUTHORIZED_REPRESENTATIVE')),
    evidence_document_id UUID,
    status VARCHAR(8) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    signed_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    CONSTRAINT ck_consent_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revocation_reason IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL
            AND revocation_reason IS NOT NULL AND length(trim(revocation_reason)) > 0))
);
CREATE UNIQUE INDEX uq_consent_active ON surgery_consent (surgery_case_id, consent_type)
    WHERE status = 'ACTIVE';
CREATE TABLE surgery_consent_history (
    consent_id UUID NOT NULL REFERENCES surgery_consent(consent_id),
    sequence_no SMALLINT NOT NULL CHECK (sequence_no IN (0, 1)),
    action VARCHAR(8) NOT NULL CHECK (action IN ('SIGNED', 'REVOKED')),
    actor_type VARCHAR(8) NOT NULL,
    account_id UUID,
    staff_id UUID,
    system_producer VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    reason TEXT,
    PRIMARY KEY (consent_id, sequence_no),
    CONSTRAINT ck_consent_audit_actor CHECK (
        (actor_type = 'HUMAN' AND account_id IS NOT NULL AND system_producer IS NULL)
        OR (actor_type = 'SYSTEM' AND account_id IS NULL
            AND staff_id IS NULL AND system_producer IS NOT NULL)),
    CONSTRAINT ck_consent_audit_sequence CHECK (
        (sequence_no = 0 AND action = 'SIGNED' AND reason IS NULL)
        OR (sequence_no = 1 AND action = 'REVOKED'
            AND reason IS NOT NULL AND length(trim(reason)) > 0))
);

CREATE TABLE surgery_schedule (
    schedule_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES surgery_case(surgery_case_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    room_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN
        ('DRAFT', 'FINALIZED', 'IN_USE', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_schedule_interval CHECK (ends_at > starts_at)
);
CREATE INDEX idx_schedule_room_time ON surgery_schedule (room_id, starts_at, ends_at);
CREATE TABLE surgery_team_assignment (
    schedule_id UUID NOT NULL REFERENCES surgery_schedule(schedule_id),
    staff_id UUID NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN
        ('PRIMARY_SURGEON', 'ASSISTANT_SURGEON', 'ANESTHESIOLOGIST', 'OR_NURSE')),
    PRIMARY KEY (schedule_id, staff_id)
);
CREATE INDEX idx_team_staff ON surgery_team_assignment (staff_id);
CREATE TABLE surgery_schedule_history (
    schedule_id UUID NOT NULL REFERENCES surgery_schedule(schedule_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    room_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (schedule_id, revision),
    CONSTRAINT ck_schedule_history_interval CHECK (ends_at > starts_at)
);
CREATE TABLE surgery_team_assignment_history (
    schedule_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    staff_id UUID NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN
        ('PRIMARY_SURGEON', 'ASSISTANT_SURGEON', 'ANESTHESIOLOGIST', 'OR_NURSE')),
    PRIMARY KEY (schedule_id, revision, staff_id),
    FOREIGN KEY (schedule_id, revision)
        REFERENCES surgery_schedule_history(schedule_id, revision)
);

-- A mutex is only a Surgery lock key, never a duplicate room/staff master record.
CREATE TABLE surgery_resource_mutex (
    resource_type VARCHAR(8) NOT NULL CHECK (resource_type IN ('ROOM', 'STAFF')),
    resource_id UUID NOT NULL,
    PRIMARY KEY (resource_type, resource_id)
);
CREATE TABLE surgery_resource_reservation (
    reservation_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    schedule_id UUID NOT NULL REFERENCES surgery_schedule(schedule_id),
    schedule_revision BIGINT NOT NULL CHECK (schedule_revision >= 1),
    resource_type VARCHAR(8) NOT NULL CHECK (resource_type IN ('ROOM', 'STAFF')),
    resource_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(8) NOT NULL CHECK (status IN ('RESERVED', 'IN_USE', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reservation_interval CHECK (ends_at > starts_at),
    CONSTRAINT fk_reservation_mutex FOREIGN KEY (resource_type, resource_id)
        REFERENCES surgery_resource_mutex(resource_type, resource_id)
);
CREATE UNIQUE INDEX uq_reservation_active ON surgery_resource_reservation
    (schedule_id, schedule_revision, resource_type, resource_id)
    WHERE status IN ('RESERVED', 'IN_USE');
CREATE INDEX idx_reservation_resource_active ON surgery_resource_reservation
    (resource_type, resource_id, starts_at, ends_at)
    WHERE status IN ('RESERVED', 'IN_USE');
CREATE INDEX idx_reservation_case_active ON surgery_resource_reservation
    (surgery_case_id, status);

CREATE TABLE surgery_readiness_snapshot (
    readiness_snapshot_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    indication_valid BOOLEAN NOT NULL,
    mandatory_checklist_complete BOOLEAN NOT NULL,
    surgery_consent_active BOOLEAN NOT NULL,
    anesthesia_consent_active BOOLEAN NOT NULL,
    team_eligible BOOLEAN NOT NULL,
    schedule_confirmed BOOLEAN NOT NULL,
    financial_clearance_valid BOOLEAN NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    CONSTRAINT ck_readiness_snapshot_validity CHECK
        (valid_until IS NULL OR valid_until > evaluated_at)
);
ALTER TABLE surgery_case ADD CONSTRAINT fk_case_readiness_snapshot
    FOREIGN KEY (readiness_snapshot_id)
    REFERENCES surgery_readiness_snapshot(readiness_snapshot_id);
CREATE TABLE surgery_readiness_dependency (
    readiness_snapshot_id UUID NOT NULL REFERENCES surgery_readiness_snapshot(readiness_snapshot_id),
    sequence_no INTEGER NOT NULL CHECK (sequence_no >= 0),
    dependency_type VARCHAR(24) NOT NULL CHECK (dependency_type IN
        ('INDICATION', 'CHECKLIST', 'SURGERY_CONSENT', 'ANESTHESIA_CONSENT',
         'FINANCIAL_CLEARANCE', 'TEAM_ELIGIBILITY', 'SCHEDULE')),
    source_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    PRIMARY KEY (readiness_snapshot_id, dependency_type),
    UNIQUE (readiness_snapshot_id, sequence_no)
);
CREATE TABLE surgery_readiness_blocking_reason (
    readiness_snapshot_id UUID NOT NULL REFERENCES surgery_readiness_snapshot(readiness_snapshot_id),
    sequence_no INTEGER NOT NULL CHECK (sequence_no >= 0),
    reason_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (readiness_snapshot_id, sequence_no)
);

CREATE TABLE surgery_result (
    result_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES surgery_case(surgery_case_id),
    procedure_code VARCHAR(64) NOT NULL,
    method_code VARCHAR(64) NOT NULL,
    treatment_outcome_code VARCHAR(64) NOT NULL,
    complication_group_code VARCHAR(64),
    actual_start_at TIMESTAMPTZ NOT NULL,
    actual_end_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(8) NOT NULL,
    account_id UUID,
    staff_id UUID,
    system_producer VARCHAR(128),
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_result_interval CHECK (actual_end_at > actual_start_at
        AND recorded_at >= actual_end_at),
    CONSTRAINT ck_result_actor CHECK (
        (actor_type = 'HUMAN' AND account_id IS NOT NULL AND system_producer IS NULL)
        OR (actor_type = 'SYSTEM' AND account_id IS NULL
            AND staff_id IS NULL AND system_producer IS NOT NULL))
);
CREATE TABLE surgery_performed_item (
    performed_item_id UUID PRIMARY KEY,
    result_id UUID NOT NULL REFERENCES surgery_result(result_id),
    item_code VARCHAR(64) NOT NULL,
    price_code VARCHAR(64) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_performed_item_result ON surgery_performed_item (result_id);

CREATE TABLE surgery_command_receipt (
    receipt_id UUID PRIMARY KEY,
    surgery_case_id UUID REFERENCES surgery_case(surgery_case_id),
    actor_scope VARCHAR(160) NOT NULL,
    command_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'APPLIED')),
    response_code VARCHAR(64),
    response_payload BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT ck_receipt_applied CHECK (
        (status = 'PENDING' AND applied_at IS NULL AND response_payload IS NULL)
        OR (status = 'APPLIED' AND applied_at IS NOT NULL AND response_payload IS NOT NULL)),
    UNIQUE (actor_scope, command_code, idempotency_key)
);
CREATE INDEX idx_receipt_case ON surgery_command_receipt (surgery_case_id);

CREATE TABLE surgery_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version >= 1),
    system_producer VARCHAR(100) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    semantic_key VARCHAR(200) NOT NULL,
    payload BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'QUARANTINED')),
    reason TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT ck_inbox_applied CHECK (
        (status = 'APPLIED' AND applied_at IS NOT NULL)
        OR (status <> 'APPLIED' AND applied_at IS NULL))
);
CREATE TABLE surgery_inbox_semantic_mutex (
    semantic_key VARCHAR(200) PRIMARY KEY
);
CREATE TABLE surgery_inbox_conflict (
    conflict_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES surgery_inbox(event_id),
    fingerprint CHAR(64) NOT NULL,
    payload BYTEA NOT NULL,
    reason VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inbox_pending ON surgery_inbox
    (next_attempt_at, received_at) WHERE status = 'PENDING';
CREATE INDEX idx_inbox_semantic ON surgery_inbox (semantic_key);

CREATE TABLE surgery_outbox (
    event_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    aggregate_revision BIGINT NOT NULL CHECK (aggregate_revision >= 0),
    sequence_no INTEGER NOT NULL CHECK (sequence_no >= 0),
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version >= 1),
    correlation_id VARCHAR(128) NOT NULL,
    payload BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN
        ('PENDING', 'CLAIMED', 'PUBLISHED', 'QUARANTINED')),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    reason TEXT,
    CONSTRAINT uq_outbox_order UNIQUE (surgery_case_id, aggregate_revision, sequence_no),
    CONSTRAINT ck_outbox_lease CHECK (
        (status = 'CLAIMED' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'CLAIMED' AND claim_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_outbox_published CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL))
);
CREATE INDEX idx_outbox_claim ON surgery_outbox
    (next_attempt_at, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_case_order ON surgery_outbox
    (surgery_case_id, aggregate_revision, sequence_no)
    WHERE status <> 'PUBLISHED';
