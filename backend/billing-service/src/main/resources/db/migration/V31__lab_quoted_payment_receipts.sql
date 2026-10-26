-- Immutable quote and actor evidence for the additive positive outpatient Lab payment route.
CREATE TABLE lab_quoted_payment_receipt (
    payment_request_id UUID PRIMARY KEY REFERENCES PAYMENT_REQUEST(payment_request_id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES PAYMENT_TRANSACTION(transaction_id),
    quote_id UUID NOT NULL,
    actor_account_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{1,120}$'),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'VND'),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH','TRANSFER')),
    quote_fingerprint CHAR(64) NOT NULL CHECK (quote_fingerprint ~ '^[0-9a-f]{64}$'),
    command_fingerprint CHAR(64) NOT NULL CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    clearance_id UUID NOT NULL UNIQUE REFERENCES FINANCIAL_CLEARANCE(clearance_id),
    completed_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (payment_request_id, quote_id)
        REFERENCES outpatient_quote(payment_request_id, quote_id)
);

CREATE FUNCTION validate_lab_quoted_payment_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE valid_receipt BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
          FROM PAYMENT_REQUEST r
          JOIN BILLING_ACCOUNT a ON a.account_id=r.account_id
          JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
          JOIN lab_test_charge_source s ON s.payment_request_id=r.payment_request_id
          JOIN outpatient_current_quote cq ON cq.payment_request_id=r.payment_request_id
          JOIN outpatient_quote q ON q.payment_request_id=cq.payment_request_id AND q.quote_id=cq.quote_id
          JOIN PAYMENT_REQUEST_CHARGE rc ON rc.payment_request_id=r.payment_request_id
          JOIN CHARGE c ON c.charge_id=rc.charge_id
          JOIN PAYMENT_TRANSACTION p ON p.transaction_id=NEW.transaction_id
          JOIN FINANCIAL_CLEARANCE f ON f.clearance_id=NEW.clearance_id
         WHERE r.payment_request_id=NEW.payment_request_id AND r.status='PAID'
           AND r.purpose='LAB_TEST' AND r.requested_amount=NEW.amount AND r.currency=NEW.currency
           AND a.status='OPEN' AND a.care_episode_type='OUTPATIENT_VISIT'
           AND a.account_id=f.account_id AND a.patient_id=s.patient_id AND a.department_id=s.department_id
           AND a.care_episode_type=s.care_episode_type AND a.care_episode_id=s.care_episode_id
           AND q.quote_id=NEW.quote_id AND q.patient_id=a.patient_id AND q.care_episode_id=a.care_episode_id
           AND q.patient_payable=r.requested_amount AND q.expires_at=r.expires_at
           AND q.snapshot->'lines' IS NOT NULL AND jsonb_typeof(q.snapshot->'lines')='array'
           AND jsonb_array_length(q.snapshot->'lines')=1
           AND q.snapshot #>> '{lines,0,sourceId}'=s.lab_id::text
           AND q.snapshot #>> '{lines,0,sourceType}'='LAB_TEST'
           AND q.snapshot #>> '{lines,0,priceCode}'=s.price_code
           AND t.record_id=s.record_id AND t.lab_test_ids=ARRAY[s.lab_id]
           AND t.appointment_id IS NULL AND t.prescription_id IS NULL
           AND t.admission_id IS NULL AND t.surgery_case_id IS NULL
           AND c.source_type='LAB_TEST' AND c.source_id=s.lab_id AND c.price_code=s.price_code
           AND c.account_id=a.account_id AND c.patient_id=a.patient_id AND c.department_id=a.department_id
           AND c.status='POSTED' AND c.description=(q.snapshot #>> '{lines,0,description}')
           AND rc.requested_amount=r.requested_amount
           AND c.quantity=(q.snapshot #>> '{lines,0,quantity}')::numeric
           AND c.unit_amount=(q.snapshot #>> '{lines,0,unitPrice}')::numeric
           AND c.gross_amount=(q.snapshot #>> '{lines,0,grossAmount}')::numeric
           AND p.payment_request_id=r.payment_request_id AND p.account_id=a.account_id
           AND p.transaction_type='PAYMENT' AND p.classification='SERVICE_PAYMENT' AND p.status='COMPLETED'
           AND p.amount=r.requested_amount AND p.currency=r.currency AND p.payment_method=NEW.payment_method
           AND p.idempotency_key=NEW.idempotency_key AND p.actor_account_id=NEW.actor_account_id
           AND p.completed_at=NEW.completed_at
           AND f.payment_request_id=r.payment_request_id AND f.invoice_id=r.invoice_id
           AND f.patient_id=a.patient_id AND f.purpose='LAB_TEST'
           AND f.care_episode_type=a.care_episode_type AND f.care_episode_id=a.care_episode_id
           AND f.record_id=s.record_id AND f.lab_test_ids=ARRAY[s.lab_id] AND f.appointment_id IS NULL
           AND f.prescription_id IS NULL AND f.admission_id IS NULL AND f.surgery_case_id IS NULL
           AND f.amount=r.requested_amount AND f.currency=r.currency
           AND f.payment_method=NEW.payment_method AND NOT f.emergency_override AND f.revoked_at IS NULL
           AND f.expires_at=r.expires_at
           AND abs(extract(epoch FROM (f.granted_at-NEW.completed_at))) <= 0.000001
           AND abs(extract(epoch FROM (r.completed_at-NEW.completed_at))) <= 0.000001
           AND (SELECT count(*) FROM PAYMENT_TRANSACTION allp WHERE allp.payment_request_id=r.payment_request_id
                 AND allp.status='COMPLETED' AND allp.transaction_type='PAYMENT')=1
           AND NOT EXISTS (SELECT 1 FROM scheduled_payment_operation o WHERE o.payment_request_id=r.payment_request_id)
           AND NOT EXISTS (SELECT 1 FROM lab_zero_payable_eligibility z WHERE z.payment_request_id=r.payment_request_id)
           AND (SELECT count(*) FROM FINANCIAL_CLEARANCE existing
                 WHERE existing.payment_request_id=r.payment_request_id)=1
           AND (SELECT count(*) FROM PAYMENT_REQUEST_CHARGE links
                 WHERE links.payment_request_id=r.payment_request_id)=1
    ) INTO valid_receipt;
    IF NOT valid_receipt THEN
        RAISE EXCEPTION 'Lab quoted payment receipt does not match current ledger evidence'
            USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER lab_quoted_payment_receipt_guard
    BEFORE INSERT ON lab_quoted_payment_receipt
    FOR EACH ROW EXECUTE FUNCTION validate_lab_quoted_payment_receipt();

CREATE FUNCTION reject_lab_quoted_payment_receipt_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Lab quoted payment receipts are immutable';
END;
$$;

CREATE TRIGGER lab_quoted_payment_receipt_immutable
    BEFORE UPDATE OR DELETE ON lab_quoted_payment_receipt
    FOR EACH ROW EXECUTE FUNCTION reject_lab_quoted_payment_receipt_mutation();
