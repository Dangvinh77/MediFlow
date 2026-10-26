CREATE TABLE lab_zero_payable_eligibility (
    eligibility_operation_id UUID PRIMARY KEY,
    payment_request_id UUID NOT NULL UNIQUE,
    quote_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE
        CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{1,120}$'),
    actor_account_id UUID NOT NULL,
    correlation_id VARCHAR(120) NOT NULL CHECK (length(btrim(correlation_id)) BETWEEN 1 AND 120),
    clearance_id UUID NOT NULL UNIQUE REFERENCES FINANCIAL_CLEARANCE(clearance_id),
    completed_at TIMESTAMPTZ NOT NULL,
    basis VARCHAR(24) NOT NULL DEFAULT 'ZERO_LIABILITY' CHECK (basis='ZERO_LIABILITY'),
    FOREIGN KEY (payment_request_id, quote_id)
        REFERENCES outpatient_quote(payment_request_id, quote_id),
    FOREIGN KEY (payment_request_id) REFERENCES PAYMENT_REQUEST(payment_request_id)
);

CREATE FUNCTION validate_lab_zero_payable_eligibility() RETURNS trigger LANGUAGE plpgsql AS $$
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
          JOIN FINANCIAL_CLEARANCE f ON f.clearance_id=NEW.clearance_id
          JOIN PAYMENT_REQUEST_CHARGE rc ON rc.payment_request_id=r.payment_request_id
          JOIN CHARGE c ON c.charge_id=rc.charge_id
         WHERE r.payment_request_id=NEW.payment_request_id AND r.status='PAID'
           AND r.purpose='LAB_TEST' AND r.requested_amount=0 AND r.currency='VND'
           AND a.status='OPEN' AND a.care_episode_type='OUTPATIENT_VISIT'
           AND a.patient_id=s.patient_id AND a.department_id=s.department_id
           AND a.care_episode_type=s.care_episode_type AND a.care_episode_id=s.care_episode_id
           AND q.quote_id=NEW.quote_id AND q.patient_id=a.patient_id
           AND q.care_episode_id=a.care_episode_id AND q.patient_payable=0
           AND q.expires_at=r.expires_at AND q.snapshot->'lines' IS NOT NULL
           AND jsonb_array_length(q.snapshot->'lines')=1
           AND q.snapshot #>> '{lines,0,sourceId}'=s.lab_id::text
           AND q.snapshot #>> '{lines,0,sourceType}'='LAB_TEST'
           AND q.snapshot #>> '{lines,0,priceCode}'=s.price_code
           AND t.record_id=s.record_id AND t.lab_test_ids=ARRAY[s.lab_id]
           AND t.appointment_id IS NULL AND t.prescription_id IS NULL
           AND t.admission_id IS NULL AND t.surgery_case_id IS NULL
           AND c.source_type='LAB_TEST' AND c.source_id=s.lab_id AND c.price_code=s.price_code
           AND c.account_id=a.account_id AND c.patient_id=a.patient_id AND c.department_id=a.department_id
           AND c.status='POSTED' AND rc.requested_amount=0
           AND c.quantity=(q.snapshot #>> '{lines,0,quantity}')::numeric
           AND c.unit_amount=(q.snapshot #>> '{lines,0,unitPrice}')::numeric
           AND c.gross_amount=(q.snapshot #>> '{lines,0,grossAmount}')::numeric
           AND f.payment_request_id=r.payment_request_id AND f.account_id=a.account_id
           AND f.invoice_id=r.invoice_id AND f.patient_id=a.patient_id
           AND f.purpose='LAB_TEST' AND f.care_episode_type=a.care_episode_type
           AND f.care_episode_id=a.care_episode_id AND f.record_id=s.record_id
           AND f.lab_test_ids=ARRAY[s.lab_id] AND f.appointment_id IS NULL
           AND f.prescription_id IS NULL AND f.admission_id IS NULL AND f.surgery_case_id IS NULL
           AND f.amount=0 AND f.currency='VND' AND f.payment_method='ZERO_LIABILITY'
           AND NOT f.emergency_override AND f.revoked_at IS NULL
           AND f.expires_at=r.expires_at AND f.granted_at=NEW.completed_at
           AND r.completed_at=NEW.completed_at
           AND NOT EXISTS (SELECT 1 FROM PAYMENT_TRANSACTION p WHERE p.payment_request_id=r.payment_request_id)
           AND NOT EXISTS (SELECT 1 FROM scheduled_payment_operation o WHERE o.payment_request_id=r.payment_request_id)
           AND (SELECT count(*) FROM FINANCIAL_CLEARANCE existing
                 WHERE existing.payment_request_id=r.payment_request_id)=1
           AND (SELECT count(*) FROM PAYMENT_REQUEST_CHARGE links
                 WHERE links.payment_request_id=r.payment_request_id)=1
    ) INTO valid_receipt;
    IF NOT valid_receipt THEN
        RAISE EXCEPTION 'Lab zero-payable receipt does not match current ledger evidence'
            USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER lab_zero_payable_eligibility_guard
    BEFORE INSERT ON lab_zero_payable_eligibility
    FOR EACH ROW EXECUTE FUNCTION validate_lab_zero_payable_eligibility();

CREATE FUNCTION reject_lab_zero_payable_eligibility_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Lab zero-payable eligibility receipts are immutable';
END;
$$;

CREATE TRIGGER lab_zero_payable_eligibility_immutable
    BEFORE UPDATE OR DELETE ON lab_zero_payable_eligibility
    FOR EACH ROW EXECUTE FUNCTION reject_lab_zero_payable_eligibility_mutation();
