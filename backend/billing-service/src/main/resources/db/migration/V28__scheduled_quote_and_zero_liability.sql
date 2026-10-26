ALTER TABLE scheduled_payment_operation
    ADD COLUMN quote_id UUID REFERENCES outpatient_quote(quote_id),
    ADD COLUMN basis VARCHAR(24) NOT NULL DEFAULT 'PATIENT_PAYMENT',
    DROP CONSTRAINT scheduled_payment_operation_amount_check,
    DROP CONSTRAINT ck_scheduled_payment_transaction_state,
    ADD CONSTRAINT ck_scheduled_payment_basis CHECK (
        (basis = 'PATIENT_PAYMENT' AND amount > 0)
        OR (basis = 'ZERO_LIABILITY' AND amount = 0 AND quote_id IS NOT NULL)),
    ADD CONSTRAINT ck_scheduled_payment_transaction_state CHECK (
        (status = 'COMPLETED' AND basis = 'PATIENT_PAYMENT' AND transaction_id IS NOT NULL)
        OR ((status <> 'COMPLETED' OR basis = 'ZERO_LIABILITY') AND transaction_id IS NULL));

CREATE FUNCTION protect_scheduled_quote_basis() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.quote_id, NEW.basis) IS DISTINCT FROM ROW(OLD.quote_id, OLD.basis) THEN
        RAISE EXCEPTION 'scheduled quote and eligibility basis are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER scheduled_quote_basis_immutable BEFORE UPDATE ON scheduled_payment_operation
    FOR EACH ROW EXECUTE FUNCTION protect_scheduled_quote_basis();
