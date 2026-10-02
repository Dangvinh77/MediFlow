-- Historical names cannot be recovered from today's catalogue. Legacy rows intentionally remain NULL.
ALTER TABLE PRESCRIPTION_LINE ADD COLUMN drug_name_snapshot VARCHAR(150);
ALTER TABLE PRESCRIPTION_LINE ADD CONSTRAINT ck_prescription_line_name_snapshot
    CHECK (drug_name_snapshot IS NULL OR length(btrim(drug_name_snapshot)) > 0);

-- JPA updated_at is an audit timestamp, not immutable business evidence. No legacy backfill.
ALTER TABLE PRESCRIPTION ADD COLUMN lifecycle_at_iso VARCHAR(35);
ALTER TABLE DISPENSE_SLIP ADD COLUMN lifecycle_at_iso VARCHAR(35);

ALTER TABLE PHARMACY_EVENT_OUTBOX ADD COLUMN delivery_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE PHARMACY_EVENT_OUTBOX ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE PHARMACY_EVENT_OUTBOX ADD COLUMN care_lifecycle_order SMALLINT;
ALTER TABLE PHARMACY_EVENT_OUTBOX ADD CONSTRAINT ck_outbox_care_contract
    CHECK ((care_contract_version = 0 AND care_lifecycle_order IS NULL)
        OR (care_contract_version = 1 AND aggregate_id IS NOT NULL
            AND care_lifecycle_order IS NOT NULL AND care_lifecycle_order IN (0, 1)
            AND NOT delivery_enabled
            AND ((care_lifecycle_order = 0 AND routing_key = 'prescription.created')
                OR (care_lifecycle_order = 1 AND routing_key IN ('prescription.filled',
                    'prescription.dispense.failed', 'prescription.cancelled', 'prescription.expired')))));
-- Exactly one creation and one terminal outcome per V1 prescription, even with new delivery IDs.
CREATE UNIQUE INDEX uq_outbox_care_lifecycle ON PHARMACY_EVENT_OUTBOX(aggregate_id, care_lifecycle_order)
    WHERE care_contract_version = 1;
-- Live V1 activation requires an explicit reviewed migration/consumer cutover, not an admin replay.
