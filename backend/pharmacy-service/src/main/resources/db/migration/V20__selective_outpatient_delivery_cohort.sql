-- No historical rows are released. Existing V1 deliveries retain NULL cohort and remain HELD.
CREATE TABLE pharmacy_outpatient_delivery_cohort (
    prescription_id UUID PRIMARY KEY REFERENCES prescription(prescription_id),
    acceptance_reference VARCHAR(255) NOT NULL CHECK (length(btrim(acceptance_reference)) BETWEEN 1 AND 255),
    approved_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
ALTER TABLE pharmacy_event_outbox ADD COLUMN care_delivery_cohort_id UUID
    REFERENCES pharmacy_outpatient_delivery_cohort(prescription_id);
ALTER TABLE pharmacy_event_outbox DROP CONSTRAINT ck_outbox_care_contract;
ALTER TABLE pharmacy_event_outbox ADD CONSTRAINT ck_outbox_care_contract CHECK (
    (care_contract_version=0 AND care_lifecycle_order IS NULL AND care_delivery_cohort_id IS NULL)
    OR (care_contract_version=1 AND aggregate_id IS NOT NULL AND care_lifecycle_order IN (0,1)
        AND care_lifecycle_order IS NOT NULL
        AND ((NOT delivery_enabled AND care_delivery_cohort_id IS NULL)
            OR (delivery_enabled AND care_delivery_cohort_id=aggregate_id AND care_delivery_cohort_id IS NOT NULL))
        AND ((care_lifecycle_order=0 AND routing_key='prescription.created')
            OR (care_lifecycle_order=1 AND routing_key IN ('prescription.filled','prescription.dispense.failed',
                'prescription.cancelled','prescription.expired')))));

CREATE FUNCTION prevent_historical_care_delivery_activation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.care_contract_version=1 AND (NEW.care_delivery_cohort_id IS DISTINCT FROM OLD.care_delivery_cohort_id
        OR NEW.delivery_enabled IS DISTINCT FROM OLD.delivery_enabled) THEN
        RAISE EXCEPTION 'V1 delivery cohort is immutable; historical HELD needs a separate audited migration';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER pharmacy_care_delivery_cohort_immutable BEFORE UPDATE ON pharmacy_event_outbox
    FOR EACH ROW EXECUTE FUNCTION prevent_historical_care_delivery_activation();
