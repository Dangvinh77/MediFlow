-- New completed outpatient refunds may opt in. No historical outbox row is released.
CREATE TABLE billing_outpatient_refund_publication (
    refund_transaction_id UUID PRIMARY KEY REFERENCES payment_transaction(transaction_id),
    payment_request_id UUID NOT NULL REFERENCES billing_outpatient_publication_cohort(payment_request_id),
    acceptance_reference VARCHAR(200) NOT NULL CHECK (length(trim(acceptance_reference)) > 0),
    approved_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE FUNCTION billing_refund_publication_new_fact_only() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM billing_event_outbox
        WHERE routing_key='payment.refunded'
          AND payload::jsonb->'payload'->>'refundTransactionId'=NEW.refund_transaction_id::text) THEN
        RAISE EXCEPTION 'Existing refund facts cannot be enrolled retroactively' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_billing_refund_publication_new_only
    BEFORE INSERT ON billing_outpatient_refund_publication
    FOR EACH ROW EXECUTE FUNCTION billing_refund_publication_new_fact_only();
CREATE TRIGGER trg_billing_refund_publication_no_update
    BEFORE UPDATE ON billing_outpatient_refund_publication
    FOR EACH ROW EXECUTE FUNCTION billing_outpatient_cohort_immutable();
CREATE TRIGGER trg_billing_refund_publication_no_delete
    BEFORE DELETE ON billing_outpatient_refund_publication
    FOR EACH ROW EXECUTE FUNCTION billing_outpatient_cohort_immutable();

-- Preserve the established predicate for the original three facts and all old migrations.
ALTER FUNCTION billing_outpatient_event_matches(UUID,UUID,UUID,TEXT,TEXT,INTEGER)
    RENAME TO billing_outpatient_core_event_matches;

CREATE FUNCTION billing_outpatient_event_matches(
    request_id UUID, event_account UUID, event_id UUID, event_type TEXT,
    event_bytes TEXT, event_version INTEGER
) RETURNS BOOLEAN LANGUAGE plpgsql STABLE AS $$
DECLARE
    envelope JSONB;
    body JSONB;
BEGIN
    IF event_type IS DISTINCT FROM 'payment.refunded' THEN
        RETURN billing_outpatient_core_event_matches(request_id,event_account,event_id,event_type,event_bytes,event_version);
    END IF;
    envelope := event_bytes::jsonb;
    body := envelope->'payload';
    IF event_version IS DISTINCT FROM 1 OR NOT COALESCE(envelope @> jsonb_build_object(
        'eventId',event_id,'eventType',event_type,'version',1,'producer','billing-service'),FALSE)
        OR jsonb_typeof(body) IS DISTINCT FROM 'object' THEN RETURN FALSE; END IF;
    RETURN EXISTS (
        SELECT 1 FROM billing_outpatient_refund_publication approval
        JOIN billing_outpatient_publication_cohort c USING(payment_request_id)
        JOIN payment_request p ON p.payment_request_id=c.payment_request_id AND p.account_id=c.account_id AND p.purpose=c.purpose
        JOIN billing_account a ON a.account_id=c.account_id
        JOIN payment_request_target t ON t.payment_request_id=p.payment_request_id
        JOIN payment_transaction refund ON refund.transaction_id=approval.refund_transaction_id
        JOIN payment_transaction original ON original.transaction_id=refund.original_transaction_id
        WHERE c.payment_request_id=request_id AND c.account_id=event_account
          AND a.care_episode_type='OUTPATIENT_VISIT'
          AND c.purpose IN ('EXAM','LAB_TEST','PRESCRIPTION')
          AND t.admission_id IS NULL AND t.surgery_case_id IS NULL
          AND ((c.purpose='EXAM' AND t.appointment_id=c.source_id AND t.record_id IS NULL
                AND cardinality(t.lab_test_ids)=0 AND t.prescription_id IS NULL)
            OR (c.purpose='LAB_TEST' AND t.lab_test_ids=ARRAY[c.source_id]
                AND t.appointment_id IS NULL AND t.prescription_id IS NULL)
            OR (c.purpose='PRESCRIPTION' AND t.prescription_id=c.source_id
                AND t.appointment_id IS NULL AND t.record_id IS NULL AND cardinality(t.lab_test_ids)=0))
          AND EXISTS (SELECT 1 FROM payment_request_charge pc JOIN charge ch USING(charge_id)
              WHERE pc.payment_request_id=p.payment_request_id AND ch.account_id=a.account_id
                AND ch.patient_id=a.patient_id AND ch.source_type=c.purpose AND ch.source_id=c.source_id)
          AND refund.payment_request_id=request_id AND original.payment_request_id=request_id
          AND refund.account_id=event_account AND original.account_id=event_account
          AND refund.transaction_type='REFUND' AND original.transaction_type='PAYMENT'
          AND refund.status='COMPLETED' AND original.status='COMPLETED'
          AND refund.classification='SERVICE_PAYMENT' AND original.classification=refund.classification
          AND refund.currency=a.currency AND original.currency=refund.currency
          AND refund.amount>0 AND refund.amount<=original.amount
          AND refund.completed_at>=original.completed_at
          AND billing_outpatient_event_instant(envelope->'occurredAt')=refund.completed_at
          AND billing_outpatient_event_instant(body->'completedAt')=refund.completed_at
          AND body @> jsonb_build_object('refundTransactionId',refund.transaction_id,
              'originalTransactionId',original.transaction_id,'accountId',event_account,
              'patientId',a.patient_id,'departmentId',a.department_id,'careEpisodeType','OUTPATIENT_VISIT',
              'careEpisodeId',a.care_episode_id,'amount',refund.amount,'currency',trim(refund.currency),
              'reason','CASHIER_RECORDED_REFUND')
    );
EXCEPTION WHEN invalid_text_representation OR invalid_datetime_format OR datetime_field_overflow THEN
    RETURN FALSE;
END;
$$;
