-- v2.0 Lab catalogue: the catalogue service a Clinical Lab order names. Null for orders placed
-- before mediflow.v2.lab-catalog; no business backfill.
ALTER TABLE clinical_lab_order ADD COLUMN service_code VARCHAR(32);
ALTER TABLE clinical_lab_order ADD CONSTRAINT ck_clinical_lab_order_service_code
    CHECK (service_code IS NULL OR length(btrim(service_code)) > 0);
