-- Service line of an outpatient receipt (EXAM / LAB_TEST / PRESCRIPTION) for operational
-- statistics. Derived from the Billing payload targets at consumption; it is not part of either
-- receipt fingerprint. NULL: deposits, or receipts recorded before this column existed.
-- IF NOT EXISTS: the synthetic demo snapshot pre-creates and backfills the column on restore.
ALTER TABLE report_cash_receipt ADD COLUMN IF NOT EXISTS service_purpose VARCHAR(16);
ALTER TABLE report_cash_receipt DROP CONSTRAINT IF EXISTS ck_cash_receipt_service_purpose;
ALTER TABLE report_cash_receipt ADD CONSTRAINT ck_cash_receipt_service_purpose
    CHECK (service_purpose IS NULL OR service_purpose IN ('EXAM','LAB_TEST','PRESCRIPTION'));
CREATE INDEX IF NOT EXISTS idx_cash_receipt_purpose_period
    ON report_cash_receipt(business_date, department_id, service_purpose);
