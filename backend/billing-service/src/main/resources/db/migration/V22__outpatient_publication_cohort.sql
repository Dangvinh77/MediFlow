-- L2: outpatient publication cohort (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §4). A new
-- outpatient request opts into V1 delivery exactly once, at issue time, in the same transaction as
-- its PAYMENT_REQUEST row. Historical HELD rows and requests issued before activation never join
-- this cohort by flag toggle, retry, or admin replay — enrollment is INSERT-only, never UPDATE.

CREATE TABLE billing_outpatient_publication_cohort (
    payment_request_id UUID PRIMARY KEY REFERENCES PAYMENT_REQUEST(payment_request_id),
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    purpose VARCHAR(32) NOT NULL CHECK (purpose IN ('EXAM', 'LAB_TEST', 'PRESCRIPTION')),
    source_id UUID NOT NULL,
    acceptance_reference VARCHAR(200) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cohort rows are write-once: no application or admin path may change or remove an enrollment.
CREATE OR REPLACE FUNCTION billing_outpatient_cohort_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'billing_outpatient_publication_cohort rows are immutable; re-enrollment is not permitted'
        USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_billing_outpatient_cohort_no_update
    BEFORE UPDATE ON billing_outpatient_publication_cohort
    FOR EACH ROW EXECUTE FUNCTION billing_outpatient_cohort_immutable();

CREATE TRIGGER trg_billing_outpatient_cohort_no_delete
    BEFORE DELETE ON billing_outpatient_publication_cohort
    FOR EACH ROW EXECUTE FUNCTION billing_outpatient_cohort_immutable();

ALTER TABLE BILLING_EVENT_OUTBOX ADD COLUMN outpatient_payment_request_id UUID
    REFERENCES billing_outpatient_publication_cohort(payment_request_id);

-- V6's all-or-nothing V1 hold is replaced by a cohort-aware fence: a V1 row may publish only when
-- it carries an immutable cohort reference. V0 compatibility rows are untouched either way.
ALTER TABLE BILLING_EVENT_OUTBOX DROP CONSTRAINT ck_ledger_event_delivery_hold;
ALTER TABLE BILLING_EVENT_OUTBOX ADD CONSTRAINT ck_ledger_event_delivery_hold CHECK (
    contract_version = 0 OR publication_enabled = FALSE OR outpatient_payment_request_id IS NOT NULL
);

-- Insert-time relational fence: a cohort-referencing row must actually belong to that cohort's own
-- account and be one of the three allowed outpatient event shapes. The application writer already
-- guarantees payload fidelity (one transactional producer, HANDOFF §4); a CHECK constraint cannot
-- express a cross-table lookup, so that part needs a trigger.
CREATE OR REPLACE FUNCTION billing_outbox_outpatient_fence() RETURNS trigger AS $$
DECLARE
    cohort_account UUID;
BEGIN
    IF NEW.outpatient_payment_request_id IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT account_id INTO cohort_account FROM billing_outpatient_publication_cohort
        WHERE payment_request_id = NEW.outpatient_payment_request_id;
    IF cohort_account IS DISTINCT FROM NEW.aggregate_id THEN
        RAISE EXCEPTION 'billing_event_outbox row % does not belong to the cohort account for request %',
            NEW.event_id, NEW.outpatient_payment_request_id USING ERRCODE = '23514';
    END IF;
    IF NEW.routing_key NOT IN ('invoice.created', 'payment.completed', 'financial.clearance.granted') THEN
        RAISE EXCEPTION 'billing_event_outbox row % is not an allowed outpatient event shape: %',
            NEW.event_id, NEW.routing_key USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_billing_outbox_outpatient_fence
    BEFORE INSERT ON BILLING_EVENT_OUTBOX
    FOR EACH ROW EXECUTE FUNCTION billing_outbox_outpatient_fence();

-- Immutability fence: once inserted, the facts that decided eligibility can never be edited; only
-- the dispatcher's own delivery-progress columns (attempts/last_error/available_at/locked_*/
-- quarantined_at/published_at) may still move.
CREATE OR REPLACE FUNCTION billing_outbox_event_facts_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.event_id IS DISTINCT FROM OLD.event_id
        OR NEW.routing_key IS DISTINCT FROM OLD.routing_key
        OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
        OR NEW.payload IS DISTINCT FROM OLD.payload
        OR NEW.contract_version IS DISTINCT FROM OLD.contract_version
        OR NEW.publication_enabled IS DISTINCT FROM OLD.publication_enabled
        OR NEW.outpatient_payment_request_id IS DISTINCT FROM OLD.outpatient_payment_request_id
    THEN
        RAISE EXCEPTION 'billing_event_outbox eligibility facts are immutable after insert (row %)', OLD.event_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_billing_outbox_event_facts_immutable
    BEFORE UPDATE ON BILLING_EVENT_OUTBOX
    FOR EACH ROW EXECUTE FUNCTION billing_outbox_event_facts_immutable();
