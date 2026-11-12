ALTER TABLE prescription_charge_item ADD COLUMN drug_name VARCHAR(255);
ALTER TABLE prescription_charge_source
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0);
ALTER TABLE prescription_charge_source ADD COLUMN price_code VARCHAR(64);
UPDATE prescription_charge_source source
   SET price_code=charge.price_code
  FROM CHARGE charge
 WHERE charge.charge_id=source.charge_id;
ALTER TABLE prescription_charge_source ALTER COLUMN price_code SET NOT NULL;

CREATE TABLE prescription_charge_revision_operation (
    operation_id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES prescription_charge_source(prescription_id),
    expected_revision BIGINT NOT NULL CHECK (expected_revision >= 0),
    revision BIGINT NOT NULL CHECK (revision > 0),
    payload_fingerprint CHAR(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PREPARED','FINALIZED','ABORTED')),
    event_id UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    finalized_at TIMESTAMPTZ,
    aborted_at TIMESTAMPTZ,
    CONSTRAINT ck_prescription_revision_step CHECK (revision = expected_revision + 1),
    CONSTRAINT ck_prescription_revision_terminal_times CHECK (
        (status='FINALIZED') = (finalized_at IS NOT NULL)
        AND (status='ABORTED') = (aborted_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_prescription_revision_prepared_source
    ON prescription_charge_revision_operation(prescription_id) WHERE status='PREPARED';
CREATE UNIQUE INDEX uq_prescription_revision_finalized_number
    ON prescription_charge_revision_operation(prescription_id,revision) WHERE status='FINALIZED';
CREATE INDEX idx_prescription_revision_operation_source
    ON prescription_charge_revision_operation(prescription_id,created_at DESC);

CREATE FUNCTION protect_prescription_charge_revision_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.operation_id, NEW.prescription_id, NEW.expected_revision, NEW.revision,
           NEW.payload_fingerprint, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.operation_id, OLD.prescription_id, OLD.expected_revision, OLD.revision,
           OLD.payload_fingerprint, OLD.created_at) THEN
        RAISE EXCEPTION 'prescription revision binding is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status <> 'PREPARED' AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION 'prescription revision terminal status is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.event_id IS NOT NULL AND NEW.event_id IS DISTINCT FROM OLD.event_id THEN
        RAISE EXCEPTION 'prescription revision event identity is immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'PREPARED' AND NEW.status = 'FINALIZED'
       AND (NEW.finalized_at IS NULL OR NEW.aborted_at IS NOT NULL) THEN
        RAISE EXCEPTION 'finalized prescription revision requires a finalization timestamp' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'PREPARED' AND NEW.status = 'ABORTED'
       AND (NEW.aborted_at IS NULL OR NEW.finalized_at IS NOT NULL) THEN
        RAISE EXCEPTION 'aborted prescription revision requires an abort timestamp' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_protect_prescription_charge_revision_operation
    BEFORE UPDATE ON prescription_charge_revision_operation
    FOR EACH ROW EXECUTE FUNCTION protect_prescription_charge_revision_operation();
