-- Sổ cái V2 (care-finance) — cộng thêm, không đụng FEE/INVOICE hiện tại.
-- Nguồn: docs/eproject_general_plan/backend-spec/care-finance-v2/06-billing.md §3.
--
-- Chỗ tự quyết: spec gốc đặt tên "V8__care_finance_ledger.sql" (đánh số hypothetical từ lúc viết
-- tài liệu, khi migration thật của module mới chỉ có V1). Ở đây đổi thành V4 cho khớp đúng dãy số
-- Flyway thật hiện có (V1 init, V2 billing_event_outbox, V3 drop_invoice_dispense_id).
--
-- Chỗ tự quyết thứ 2: KHÔNG tạo lại bảng BILLING_OUTBOX_EVENT như spec liệt kê — billing-service đã
-- có sẵn một outbox tổng quát đáng tin cậy (BILLING_EVENT_OUTBOX, thêm bởi Huy ở
-- V2__billing_event_outbox.sql, dùng chung cho mọi loại sự kiện qua BillingEventPublisherAdapter).
-- Dựng thêm một bảng outbox thứ hai sẽ trùng lặp cơ chế đã có; các sự kiện V2 ledger (bao gồm
-- financial.clearance.granted) sẽ tái dùng BILLING_EVENT_OUTBOX khi tới tầng application/infrastructure.

CREATE TABLE BILLING_ACCOUNT (
    account_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    version BIGINT NOT NULL DEFAULT 0,
    opened_at TIMESTAMPTZ NOT NULL,
    charge_closed_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_billing_account_episode UNIQUE(care_episode_type, care_episode_id),
    CONSTRAINT ck_billing_account_episode CHECK
        (care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')),
    CONSTRAINT ck_billing_account_status CHECK
        (status IN ('OPEN', 'CHARGE_CLOSED', 'SETTLEMENT_PENDING', 'SETTLED', 'CLOSED'))
);

CREATE TABLE CHARGE (
    charge_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    price_code VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity DECIMAL(12,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_amount DECIMAL(19,2) NOT NULL CHECK (unit_amount >= 0),
    gross_amount DECIMAL(19,2) NOT NULL CHECK (gross_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    void_reason VARCHAR(500),
    incurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_source UNIQUE(source_type, source_id, price_code),
    CONSTRAINT ck_charge_status CHECK (status IN ('POSTED', 'VOIDED'))
);
CREATE INDEX idx_charge_account ON CHARGE(account_id, status, incurred_at);

CREATE TABLE PAYMENT_REQUEST (
    payment_request_id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    requested_amount DECIMAL(19,2) NOT NULL CHECK (requested_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_payment_request_purpose CHECK
        (purpose IN ('EXAM', 'LAB_TEST', 'PRESCRIPTION', 'ADMISSION_DEPOSIT', 'SURGERY', 'SETTLEMENT')),
    CONSTRAINT ck_payment_request_status CHECK
        (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'EXPIRED', 'CANCELLED'))
);

CREATE TABLE PAYMENT_REQUEST_CHARGE (
    payment_request_id UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    requested_amount DECIMAL(19,2) NOT NULL CHECK (requested_amount >= 0),
    PRIMARY KEY(payment_request_id, charge_id)
);

CREATE TABLE PAYMENT_TRANSACTION (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    payment_request_id UUID REFERENCES PAYMENT_REQUEST(payment_request_id),
    transaction_type VARCHAR(20) NOT NULL,
    classification VARCHAR(24) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(120),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    original_transaction_id UUID REFERENCES PAYMENT_TRANSACTION(transaction_id),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_transaction_type CHECK
        (transaction_type IN ('PAYMENT', 'REFUND', 'REVERSAL')),
    CONSTRAINT ck_payment_classification CHECK
        (classification IN ('SERVICE_PAYMENT', 'ADMISSION_DEPOSIT', 'SETTLEMENT_PAYMENT')),
    CONSTRAINT ck_payment_transaction_status CHECK
        (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_refund_original CHECK
        (transaction_type = 'PAYMENT' OR original_transaction_id IS NOT NULL)
);

CREATE TABLE PAYMENT_ALLOCATION (
    allocation_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_allocation UNIQUE(transaction_id, charge_id)
);

CREATE TABLE FINANCIAL_CLEARANCE (
    clearance_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    payment_request_id UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id),
    invoice_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    appointment_id UUID,
    record_id UUID,
    lab_test_ids UUID[] NOT NULL DEFAULT '{}',
    prescription_id UUID,
    admission_id UUID,
    surgery_case_id UUID,
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(20) NOT NULL,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_financial_clearance_request UNIQUE(payment_request_id),
    CONSTRAINT ck_clearance_purpose CHECK
        (purpose IN ('EXAM', 'LAB_TEST', 'PRESCRIPTION', 'ADMISSION_DEPOSIT', 'SURGERY')),
    CONSTRAINT ck_clearance_target CHECK (
        (purpose = 'EXAM' AND (appointment_id IS NOT NULL OR record_id IS NOT NULL))
        OR (purpose = 'LAB_TEST' AND cardinality(lab_test_ids) > 0)
        OR (purpose = 'PRESCRIPTION' AND prescription_id IS NOT NULL)
        OR (purpose = 'ADMISSION_DEPOSIT' AND admission_id IS NOT NULL)
        OR (purpose = 'SURGERY' AND surgery_case_id IS NOT NULL)
    )
);

CREATE TABLE INSURANCE_ADJUSTMENT (
    adjustment_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    admission_id UUID NOT NULL,
    decision_reference VARCHAR(120) NOT NULL,
    adjustment_type VARCHAR(20) NOT NULL,
    original_adjustment_id UUID REFERENCES INSURANCE_ADJUSTMENT(adjustment_id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_insurance_decision
        UNIQUE(account_id, decision_reference, adjustment_type),
    CONSTRAINT ck_insurance_adjustment_type
        CHECK (adjustment_type IN ('APPROVAL', 'REVERSAL')),
    CONSTRAINT ck_insurance_reversal_original CHECK
        (adjustment_type = 'APPROVAL' OR original_adjustment_id IS NOT NULL)
);

CREATE TABLE SETTLEMENT (
    settlement_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    admission_id UUID NOT NULL,
    settlement_version INTEGER NOT NULL,
    supersedes_settlement_id UUID REFERENCES SETTLEMENT(settlement_id),
    gross_amount DECIMAL(19,2) NOT NULL,
    insurance_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    patient_liability DECIMAL(19,2) NOT NULL,
    completed_payments DECIMAL(19,2) NOT NULL,
    completed_refunds DECIMAL(19,2) NOT NULL,
    balance DECIMAL(19,2) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_settlement_account_version UNIQUE(account_id, settlement_version),
    CONSTRAINT ck_settlement_outcome CHECK (outcome IN
        ('PAID_IN_FULL', 'ADDITIONAL_PAYMENT_REQUIRED', 'REFUND_DUE', 'DEBT_APPROVED', 'WAIVED'))
);
