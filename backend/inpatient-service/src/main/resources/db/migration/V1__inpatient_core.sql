CREATE TABLE dot_noi_tru (
    admission_id UUID PRIMARY KEY,
    admission_request_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    source_record_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    diagnosis_summary TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    attending_doctor_id UUID,
    department_id UUID NOT NULL,
    priority VARCHAR(20) NOT NULL,
    emergency BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    deposit_clearance_id UUID,
    deposit_requested_at TIMESTAMPTZ,
    emergency_override_id UUID,
    settlement_id UUID,
    close_override_id UUID,
    discharge_summary_id UUID,
    admitted_at TIMESTAMPTZ,
    medically_discharged_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dot_noi_tru_priority
        CHECK (priority IN ('ROUTINE', 'URGENT', 'EMERGENCY')),
    CONSTRAINT ck_dot_noi_tru_status
        CHECK (status IN ('REQUESTED', 'AWAITING_BED', 'AWAITING_DEPOSIT', 'READY',
                          'ADMITTED', 'MEDICALLY_DISCHARGED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_dot_noi_tru_close
        CHECK (status <> 'CLOSED' OR
              (medically_discharged_at IS NOT NULL AND closed_at IS NOT NULL
               AND (settlement_id IS NOT NULL OR close_override_id IS NOT NULL))),
    CONSTRAINT ck_dot_noi_tru_cancel
        CHECK (status <> 'CANCELLED' OR
              (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL))
);
CREATE INDEX idx_dot_noi_tru_patient ON dot_noi_tru(patient_id, created_at DESC);
CREATE INDEX idx_dot_noi_tru_department_status ON dot_noi_tru(department_id, status);

CREATE TABLE giuong_benh (
    bed_id UUID PRIMARY KEY,
    department_id UUID NOT NULL,
    ward_code VARCHAR(32) NOT NULL,
    room_code VARCHAR(32) NOT NULL,
    bed_code VARCHAR(32) NOT NULL,
    bed_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_giuong_benh_code UNIQUE(department_id, ward_code, room_code, bed_code),
    CONSTRAINT ck_giuong_benh_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'OUT_OF_SERVICE'))
);
CREATE INDEX idx_giuong_benh_available
    ON giuong_benh(department_id, ward_code, status) WHERE active = TRUE;

CREATE TABLE phan_giuong (
    assignment_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    bed_id UUID NOT NULL REFERENCES giuong_benh(bed_id),
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    released_by UUID,
    released_at TIMESTAMPTZ,
    release_reason TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_phan_giuong_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_phan_giuong_release CHECK (
        (status = 'ACTIVE' AND released_at IS NULL AND released_by IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND released_by IS NOT NULL
            AND release_reason IS NOT NULL)
    ),
    CONSTRAINT ck_phan_giuong_time CHECK (released_at IS NULL OR released_at >= assigned_at)
);
CREATE UNIQUE INDEX uq_phan_giuong_active_bed
    ON phan_giuong(bed_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_phan_giuong_active_admission
    ON phan_giuong(admission_id) WHERE status = 'ACTIVE';

CREATE TABLE dien_bien_dieu_tri (
    entry_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    entry_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    authored_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    correction_of_entry_id UUID REFERENCES dien_bien_dieu_tri(entry_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dien_bien_type CHECK (entry_type IN
        ('NOTE', 'OBSERVATION', 'PROCEDURE', 'LAB', 'MEDICATION', 'SURGERY', 'CORRECTION')),
    CONSTRAINT ck_dien_bien_correction CHECK (
        (entry_type = 'CORRECTION' AND correction_of_entry_id IS NOT NULL)
        OR (entry_type <> 'CORRECTION' AND correction_of_entry_id IS NULL)
    )
);
CREATE INDEX idx_dien_bien_admission_time
    ON dien_bien_dieu_tri(admission_id, recorded_at, entry_id);

CREATE TABLE tham_chieu_y_lenh (
    order_ref_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    order_type VARCHAR(24) NOT NULL,
    external_order_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary TEXT,
    event_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tham_chieu_y_lenh UNIQUE(order_type, external_order_id),
    CONSTRAINT ck_y_lenh_type CHECK (order_type IN ('LAB_TEST', 'PRESCRIPTION', 'SURGERY')),
    CONSTRAINT ck_y_lenh_status CHECK (status IN
        ('REQUESTED', 'READY', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'FAILED'))
);
CREATE INDEX idx_y_lenh_admission ON tham_chieu_y_lenh(admission_id, order_type, status);

CREATE TABLE tom_tat_ra_vien (
    summary_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL UNIQUE REFERENCES dot_noi_tru(admission_id),
    diagnosis_summary TEXT NOT NULL,
    treatment_summary TEXT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    follow_up_plan TEXT NOT NULL,
    approved_by UUID NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tom_tat_outcome CHECK
        (outcome IN ('RECOVERED', 'IMPROVED', 'UNCHANGED', 'TRANSFERRED', 'DECEASED', 'OTHER'))
);

CREATE TABLE lich_su_trang_thai_noi_tru (
    history_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id UUID,
    reason TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lich_su_admission
    ON lich_su_trang_thai_noi_tru(admission_id, changed_at, history_id);

CREATE TABLE xac_nhan_tai_chinh_noi_tru (
    clearance_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    patient_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE quyet_toan_noi_tru (
    settlement_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    account_id UUID NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    insurance_amount NUMERIC(19,2) NOT NULL,
    patient_liability NUMERIC(19,2) NOT NULL,
    completed_payments NUMERIC(19,2) NOT NULL,
    completed_refunds NUMERIC(19,2) NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_quyet_toan_outcome CHECK (outcome IN
        ('PAID_IN_FULL', 'ADDITIONAL_PAYMENT_REQUIRED', 'REFUND_DUE', 'DEBT_APPROVED', 'WAIVED'))
);
CREATE INDEX idx_quyet_toan_admission
    ON quyet_toan_noi_tru(admission_id, completed_at DESC);

CREATE TABLE yeu_cau_bo_sung_tam_ung (
    topup_request_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    account_id UUID NOT NULL,
    current_balance NUMERIC(19,2) NOT NULL,
    requested_amount NUMERIC(19,2) NOT NULL CHECK (requested_amount > 0),
    reason TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE phe_duyet_ngoai_le_noi_tru (
    override_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    override_type VARCHAR(24) NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ngoai_le_type CHECK
        (override_type IN ('EMERGENCY_ADMIT', 'DEBT_CLOSE', 'WAIVER_CLOSE'))
);

CREATE TABLE su_kien_da_xu_ly (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE su_kien_outbox_noi_tru (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500)
);
CREATE INDEX idx_outbox_noi_tru_unpublished
    ON su_kien_outbox_noi_tru(occurred_at) WHERE published_at IS NULL;

ALTER TABLE dot_noi_tru
    ADD CONSTRAINT fk_dot_noi_tru_clearance
        FOREIGN KEY (deposit_clearance_id) REFERENCES xac_nhan_tai_chinh_noi_tru(clearance_id),
    ADD CONSTRAINT fk_dot_noi_tru_settlement
        FOREIGN KEY (settlement_id) REFERENCES quyet_toan_noi_tru(settlement_id),
    ADD CONSTRAINT fk_dot_noi_tru_emergency_override
        FOREIGN KEY (emergency_override_id) REFERENCES phe_duyet_ngoai_le_noi_tru(override_id),
    ADD CONSTRAINT fk_dot_noi_tru_close_override
        FOREIGN KEY (close_override_id) REFERENCES phe_duyet_ngoai_le_noi_tru(override_id),
    ADD CONSTRAINT fk_dot_noi_tru_discharge_summary
        FOREIGN KEY (discharge_summary_id) REFERENCES tom_tat_ra_vien(summary_id);
