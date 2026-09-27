ALTER TABLE lab_test
    ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN care_episode_type VARCHAR(32),
    ADD COLUMN care_episode_id UUID,
    ADD COLUMN source_order_id UUID,
    ADD COLUMN price_code VARCHAR(64),
    ADD COLUMN clearance_id UUID,
    ADD COLUMN clearance_granted_at TIMESTAMPTZ,
    ADD COLUMN emergency_override_id UUID,
    ADD COLUMN result_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE lab_test
    ADD CONSTRAINT ck_lab_contract_version
        CHECK (care_contract_version IN (0, 1)),
    ADD CONSTRAINT ck_lab_v1_episode
        CHECK (care_contract_version = 0 OR (
            care_episode_type IS NOT NULL
            AND care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')
            AND care_episode_id IS NOT NULL
            AND price_code IS NOT NULL
            AND length(trim(price_code)) > 0
        )),
    ADD CONSTRAINT ck_lab_execution_gate
        CHECK (care_contract_version = 0 OR status NOT IN ('IN_PROGRESS', 'COMPLETED')
            OR clearance_id IS NOT NULL OR emergency_override_id IS NOT NULL);

CREATE UNIQUE INDEX uq_lab_source_order
    ON lab_test(source_order_id) WHERE source_order_id IS NOT NULL;
CREATE INDEX idx_lab_episode ON lab_test(care_episode_type, care_episode_id);

CREATE TABLE lab_financial_clearance (
    clearance_target_id UUID PRIMARY KEY,
    clearance_id UUID NOT NULL,
    event_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    test_id UUID NOT NULL REFERENCES lab_test(test_id),
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_clearance_target UNIQUE(clearance_id, test_id),
    CONSTRAINT uq_lab_clearance_event_target UNIQUE(event_id, test_id)
);
CREATE INDEX idx_lab_clearance_test_expiry
    ON lab_financial_clearance(test_id, expires_at, granted_at DESC);

CREATE TABLE lab_emergency_override (
    override_id UUID PRIMARY KEY,
    test_id UUID NOT NULL REFERENCES lab_test(test_id),
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lab_override_test ON lab_emergency_override(test_id);

CREATE TABLE lab_outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_lab_outbox_unpublished
    ON lab_outbox_event(occurred_at) WHERE published_at IS NULL;
