-- Non-final account stock is separate from daily flows and from accepted/public financial reports.
CREATE TABLE report_settlement_snapshot (
    settlement_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    settlement_version INTEGER NOT NULL CHECK (settlement_version > 0),
    supersedes_settlement_id UUID,
    snapshot_version INTEGER NOT NULL CHECK (snapshot_version = 1),
    payload TEXT NOT NULL,
    fact_fingerprint CHAR(64) NOT NULL,
    snapshot_fingerprint CHAR(64) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_settlement_revision UNIQUE(account_id, settlement_version)
);
CREATE INDEX idx_report_settlement_predecessor ON report_settlement_snapshot(supersedes_settlement_id);
CREATE UNIQUE INDEX uq_report_settlement_successor ON report_settlement_snapshot(supersedes_settlement_id)
    WHERE supersedes_settlement_id IS NOT NULL;
CREATE TABLE report_settlement_delivery (
    event_id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES report_settlement_snapshot(settlement_id),
    envelope_fingerprint CHAR(64) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE report_receivable_current (
    account_id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES report_settlement_snapshot(settlement_id),
    settlement_version INTEGER NOT NULL CHECK (settlement_version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE report_receivable_stock (
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    report_zone VARCHAR(80) NOT NULL,
    department_id UUID,
    outstanding_receivable NUMERIC(19,2) NOT NULL CHECK (outstanding_receivable >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_report_receivable_stock_scope
    ON report_receivable_stock(currency, report_zone, department_id) NULLS NOT DISTINCT;
