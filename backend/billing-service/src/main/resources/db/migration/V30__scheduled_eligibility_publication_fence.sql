-- New INSERT-only publication authority. Historical held events remain held forever.
ALTER TABLE BILLING_EVENT_OUTBOX
    ADD COLUMN scheduling_payment_operation_id UUID REFERENCES scheduled_payment_operation(payment_operation_id),
    ADD COLUMN scheduling_acceptance_reference VARCHAR(200);

ALTER TABLE BILLING_EVENT_OUTBOX DROP CONSTRAINT ck_ledger_event_delivery_hold;
ALTER TABLE BILLING_EVENT_OUTBOX ADD CONSTRAINT ck_ledger_event_delivery_hold CHECK (
    contract_version = 0 OR publication_enabled = FALSE
    OR outpatient_payment_request_id IS NOT NULL OR scheduling_payment_operation_id IS NOT NULL
);
ALTER TABLE BILLING_EVENT_OUTBOX ADD CONSTRAINT ck_scheduling_eligibility_publication CHECK (
    (scheduling_payment_operation_id IS NULL AND scheduling_acceptance_reference IS NULL)
    OR (scheduling_payment_operation_id IS NOT NULL AND contract_version = 1
        AND routing_key = 'outpatient.financial.eligibility.granted'
        AND outpatient_payment_request_id IS NULL
        AND scheduling_acceptance_reference IS NOT NULL
        AND length(trim(scheduling_acceptance_reference)) BETWEEN 1 AND 200)
);

CREATE FUNCTION billing_scheduling_outbox_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.scheduling_payment_operation_id IS DISTINCT FROM OLD.scheduling_payment_operation_id
        OR NEW.scheduling_acceptance_reference IS DISTINCT FROM OLD.scheduling_acceptance_reference THEN
        RAISE EXCEPTION 'Scheduled publication authority is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_billing_scheduling_outbox_immutable BEFORE UPDATE ON BILLING_EVENT_OUTBOX
    FOR EACH ROW EXECUTE FUNCTION billing_scheduling_outbox_immutable();

-- Deferral lets the producer insert the event before its operation is marked COMPLETED
-- in the same transaction, while refusing publication without durable exact completion proof.
CREATE FUNCTION billing_scheduling_outbox_proof() RETURNS trigger AS $$
DECLARE
    operation scheduled_payment_operation%ROWTYPE;
    fact JSONB;
BEGIN
    IF NEW.scheduling_payment_operation_id IS NULL THEN RETURN NEW; END IF;
    SELECT * INTO operation FROM scheduled_payment_operation
        WHERE payment_operation_id = NEW.scheduling_payment_operation_id;
    fact := NEW.payload::jsonb -> 'payload';
    IF operation.status IS DISTINCT FROM 'COMPLETED'
        OR NEW.aggregate_id IS DISTINCT FROM operation.registration_id
        OR (NEW.payload::jsonb ->> 'producer') IS DISTINCT FROM 'billing-service'
        OR (NEW.payload::jsonb ->> 'eventType') IS DISTINCT FROM NEW.routing_key
        OR (NEW.payload::jsonb ->> 'version') IS DISTINCT FROM '1'
        OR (fact ->> 'paymentOperationId') IS DISTINCT FROM operation.payment_operation_id::text
        OR (fact ->> 'paymentRequestId') IS DISTINCT FROM operation.payment_request_id::text
        OR (fact ->> 'registrationId') IS DISTINCT FROM operation.registration_id::text
        OR (fact ->> 'reservationId') IS DISTINCT FROM operation.reservation_id::text
        OR (fact ->> 'patientId') IS DISTINCT FROM operation.patient_id::text
        OR (fact ->> 'sourceType') IS DISTINCT FROM operation.source_type
        OR (fact ->> 'sourceId') IS DISTINCT FROM operation.source_id::text
        OR (fact ->> 'careEpisodeType') IS DISTINCT FROM 'OUTPATIENT_VISIT'
        OR (fact ->> 'careEpisodeId') IS DISTINCT FROM operation.care_episode_id::text
        OR (fact ->> 'fenceToken')::bigint IS DISTINCT FROM operation.fence_token
        OR (fact ->> 'basis') IS DISTINCT FROM operation.basis
        OR (fact ->> 'quoteId') IS DISTINCT FROM operation.quote_id::text
        OR (fact ->> 'transactionId') IS DISTINCT FROM operation.transaction_id::text
        OR (fact ->> 'clearanceId') IS DISTINCT FROM operation.clearance_id::text
        OR (fact ->> 'amount')::numeric IS DISTINCT FROM operation.amount
        OR (fact ->> 'currency') IS DISTINCT FROM operation.currency
        OR abs(extract(epoch FROM ((fact ->> 'completedAt')::timestamptz - operation.completed_at))) > 0.000001
        OR (fact ->> 'completedAt') IS NULL
    THEN
        RAISE EXCEPTION 'Scheduled eligibility event lacks exact committed operation proof'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER trg_billing_scheduling_outbox_proof AFTER INSERT ON BILLING_EVENT_OUTBOX
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION billing_scheduling_outbox_proof();
