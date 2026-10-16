-- Keep V22 and all historical bytes unchanged. Application and SQL inserts use the same
-- predicate, evaluated against Billing's own authoritative rows in the writer transaction.
-- Jackson may encode Instant as ISO text or epoch seconds; compare either to the stored instant.
CREATE FUNCTION billing_outpatient_event_instant(value JSONB) RETURNS TIMESTAMPTZ
LANGUAGE sql STABLE AS $$
    SELECT CASE jsonb_typeof(value)
        WHEN 'number' THEN to_timestamp((value #>> '{}')::double precision)
        WHEN 'string' THEN (value #>> '{}')::timestamptz
        ELSE NULL END;
$$;

CREATE FUNCTION billing_outpatient_event_matches(
    request_id UUID, event_account UUID, event_id UUID, event_type TEXT, event_bytes TEXT,
    event_version INTEGER
) RETURNS BOOLEAN LANGUAGE plpgsql STABLE AS $$
DECLARE
    envelope JSONB;
    body JSONB;
    expected JSONB;
    r RECORD;
BEGIN
    envelope := event_bytes::jsonb;
    body := envelope->'payload';
    IF event_version IS DISTINCT FROM 1 OR NOT COALESCE(envelope @> jsonb_build_object(
        'eventId', event_id, 'eventType', event_type, 'version', 1, 'producer', 'billing-service'), FALSE)
        OR jsonb_typeof(body) IS DISTINCT FROM 'object' THEN
        RETURN FALSE;
    END IF;

    SELECT c.source_id, p.*, a.patient_id, a.department_id,
           a.care_episode_type, a.care_episode_id, t.appointment_id, t.record_id,
           t.lab_test_ids, t.prescription_id, t.admission_id, t.surgery_case_id
    INTO r FROM billing_outpatient_publication_cohort c
    JOIN payment_request p ON p.payment_request_id=c.payment_request_id AND p.account_id=c.account_id AND p.purpose=c.purpose
    JOIN billing_account a ON a.account_id=c.account_id
    JOIN payment_request_target t ON t.payment_request_id=p.payment_request_id
    WHERE c.payment_request_id=request_id AND c.account_id=event_account
      AND a.care_episode_type='OUTPATIENT_VISIT'
      AND t.admission_id IS NULL AND t.surgery_case_id IS NULL
      AND ((c.purpose='EXAM' AND t.appointment_id=c.source_id AND t.record_id IS NULL
            AND cardinality(t.lab_test_ids)=0 AND t.prescription_id IS NULL)
        OR (c.purpose='LAB_TEST' AND t.lab_test_ids=ARRAY[c.source_id]
            AND t.appointment_id IS NULL AND t.prescription_id IS NULL)
        OR (c.purpose='PRESCRIPTION' AND t.prescription_id=c.source_id
            AND t.appointment_id IS NULL AND t.record_id IS NULL AND cardinality(t.lab_test_ids)=0))
      AND EXISTS (SELECT 1 FROM payment_request_charge pc JOIN charge ch USING(charge_id)
          WHERE pc.payment_request_id=p.payment_request_id AND ch.account_id=a.account_id
            AND ch.patient_id=a.patient_id AND ch.source_type=c.purpose AND ch.source_id=c.source_id);
    IF NOT FOUND THEN RETURN FALSE; END IF;

    expected := jsonb_build_object('invoiceId', r.invoice_id, 'accountId', event_account,
        'patientId', r.patient_id, 'careEpisodeType', r.care_episode_type, 'careEpisodeId', r.care_episode_id,
        'currency', trim(r.currency));
    IF NOT COALESCE(body @> expected, FALSE) THEN RETURN FALSE; END IF;

    IF event_type='invoice.created' AND r.purpose='PRESCRIPTION' THEN
        RETURN body @> jsonb_build_object('paymentRequestId', request_id, 'departmentId', r.department_id,
            'purpose', r.purpose, 'prescriptionId', r.source_id, 'totalAmount', r.requested_amount);
    ELSIF event_type='payment.completed' THEN
        RETURN EXISTS (SELECT 1 FROM payment_transaction p
            WHERE p.transaction_id::text=body->>'transactionId' AND p.payment_request_id=request_id
              AND p.account_id=event_account AND p.transaction_type='PAYMENT'
              AND p.classification='SERVICE_PAYMENT' AND p.status='COMPLETED' AND p.completed_at IS NOT NULL
              AND p.currency=r.currency AND body->'labTestIds'=to_jsonb(r.lab_test_ids)
              AND body @> jsonb_build_object('paymentRequestId', request_id, 'departmentId', r.department_id,
                  'classification', p.classification, 'totalAmount', p.amount, 'currency', trim(p.currency),
                  'paymentMethod', p.payment_method, 'prescriptionId', r.prescription_id, 'labTestIds', r.lab_test_ids));
    ELSIF event_type='financial.clearance.granted' THEN
        RETURN r.status='PAID' AND EXISTS (SELECT 1 FROM financial_clearance f
            WHERE f.clearance_id::text=body->>'clearanceId' AND f.payment_request_id=request_id
              AND f.account_id=event_account AND f.invoice_id=r.invoice_id AND f.patient_id=r.patient_id
              AND f.purpose=r.purpose AND f.care_episode_type=r.care_episode_type AND f.care_episode_id=r.care_episode_id
              AND f.appointment_id IS NOT DISTINCT FROM r.appointment_id
              AND f.record_id IS NOT DISTINCT FROM r.record_id AND f.lab_test_ids=r.lab_test_ids
              AND f.prescription_id IS NOT DISTINCT FROM r.prescription_id
              AND f.admission_id IS NULL AND f.surgery_case_id IS NULL
              AND NOT f.emergency_override AND f.revoked_at IS NULL AND f.amount=r.requested_amount
              AND f.currency=r.currency AND body->'labTestIds'=to_jsonb(r.lab_test_ids)
              AND f.expires_at IS NOT DISTINCT FROM r.expires_at
              AND billing_outpatient_event_instant(body->'expiresAt') IS NOT DISTINCT FROM f.expires_at
              AND body @> jsonb_build_object('purpose', r.purpose, 'appointmentId', r.appointment_id,
                  'recordId', r.record_id, 'labTestIds', r.lab_test_ids, 'prescriptionId', r.prescription_id,
                  'admissionId', NULL, 'surgeryCaseId', NULL, 'amount', f.amount,
                  'paymentMethod', f.payment_method, 'emergencyOverride', FALSE))
            AND (SELECT COALESCE(sum(p.amount),0) FROM payment_transaction p
                WHERE p.payment_request_id=request_id AND p.account_id=event_account
                  AND p.transaction_type='PAYMENT' AND p.classification='SERVICE_PAYMENT' AND p.status='COMPLETED')
                =r.requested_amount;
    END IF;
    RETURN FALSE;
EXCEPTION WHEN invalid_text_representation OR invalid_datetime_format OR datetime_field_overflow THEN
    RETURN FALSE;
END;
$$;

CREATE OR REPLACE FUNCTION billing_outbox_outpatient_fence() RETURNS trigger AS $$
BEGIN
    IF NEW.outpatient_payment_request_id IS NOT NULL AND NOT COALESCE(billing_outpatient_event_matches(
        NEW.outpatient_payment_request_id, NEW.aggregate_id, NEW.event_id, NEW.routing_key,
        NEW.payload, NEW.contract_version), FALSE) THEN
        RAISE EXCEPTION 'billing_event_outbox row % conflicts with outpatient request %',
            NEW.event_id, NEW.outpatient_payment_request_id USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
