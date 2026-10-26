CREATE TABLE outpatient_quote (
    quote_id UUID PRIMARY KEY,
    payment_request_id UUID NOT NULL REFERENCES payment_request(payment_request_id),
    patient_id UUID NOT NULL,
    care_episode_id UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at),
    gross_amount NUMERIC(19,2) NOT NULL CHECK (gross_amount >= 0),
    support_amount NUMERIC(19,2) NOT NULL CHECK (support_amount >= 0 AND support_amount <= gross_amount),
    patient_payable NUMERIC(19,2) NOT NULL CHECK (patient_payable = gross_amount - support_amount),
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    UNIQUE (payment_request_id, quote_id)
);
CREATE TABLE outpatient_current_quote (
    payment_request_id UUID PRIMARY KEY REFERENCES payment_request(payment_request_id),
    quote_id UUID NOT NULL UNIQUE,
    FOREIGN KEY (payment_request_id, quote_id) REFERENCES outpatient_quote(payment_request_id, quote_id)
);
CREATE FUNCTION reject_outpatient_quote_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'outpatient quote snapshots are immutable';
END;
$$;
CREATE TRIGGER outpatient_quote_immutable BEFORE UPDATE OR DELETE ON outpatient_quote
FOR EACH ROW EXECUTE FUNCTION reject_outpatient_quote_mutation();
