-- Existing staff need verified identity backfill; never fabricate citizen IDs.
ALTER TABLE staff ADD COLUMN identity_number VARCHAR(12);
ALTER TABLE staff ADD CONSTRAINT ck_staff_identity_number
    CHECK (identity_number IS NULL OR identity_number ~ '^[0-9]{12}$');
ALTER TABLE staff ADD CONSTRAINT uk_staff_identity_number UNIQUE (identity_number);
