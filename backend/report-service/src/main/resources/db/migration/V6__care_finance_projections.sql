-- Additive V2 read models. V1/V2 compatibility projections and their history remain untouched.
-- event_id records delivery provenance; business-operation keys own semantic idempotency.

CREATE TABLE financial_contribution (
    contribution_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    source_revision INTEGER NOT NULL,
    account_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    contribution_type VARCHAR(32) NOT NULL,
    cash_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    liability_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    revenue_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    refund_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    receivable_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    original_contribution_id UUID REFERENCES financial_contribution(contribution_id),
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_financial_contribution_source_type CHECK (btrim(source_type) <> ''),
    CONSTRAINT ck_financial_contribution_revision CHECK (source_revision > 0),
    CONSTRAINT ck_financial_contribution_episode_type CHECK (
        care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')
    ),
    CONSTRAINT ck_financial_contribution_type CHECK (contribution_type IN
        ('PAYMENT', 'DEPOSIT', 'REVENUE_RECOGNITION', 'REFUND', 'RECEIVABLE', 'REVERSAL'))
);

-- A redelivery with a new eventId must still be idempotent for the same business operation.
-- department_id participates so one operation may contain explicit department allocations;
-- NULL is a real unallocated/hospital scope, not a UUID sentinel.
CREATE UNIQUE INDEX uq_financial_contribution_business_operation
    ON financial_contribution
        (source_type, source_id, source_revision, contribution_type, department_id)
        NULLS NOT DISTINCT;
CREATE INDEX idx_financial_contribution_event_id
    ON financial_contribution(event_id);
CREATE INDEX idx_financial_contribution_period
    ON financial_contribution(business_date, department_id);
CREATE INDEX idx_financial_contribution_episode
    ON financial_contribution(care_episode_type, care_episode_id);

CREATE TABLE daily_financial_report (
    report_id UUID PRIMARY KEY,
    report_date DATE NOT NULL,
    department_id UUID,
    cash_received DECIMAL(19, 2) NOT NULL DEFAULT 0,
    deposit_liability DECIMAL(19, 2) NOT NULL DEFAULT 0,
    earned_revenue DECIMAL(19, 2) NOT NULL DEFAULT 0,
    refunds DECIMAL(19, 2) NOT NULL DEFAULT 0,
    outstanding_receivable DECIMAL(19, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_daily_financial_report_scope
    ON daily_financial_report(report_date, department_id) NULLS NOT DISTINCT;

CREATE TABLE operational_contribution (
    contribution_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    source_revision INTEGER NOT NULL,
    metric_type VARCHAR(40) NOT NULL,
    department_id UUID,
    care_episode_type VARCHAR(32),
    care_episode_id UUID,
    metric_date DATE NOT NULL,
    numeric_value DECIMAL(19, 3) NOT NULL DEFAULT 1,
    category VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operational_contribution_source_type CHECK (btrim(source_type) <> ''),
    CONSTRAINT ck_operational_contribution_metric_type CHECK (btrim(metric_type) <> ''),
    CONSTRAINT ck_operational_contribution_revision CHECK (source_revision > 0),
    CONSTRAINT ck_operational_contribution_episode_pair CHECK (
        (care_episode_type IS NULL AND care_episode_id IS NULL)
        OR (care_episode_type IS NOT NULL
            AND care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION') AND care_episode_id IS NOT NULL)
    ),
    CONSTRAINT ck_operational_contribution_category CHECK (
        category IS NULL OR btrim(category) <> ''
    )
);
CREATE UNIQUE INDEX uq_operational_contribution_business_operation
    ON operational_contribution
        (source_type, source_id, source_revision, metric_type, department_id)
        NULLS NOT DISTINCT;
CREATE INDEX idx_operational_contribution_event_id
    ON operational_contribution(event_id);
CREATE INDEX idx_operational_contribution_period
    ON operational_contribution(metric_date, department_id);
CREATE INDEX idx_operational_contribution_episode
    ON operational_contribution(care_episode_type, care_episode_id);

CREATE TABLE daily_operational_report (
    report_id UUID PRIMARY KEY,
    report_date DATE NOT NULL,
    department_id UUID,
    completed_visits BIGINT NOT NULL DEFAULT 0,
    admissions BIGINT NOT NULL DEFAULT 0,
    discharges BIGINT NOT NULL DEFAULT 0,
    inpatient_days BIGINT NOT NULL DEFAULT 0,
    surgeries_completed BIGINT NOT NULL DEFAULT 0,
    surgeries_cancelled BIGINT NOT NULL DEFAULT 0,
    surgery_duration_minutes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_daily_operational_report_scope
    ON daily_operational_report(report_date, department_id) NULLS NOT DISTINCT;
