-- Report read model schema. Source ids are UUID references only; no cross-service FKs.

CREATE TABLE DAILY_VISIT_REPORT (
    report_id           UUID PRIMARY KEY,
    report_date         DATE NOT NULL,
    department_id       UUID,
    visit_count         INT NOT NULL DEFAULT 0,
    lab_count           INT NOT NULL DEFAULT 0,
    prescription_count  INT NOT NULL DEFAULT 0,
    revenue             DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT ck_daily_visit_count_non_negative CHECK (visit_count >= 0),
    CONSTRAINT ck_daily_lab_count_non_negative CHECK (lab_count >= 0),
    CONSTRAINT ck_daily_prescription_count_non_negative CHECK (prescription_count >= 0),
    CONSTRAINT ck_daily_revenue_non_negative CHECK (revenue >= 0)
);
CREATE UNIQUE INDEX uq_daily_report_date_dept
    ON DAILY_VISIT_REPORT (report_date, department_id) NULLS NOT DISTINCT;

CREATE TABLE MONTHLY_REVENUE_REPORT (
    report_id       UUID PRIMARY KEY,
    month           INT NOT NULL,
    year            INT NOT NULL,
    department_id   UUID,
    total_revenue   DECIMAL(15,2) NOT NULL DEFAULT 0,
    invoice_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT ck_monthly_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_monthly_year_positive CHECK (year > 0),
    CONSTRAINT ck_monthly_revenue_non_negative CHECK (total_revenue >= 0),
    CONSTRAINT ck_monthly_invoice_count_non_negative CHECK (invoice_count >= 0)
);
CREATE UNIQUE INDEX uq_monthly_revenue_scope
    ON MONTHLY_REVENUE_REPORT (year, month, department_id) NULLS NOT DISTINCT;

CREATE TABLE DRUG_STATISTIC (
    statistic_id       UUID PRIMARY KEY,
    drug_id            UUID NOT NULL,
    drug_name          VARCHAR(150) NOT NULL,
    report_date        DATE NOT NULL,
    department_id      UUID,
    dispensed_quantity  INT NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ,
    CONSTRAINT ck_drug_name_not_blank CHECK (btrim(drug_name) <> ''),
    CONSTRAINT ck_drug_quantity_non_negative CHECK (dispensed_quantity >= 0)
);
CREATE UNIQUE INDEX uq_drug_statistic_scope
    ON DRUG_STATISTIC (drug_id, report_date, department_id) NULLS NOT DISTINCT;
CREATE INDEX idx_drug_statistic_date ON DRUG_STATISTIC (report_date);

CREATE TABLE PROCESSED_EVENT (
    event_id      UUID PRIMARY KEY,
    routing_key   VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE PAYMENT_CONTRIBUTION (
    invoice_id          UUID PRIMARY KEY,
    completed_event_id  UUID UNIQUE,
    failed_event_id     UUID UNIQUE,
    payment_date        DATE,
    department_id       UUID,
    amount              DECIMAL(15,2),
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING_REVERSAL', 'APPLIED', 'REVERSED')),
    CONSTRAINT ck_payment_amount_positive CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT ck_payment_state_data CHECK (
        (status = 'PENDING_REVERSAL' AND failed_event_id IS NOT NULL AND completed_event_id IS NULL
            AND payment_date IS NULL AND amount IS NULL)
        OR (status = 'APPLIED' AND completed_event_id IS NOT NULL AND failed_event_id IS NULL
            AND payment_date IS NOT NULL AND amount IS NOT NULL)
        OR (status = 'REVERSED' AND completed_event_id IS NOT NULL AND failed_event_id IS NOT NULL
            AND payment_date IS NOT NULL AND amount IS NOT NULL)
    )
);
