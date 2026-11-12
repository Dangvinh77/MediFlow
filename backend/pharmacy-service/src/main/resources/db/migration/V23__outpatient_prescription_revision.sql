-- V1 outpatient prescription content has a monotonic revision shared with Billing.
ALTER TABLE prescription
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN revision_at_iso VARCHAR(35),
    ADD CONSTRAINT ck_prescription_revision_nonnegative CHECK (revision >= 0);
ALTER TABLE prescription ADD CONSTRAINT ck_prescription_revision_time
    CHECK ((revision = 0 AND revision_at_iso IS NULL) OR (revision > 0 AND revision_at_iso IS NOT NULL));

ALTER TABLE stock_reservation DROP CONSTRAINT ck_reservation_release_reason;
ALTER TABLE stock_reservation ADD CONSTRAINT ck_reservation_release_reason CHECK (
    release_reason IS NULL OR release_reason IN (
        'PRESCRIPTION_CANCELLED', 'PAYMENT_FAILED', 'DISPENSE_FAILED', 'PRESCRIPTION_EDITED',
        'ADMIN_OVERRIDE', 'TTL_EXPIRED', 'LEGACY_MIGRATION'
    )
);

-- Revision events occupy their revision number (creation remains 0); terminal events follow it.
ALTER TABLE pharmacy_event_outbox DROP CONSTRAINT ck_outbox_care_contract;
DROP INDEX uq_outbox_care_lifecycle;
ALTER TABLE pharmacy_event_outbox ALTER COLUMN care_lifecycle_order TYPE INTEGER USING care_lifecycle_order::INTEGER;
CREATE UNIQUE INDEX uq_outbox_care_lifecycle ON pharmacy_event_outbox(aggregate_id, care_lifecycle_order)
    WHERE care_contract_version = 1;
ALTER TABLE pharmacy_event_outbox ADD CONSTRAINT ck_outbox_care_contract CHECK (
    (care_contract_version = 0 AND care_lifecycle_order IS NULL AND care_delivery_cohort_id IS NULL)
    OR (care_contract_version = 1 AND aggregate_id IS NOT NULL AND care_lifecycle_order >= 0
        AND ((NOT delivery_enabled AND care_delivery_cohort_id IS NULL)
            OR (delivery_enabled AND care_delivery_cohort_id = aggregate_id AND care_delivery_cohort_id IS NOT NULL))
        AND ((care_lifecycle_order = 0 AND routing_key = 'prescription.created')
            OR (care_lifecycle_order > 0 AND routing_key IN ('prescription.revised', 'prescription.filled',
                'prescription.dispense.failed', 'prescription.cancelled', 'prescription.expired')))
    )
);

-- A durable intent is created before Billing prepare. APPLIED is written in the same transaction
-- as the Pharmacy aggregate, reservation replacement, and immutable outbox event.
CREATE TABLE pharmacy_prescription_revision_operation (
    operation_id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES prescription(prescription_id),
    expected_revision INTEGER NOT NULL CHECK (expected_revision >= 0),
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID,
    actor_role VARCHAR(30) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL CHECK (length(btrim(correlation_id)) BETWEEN 1 AND 120),
    command_fingerprint CHAR(64) NOT NULL CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    requested_lines JSONB NOT NULL,
    billing_payload JSONB NOT NULL,
    operation_status VARCHAR(20) NOT NULL CHECK (operation_status IN ('PENDING', 'PREPARED', 'APPLIED', 'COMMITTED', 'ABORTING', 'ABORTED')),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK ((operation_status IN ('ABORTING', 'ABORTED')) = (failure_code IS NOT NULL AND failure_message IS NOT NULL))
);
CREATE UNIQUE INDEX uq_pharmacy_active_prescription_revision
    ON pharmacy_prescription_revision_operation(prescription_id)
    WHERE operation_status IN ('PENDING', 'PREPARED', 'APPLIED', 'ABORTING');
CREATE INDEX idx_pharmacy_revision_recovery
    ON pharmacy_prescription_revision_operation(created_at, operation_id)
    WHERE operation_status IN ('PENDING', 'PREPARED', 'APPLIED', 'ABORTING');

-- Intent identity and priced source snapshot are immutable; only state/error metadata can move.
CREATE FUNCTION protect_pharmacy_prescription_revision_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.operation_id, NEW.prescription_id, NEW.expected_revision, NEW.actor_account_id, NEW.actor_staff_id,
           NEW.actor_role, NEW.correlation_id, NEW.command_fingerprint, NEW.requested_lines, NEW.billing_payload, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.operation_id, OLD.prescription_id, OLD.expected_revision, OLD.actor_account_id, OLD.actor_staff_id,
           OLD.actor_role, OLD.correlation_id, OLD.command_fingerprint, OLD.requested_lines, OLD.billing_payload, OLD.created_at) THEN
        RAISE EXCEPTION 'Prescription revision operation identity and priced snapshot are immutable';
    END IF;
    IF OLD.operation_status <> NEW.operation_status AND NOT (
        (OLD.operation_status = 'PENDING' AND NEW.operation_status IN ('PREPARED', 'ABORTING'))
        OR (OLD.operation_status = 'PREPARED' AND NEW.operation_status IN ('APPLIED', 'ABORTING'))
        OR (OLD.operation_status = 'APPLIED' AND NEW.operation_status = 'COMMITTED')
        OR (OLD.operation_status = 'ABORTING' AND NEW.operation_status = 'ABORTED')
    ) THEN
        RAISE EXCEPTION 'Invalid pharmacy prescription revision operation transition';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER pharmacy_prescription_revision_operation_immutable
    BEFORE UPDATE ON pharmacy_prescription_revision_operation
    FOR EACH ROW EXECUTE FUNCTION protect_pharmacy_prescription_revision_operation();
