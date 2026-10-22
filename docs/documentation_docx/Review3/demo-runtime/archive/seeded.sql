--
-- PostgreSQL database cluster dump
--

\restrict mdOWYRVtQRqUs6KmIMDuIqMVS8mwPGLMYxmyfEDQLq6GUyF1SIPItsMGN9q84Vv

SET default_transaction_read_only = off;

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

--
-- Roles
--

CREATE ROLE test;
ALTER ROLE test WITH SUPERUSER INHERIT CREATEROLE CREATEDB LOGIN REPLICATION BYPASSRLS;

--
-- User Configurations
--








\unrestrict mdOWYRVtQRqUs6KmIMDuIqMVS8mwPGLMYxmyfEDQLq6GUyF1SIPItsMGN9q84Vv

--
-- Databases
--

--
-- Database "template1" dump
--

\connect template1

--
-- PostgreSQL database dump
--

\restrict 2XwdlD6AwYDeFqn977gte7rpBkIPYQYmmZ0dh8AyqsJtLlJv4UbLw7jtjAAbC18

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- PostgreSQL database dump complete
--

\unrestrict 2XwdlD6AwYDeFqn977gte7rpBkIPYQYmmZ0dh8AyqsJtLlJv4UbLw7jtjAAbC18

--
-- Database "mediflow_billing" dump
--

--
-- PostgreSQL database dump
--

\restrict D7gjs5DlGaFPkXVVtj0p7Zo3xQa8WUR9fvmcGmqsOG85CugiVhLhocO7SjqVHoW

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_billing; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_billing WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_billing OWNER TO test;

\unrestrict D7gjs5DlGaFPkXVVtj0p7Zo3xQa8WUR9fvmcGmqsOG85CugiVhLhocO7SjqVHoW
\connect mediflow_billing
\restrict D7gjs5DlGaFPkXVVtj0p7Zo3xQa8WUR9fvmcGmqsOG85CugiVhLhocO7SjqVHoW

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: billing_outbox_event_facts_immutable(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_outbox_event_facts_immutable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


ALTER FUNCTION public.billing_outbox_event_facts_immutable() OWNER TO test;

--
-- Name: billing_outbox_outpatient_fence(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_outbox_outpatient_fence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.outpatient_payment_request_id IS NOT NULL AND NOT COALESCE(billing_outpatient_event_matches(
        NEW.outpatient_payment_request_id, NEW.aggregate_id, NEW.event_id, NEW.routing_key,
        NEW.payload, NEW.contract_version), FALSE) THEN
        RAISE EXCEPTION 'billing_event_outbox row % conflicts with outpatient request %',
            NEW.event_id, NEW.outpatient_payment_request_id USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.billing_outbox_outpatient_fence() OWNER TO test;

--
-- Name: billing_outpatient_cohort_immutable(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_outpatient_cohort_immutable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'billing_outpatient_publication_cohort rows are immutable; re-enrollment is not permitted'
        USING ERRCODE = '23514';
END;
$$;


ALTER FUNCTION public.billing_outpatient_cohort_immutable() OWNER TO test;

--
-- Name: billing_outpatient_core_event_matches(uuid, uuid, uuid, text, text, integer); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_outpatient_core_event_matches(request_id uuid, event_account uuid, event_id uuid, event_type text, event_bytes text, event_version integer) RETURNS boolean
    LANGUAGE plpgsql STABLE
    AS $$
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


ALTER FUNCTION public.billing_outpatient_core_event_matches(request_id uuid, event_account uuid, event_id uuid, event_type text, event_bytes text, event_version integer) OWNER TO test;

--
-- Name: billing_outpatient_event_instant(jsonb); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_outpatient_event_instant(value jsonb) RETURNS timestamp with time zone
    LANGUAGE sql STABLE
    AS $$
    SELECT CASE jsonb_typeof(value)
        WHEN 'number' THEN to_timestamp((value #>> '{}')::double precision)
        WHEN 'string' THEN (value #>> '{}')::timestamptz
        ELSE NULL END;
$$;


ALTER FUNCTION public.billing_outpatient_event_instant(value jsonb) OWNER TO test;

--
-- Name: billing_outpatient_event_matches(uuid, uuid, uuid, text, text, integer); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_outpatient_event_matches(request_id uuid, event_account uuid, event_id uuid, event_type text, event_bytes text, event_version integer) RETURNS boolean
    LANGUAGE plpgsql STABLE
    AS $$
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


ALTER FUNCTION public.billing_outpatient_event_matches(request_id uuid, event_account uuid, event_id uuid, event_type text, event_bytes text, event_version integer) OWNER TO test;

--
-- Name: billing_refund_publication_new_fact_only(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.billing_refund_publication_new_fact_only() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM billing_event_outbox
        WHERE routing_key='payment.refunded'
          AND payload::jsonb->'payload'->>'refundTransactionId'=NEW.refund_transaction_id::text) THEN
        RAISE EXCEPTION 'Existing refund facts cannot be enrolled retroactively' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.billing_refund_publication_new_fact_only() OWNER TO test;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admission_charge_close; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_charge_close (
    admission_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    summary_id uuid NOT NULL,
    approved_by uuid NOT NULL,
    approved_at_iso character varying(40) NOT NULL,
    source_fingerprint character(64) NOT NULL,
    first_event_id uuid NOT NULL,
    status character varying(24) NOT NULL,
    account_id uuid,
    applied_at timestamp with time zone,
    CONSTRAINT admission_charge_close_check CHECK (((((status)::text = 'APPLIED'::text) AND (account_id IS NOT NULL) AND (applied_at IS NOT NULL)) OR (((status)::text <> 'APPLIED'::text) AND (account_id IS NULL) AND (applied_at IS NULL)))),
    CONSTRAINT admission_charge_close_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT admission_charge_close_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING_ACCOUNT'::character varying)::text, ('APPLIED'::character varying)::text])))
);


ALTER TABLE public.admission_charge_close OWNER TO test;

--
-- Name: admission_charge_close_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_charge_close_delivery (
    event_id uuid NOT NULL,
    admission_id uuid NOT NULL,
    delivery_fingerprint character(64) NOT NULL,
    CONSTRAINT admission_charge_close_delivery_delivery_fingerprint_check CHECK ((delivery_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.admission_charge_close_delivery OWNER TO test;

--
-- Name: admission_deposit_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_deposit_delivery (
    event_id uuid NOT NULL,
    admission_id uuid NOT NULL,
    payload_fingerprint character(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT admission_deposit_delivery_payload_fingerprint_check CHECK ((payload_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.admission_deposit_delivery OWNER TO test;

--
-- Name: admission_deposit_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_deposit_source (
    admission_id uuid NOT NULL,
    source_fingerprint character(64) NOT NULL,
    payment_request_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    CONSTRAINT admission_deposit_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.admission_deposit_source OWNER TO test;

--
-- Name: admission_reconciliation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_reconciliation (
    settlement_id uuid NOT NULL,
    account_id uuid NOT NULL,
    settlement_version integer NOT NULL,
    supersedes_settlement_id uuid,
    actor_id uuid NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    reconciled_at timestamp with time zone NOT NULL,
    payload text NOT NULL,
    CONSTRAINT admission_reconciliation_settlement_version_check CHECK ((settlement_version > 0)),
    CONSTRAINT ck_admission_reconciliation_predecessor CHECK ((((settlement_version = 1) AND (supersedes_settlement_id IS NULL)) OR ((settlement_version > 1) AND (supersedes_settlement_id IS NOT NULL))))
);


ALTER TABLE public.admission_reconciliation OWNER TO test;

--
-- Name: billing_account; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.billing_account (
    account_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    status character varying(32) DEFAULT 'OPEN'::character varying NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    opened_at timestamp with time zone NOT NULL,
    charge_closed_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_billing_account_episode CHECK (((care_episode_type)::text = ANY (ARRAY[('OUTPATIENT_VISIT'::character varying)::text, ('ADMISSION'::character varying)::text]))),
    CONSTRAINT ck_billing_account_status CHECK (((status)::text = ANY (ARRAY[('OPEN'::character varying)::text, ('CHARGE_CLOSED'::character varying)::text, ('SETTLEMENT_PENDING'::character varying)::text, ('SETTLED'::character varying)::text, ('CLOSED'::character varying)::text])))
);


ALTER TABLE public.billing_account OWNER TO test;

--
-- Name: billing_event_outbox; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.billing_event_outbox (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    aggregate_id uuid NOT NULL,
    payload text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    last_error character varying(500),
    available_at timestamp with time zone DEFAULT now() NOT NULL,
    locked_at timestamp with time zone,
    locked_by character varying(100),
    quarantined_at timestamp with time zone,
    publication_enabled boolean DEFAULT true NOT NULL,
    contract_version integer DEFAULT 0 NOT NULL,
    outpatient_payment_request_id uuid,
    CONSTRAINT billing_event_outbox_contract_version_check CHECK ((contract_version >= 0)),
    CONSTRAINT ck_ledger_event_delivery_hold CHECK (((contract_version = 0) OR (publication_enabled = false) OR (outpatient_payment_request_id IS NOT NULL)))
);


ALTER TABLE public.billing_event_outbox OWNER TO test;

--
-- Name: billing_outpatient_publication_cohort; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.billing_outpatient_publication_cohort (
    payment_request_id uuid NOT NULL,
    account_id uuid NOT NULL,
    purpose character varying(32) NOT NULL,
    source_id uuid NOT NULL,
    acceptance_reference character varying(200) NOT NULL,
    enrolled_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT billing_outpatient_publication_cohort_purpose_check CHECK (((purpose)::text = ANY (ARRAY[('EXAM'::character varying)::text, ('LAB_TEST'::character varying)::text, ('PRESCRIPTION'::character varying)::text])))
);


ALTER TABLE public.billing_outpatient_publication_cohort OWNER TO test;

--
-- Name: billing_outpatient_refund_publication; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.billing_outpatient_refund_publication (
    refund_transaction_id uuid NOT NULL,
    payment_request_id uuid NOT NULL,
    acceptance_reference character varying(200) NOT NULL,
    approved_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT billing_outpatient_refund_publicatio_acceptance_reference_check CHECK ((length(TRIM(BOTH FROM acceptance_reference)) > 0))
);


ALTER TABLE public.billing_outpatient_refund_publication OWNER TO test;

--
-- Name: charge; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.charge (
    charge_id uuid NOT NULL,
    account_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    source_type character varying(32) NOT NULL,
    source_id uuid NOT NULL,
    price_code character varying(64) NOT NULL,
    description character varying(255) NOT NULL,
    quantity numeric(19,4) DEFAULT 1 NOT NULL,
    unit_amount numeric(19,2) NOT NULL,
    gross_amount numeric(19,2) NOT NULL,
    status character varying(20) DEFAULT 'POSTED'::character varying NOT NULL,
    void_reason character varying(500),
    incurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    reconciled_result_id uuid,
    CONSTRAINT charge_gross_amount_check CHECK ((gross_amount >= (0)::numeric)),
    CONSTRAINT charge_quantity_check CHECK ((quantity > (0)::numeric)),
    CONSTRAINT charge_unit_amount_check CHECK ((unit_amount >= (0)::numeric)),
    CONSTRAINT ck_charge_status CHECK (((status)::text = ANY (ARRAY[('POSTED'::character varying)::text, ('VOIDED'::character varying)::text])))
);


ALTER TABLE public.charge OWNER TO test;

--
-- Name: deposit_allocation_operation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.deposit_allocation_operation (
    allocation_operation_id uuid NOT NULL,
    account_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    intent_hash character(64) NOT NULL,
    allocated_at timestamp with time zone NOT NULL,
    payload text NOT NULL,
    CONSTRAINT deposit_allocation_operation_intent_hash_check CHECK ((intent_hash ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.deposit_allocation_operation OWNER TO test;

--
-- Name: exam_charge_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.exam_charge_delivery (
    event_id uuid NOT NULL,
    appointment_id uuid NOT NULL,
    payload_fingerprint character(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT exam_charge_delivery_payload_fingerprint_check CHECK ((payload_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.exam_charge_delivery OWNER TO test;

--
-- Name: exam_charge_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.exam_charge_source (
    appointment_id uuid NOT NULL,
    source_fingerprint character(64) NOT NULL,
    payment_request_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    patient_id uuid,
    department_id uuid,
    care_episode_type character varying(32),
    care_episode_id uuid,
    price_code character varying(64),
    requested_at_iso character varying(40),
    CONSTRAINT exam_charge_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.exam_charge_source OWNER TO test;

--
-- Name: fee; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.fee (
    fee_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    record_id uuid,
    department_id uuid NOT NULL,
    source_ref_id uuid,
    fee_type character varying(10) NOT NULL,
    incurred_date date NOT NULL,
    amount numeric(15,2) NOT NULL,
    is_paid boolean DEFAULT false NOT NULL,
    invoice_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_fee_amount_non_negative CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.fee OWNER TO test;

--
-- Name: final_admission_settlement_operation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.final_admission_settlement_operation (
    recognition_operation_id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    account_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    payload text NOT NULL
);


ALTER TABLE public.final_admission_settlement_operation OWNER TO test;

--
-- Name: financial_clearance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_clearance (
    clearance_id uuid NOT NULL,
    account_id uuid NOT NULL,
    payment_request_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    purpose character varying(32) NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    appointment_id uuid,
    record_id uuid,
    lab_test_ids uuid[] DEFAULT '{}'::uuid[] NOT NULL,
    prescription_id uuid,
    admission_id uuid,
    surgery_case_id uuid,
    amount numeric(19,2) NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    payment_method character varying(20) NOT NULL,
    emergency_override boolean DEFAULT false NOT NULL,
    expires_at timestamp with time zone,
    granted_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT ck_clearance_purpose CHECK (((purpose)::text = ANY (ARRAY[('EXAM'::character varying)::text, ('LAB_TEST'::character varying)::text, ('PRESCRIPTION'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text, ('SURGERY'::character varying)::text]))),
    CONSTRAINT ck_clearance_target CHECK (((((purpose)::text = 'EXAM'::text) AND ((appointment_id IS NOT NULL) OR (record_id IS NOT NULL))) OR (((purpose)::text = 'LAB_TEST'::text) AND (cardinality(lab_test_ids) > 0)) OR (((purpose)::text = 'PRESCRIPTION'::text) AND (prescription_id IS NOT NULL)) OR (((purpose)::text = 'ADMISSION_DEPOSIT'::text) AND (admission_id IS NOT NULL)) OR (((purpose)::text = 'SURGERY'::text) AND (surgery_case_id IS NOT NULL)))),
    CONSTRAINT financial_clearance_amount_check CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.financial_clearance OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: insurance_adjustment; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.insurance_adjustment (
    adjustment_id uuid NOT NULL,
    account_id uuid NOT NULL,
    admission_id uuid NOT NULL,
    decision_reference character varying(120) NOT NULL,
    adjustment_type character varying(20) NOT NULL,
    original_adjustment_id uuid,
    amount numeric(19,2) NOT NULL,
    reason character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_insurance_adjustment_type CHECK (((adjustment_type)::text = ANY (ARRAY[('APPROVAL'::character varying)::text, ('REVERSAL'::character varying)::text]))),
    CONSTRAINT ck_insurance_reversal_original CHECK ((((adjustment_type)::text = 'APPROVAL'::text) OR (original_adjustment_id IS NOT NULL))),
    CONSTRAINT insurance_adjustment_amount_check CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.insurance_adjustment OWNER TO test;

--
-- Name: invoice; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.invoice (
    invoice_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    created_date date NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    is_paid boolean DEFAULT false NOT NULL,
    payment_method character varying(20),
    prescription_id uuid,
    saga_status character varying(20) DEFAULT 'NONE'::character varying NOT NULL,
    paid_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_invoice_total_non_negative CHECK ((total_amount >= (0)::numeric))
);


ALTER TABLE public.invoice OWNER TO test;

--
-- Name: lab_test_charge_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_test_charge_delivery (
    event_id uuid NOT NULL,
    lab_id uuid NOT NULL,
    payload_fingerprint character(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT lab_test_charge_delivery_payload_fingerprint_check CHECK ((payload_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.lab_test_charge_delivery OWNER TO test;

--
-- Name: lab_test_charge_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_test_charge_source (
    lab_id uuid NOT NULL,
    source_order_id uuid NOT NULL,
    source_fingerprint character(64) NOT NULL,
    payment_request_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    patient_id uuid,
    department_id uuid,
    record_id uuid,
    care_episode_type character varying(32),
    care_episode_id uuid,
    price_code character varying(64),
    lab_type character varying(64),
    requested_at_iso character varying(40),
    CONSTRAINT lab_test_charge_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.lab_test_charge_source OWNER TO test;

--
-- Name: lab_test_type; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_test_type (
    lab_id uuid NOT NULL,
    lab_type character varying(100) NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.lab_test_type OWNER TO test;

--
-- Name: ledger_refund_evidence; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.ledger_refund_evidence (
    refund_transaction_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    reason character varying(500) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ledger_refund_evidence_reason_check CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


ALTER TABLE public.ledger_refund_evidence OWNER TO test;

--
-- Name: outpatient_cancellation_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.outpatient_cancellation_delivery (
    event_id uuid NOT NULL,
    source_type character varying(16) NOT NULL,
    source_id uuid NOT NULL,
    delivery_fingerprint character(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT outpatient_cancellation_delivery_delivery_fingerprint_check CHECK ((delivery_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.outpatient_cancellation_delivery OWNER TO test;

--
-- Name: outpatient_cancellation_refund_due; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.outpatient_cancellation_refund_due (
    source_type character varying(16) NOT NULL,
    source_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    charge_id uuid NOT NULL,
    account_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character(3) NOT NULL,
    CONSTRAINT outpatient_cancellation_refund_due_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.outpatient_cancellation_refund_due OWNER TO test;

--
-- Name: outpatient_cancellation_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.outpatient_cancellation_source (
    source_type character varying(16) NOT NULL,
    source_id uuid NOT NULL,
    event_type character varying(40) NOT NULL,
    source_fingerprint character(64) NOT NULL,
    first_event_id uuid NOT NULL,
    correlation_id character varying(120) NOT NULL,
    source_order_id uuid,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    record_id uuid,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    price_code character varying(64),
    lab_type character varying(64),
    cancelled_by uuid NOT NULL,
    reason character varying(1000) NOT NULL,
    cancelled_at_iso character varying(40) NOT NULL,
    status character varying(12) DEFAULT 'PENDING'::character varying NOT NULL,
    rejection_code character varying(80),
    applied_at timestamp with time zone,
    CONSTRAINT outpatient_cancellation_source_check CHECK (((((source_type)::text = 'LAB_TEST'::text) AND ((event_type)::text = 'lab.request.cancelled'::text) AND (source_order_id IS NOT NULL) AND (lab_type IS NOT NULL) AND (price_code IS NOT NULL)) OR (((source_type)::text = 'EXAM'::text) AND ((event_type)::text = 'appointment.exam.cancelled'::text) AND (source_order_id IS NULL) AND (record_id IS NULL) AND (lab_type IS NULL) AND (price_code IS NULL)))),
    CONSTRAINT outpatient_cancellation_source_check1 CHECK ((((status)::text = 'REJECTED'::text) = (rejection_code IS NOT NULL))),
    CONSTRAINT outpatient_cancellation_source_check2 CHECK ((((status)::text = 'APPLIED'::text) = (applied_at IS NOT NULL))),
    CONSTRAINT outpatient_cancellation_source_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('appointment.exam.cancelled'::character varying)::text, ('lab.request.cancelled'::character varying)::text]))),
    CONSTRAINT outpatient_cancellation_source_reason_check CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT outpatient_cancellation_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT outpatient_cancellation_source_source_type_check CHECK (((source_type)::text = ANY (ARRAY[('EXAM'::character varying)::text, ('LAB_TEST'::character varying)::text]))),
    CONSTRAINT outpatient_cancellation_source_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.outpatient_cancellation_source OWNER TO test;

--
-- Name: outpatient_execution_start; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.outpatient_execution_start (
    operation_id uuid NOT NULL,
    clearance_id uuid NOT NULL,
    account_id uuid NOT NULL,
    source_type character varying(16) NOT NULL,
    source_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    state character varying(12) DEFAULT 'STARTED'::character varying NOT NULL,
    started_at timestamp with time zone NOT NULL,
    CONSTRAINT outpatient_execution_start_source_type_check CHECK (((source_type)::text = ANY (ARRAY[('EXAM'::character varying)::text, ('LAB_TEST'::character varying)::text]))),
    CONSTRAINT outpatient_execution_start_state_check CHECK (((state)::text = 'STARTED'::text))
);


ALTER TABLE public.outpatient_execution_start OWNER TO test;

--
-- Name: payment_allocation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_allocation (
    allocation_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    charge_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT payment_allocation_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.payment_allocation OWNER TO test;

--
-- Name: payment_request; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_request (
    payment_request_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    account_id uuid NOT NULL,
    purpose character varying(32) NOT NULL,
    status character varying(24) DEFAULT 'PENDING'::character varying NOT NULL,
    requested_amount numeric(19,2) NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    expires_at timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT ck_payment_request_purpose CHECK (((purpose)::text = ANY (ARRAY[('EXAM'::character varying)::text, ('LAB_TEST'::character varying)::text, ('PRESCRIPTION'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text, ('SURGERY'::character varying)::text, ('SETTLEMENT'::character varying)::text]))),
    CONSTRAINT ck_payment_request_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PARTIALLY_PAID'::character varying)::text, ('PAID'::character varying)::text, ('EXPIRED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT payment_request_requested_amount_check CHECK ((requested_amount >= (0)::numeric))
);


ALTER TABLE public.payment_request OWNER TO test;

--
-- Name: payment_request_charge; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_request_charge (
    payment_request_id uuid NOT NULL,
    charge_id uuid NOT NULL,
    requested_amount numeric(19,2) NOT NULL,
    CONSTRAINT payment_request_charge_requested_amount_check CHECK ((requested_amount >= (0)::numeric))
);


ALTER TABLE public.payment_request_charge OWNER TO test;

--
-- Name: payment_request_target; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_request_target (
    payment_request_id uuid NOT NULL,
    appointment_id uuid,
    record_id uuid,
    lab_test_ids uuid[] DEFAULT '{}'::uuid[] NOT NULL,
    prescription_id uuid,
    admission_id uuid,
    surgery_case_id uuid
);


ALTER TABLE public.payment_request_target OWNER TO test;

--
-- Name: payment_transaction; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_transaction (
    transaction_id uuid NOT NULL,
    account_id uuid NOT NULL,
    payment_request_id uuid,
    transaction_type character varying(20) NOT NULL,
    classification character varying(24) NOT NULL,
    status character varying(20) NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    payment_method character varying(20) NOT NULL,
    provider_reference character varying(120),
    idempotency_key character varying(120) NOT NULL,
    original_transaction_id uuid,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_account_id uuid,
    CONSTRAINT ck_payment_classification CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text, ('SETTLEMENT_PAYMENT'::character varying)::text]))),
    CONSTRAINT ck_payment_transaction_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT ck_payment_transaction_type CHECK (((transaction_type)::text = ANY (ARRAY[('PAYMENT'::character varying)::text, ('REFUND'::character varying)::text, ('REVERSAL'::character varying)::text]))),
    CONSTRAINT ck_refund_original CHECK ((((transaction_type)::text = 'PAYMENT'::text) OR (original_transaction_id IS NOT NULL))),
    CONSTRAINT payment_transaction_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.payment_transaction OWNER TO test;

--
-- Name: prescription_charge_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_charge_delivery (
    event_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    payload_fingerprint character varying(64) NOT NULL,
    CONSTRAINT prescription_charge_delivery_payload_fingerprint_check CHECK (((payload_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.prescription_charge_delivery OWNER TO test;

--
-- Name: prescription_charge_item; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_charge_item (
    prescription_id uuid NOT NULL,
    drug_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(19,2) NOT NULL,
    CONSTRAINT prescription_charge_item_quantity_check CHECK ((quantity > 0)),
    CONSTRAINT prescription_charge_item_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


ALTER TABLE public.prescription_charge_item OWNER TO test;

--
-- Name: prescription_charge_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_charge_source (
    prescription_id uuid NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    account_id uuid NOT NULL,
    charge_id uuid NOT NULL,
    payment_request_id uuid,
    invoice_id uuid,
    care_context character varying(20) NOT NULL,
    record_id uuid,
    created_at_iso character varying(40) NOT NULL,
    snapshot_fingerprint character varying(64),
    CONSTRAINT ck_prescription_charge_request CHECK (((((care_context)::text = 'OUTPATIENT'::text) AND (payment_request_id IS NOT NULL) AND (invoice_id IS NOT NULL)) OR (((care_context)::text = 'ADMISSION'::text) AND (payment_request_id IS NULL) AND (invoice_id IS NULL)))),
    CONSTRAINT prescription_charge_source_care_context_check CHECK (((care_context)::text = ANY (ARRAY[('OUTPATIENT'::character varying)::text, ('ADMISSION'::character varying)::text]))),
    CONSTRAINT prescription_charge_source_snapshot_fingerprint_check CHECK (((snapshot_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT prescription_charge_source_source_fingerprint_check CHECK (((source_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.prescription_charge_source OWNER TO test;

--
-- Name: prescription_terminal_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_terminal_delivery (
    event_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    delivery_fingerprint character varying(64) NOT NULL,
    CONSTRAINT prescription_terminal_delivery_delivery_fingerprint_check CHECK (((delivery_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.prescription_terminal_delivery OWNER TO test;

--
-- Name: prescription_terminal_refund_due; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_terminal_refund_due (
    prescription_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    charge_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    CONSTRAINT prescription_terminal_refund_due_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.prescription_terminal_refund_due OWNER TO test;

--
-- Name: prescription_terminal_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_terminal_source (
    prescription_id uuid NOT NULL,
    event_type character varying(48) NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    snapshot_fingerprint character varying(64) NOT NULL,
    terminal_at_iso character varying(40) NOT NULL,
    status character varying(12) DEFAULT 'PENDING'::character varying NOT NULL,
    rejection_code character varying(80),
    applied_at timestamp with time zone,
    CONSTRAINT prescription_terminal_source_check CHECK ((((status)::text = 'REJECTED'::text) = (rejection_code IS NOT NULL))),
    CONSTRAINT prescription_terminal_source_check1 CHECK ((((status)::text = 'APPLIED'::text) = (applied_at IS NOT NULL))),
    CONSTRAINT prescription_terminal_source_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('prescription.filled'::character varying)::text, ('prescription.cancelled'::character varying)::text, ('prescription.expired'::character varying)::text, ('prescription.dispense.failed'::character varying)::text]))),
    CONSTRAINT prescription_terminal_source_snapshot_fingerprint_check CHECK (((snapshot_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT prescription_terminal_source_source_fingerprint_check CHECK (((source_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT prescription_terminal_source_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.prescription_terminal_source OWNER TO test;

--
-- Name: processed_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.processed_event OWNER TO test;

--
-- Name: settlement; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.settlement (
    settlement_id uuid NOT NULL,
    account_id uuid NOT NULL,
    admission_id uuid NOT NULL,
    settlement_version integer NOT NULL,
    supersedes_settlement_id uuid,
    gross_amount numeric(19,2) NOT NULL,
    insurance_amount numeric(19,2) DEFAULT 0 NOT NULL,
    patient_liability numeric(19,2) NOT NULL,
    completed_payments numeric(19,2) NOT NULL,
    completed_refunds numeric(19,2) NOT NULL,
    balance numeric(19,2) NOT NULL,
    outcome character varying(40) NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_settlement_outcome CHECK (((outcome)::text = ANY (ARRAY[('PAID_IN_FULL'::character varying)::text, ('ADDITIONAL_PAYMENT_REQUIRED'::character varying)::text, ('REFUND_DUE'::character varying)::text, ('DEBT_APPROVED'::character varying)::text, ('WAIVED'::character varying)::text])))
);


ALTER TABLE public.settlement OWNER TO test;

--
-- Name: surgery_cancellation_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_cancellation_delivery (
    event_id uuid NOT NULL,
    surgery_case_id uuid NOT NULL,
    delivery_fingerprint character(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT surgery_cancellation_delivery_delivery_fingerprint_check CHECK ((delivery_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.surgery_cancellation_delivery OWNER TO test;

--
-- Name: surgery_cancellation_refund_due; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_cancellation_refund_due (
    cancellation_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    charge_id uuid NOT NULL,
    account_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character(3) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT surgery_cancellation_refund_due_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.surgery_cancellation_refund_due OWNER TO test;

--
-- Name: surgery_cancellation_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_cancellation_source (
    surgery_case_id uuid NOT NULL,
    cancellation_id uuid NOT NULL,
    surgery_request_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    source_fingerprint character(64) NOT NULL,
    delivery_fingerprint character(64) NOT NULL,
    correlation_id character varying(120) NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    admission_id uuid,
    record_id uuid,
    case_revision bigint NOT NULL,
    cancellation_stage character varying(24) NOT NULL,
    cancelled_by uuid NOT NULL,
    cancelled_by_staff_id uuid,
    cancelled_at_iso character varying(40) NOT NULL,
    status character varying(12) DEFAULT 'PENDING'::character varying NOT NULL,
    rejection_code character varying(80),
    retry_at timestamp with time zone DEFAULT now() NOT NULL,
    applied_at timestamp with time zone,
    CONSTRAINT surgery_cancellation_source_cancellation_stage_check CHECK (((cancellation_stage)::text = ANY (ARRAY[('BEFORE_PREOP'::character varying)::text, ('AFTER_PREOP'::character varying)::text, ('BEFORE_START'::character varying)::text]))),
    CONSTRAINT surgery_cancellation_source_care_episode_type_check CHECK (((care_episode_type)::text = ANY (ARRAY[('ADMISSION'::character varying)::text, ('OUTPATIENT_VISIT'::character varying)::text]))),
    CONSTRAINT surgery_cancellation_source_case_revision_check CHECK ((case_revision > 0)),
    CONSTRAINT surgery_cancellation_source_check CHECK (((((care_episode_type)::text = 'ADMISSION'::text) AND (admission_id IS NOT NULL) AND (admission_id = care_episode_id)) OR (((care_episode_type)::text = 'OUTPATIENT_VISIT'::text) AND (admission_id IS NULL) AND (record_id IS NOT NULL)))),
    CONSTRAINT surgery_cancellation_source_check1 CHECK ((((status)::text = 'REJECTED'::text) = (rejection_code IS NOT NULL))),
    CONSTRAINT surgery_cancellation_source_check2 CHECK ((((status)::text = 'APPLIED'::text) = (applied_at IS NOT NULL))),
    CONSTRAINT surgery_cancellation_source_correlation_id_check CHECK ((length(btrim((correlation_id)::text)) > 0)),
    CONSTRAINT surgery_cancellation_source_delivery_fingerprint_check CHECK ((delivery_fingerprint ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT surgery_cancellation_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT surgery_cancellation_source_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.surgery_cancellation_source OWNER TO test;

--
-- Name: surgery_charge_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_charge_delivery (
    event_id uuid NOT NULL,
    surgery_case_id uuid NOT NULL,
    payload_fingerprint character(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT surgery_charge_delivery_payload_fingerprint_check CHECK ((payload_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.surgery_charge_delivery OWNER TO test;

--
-- Name: surgery_charge_item; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_charge_item (
    surgery_case_id uuid NOT NULL,
    item_code character varying(64) NOT NULL,
    price_code character varying(64) NOT NULL,
    quantity numeric(19,4) NOT NULL,
    charge_id uuid NOT NULL,
    CONSTRAINT surgery_charge_item_quantity_check CHECK ((quantity > (0)::numeric))
);


ALTER TABLE public.surgery_charge_item OWNER TO test;

--
-- Name: surgery_charge_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_charge_source (
    surgery_case_id uuid NOT NULL,
    surgery_request_id uuid NOT NULL,
    source_fingerprint character(64) NOT NULL,
    payment_request_id uuid NOT NULL,
    record_id uuid,
    requested_at timestamp with time zone NOT NULL,
    requested_at_iso character varying(40),
    CONSTRAINT surgery_charge_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.surgery_charge_source OWNER TO test;

--
-- Name: surgery_completion_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_completion_delivery (
    event_id uuid NOT NULL,
    surgery_case_id uuid NOT NULL,
    delivery_fingerprint character(64) NOT NULL,
    CONSTRAINT surgery_completion_delivery_delivery_fingerprint_check CHECK ((delivery_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.surgery_completion_delivery OWNER TO test;

--
-- Name: surgery_completion_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_completion_source (
    surgery_case_id uuid NOT NULL,
    surgery_request_id uuid NOT NULL,
    account_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    care_episode_type character varying(24) NOT NULL,
    care_episode_id uuid NOT NULL,
    result_id uuid NOT NULL,
    case_revision integer NOT NULL,
    source_revision integer NOT NULL,
    completed_at_iso character varying(40) NOT NULL,
    recorded_at_iso character varying(40) NOT NULL,
    source_fingerprint character(64) NOT NULL,
    CONSTRAINT surgery_completion_source_care_episode_type_check CHECK (((care_episode_type)::text = ANY (ARRAY[('ADMISSION'::character varying)::text, ('OUTPATIENT_VISIT'::character varying)::text]))),
    CONSTRAINT surgery_completion_source_case_revision_check CHECK ((case_revision > 0)),
    CONSTRAINT surgery_completion_source_source_fingerprint_check CHECK ((source_fingerprint ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT surgery_completion_source_source_revision_check CHECK ((source_revision = 1))
);


ALTER TABLE public.surgery_completion_source OWNER TO test;

--
-- Data for Name: admission_charge_close; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_charge_close (admission_id, patient_id, summary_id, approved_by, approved_at_iso, source_fingerprint, first_event_id, status, account_id, applied_at) FROM stdin;
\.


--
-- Data for Name: admission_charge_close_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_charge_close_delivery (event_id, admission_id, delivery_fingerprint) FROM stdin;
\.


--
-- Data for Name: admission_deposit_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_deposit_delivery (event_id, admission_id, payload_fingerprint, received_at) FROM stdin;
\.


--
-- Data for Name: admission_deposit_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_deposit_source (admission_id, source_fingerprint, payment_request_id, requested_at) FROM stdin;
\.


--
-- Data for Name: admission_reconciliation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_reconciliation (settlement_id, account_id, settlement_version, supersedes_settlement_id, actor_id, idempotency_key, reconciled_at, payload) FROM stdin;
\.


--
-- Data for Name: billing_account; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.billing_account (account_id, patient_id, department_id, care_episode_type, care_episode_id, status, currency, version, opened_at, charge_closed_at, closed_at, created_at, updated_at) FROM stdin;
2b0753ea-f892-4484-81c6-644b08832cc2	c36277c4-347f-480d-8473-bcf9f98c8a44	b134eebd-d327-4718-8bef-92525164178d	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	OPEN	VND	1	2026-10-22 00:40:15.984936+00	\N	\N	2026-10-22 00:40:16.340778+00	2026-10-22 00:40:16.340778+00
e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	OPEN	VND	3	2026-10-22 00:40:38.443302+00	\N	\N	2026-10-22 00:40:38.521027+00	2026-10-22 00:40:38.521027+00
d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	OPEN	VND	3	2026-10-22 00:44:55.899375+00	\N	\N	2026-10-22 00:44:56.008084+00	2026-10-22 00:44:56.008084+00
8f3e4603-9e61-481a-8cb5-3b485b4c8560	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	OPEN	VND	2	2026-10-22 00:45:37.87203+00	\N	\N	2026-10-22 00:45:37.888384+00	2026-10-22 00:45:37.888384+00
da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	OPEN	VND	1	2026-10-22 00:45:46.741006+00	\N	\N	2026-10-22 00:45:46.77355+00	2026-10-22 00:45:46.77355+00
86874f73-ccd3-492e-ad59-d064e2520f62	1270368c-7e70-40eb-852c-b58d7b052cf7	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	a4fde265-3562-4528-8ea6-0db0e835c1d6	OPEN	VND	1	2026-10-22 00:45:49.368883+00	\N	\N	2026-10-22 00:45:52.227013+00	2026-10-22 00:45:52.227013+00
a806fda3-0b26-4bc1-96fd-85f6a74ca294	3ad6444d-54b2-467a-9e25-beb664d3975f	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	b264777a-2a76-4793-b4ac-91e6a8779f0d	OPEN	VND	1	2026-10-22 00:50:46.53268+00	\N	\N	2026-10-22 00:50:46.657987+00	2026-10-22 00:50:46.657987+00
4baa16fa-2af8-43b0-9b19-1f32c7f275ac	83ee7cd5-ccb4-4005-96f3-42abb0261400	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	OPEN	VND	1	2026-10-22 00:45:54.593407+00	\N	\N	2026-10-22 00:45:54.810977+00	2026-10-22 00:45:54.810977+00
5c325e27-b74d-49d7-b8ef-2c763630967b	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	b9de10f8-44e8-4e44-8637-09135b5691e8	OPEN	VND	1	2026-10-22 10:49:28.989701+00	\N	\N	2026-10-22 10:49:29.360719+00	2026-10-22 10:49:29.360719+00
6941b792-a948-4acb-b52f-6cdcb26b5956	a2c54b05-3010-4b8d-93ee-67fb06469d77	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	OPEN	VND	1	2026-10-22 11:37:33.301207+00	\N	\N	2026-10-22 11:37:33.793302+00	2026-10-22 11:37:33.793302+00
0e6d5b39-82f4-4631-8851-60a1191ed085	b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	OPEN	VND	0	2026-10-22 11:56:40.509471+00	\N	\N	2026-10-22 11:56:40.771836+00	2026-10-22 11:56:40.771836+00
\.


--
-- Data for Name: billing_event_outbox; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.billing_event_outbox (event_id, routing_key, aggregate_id, payload, created_at, published_at, attempts, last_error, available_at, locked_at, locked_by, quarantined_at, publication_enabled, contract_version, outpatient_payment_request_id) FROM stdin;
3db56074-a1a2-40a2-a984-a0ab45e1d443	invoice.created	2b0753ea-f892-4484-81c6-644b08832cc2	{"eventId":"3db56074-a1a2-40a2-a984-a0ab45e1d443","eventType":"invoice.created","version":1,"occurredAt":"2026-10-22T00:40:21.464990100Z","correlationId":"0acb8270-c779-4db9-befb-ff2a4fd77b6e","producer":"billing-service","payload":{"invoiceId":"eb118cfb-934a-47ff-aed1-38fff5ef32a9","paymentRequestId":"cfab1271-de78-43bd-8b3d-d4425096c5db","accountId":"2b0753ea-f892-4484-81c6-644b08832cc2","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44","departmentId":"b134eebd-d327-4718-8bef-92525164178d","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"11935f4f-6428-4051-ba9e-8e8a1b7d9f11","purpose":"PRESCRIPTION","prescriptionId":"583fb28a-4e20-4e2a-9bc4-9ec9acd2363b","totalAmount":30000.00,"currency":"VND","createdAt":"2026-10-22T00:40:21.464990100Z","expiresAt":null}}	2026-10-22 00:40:21.308482+00	2026-10-22 00:40:21.909109+00	0	\N	2026-10-22 00:40:21.308482+00	\N	\N	\N	t	1	cfab1271-de78-43bd-8b3d-d4425096c5db
99a7d7b4-2942-4dfb-b648-a37002ffe6a7	financial.clearance.granted	2b0753ea-f892-4484-81c6-644b08832cc2	{"eventId":"99a7d7b4-2942-4dfb-b648-a37002ffe6a7","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:40:16.917045600Z","correlationId":"449d52da-28b5-44d1-a513-99db59cfe7d2","producer":"billing-service","payload":{"clearanceId":"86de69c5-4b2e-4458-83e0-6677efc159aa","invoiceId":"48bdd0f9-a9c4-45bc-9987-40df9770e1b0","accountId":"2b0753ea-f892-4484-81c6-644b08832cc2","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"11935f4f-6428-4051-ba9e-8e8a1b7d9f11","purpose":"EXAM","appointmentId":"11935f4f-6428-4051-ba9e-8e8a1b7d9f11","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:40:16.905072+00	2026-10-22 00:40:17.426054+00	0	\N	2026-10-22 00:40:16.905072+00	\N	\N	\N	t	1	5e891e9f-3f19-447f-87dd-a4d1dbff1f54
f5f81d95-ffb2-476c-a67a-d5eaff2beaf2	payment.completed	2b0753ea-f892-4484-81c6-644b08832cc2	{"eventId":"f5f81d95-ffb2-476c-a67a-d5eaff2beaf2","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:40:16.917045600Z","correlationId":"449d52da-28b5-44d1-a513-99db59cfe7d2","producer":"billing-service","payload":{"transactionId":"c4830cfc-8783-4fe7-93a6-834d90b31eba","invoiceId":"48bdd0f9-a9c4-45bc-9987-40df9770e1b0","paymentRequestId":"5e891e9f-3f19-447f-87dd-a4d1dbff1f54","accountId":"2b0753ea-f892-4484-81c6-644b08832cc2","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44","departmentId":"b134eebd-d327-4718-8bef-92525164178d","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"11935f4f-6428-4051-ba9e-8e8a1b7d9f11","classification":"SERVICE_PAYMENT","totalAmount":100000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:40:16.917045Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:40:16.905072+00	2026-10-22 00:40:18.575719+00	0	\N	2026-10-22 00:40:16.905072+00	\N	\N	\N	t	1	5e891e9f-3f19-447f-87dd-a4d1dbff1f54
a7a1957a-cad4-479f-8f77-d2106b1945a0	payment.completed	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"a7a1957a-cad4-479f-8f77-d2106b1945a0","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:40:39.521338800Z","correlationId":"2aa427c6-a5f4-4024-ac36-8beed130d070","producer":"billing-service","payload":{"transactionId":"2ba1e00e-ccb0-47ea-b27e-e81042f78ce2","invoiceId":"92244ff4-319a-4ccc-8bcd-cb76aa714e08","paymentRequestId":"615649a6-dcfb-4c4e-87cf-44a747595daa","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","classification":"SERVICE_PAYMENT","totalAmount":100000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:40:39.521338Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:40:39.513554+00	2026-10-22 00:40:41.308968+00	0	\N	2026-10-22 00:40:39.513554+00	\N	\N	\N	t	1	615649a6-dcfb-4c4e-87cf-44a747595daa
57bdeb4a-1402-44d3-8e9a-fe70eec15d8d	financial.clearance.granted	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"57bdeb4a-1402-44d3-8e9a-fe70eec15d8d","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:40:39.521338800Z","correlationId":"2aa427c6-a5f4-4024-ac36-8beed130d070","producer":"billing-service","payload":{"clearanceId":"49f69633-3e1f-4ef3-ab1e-c7b344ab174a","invoiceId":"92244ff4-319a-4ccc-8bcd-cb76aa714e08","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","purpose":"EXAM","appointmentId":"71626749-ef62-42f4-9d40-35097f05dfb9","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:40:39.513554+00	2026-10-22 00:40:40.255054+00	0	\N	2026-10-22 00:40:39.513554+00	\N	\N	\N	t	1	615649a6-dcfb-4c4e-87cf-44a747595daa
1330fe0c-c39d-435f-91b6-f044567dc77f	payment.completed	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"1330fe0c-c39d-435f-91b6-f044567dc77f","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:40:44.583111400Z","correlationId":"dc45a22e-0e75-46ba-a2a0-6afdcb17233d","producer":"billing-service","payload":{"transactionId":"cc0c7042-4358-4039-81dc-19d81ae7b14a","invoiceId":"cb97113f-6b14-469f-8439-ce19ae2aa207","paymentRequestId":"cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","classification":"SERVICE_PAYMENT","totalAmount":150000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:40:44.583111Z","prescriptionId":null,"labTestIds":["4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088"]}}	2026-10-22 00:40:44.572895+00	2026-10-22 00:40:45.394637+00	0	\N	2026-10-22 00:40:44.572895+00	\N	\N	\N	t	1	cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7
f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2	payment.completed	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:44:56.990696400Z","correlationId":"14fe00ba-b80c-4093-9fa2-0e8f2bddece9","producer":"billing-service","payload":{"transactionId":"e1cffe9a-9228-45bf-9f63-511d4c6f550c","invoiceId":"1f14c914-9c13-4e48-9003-dd73422859fa","paymentRequestId":"c626fe0e-332f-4ec6-820c-462757683d5c","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","classification":"SERVICE_PAYMENT","totalAmount":100000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:44:56.990696Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:44:56.984815+00	2026-10-22 00:44:58.947261+00	0	\N	2026-10-22 00:44:56.984815+00	\N	\N	\N	t	1	c626fe0e-332f-4ec6-820c-462757683d5c
db0ade44-9af5-4b37-9105-3ec7930ad00b	financial.clearance.granted	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"db0ade44-9af5-4b37-9105-3ec7930ad00b","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:40:44.583111400Z","correlationId":"dc45a22e-0e75-46ba-a2a0-6afdcb17233d","producer":"billing-service","payload":{"clearanceId":"f774ec12-0ace-4926-b47a-1a97274cc814","invoiceId":"cb97113f-6b14-469f-8439-ce19ae2aa207","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","purpose":"LAB_TEST","appointmentId":null,"recordId":"4447ad07-fe64-4d41-8c96-7a278d167be2","labTestIds":["4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088"],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":150000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:40:44.572895+00	2026-10-22 00:40:46.445478+00	0	\N	2026-10-22 00:40:44.572895+00	\N	\N	\N	t	1	cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7
ec5b3a7c-269d-42d5-a3b7-fc28391aa064	financial.clearance.granted	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"ec5b3a7c-269d-42d5-a3b7-fc28391aa064","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:44:56.990696400Z","correlationId":"14fe00ba-b80c-4093-9fa2-0e8f2bddece9","producer":"billing-service","payload":{"clearanceId":"b9ec057b-c3a9-45b5-9e95-36109f530bc1","invoiceId":"1f14c914-9c13-4e48-9003-dd73422859fa","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","purpose":"EXAM","appointmentId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:44:56.984815+00	2026-10-22 00:44:57.90783+00	0	\N	2026-10-22 00:44:56.984815+00	\N	\N	\N	t	1	c626fe0e-332f-4ec6-820c-462757683d5c
9099c95d-829c-4360-910f-f52198801584	payment.completed	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"9099c95d-829c-4360-910f-f52198801584","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:02.489562400Z","correlationId":"c05c7165-e220-4977-b1fa-dacc80846f1d","producer":"billing-service","payload":{"transactionId":"cf4b6b6a-d59d-444c-92fd-f4905bf85509","invoiceId":"4541152d-f447-4a88-ba0d-b63ee9244d34","paymentRequestId":"17047f8c-0ddf-443f-8e19-b056667afbe5","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","classification":"SERVICE_PAYMENT","totalAmount":150000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:02.489562Z","prescriptionId":null,"labTestIds":["a92c9118-e58f-4afb-b666-75030fe10600"]}}	2026-10-22 00:45:02.485025+00	2026-10-22 00:45:04.058323+00	0	\N	2026-10-22 00:45:02.485025+00	\N	\N	\N	t	1	17047f8c-0ddf-443f-8e19-b056667afbe5
7db9d05c-3d1c-40ae-84c5-6d427809bf76	financial.clearance.granted	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"7db9d05c-3d1c-40ae-84c5-6d427809bf76","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:02.489562400Z","correlationId":"c05c7165-e220-4977-b1fa-dacc80846f1d","producer":"billing-service","payload":{"clearanceId":"b9dc3502-c045-47d3-973d-07f7c296e12d","invoiceId":"4541152d-f447-4a88-ba0d-b63ee9244d34","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","purpose":"LAB_TEST","appointmentId":null,"recordId":"1361a316-8aa3-4ad4-8774-f852d8c64fa1","labTestIds":["a92c9118-e58f-4afb-b666-75030fe10600"],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":150000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:02.485025+00	2026-10-22 00:45:03.01567+00	0	\N	2026-10-22 00:45:02.485025+00	\N	\N	\N	t	1	17047f8c-0ddf-443f-8e19-b056667afbe5
6bf6d013-9e64-412c-ba9c-7a565179f4db	invoice.created	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"6bf6d013-9e64-412c-ba9c-7a565179f4db","eventType":"invoice.created","version":1,"occurredAt":"2026-10-22T00:45:31.288281Z","correlationId":"9ee4d903-7954-4826-b1df-a47d706b0e22","producer":"billing-service","payload":{"invoiceId":"eb37200c-eb91-4c71-b45e-732a9ba773c8","paymentRequestId":"0cff9bd3-381b-4b73-970f-b66e657e4435","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","purpose":"PRESCRIPTION","prescriptionId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","totalAmount":9000.00,"currency":"VND","createdAt":"2026-10-22T00:45:31.288281Z","expiresAt":null}}	2026-10-22 00:45:31.283091+00	2026-10-22 00:45:31.396295+00	0	\N	2026-10-22 00:45:31.283091+00	\N	\N	\N	t	1	0cff9bd3-381b-4b73-970f-b66e657e4435
355f326c-1b48-451e-bda2-779db121bc00	payment.completed	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"355f326c-1b48-451e-bda2-779db121bc00","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:32.201665800Z","correlationId":"f83fee75-9b71-43f9-887c-f6722a6762ab","producer":"billing-service","payload":{"transactionId":"55a1ae72-572d-4daa-a23a-0176b0834aa0","invoiceId":"eb37200c-eb91-4c71-b45e-732a9ba773c8","paymentRequestId":"0cff9bd3-381b-4b73-970f-b66e657e4435","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","classification":"SERVICE_PAYMENT","totalAmount":9000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:32.201665Z","prescriptionId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","labTestIds":[]}}	2026-10-22 00:45:32.196472+00	2026-10-22 00:45:32.42898+00	0	\N	2026-10-22 00:45:32.196472+00	\N	\N	\N	t	1	0cff9bd3-381b-4b73-970f-b66e657e4435
5370b35c-27ba-4584-a002-f7a43727cae2	invoice.created	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"5370b35c-27ba-4584-a002-f7a43727cae2","eventType":"invoice.created","version":1,"occurredAt":"2026-10-22T00:45:35.510580100Z","correlationId":"d25b9aee-1f1a-4609-a518-0a202019b6e8","producer":"billing-service","payload":{"invoiceId":"88e40197-ac94-4a80-be9b-f94934eea7ff","paymentRequestId":"1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","purpose":"PRESCRIPTION","prescriptionId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","totalAmount":9000.00,"currency":"VND","createdAt":"2026-10-22T00:45:35.510580100Z","expiresAt":null}}	2026-10-22 00:45:35.504701+00	2026-10-22 00:45:36.512689+00	0	\N	2026-10-22 00:45:35.504701+00	\N	\N	\N	t	1	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6
74190a4b-a6b9-419f-8a27-c5882393cf55	financial.clearance.granted	e4463013-b866-45d6-aeeb-20d5e6c07197	{"eventId":"74190a4b-a6b9-419f-8a27-c5882393cf55","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:32.201665800Z","correlationId":"f83fee75-9b71-43f9-887c-f6722a6762ab","producer":"billing-service","payload":{"clearanceId":"595165d5-e8c4-41e1-9203-1cd72c053d30","invoiceId":"eb37200c-eb91-4c71-b45e-732a9ba773c8","accountId":"e4463013-b866-45d6-aeeb-20d5e6c07197","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","purpose":"PRESCRIPTION","appointmentId":null,"recordId":null,"labTestIds":[],"prescriptionId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","admissionId":null,"surgeryCaseId":null,"amount":9000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:32.196472+00	2026-10-22 00:45:33.451908+00	0	\N	2026-10-22 00:45:32.196472+00	\N	\N	\N	t	1	0cff9bd3-381b-4b73-970f-b66e657e4435
c3d7ad2a-36de-46bd-ba16-bcfba18bc7be	payment.completed	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"c3d7ad2a-36de-46bd-ba16-bcfba18bc7be","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:35.739515500Z","correlationId":"8faca011-8c10-4937-995f-9f2d2cc59a6b","producer":"billing-service","payload":{"transactionId":"7649919b-93b2-4aea-906c-6e69d5cc3afb","invoiceId":"88e40197-ac94-4a80-be9b-f94934eea7ff","paymentRequestId":"1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","classification":"SERVICE_PAYMENT","totalAmount":9000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:35.739515Z","prescriptionId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","labTestIds":[]}}	2026-10-22 00:45:35.734652+00	2026-10-22 00:45:38.599405+00	0	\N	2026-10-22 00:45:35.734652+00	\N	\N	\N	t	1	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6
9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b	payment.completed	8f3e4603-9e61-481a-8cb5-3b485b4c8560	{"eventId":"9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:38.928545Z","correlationId":"7691df7e-aca1-443c-b10b-7e3c35533aab","producer":"billing-service","payload":{"transactionId":"e55fe586-5ddb-48d7-9338-1836225c52c5","invoiceId":"2659a221-7b81-4a18-be74-2c0a873edad7","paymentRequestId":"ad6bd3f9-53dd-49ef-9750-27bd01a6b24a","accountId":"8f3e4603-9e61-481a-8cb5-3b485b4c8560","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"037fcbd9-127b-44d6-96ee-73c571763c5e","classification":"SERVICE_PAYMENT","totalAmount":100000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:38.928545Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:45:38.923668+00	2026-10-22 00:45:40.745083+00	0	\N	2026-10-22 00:45:38.923668+00	\N	\N	\N	t	1	ad6bd3f9-53dd-49ef-9750-27bd01a6b24a
736eea19-daa1-489e-8eda-499a93f4165b	financial.clearance.granted	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	{"eventId":"736eea19-daa1-489e-8eda-499a93f4165b","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:35.739515500Z","correlationId":"8faca011-8c10-4937-995f-9f2d2cc59a6b","producer":"billing-service","payload":{"clearanceId":"3dc5587a-41d2-4b78-8ea6-cd1fc05e9294","invoiceId":"88e40197-ac94-4a80-be9b-f94934eea7ff","accountId":"d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","purpose":"PRESCRIPTION","appointmentId":null,"recordId":null,"labTestIds":[],"prescriptionId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","admissionId":null,"surgeryCaseId":null,"amount":9000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:35.734652+00	2026-10-22 00:45:37.554583+00	0	\N	2026-10-22 00:45:35.734652+00	\N	\N	\N	t	1	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6
9c03fa80-daba-4a73-b1af-9dedd6e60eb2	financial.clearance.granted	8f3e4603-9e61-481a-8cb5-3b485b4c8560	{"eventId":"9c03fa80-daba-4a73-b1af-9dedd6e60eb2","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:38.928545Z","correlationId":"7691df7e-aca1-443c-b10b-7e3c35533aab","producer":"billing-service","payload":{"clearanceId":"db1a5449-6c73-4772-8791-54039cb8111e","invoiceId":"2659a221-7b81-4a18-be74-2c0a873edad7","accountId":"8f3e4603-9e61-481a-8cb5-3b485b4c8560","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"037fcbd9-127b-44d6-96ee-73c571763c5e","purpose":"EXAM","appointmentId":"037fcbd9-127b-44d6-96ee-73c571763c5e","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:38.923668+00	2026-10-22 00:45:39.663688+00	0	\N	2026-10-22 00:45:38.923668+00	\N	\N	\N	t	1	ad6bd3f9-53dd-49ef-9750-27bd01a6b24a
12e1525e-15f7-426f-ba92-1aa941eab204	financial.clearance.granted	8f3e4603-9e61-481a-8cb5-3b485b4c8560	{"eventId":"12e1525e-15f7-426f-ba92-1aa941eab204","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:44.434798300Z","correlationId":"fdd5f599-4fb5-4de7-b89e-b4ee806afbaa","producer":"billing-service","payload":{"clearanceId":"432e20d2-1ca3-49ed-8aed-a17948dca7aa","invoiceId":"e4c1681c-a2ac-40a9-9a63-e871388725fe","accountId":"8f3e4603-9e61-481a-8cb5-3b485b4c8560","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"037fcbd9-127b-44d6-96ee-73c571763c5e","purpose":"LAB_TEST","appointmentId":null,"recordId":"acc4a82c-6856-469d-80be-8dc343a9e06e","labTestIds":["bbbe2b41-5061-4381-ab46-472b63180bad"],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":150000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:44.429168+00	2026-10-22 00:45:45.876677+00	0	\N	2026-10-22 00:45:44.429168+00	\N	\N	\N	t	1	4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc
0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f	payment.completed	8f3e4603-9e61-481a-8cb5-3b485b4c8560	{"eventId":"0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:44.434798300Z","correlationId":"fdd5f599-4fb5-4de7-b89e-b4ee806afbaa","producer":"billing-service","payload":{"transactionId":"b52f3117-223d-4432-ac82-4e7f06ccee62","invoiceId":"e4c1681c-a2ac-40a9-9a63-e871388725fe","paymentRequestId":"4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc","accountId":"8f3e4603-9e61-481a-8cb5-3b485b4c8560","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"037fcbd9-127b-44d6-96ee-73c571763c5e","classification":"SERVICE_PAYMENT","totalAmount":150000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:44.434798Z","prescriptionId":null,"labTestIds":["bbbe2b41-5061-4381-ab46-472b63180bad"]}}	2026-10-22 00:45:44.429168+00	2026-10-22 00:45:44.834378+00	0	\N	2026-10-22 00:45:44.429168+00	\N	\N	\N	t	1	4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc
49d2333c-73de-4226-a373-98b92a58b110	invoice.created	8f3e4603-9e61-481a-8cb5-3b485b4c8560	{"eventId":"49d2333c-73de-4226-a373-98b92a58b110","eventType":"invoice.created","version":1,"occurredAt":"2026-10-22T00:45:46.712576700Z","correlationId":"12989ae3-0597-4983-a637-b6c0c4c8198b","producer":"billing-service","payload":{"invoiceId":"4a69d756-b855-4c6f-9eae-a4560ce74904","paymentRequestId":"c80ea7fa-076a-4a07-98bc-8fe4b08982c9","accountId":"8f3e4603-9e61-481a-8cb5-3b485b4c8560","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"037fcbd9-127b-44d6-96ee-73c571763c5e","purpose":"PRESCRIPTION","prescriptionId":"0a3f84b7-2905-405b-a0f8-c68afd9ca87a","totalAmount":9000.00,"currency":"VND","createdAt":"2026-10-22T00:45:46.712576700Z","expiresAt":null}}	2026-10-22 00:45:46.703085+00	2026-10-22 00:45:46.910477+00	0	\N	2026-10-22 00:45:46.703085+00	\N	\N	\N	t	1	c80ea7fa-076a-4a07-98bc-8fe4b08982c9
9c3e6353-a2b1-4d52-be28-7689b60257d2	financial.clearance.granted	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	{"eventId":"9c3e6353-a2b1-4d52-be28-7689b60257d2","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:47.836519200Z","correlationId":"d1e37e94-c5c3-4a71-844a-79bac0093ac1","producer":"billing-service","payload":{"clearanceId":"33e1d0e6-f85f-450b-9813-41a6e5b56cf9","invoiceId":"70cfd0e8-1d1d-41a3-93c2-b7e0d9eccbb4","accountId":"da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"250352fd-6ae9-4108-a147-2d76e9b49eb9","purpose":"EXAM","appointmentId":"250352fd-6ae9-4108-a147-2d76e9b49eb9","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:47.833124+00	2026-10-22 00:45:47.976918+00	0	\N	2026-10-22 00:45:47.833124+00	\N	\N	\N	t	1	cc950a37-4205-444f-a620-352b617b09fe
fa53bcf9-3b37-4ead-9583-7ed10ad93cbe	payment.completed	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	{"eventId":"fa53bcf9-3b37-4ead-9583-7ed10ad93cbe","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:47.836519200Z","correlationId":"d1e37e94-c5c3-4a71-844a-79bac0093ac1","producer":"billing-service","payload":{"transactionId":"add16c27-3994-4ef8-a7a6-80c3a01f5090","invoiceId":"70cfd0e8-1d1d-41a3-93c2-b7e0d9eccbb4","paymentRequestId":"cc950a37-4205-444f-a620-352b617b09fe","accountId":"da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"250352fd-6ae9-4108-a147-2d76e9b49eb9","classification":"SERVICE_PAYMENT","totalAmount":100000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:47.836519Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:45:47.833124+00	2026-10-22 00:45:49.024976+00	0	\N	2026-10-22 00:45:47.833124+00	\N	\N	\N	t	1	cc950a37-4205-444f-a620-352b617b09fe
e3bc9094-e906-4e9f-852e-0b2e1c4c8eaf	invoice.created	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	{"eventId":"e3bc9094-e906-4e9f-852e-0b2e1c4c8eaf","eventType":"invoice.created","version":1,"occurredAt":"2026-10-22T00:45:52.242505800Z","correlationId":"775e7710-bcab-4da5-a3fb-1fb0e41627ea","producer":"billing-service","payload":{"invoiceId":"48e6e851-6b2c-4bee-ba91-fe906ad737aa","paymentRequestId":"cba26ee1-8c27-4fc1-9b74-1083946a1db0","accountId":"da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"250352fd-6ae9-4108-a147-2d76e9b49eb9","purpose":"PRESCRIPTION","prescriptionId":"7b8b6d90-897d-4439-8f89-4f6905e825ae","totalAmount":9000.00,"currency":"VND","createdAt":"2026-10-22T00:45:52.242505800Z","expiresAt":null}}	2026-10-22 00:45:52.239028+00	2026-10-22 00:45:53.098037+00	0	\N	2026-10-22 00:45:52.239028+00	\N	\N	\N	t	1	cba26ee1-8c27-4fc1-9b74-1083946a1db0
33f4d6d9-99f8-407c-b685-4f95b4a0ac95	payment.completed	86874f73-ccd3-492e-ad59-d064e2520f62	{"eventId":"33f4d6d9-99f8-407c-b685-4f95b4a0ac95","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:45:52.492536300Z","correlationId":"a7692903-7ec0-4663-9424-36140d210740","producer":"billing-service","payload":{"transactionId":"bced71a5-0b2f-45d2-87d2-9b41bf72d8b3","invoiceId":"6a11c00e-afd8-4eb9-9a6f-6c26ffa86d92","paymentRequestId":"e62ca364-6afa-4a41-a7cb-e1654821950d","accountId":"86874f73-ccd3-492e-ad59-d064e2520f62","patientId":"1270368c-7e70-40eb-852c-b58d7b052cf7","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"a4fde265-3562-4528-8ea6-0db0e835c1d6","classification":"SERVICE_PAYMENT","totalAmount":100000.0,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:45:52.492536Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:45:52.488902+00	2026-10-22 00:45:53.124258+00	0	\N	2026-10-22 00:45:52.488902+00	\N	\N	\N	t	1	e62ca364-6afa-4a41-a7cb-e1654821950d
68d00a96-48cf-4e4d-a88f-b71c0d9a5010	financial.clearance.granted	a806fda3-0b26-4bc1-96fd-85f6a74ca294	{"eventId":"68d00a96-48cf-4e4d-a88f-b71c0d9a5010","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:52:51.000334900Z","correlationId":"7322e252-9e00-4a38-983e-775341b5ceef","producer":"billing-service","payload":{"clearanceId":"76283443-9e57-4e50-b63c-ede3cac9664d","invoiceId":"fbbb7a7c-7227-4118-a21c-66f30fb3ad72","accountId":"a806fda3-0b26-4bc1-96fd-85f6a74ca294","patientId":"3ad6444d-54b2-467a-9e25-beb664d3975f","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"b264777a-2a76-4793-b4ac-91e6a8779f0d","purpose":"EXAM","appointmentId":"b264777a-2a76-4793-b4ac-91e6a8779f0d","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:52:50.995058+00	2026-10-22 00:52:52.744679+00	0	\N	2026-10-22 00:52:50.995058+00	\N	\N	\N	t	1	1d51b9bf-3af2-4263-8b04-b868a82bffa9
8aa0aec0-16ae-400e-880b-36342e617c0e	financial.clearance.granted	86874f73-ccd3-492e-ad59-d064e2520f62	{"eventId":"8aa0aec0-16ae-400e-880b-36342e617c0e","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:45:52.492536300Z","correlationId":"a7692903-7ec0-4663-9424-36140d210740","producer":"billing-service","payload":{"clearanceId":"fa6622b0-d820-4c4e-9bf6-26671394453b","invoiceId":"6a11c00e-afd8-4eb9-9a6f-6c26ffa86d92","accountId":"86874f73-ccd3-492e-ad59-d064e2520f62","patientId":"1270368c-7e70-40eb-852c-b58d7b052cf7","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"a4fde265-3562-4528-8ea6-0db0e835c1d6","purpose":"EXAM","appointmentId":"a4fde265-3562-4528-8ea6-0db0e835c1d6","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:45:52.488902+00	2026-10-22 00:45:54.147056+00	0	\N	2026-10-22 00:45:52.488902+00	\N	\N	\N	t	1	e62ca364-6afa-4a41-a7cb-e1654821950d
608193a2-1f8e-493f-a59b-a7d83eb512e7	payment.completed	a806fda3-0b26-4bc1-96fd-85f6a74ca294	{"eventId":"608193a2-1f8e-493f-a59b-a7d83eb512e7","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:52:51.000334900Z","correlationId":"7322e252-9e00-4a38-983e-775341b5ceef","producer":"billing-service","payload":{"transactionId":"a33cb5ed-d048-4908-a7ec-2ae3f8416d77","invoiceId":"fbbb7a7c-7227-4118-a21c-66f30fb3ad72","paymentRequestId":"1d51b9bf-3af2-4263-8b04-b868a82bffa9","accountId":"a806fda3-0b26-4bc1-96fd-85f6a74ca294","patientId":"3ad6444d-54b2-467a-9e25-beb664d3975f","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"b264777a-2a76-4793-b4ac-91e6a8779f0d","classification":"SERVICE_PAYMENT","totalAmount":100000,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:52:51.000334Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:52:50.995058+00	2026-10-22 00:52:51.712809+00	0	\N	2026-10-22 00:52:50.995058+00	\N	\N	\N	t	1	1d51b9bf-3af2-4263-8b04-b868a82bffa9
b136373c-a447-4817-a839-e28c940fd939	payment.completed	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	{"eventId":"b136373c-a447-4817-a839-e28c940fd939","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T00:56:55.535559400Z","correlationId":"b8a54376-397e-4166-b1e5-f3a454d5aa79","producer":"billing-service","payload":{"transactionId":"65284733-97d8-4108-9d85-6fa449d75310","invoiceId":"663b13cc-9758-4615-a7a1-d401ddbe6e21","paymentRequestId":"ea2b7648-09a0-4906-9ee4-ca1224c29368","accountId":"4baa16fa-2af8-43b0-9b19-1f32c7f275ac","patientId":"83ee7cd5-ccb4-4005-96f3-42abb0261400","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"74fcae55-0ff2-4f0c-ae64-ea847dc3a66b","classification":"SERVICE_PAYMENT","totalAmount":100000,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T00:56:55.535559Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 00:56:55.529997+00	2026-10-22 00:56:56.953847+00	0	\N	2026-10-22 00:56:55.529997+00	\N	\N	\N	t	1	ea2b7648-09a0-4906-9ee4-ca1224c29368
20a4d671-939e-43a8-b642-62a283362106	financial.clearance.granted	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	{"eventId":"20a4d671-939e-43a8-b642-62a283362106","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T00:56:55.535559400Z","correlationId":"b8a54376-397e-4166-b1e5-f3a454d5aa79","producer":"billing-service","payload":{"clearanceId":"0c7cc504-61b9-4f1d-ac04-fda127f2cb86","invoiceId":"663b13cc-9758-4615-a7a1-d401ddbe6e21","accountId":"4baa16fa-2af8-43b0-9b19-1f32c7f275ac","patientId":"83ee7cd5-ccb4-4005-96f3-42abb0261400","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"74fcae55-0ff2-4f0c-ae64-ea847dc3a66b","purpose":"EXAM","appointmentId":"74fcae55-0ff2-4f0c-ae64-ea847dc3a66b","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 00:56:55.529997+00	2026-10-22 00:56:55.919664+00	0	\N	2026-10-22 00:56:55.529997+00	\N	\N	\N	t	1	ea2b7648-09a0-4906-9ee4-ca1224c29368
0e7ccf12-b2f0-439e-b271-6232e8d66f39	financial.clearance.granted	6941b792-a948-4acb-b52f-6cdcb26b5956	{"eventId":"0e7ccf12-b2f0-439e-b271-6232e8d66f39","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T11:41:34.165631700Z","correlationId":"8d5ed7da-380e-4c96-bae9-a4db20ca8081","producer":"billing-service","payload":{"clearanceId":"fe8a7fbe-f625-4e22-a6be-a2f5f997cdde","invoiceId":"a5d788e6-3ae0-4ed2-bb3d-0383e921a628","accountId":"6941b792-a948-4acb-b52f-6cdcb26b5956","patientId":"a2c54b05-3010-4b8d-93ee-67fb06469d77","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"f91a4cad-6e7a-4b23-9f3f-08187f1687ec","purpose":"EXAM","appointmentId":"f91a4cad-6e7a-4b23-9f3f-08187f1687ec","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 11:41:34.158021+00	2026-10-22 11:41:34.689696+00	0	\N	2026-10-22 11:41:34.158021+00	\N	\N	\N	t	1	8f673d22-4cef-4eb7-8a29-134fdc8ba990
ad4889ed-ca90-4c64-8368-204493565785	financial.clearance.granted	5c325e27-b74d-49d7-b8ef-2c763630967b	{"eventId":"ad4889ed-ca90-4c64-8368-204493565785","eventType":"financial.clearance.granted","version":1,"occurredAt":"2026-10-22T10:53:22.794782300Z","correlationId":"017ff56c-21ff-4ab0-8841-68ace230595c","producer":"billing-service","payload":{"clearanceId":"80f4ed5d-2194-4056-be84-bd056d4e1599","invoiceId":"8e705b7f-804d-48d8-a0f4-10a8a4df1e9d","accountId":"5c325e27-b74d-49d7-b8ef-2c763630967b","patientId":"fc4fe9c4-a34c-4252-bd0b-3e171604d3b5","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"b9de10f8-44e8-4e44-8637-09135b5691e8","purpose":"EXAM","appointmentId":"b9de10f8-44e8-4e44-8637-09135b5691e8","recordId":null,"labTestIds":[],"prescriptionId":null,"admissionId":null,"surgeryCaseId":null,"amount":100000.00,"currency":"VND","paymentMethod":"CASH","expiresAt":null,"emergencyOverride":false}}	2026-10-22 10:53:22.765133+00	2026-10-22 10:53:23.656872+00	0	\N	2026-10-22 10:53:22.765133+00	\N	\N	\N	t	1	33467dbe-11a2-4c7a-8742-fa27f9fb2641
dee0992f-cbc0-4af3-aeca-3d1f806333b3	payment.completed	5c325e27-b74d-49d7-b8ef-2c763630967b	{"eventId":"dee0992f-cbc0-4af3-aeca-3d1f806333b3","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T10:53:22.794782300Z","correlationId":"017ff56c-21ff-4ab0-8841-68ace230595c","producer":"billing-service","payload":{"transactionId":"c3802b07-5f4f-47e8-afa2-0d08b2d9fe2e","invoiceId":"8e705b7f-804d-48d8-a0f4-10a8a4df1e9d","paymentRequestId":"33467dbe-11a2-4c7a-8742-fa27f9fb2641","accountId":"5c325e27-b74d-49d7-b8ef-2c763630967b","patientId":"fc4fe9c4-a34c-4252-bd0b-3e171604d3b5","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"b9de10f8-44e8-4e44-8637-09135b5691e8","classification":"SERVICE_PAYMENT","totalAmount":100000,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T10:53:22.794782Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 10:53:22.765133+00	2026-10-22 10:53:24.761614+00	0	\N	2026-10-22 10:53:22.765133+00	\N	\N	\N	t	1	33467dbe-11a2-4c7a-8742-fa27f9fb2641
b2531d42-b612-4cb9-a5f0-0781e3d5c106	payment.completed	6941b792-a948-4acb-b52f-6cdcb26b5956	{"eventId":"b2531d42-b612-4cb9-a5f0-0781e3d5c106","eventType":"payment.completed","version":1,"occurredAt":"2026-10-22T11:41:34.165631700Z","correlationId":"8d5ed7da-380e-4c96-bae9-a4db20ca8081","producer":"billing-service","payload":{"transactionId":"2dc1ba9c-e65f-4396-be2e-f94e5d0f4a16","invoiceId":"a5d788e6-3ae0-4ed2-bb3d-0383e921a628","paymentRequestId":"8f673d22-4cef-4eb7-8a29-134fdc8ba990","accountId":"6941b792-a948-4acb-b52f-6cdcb26b5956","patientId":"a2c54b05-3010-4b8d-93ee-67fb06469d77","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"f91a4cad-6e7a-4b23-9f3f-08187f1687ec","classification":"SERVICE_PAYMENT","totalAmount":100000,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-22T11:41:34.165631Z","prescriptionId":null,"labTestIds":[]}}	2026-10-22 11:41:34.158021+00	2026-10-22 11:41:35.785334+00	0	\N	2026-10-22 11:41:34.158021+00	\N	\N	\N	t	1	8f673d22-4cef-4eb7-8a29-134fdc8ba990
\.


--
-- Data for Name: billing_outpatient_publication_cohort; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.billing_outpatient_publication_cohort (payment_request_id, account_id, purpose, source_id, acceptance_reference, enrolled_at) FROM stdin;
5e891e9f-3f19-447f-87dd-a4d1dbff1f54	2b0753ea-f892-4484-81c6-644b08832cc2	EXAM	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	V-CLOSE-03-isolated-EXAM	2026-10-22 00:40:16.340778+00
cfab1271-de78-43bd-8b3d-d4425096c5db	2b0753ea-f892-4484-81c6-644b08832cc2	PRESCRIPTION	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	V-CLOSE-03-isolated-EXAM	2026-10-22 00:40:21.308482+00
615649a6-dcfb-4c4e-87cf-44a747595daa	e4463013-b866-45d6-aeeb-20d5e6c07197	EXAM	71626749-ef62-42f4-9d40-35097f05dfb9	V-CLOSE-03-isolated-EXAM	2026-10-22 00:40:38.521027+00
cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	e4463013-b866-45d6-aeeb-20d5e6c07197	LAB_TEST	4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	V-CLOSE-03-isolated-EXAM	2026-10-22 00:40:43.998292+00
c626fe0e-332f-4ec6-820c-462757683d5c	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	EXAM	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	V-CLOSE-03-isolated-EXAM	2026-10-22 00:44:56.008084+00
17047f8c-0ddf-443f-8e19-b056667afbe5	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	LAB_TEST	a92c9118-e58f-4afb-b666-75030fe10600	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:01.462161+00
0cff9bd3-381b-4b73-970f-b66e657e4435	e4463013-b866-45d6-aeeb-20d5e6c07197	PRESCRIPTION	84116e5c-b8d2-4afa-a586-0c2af1c350ab	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:31.283091+00
1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	PRESCRIPTION	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:35.504701+00
ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	8f3e4603-9e61-481a-8cb5-3b485b4c8560	EXAM	037fcbd9-127b-44d6-96ee-73c571763c5e	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:37.888384+00
4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	8f3e4603-9e61-481a-8cb5-3b485b4c8560	LAB_TEST	bbbe2b41-5061-4381-ab46-472b63180bad	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:43.362269+00
c80ea7fa-076a-4a07-98bc-8fe4b08982c9	8f3e4603-9e61-481a-8cb5-3b485b4c8560	PRESCRIPTION	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:46.703085+00
cc950a37-4205-444f-a620-352b617b09fe	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	EXAM	250352fd-6ae9-4108-a147-2d76e9b49eb9	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:46.77355+00
e62ca364-6afa-4a41-a7cb-e1654821950d	86874f73-ccd3-492e-ad59-d064e2520f62	EXAM	a4fde265-3562-4528-8ea6-0db0e835c1d6	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:52.227013+00
cba26ee1-8c27-4fc1-9b74-1083946a1db0	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	PRESCRIPTION	7b8b6d90-897d-4439-8f89-4f6905e825ae	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:52.239028+00
e9af22b5-ef29-41f9-bb49-8eca6f3bd792	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	LAB_TEST	ad116d89-b9f6-4cef-a3de-028d541b00ef	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:52.255675+00
ea2b7648-09a0-4906-9ee4-ca1224c29368	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	EXAM	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	V-CLOSE-03-isolated-EXAM	2026-10-22 00:45:54.810977+00
c9614526-fddd-41ef-ab85-4e8be1204adb	e4463013-b866-45d6-aeeb-20d5e6c07197	LAB_TEST	cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932	V-CLOSE-03-isolated-EXAM	2026-10-22 00:49:16.592778+00
1d51b9bf-3af2-4263-8b04-b868a82bffa9	a806fda3-0b26-4bc1-96fd-85f6a74ca294	EXAM	b264777a-2a76-4793-b4ac-91e6a8779f0d	V-CLOSE-03-isolated-EXAM	2026-10-22 00:50:46.657987+00
33467dbe-11a2-4c7a-8742-fa27f9fb2641	5c325e27-b74d-49d7-b8ef-2c763630967b	EXAM	b9de10f8-44e8-4e44-8637-09135b5691e8	V-CLOSE-03-isolated-EXAM	2026-10-22 10:49:29.360719+00
9432ae58-7a17-4420-b0f4-18ed6849e40e	e4463013-b866-45d6-aeeb-20d5e6c07197	LAB_TEST	31142159-4304-44d2-9e21-acae9bd325b3	V-CLOSE-03-isolated-EXAM	2026-10-22 10:57:05.424467+00
8f673d22-4cef-4eb7-8a29-134fdc8ba990	6941b792-a948-4acb-b52f-6cdcb26b5956	EXAM	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	V-CLOSE-03-isolated-EXAM	2026-10-22 11:37:33.793302+00
faa86de8-488d-415b-b02a-a6eb72898929	0e6d5b39-82f4-4631-8851-60a1191ed085	EXAM	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	V-CLOSE-03-isolated-EXAM	2026-10-22 11:56:40.771836+00
\.


--
-- Data for Name: billing_outpatient_refund_publication; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.billing_outpatient_refund_publication (refund_transaction_id, payment_request_id, acceptance_reference, approved_at) FROM stdin;
\.


--
-- Data for Name: charge; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.charge (charge_id, account_id, patient_id, department_id, source_type, source_id, price_code, description, quantity, unit_amount, gross_amount, status, void_reason, incurred_at, created_at, reconciled_result_id) FROM stdin;
96224989-bf0d-43fd-b8ab-10e9398d5a28	2b0753ea-f892-4484-81c6-644b08832cc2	c36277c4-347f-480d-8473-bcf9f98c8a44	b134eebd-d327-4718-8bef-92525164178d	EXAM	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:40:15.984936+00	2026-10-22 00:40:16.340778+00	\N
6549563e-4097-439c-80e4-bf172df66a18	2b0753ea-f892-4484-81c6-644b08832cc2	c36277c4-347f-480d-8473-bcf9f98c8a44	b134eebd-d327-4718-8bef-92525164178d	PRESCRIPTION	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	DRUG	Prescription medication bundle	1.0000	30000.00	30000.00	VOIDED	prescription.cancelled	2026-10-22 00:40:19.501589+00	2026-10-22 00:40:21.308482+00	\N
535a41e6-f862-4b81-baf4-f4221f45c388	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	71626749-ef62-42f4-9d40-35097f05dfb9	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:40:38.443302+00	2026-10-22 00:40:38.521027+00	\N
6566a9c1-9bcd-45be-83dc-79d006f9bdad	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	LAB_TEST	4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	LAB	Isolated frontend Lab acceptance	1.0000	150000.00	150000.00	POSTED	\N	2026-10-22 00:40:41.435905+00	2026-10-22 00:40:43.998292+00	\N
b20cb508-4ac7-4be2-b306-165983dae7c8	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:44:55.899375+00	2026-10-22 00:44:56.008084+00	\N
bef6f14e-f3b1-4a17-92f1-264b1becba6a	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	LAB_TEST	a92c9118-e58f-4afb-b666-75030fe10600	LAB	Isolated frontend Lab acceptance	1.0000	150000.00	150000.00	POSTED	\N	2026-10-22 00:44:58.33653+00	2026-10-22 00:45:01.462161+00	\N
1ab24bed-3bac-40f3-bdba-16c5a8099dda	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	PRESCRIPTION	84116e5c-b8d2-4afa-a586-0c2af1c350ab	DRUG	Prescription medication bundle	1.0000	9000.00	9000.00	POSTED	\N	2026-10-22 00:45:31.109138+00	2026-10-22 00:45:31.283091+00	\N
a8123829-9a69-4681-b3af-2f8899dfda2a	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	PRESCRIPTION	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	DRUG	Prescription medication bundle	1.0000	9000.00	9000.00	POSTED	\N	2026-10-22 00:45:34.633066+00	2026-10-22 00:45:35.504701+00	\N
075f291a-6e18-4bee-92ae-947c1a001741	8f3e4603-9e61-481a-8cb5-3b485b4c8560	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	037fcbd9-127b-44d6-96ee-73c571763c5e	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:45:37.87203+00	2026-10-22 00:45:37.888384+00	\N
c24ceadc-cf39-4535-b829-f8d8e6478e05	8f3e4603-9e61-481a-8cb5-3b485b4c8560	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	LAB_TEST	bbbe2b41-5061-4381-ab46-472b63180bad	LAB	Isolated frontend Lab acceptance	1.0000	150000.00	150000.00	POSTED	\N	2026-10-22 00:45:40.287795+00	2026-10-22 00:45:43.362269+00	\N
df36b6e0-3679-43ad-9f60-5ec204596f9d	8f3e4603-9e61-481a-8cb5-3b485b4c8560	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	PRESCRIPTION	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	DRUG	Prescription medication bundle	1.0000	9000.00	9000.00	POSTED	\N	2026-10-22 00:45:46.632375+00	2026-10-22 00:45:46.703085+00	\N
03fc1127-625e-4c64-8fc5-e9c43fb10700	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	250352fd-6ae9-4108-a147-2d76e9b49eb9	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:45:46.741006+00	2026-10-22 00:45:46.77355+00	\N
db6e6f4a-9d10-4686-8937-628089ea5d01	86874f73-ccd3-492e-ad59-d064e2520f62	1270368c-7e70-40eb-852c-b58d7b052cf7	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	a4fde265-3562-4528-8ea6-0db0e835c1d6	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:45:49.368883+00	2026-10-22 00:45:52.227013+00	\N
3e0fb6a6-da75-472a-bde9-8052d0074770	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	PRESCRIPTION	7b8b6d90-897d-4439-8f89-4f6905e825ae	DRUG	Prescription medication bundle	1.0000	9000.00	9000.00	POSTED	\N	2026-10-22 00:45:49.267844+00	2026-10-22 00:45:52.239028+00	\N
b3b62ff8-9966-4bd6-8e29-ba1c4475f9dc	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	LAB_TEST	ad116d89-b9f6-4cef-a3de-028d541b00ef	LAB	Isolated frontend Lab acceptance	1.0000	150000.00	150000.00	POSTED	\N	2026-10-22 00:45:49.151243+00	2026-10-22 00:45:52.255675+00	\N
17627d7b-ea5d-406e-92c8-d3bf644fffec	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	83ee7cd5-ccb4-4005-96f3-42abb0261400	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:45:54.593407+00	2026-10-22 00:45:54.810977+00	\N
f03f290b-f182-4f27-bf18-b445ec7a13a9	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	LAB_TEST	cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932	LAB	Isolated frontend Lab acceptance	1.0000	150000.00	150000.00	POSTED	\N	2026-10-22 00:49:15.753992+00	2026-10-22 00:49:16.592778+00	\N
1f8f920f-f6c0-48e8-96b2-36c62b21335a	a806fda3-0b26-4bc1-96fd-85f6a74ca294	3ad6444d-54b2-467a-9e25-beb664d3975f	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	b264777a-2a76-4793-b4ac-91e6a8779f0d	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 00:50:46.53268+00	2026-10-22 00:50:46.657987+00	\N
149bb948-f905-48a2-963f-476caa1c8e7c	5c325e27-b74d-49d7-b8ef-2c763630967b	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	b9de10f8-44e8-4e44-8637-09135b5691e8	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 10:49:28.989701+00	2026-10-22 10:49:29.360719+00	\N
7eb54b4c-60af-45bc-8113-934570a7da73	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	LAB_TEST	31142159-4304-44d2-9e21-acae9bd325b3	LAB	Isolated frontend Lab acceptance	1.0000	150000.00	150000.00	POSTED	\N	2026-10-22 10:57:04.372528+00	2026-10-22 10:57:05.424467+00	\N
7eac2be7-f638-491e-8171-340be47b746f	6941b792-a948-4acb-b52f-6cdcb26b5956	a2c54b05-3010-4b8d-93ee-67fb06469d77	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 11:37:33.301207+00	2026-10-22 11:37:33.793302+00	\N
16024a61-3635-41d8-a0ae-40be3fb37969	0e6d5b39-82f4-4631-8851-60a1191ed085	b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	EXAM	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	OUTPATIENT_EXAM	Outpatient exam acceptance	1.0000	100000.00	100000.00	POSTED	\N	2026-10-22 11:56:40.509471+00	2026-10-22 11:56:40.771836+00	\N
\.


--
-- Data for Name: deposit_allocation_operation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.deposit_allocation_operation (allocation_operation_id, account_id, original_transaction_id, actor_id, idempotency_key, intent_hash, allocated_at, payload) FROM stdin;
\.


--
-- Data for Name: exam_charge_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.exam_charge_delivery (event_id, appointment_id, payload_fingerprint, received_at) FROM stdin;
86cf892c-01ee-4f3d-a21a-8423b53d7156	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	f35f794f39a1d1d601607a69317acee89635e14828190d31c97d6c889c9f4824	2026-10-22 00:40:16.340778+00
37a45f81-b95b-41b1-9efc-b43ffb9f1f83	71626749-ef62-42f4-9d40-35097f05dfb9	4ed2148438acf8cb6c0ad535472abb2ed1c9af7eb3719eeffa45d63e5a019fb2	2026-10-22 00:40:38.521027+00
44e26346-c657-4b9e-80ae-1e1a3f5ef77f	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	8a254bce81c8c0017da466694a402d33e2165a851401f71ad15bb2b8f09033d6	2026-10-22 00:44:56.008084+00
49d4b575-7d49-4098-b1ba-9d975abf8cd6	037fcbd9-127b-44d6-96ee-73c571763c5e	b3628e52e5e2950c00529f3478ee39dd47000cb8c5798aa32172a83142fca460	2026-10-22 00:45:37.888384+00
6214f2ae-0a06-4198-bbe0-3006e57b8e14	250352fd-6ae9-4108-a147-2d76e9b49eb9	5edb3052843582c5d222f63c9bb7659c3c293a820c817d8e119f7ec7af9f22ca	2026-10-22 00:45:46.77355+00
879e9f5d-05d4-4a11-93d5-7864a8e009f8	a4fde265-3562-4528-8ea6-0db0e835c1d6	58e7bd0434a1471a0cbdc00ee5cbe29c3684e16612f2c4735e9a547513b288c0	2026-10-22 00:45:52.227013+00
724aff72-bf5a-4b1d-9dbf-e43a35e5a2d2	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	4524d9e9799a796842e9fb6e88edbb1bfc76452932375e953757fbe863fff066	2026-10-22 00:45:54.810977+00
1f308854-58a8-4503-90e2-378ea0fab161	b264777a-2a76-4793-b4ac-91e6a8779f0d	bb133f29fe4109acf5e31ba9fb5c3f28abc0b0180063a2aed3cf5b68410f1feb	2026-10-22 00:50:46.657987+00
0dae3cab-312e-4bb6-8aba-7026d7eb9751	b9de10f8-44e8-4e44-8637-09135b5691e8	7ab0d37069865c58a2ec636fe6686828c974a2cb4f3b22b9d192365a73a5ec71	2026-10-22 10:49:29.360719+00
d056593d-84f3-44fc-896c-6b5903c12175	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	82c71a125eac0b1276150854054a224a0a1fd0b701486a7d4ce027e1aa5dbb31	2026-10-22 11:37:33.793302+00
9726abd3-fbea-4b34-ac89-8963d0ad6f68	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	51e50f7bbb2319ae06afa7bc3e89790af06906a89b28d255553fbec2ef45bccd	2026-10-22 11:56:40.771836+00
\.


--
-- Data for Name: exam_charge_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.exam_charge_source (appointment_id, source_fingerprint, payment_request_id, requested_at, patient_id, department_id, care_episode_type, care_episode_id, price_code, requested_at_iso) FROM stdin;
11935f4f-6428-4051-ba9e-8e8a1b7d9f11	83267ba6a6fe65d7e231a2d9e1812b9c41b48f853705323d3e0e007c617242c3	5e891e9f-3f19-447f-87dd-a4d1dbff1f54	2026-10-22 00:40:15.984936+00	c36277c4-347f-480d-8473-bcf9f98c8a44	b134eebd-d327-4718-8bef-92525164178d	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	OUTPATIENT_EXAM	2026-10-22T00:40:15.984936300Z
71626749-ef62-42f4-9d40-35097f05dfb9	897b6364a36a916b50e92b693128c5a29904a651f840b7fa93866d6b5857fdca	615649a6-dcfb-4c4e-87cf-44a747595daa	2026-10-22 00:40:38.443302+00	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	OUTPATIENT_EXAM	2026-10-22T00:40:38.443302Z
eab4cb0a-de3f-495c-9304-73ff1f18b5d4	7156a95fdf8dc0efad3368d20781459d17eeb8042b4c0282b7b926802532999d	c626fe0e-332f-4ec6-820c-462757683d5c	2026-10-22 00:44:55.899375+00	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	OUTPATIENT_EXAM	2026-10-22T00:44:55.899375100Z
037fcbd9-127b-44d6-96ee-73c571763c5e	0fc56df7e79a35dfd8638987927d0d4c1faf3e60d913980997fd822c992a5de6	ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	2026-10-22 00:45:37.87203+00	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	OUTPATIENT_EXAM	2026-10-22T00:45:37.872029600Z
250352fd-6ae9-4108-a147-2d76e9b49eb9	d213443c89324836e16cd7ff41c97fcc865feb7759bafb3d730066ce374179d7	cc950a37-4205-444f-a620-352b617b09fe	2026-10-22 00:45:46.741006+00	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	OUTPATIENT_EXAM	2026-10-22T00:45:46.741005900Z
a4fde265-3562-4528-8ea6-0db0e835c1d6	b21a99e1b08199cd039797f58c2cc12ca4a966bbb822ad31e4a390e6e4aabbbe	e62ca364-6afa-4a41-a7cb-e1654821950d	2026-10-22 00:45:49.368883+00	1270368c-7e70-40eb-852c-b58d7b052cf7	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	a4fde265-3562-4528-8ea6-0db0e835c1d6	OUTPATIENT_EXAM	2026-10-22T00:45:49.368882900Z
74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	3602106f5fb00ad35c53a885ce7b1b91e1bbcb92f903b1a098803c1c5ae832cf	ea2b7648-09a0-4906-9ee4-ca1224c29368	2026-10-22 00:45:54.593407+00	83ee7cd5-ccb4-4005-96f3-42abb0261400	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	OUTPATIENT_EXAM	2026-10-22T00:45:54.593407Z
b264777a-2a76-4793-b4ac-91e6a8779f0d	f0c6fb8f21ab1f959da0ce4ddae39487b5989865f344ad9baad5169abbea8167	1d51b9bf-3af2-4263-8b04-b868a82bffa9	2026-10-22 00:50:46.53268+00	3ad6444d-54b2-467a-9e25-beb664d3975f	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	b264777a-2a76-4793-b4ac-91e6a8779f0d	OUTPATIENT_EXAM	2026-10-22T00:50:46.532680200Z
b9de10f8-44e8-4e44-8637-09135b5691e8	4a621aab6cdd2faf190c8ea66caaba2efdc73d8f413b770dbbabf324e4bf6c5d	33467dbe-11a2-4c7a-8742-fa27f9fb2641	2026-10-22 10:49:28.989701+00	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	b9de10f8-44e8-4e44-8637-09135b5691e8	OUTPATIENT_EXAM	2026-10-22T10:49:28.989701100Z
f91a4cad-6e7a-4b23-9f3f-08187f1687ec	49ea7e3044dbafd987a8c04ab37aadcc6d1472bb0466318c9954035d13b45ecb	8f673d22-4cef-4eb7-8a29-134fdc8ba990	2026-10-22 11:37:33.301207+00	a2c54b05-3010-4b8d-93ee-67fb06469d77	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	OUTPATIENT_EXAM	2026-10-22T11:37:33.301206600Z
52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	162df1f731544cd7b1517e366e7b9abeeae217ae41b4575e3ae2da98fc4aed93	faa86de8-488d-415b-b02a-a6eb72898929	2026-10-22 11:56:40.509471+00	b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	OUTPATIENT_EXAM	2026-10-22T11:56:40.509471300Z
\.


--
-- Data for Name: fee; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.fee (fee_id, patient_id, record_id, department_id, source_ref_id, fee_type, incurred_date, amount, is_paid, invoice_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: final_admission_settlement_operation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.final_admission_settlement_operation (recognition_operation_id, settlement_id, account_id, actor_id, idempotency_key, completed_at, payload) FROM stdin;
\.


--
-- Data for Name: financial_clearance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_clearance (clearance_id, account_id, payment_request_id, invoice_id, patient_id, purpose, care_episode_type, care_episode_id, appointment_id, record_id, lab_test_ids, prescription_id, admission_id, surgery_case_id, amount, currency, payment_method, emergency_override, expires_at, granted_at, revoked_at) FROM stdin;
86de69c5-4b2e-4458-83e0-6677efc159aa	2b0753ea-f892-4484-81c6-644b08832cc2	5e891e9f-3f19-447f-87dd-a4d1dbff1f54	48bdd0f9-a9c4-45bc-9987-40df9770e1b0	c36277c4-347f-480d-8473-bcf9f98c8a44	EXAM	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:40:16.917046+00	\N
49f69633-3e1f-4ef3-ab1e-c7b344ab174a	e4463013-b866-45d6-aeeb-20d5e6c07197	615649a6-dcfb-4c4e-87cf-44a747595daa	92244ff4-319a-4ccc-8bcd-cb76aa714e08	30820325-711c-4d7b-b364-be73c78a43b0	EXAM	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	71626749-ef62-42f4-9d40-35097f05dfb9	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:40:39.521339+00	\N
f774ec12-0ace-4926-b47a-1a97274cc814	e4463013-b866-45d6-aeeb-20d5e6c07197	cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	cb97113f-6b14-469f-8439-ce19ae2aa207	30820325-711c-4d7b-b364-be73c78a43b0	LAB_TEST	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	\N	4447ad07-fe64-4d41-8c96-7a278d167be2	{4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088}	\N	\N	\N	150000.00	VND	CASH	f	\N	2026-10-22 00:40:44.583111+00	\N
b9ec057b-c3a9-45b5-9e95-36109f530bc1	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	c626fe0e-332f-4ec6-820c-462757683d5c	1f14c914-9c13-4e48-9003-dd73422859fa	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	EXAM	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:44:56.990696+00	\N
b9dc3502-c045-47d3-973d-07f7c296e12d	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	17047f8c-0ddf-443f-8e19-b056667afbe5	4541152d-f447-4a88-ba0d-b63ee9244d34	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	LAB_TEST	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	\N	1361a316-8aa3-4ad4-8774-f852d8c64fa1	{a92c9118-e58f-4afb-b666-75030fe10600}	\N	\N	\N	150000.00	VND	CASH	f	\N	2026-10-22 00:45:02.489562+00	\N
595165d5-e8c4-41e1-9203-1cd72c053d30	e4463013-b866-45d6-aeeb-20d5e6c07197	0cff9bd3-381b-4b73-970f-b66e657e4435	eb37200c-eb91-4c71-b45e-732a9ba773c8	30820325-711c-4d7b-b364-be73c78a43b0	PRESCRIPTION	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	\N	\N	{}	84116e5c-b8d2-4afa-a586-0c2af1c350ab	\N	\N	9000.00	VND	CASH	f	\N	2026-10-22 00:45:32.201666+00	\N
3dc5587a-41d2-4b78-8ea6-cd1fc05e9294	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	88e40197-ac94-4a80-be9b-f94934eea7ff	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	PRESCRIPTION	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	\N	\N	{}	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	\N	\N	9000.00	VND	CASH	f	\N	2026-10-22 00:45:35.739516+00	\N
db1a5449-6c73-4772-8791-54039cb8111e	8f3e4603-9e61-481a-8cb5-3b485b4c8560	ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	2659a221-7b81-4a18-be74-2c0a873edad7	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	EXAM	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	037fcbd9-127b-44d6-96ee-73c571763c5e	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:45:38.928545+00	\N
432e20d2-1ca3-49ed-8aed-a17948dca7aa	8f3e4603-9e61-481a-8cb5-3b485b4c8560	4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	e4c1681c-a2ac-40a9-9a63-e871388725fe	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	LAB_TEST	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	\N	acc4a82c-6856-469d-80be-8dc343a9e06e	{bbbe2b41-5061-4381-ab46-472b63180bad}	\N	\N	\N	150000.00	VND	CASH	f	\N	2026-10-22 00:45:44.434798+00	\N
33e1d0e6-f85f-450b-9813-41a6e5b56cf9	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	cc950a37-4205-444f-a620-352b617b09fe	70cfd0e8-1d1d-41a3-93c2-b7e0d9eccbb4	6f75b635-a491-4191-99b3-1df16c1a3832	EXAM	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	250352fd-6ae9-4108-a147-2d76e9b49eb9	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:45:47.836519+00	\N
fa6622b0-d820-4c4e-9bf6-26671394453b	86874f73-ccd3-492e-ad59-d064e2520f62	e62ca364-6afa-4a41-a7cb-e1654821950d	6a11c00e-afd8-4eb9-9a6f-6c26ffa86d92	1270368c-7e70-40eb-852c-b58d7b052cf7	EXAM	OUTPATIENT_VISIT	a4fde265-3562-4528-8ea6-0db0e835c1d6	a4fde265-3562-4528-8ea6-0db0e835c1d6	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:45:52.492536+00	\N
76283443-9e57-4e50-b63c-ede3cac9664d	a806fda3-0b26-4bc1-96fd-85f6a74ca294	1d51b9bf-3af2-4263-8b04-b868a82bffa9	fbbb7a7c-7227-4118-a21c-66f30fb3ad72	3ad6444d-54b2-467a-9e25-beb664d3975f	EXAM	OUTPATIENT_VISIT	b264777a-2a76-4793-b4ac-91e6a8779f0d	b264777a-2a76-4793-b4ac-91e6a8779f0d	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:52:51.000335+00	\N
0c7cc504-61b9-4f1d-ac04-fda127f2cb86	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	ea2b7648-09a0-4906-9ee4-ca1224c29368	663b13cc-9758-4615-a7a1-d401ddbe6e21	83ee7cd5-ccb4-4005-96f3-42abb0261400	EXAM	OUTPATIENT_VISIT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 00:56:55.535559+00	\N
80f4ed5d-2194-4056-be84-bd056d4e1599	5c325e27-b74d-49d7-b8ef-2c763630967b	33467dbe-11a2-4c7a-8742-fa27f9fb2641	8e705b7f-804d-48d8-a0f4-10a8a4df1e9d	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	EXAM	OUTPATIENT_VISIT	b9de10f8-44e8-4e44-8637-09135b5691e8	b9de10f8-44e8-4e44-8637-09135b5691e8	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 10:53:22.794782+00	\N
fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	6941b792-a948-4acb-b52f-6cdcb26b5956	8f673d22-4cef-4eb7-8a29-134fdc8ba990	a5d788e6-3ae0-4ed2-bb3d-0383e921a628	a2c54b05-3010-4b8d-93ee-67fb06469d77	EXAM	OUTPATIENT_VISIT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	\N	{}	\N	\N	\N	100000.00	VND	CASH	f	\N	2026-10-22 11:41:34.165632+00	\N
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	-1157090374	test	2026-10-22 07:39:25.924854	32	t
2	2	billing event outbox	SQL	V2__billing_event_outbox.sql	-887704231	test	2026-10-22 07:39:25.994825	13	t
3	3	drop invoice dispense id	SQL	V3__drop_invoice_dispense_id.sql	408438263	test	2026-10-22 07:39:26.027989	6	t
4	4	care finance ledger	SQL	V4__care_finance_ledger.sql	1164220250	test	2026-10-22 07:39:26.047576	53	t
5	5	ledger payment authority	SQL	V5__ledger_payment_authority.sql	-135252613	test	2026-10-22 07:39:26.118514	11	t
6	6	ledger event delivery fence	SQL	V6__ledger_event_delivery_fence.sql	1864487430	test	2026-10-22 07:39:26.139535	6	t
7	7	surgery charge reconciliation	SQL	V7__surgery_charge_reconciliation.sql	1726750982	test	2026-10-22 07:39:26.15602	4	t
8	8	ledger refund evidence	SQL	V8__ledger_refund_evidence.sql	-372227079	test	2026-10-22 07:39:26.168385	9	t
9	9	surgery planned request issuance	SQL	V9__surgery_planned_request_issuance.sql	1782026415	test	2026-10-22 07:39:26.184767	20	t
10	10	surgery cancellation adjustment	SQL	V10__surgery_cancellation_adjustment.sql	-845912837	test	2026-10-22 07:39:26.214788	21	t
11	11	surgery requested at exact time	SQL	V11__surgery_requested_at_exact_time.sql	967443409	test	2026-10-22 07:39:26.245955	3	t
12	12	lab test charge issuance	SQL	V12__lab_test_charge_issuance.sql	1576970466	test	2026-10-22 07:39:26.256814	11	t
13	13	admission deposit request issuance	SQL	V13__admission_deposit_request_issuance.sql	-889559477	test	2026-10-22 07:39:26.276397	10	t
14	14	admission reconciliation snapshots	SQL	V14__admission_reconciliation_snapshots.sql	494610938	test	2026-10-22 07:39:26.294598	6	t
15	15	admission medical charge close	SQL	V15__admission_medical_charge_close.sql	-180561807	test	2026-10-22 07:39:26.308421	15	t
16	16	admission deposit allocation	SQL	V16__admission_deposit_allocation.sql	-109028415	test	2026-10-22 07:39:26.331155	7	t
17	17	surgery completion proof	SQL	V17__surgery_completion_proof.sql	-1881086338	test	2026-10-22 07:39:26.346008	9	t
18	18	final admission settlement operation	SQL	V18__final_admission_settlement_operation.sql	-317742570	test	2026-10-22 07:39:26.363508	8	t
19	19	prescription charge issuance	SQL	V19__prescription_charge_issuance.sql	1683483455	test	2026-10-22 07:39:26.380203	13	t
20	20	prescription terminal adjustment	SQL	V20__prescription_terminal_adjustment.sql	330426471	test	2026-10-22 07:39:26.40137	15	t
21	21	exam charge issuance	SQL	V21__exam_charge_issuance.sql	205939614	test	2026-10-22 07:39:26.426236	9	t
22	22	outpatient publication cohort	SQL	V22__outpatient_publication_cohort.sql	-4595576	test	2026-10-22 07:39:26.444088	16	t
23	23	outpatient publication payload fence	SQL	V23__outpatient_publication_payload_fence.sql	1738568821	test	2026-10-22 07:39:26.469693	6	t
24	24	outpatient refund publication	SQL	V24__outpatient_refund_publication.sql	-1425383411	test	2026-10-22 07:39:26.483753	10	t
25	25	outpatient execution and terminal cancellation	SQL	V25__outpatient_execution_and_terminal_cancellation.sql	-1972068680	test	2026-10-22 07:39:26.502575	34	t
\.


--
-- Data for Name: insurance_adjustment; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.insurance_adjustment (adjustment_id, account_id, admission_id, decision_reference, adjustment_type, original_adjustment_id, amount, reason, created_at) FROM stdin;
\.


--
-- Data for Name: invoice; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.invoice (invoice_id, patient_id, created_date, total_amount, is_paid, payment_method, prescription_id, saga_status, paid_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: lab_test_charge_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_test_charge_delivery (event_id, lab_id, payload_fingerprint, received_at) FROM stdin;
0bf095e0-3af3-49d5-953c-c32248a202f9	4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	0bc8fac237412eb45b0615da84eb6cd9533293a8460019701bbfa94bd737a32f	2026-10-22 00:40:43.998292+00
9fc128f4-247e-4ca3-a85b-e7c5b56b481b	a92c9118-e58f-4afb-b666-75030fe10600	bb7b1bcdddf00cb8cd141fab652f606852efb4479c35815f505b6b3277215d5b	2026-10-22 00:45:01.462161+00
4bed985d-04ff-4eee-87a2-7e24dd3ec44d	bbbe2b41-5061-4381-ab46-472b63180bad	bfc3dc4c00a70b4e8098131b951cbda74184c7b750ad2f2f454a742f7800a1d2	2026-10-22 00:45:43.362269+00
3f1d13bb-a8f6-461c-99e3-99d34c9e1049	ad116d89-b9f6-4cef-a3de-028d541b00ef	d9d9ddade3f64053916cf828d55720b3eecad336ff4cc25666b908ecccab2e6f	2026-10-22 00:45:52.255675+00
53d52fd0-74cb-4b31-853b-d2793e83af2d	cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932	955df68807dcb33caf8a9bb254dfc48690b552abe627bb65b65067a1cec665d2	2026-10-22 00:49:16.592778+00
8ffb5648-45e2-4e02-9130-dd21134bacde	31142159-4304-44d2-9e21-acae9bd325b3	0ea90e6d9f24e579ceb3a54714d82df02c2450311480aaa5718eb762efc09f51	2026-10-22 10:57:05.424467+00
\.


--
-- Data for Name: lab_test_charge_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_test_charge_source (lab_id, source_order_id, source_fingerprint, payment_request_id, requested_at, patient_id, department_id, record_id, care_episode_type, care_episode_id, price_code, lab_type, requested_at_iso) FROM stdin;
4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	bfb6c454-9d0f-4777-9460-eea5721d50f8	66d85875e49822750e285acc1c9ac874f36b36d4119515fd85dae6ee1125454d	cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	2026-10-22 00:40:41.435905+00	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	4447ad07-fe64-4d41-8c96-7a278d167be2	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	LAB	CBC	2026-10-22T00:40:41.435904600Z
a92c9118-e58f-4afb-b666-75030fe10600	08f32690-ca20-4c8c-b075-d9a019e281ad	cf5856aff23f2c9a5c0fd61f341477c3fba706451a9a55ddbd68ab48af3b00cc	17047f8c-0ddf-443f-8e19-b056667afbe5	2026-10-22 00:44:58.33653+00	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	1361a316-8aa3-4ad4-8774-f852d8c64fa1	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	LAB	CBC	2026-10-22T00:44:58.336530400Z
bbbe2b41-5061-4381-ab46-472b63180bad	1c25abad-5ea6-4e62-b72a-a9f5176e29f6	757561766a3fdd047057217bb95c9b1a405df7f86b634419e00e953df1d24b38	4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	2026-10-22 00:45:40.287795+00	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	acc4a82c-6856-469d-80be-8dc343a9e06e	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	LAB	CBC	2026-10-22T00:45:40.287795300Z
ad116d89-b9f6-4cef-a3de-028d541b00ef	7d0abb3e-4dc3-4f47-956a-07abf29bceeb	784c072d496c20fd195d993ca30aa53a5c0995a8fb3cf25f65711af4469e6790	e9af22b5-ef29-41f9-bb49-8eca6f3bd792	2026-10-22 00:45:49.151243+00	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	1d4e99c7-3572-42be-9520-a43c1eedc030	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	LAB	CBC	2026-10-22T00:45:49.151243300Z
cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932	53ed2165-8991-493e-bc9b-0067d26283b9	3ab3d12aff800d95b6fc365d62c54f4d2f34a2936bb706a77f6f67ae2a7de07d	c9614526-fddd-41ef-ab85-4e8be1204adb	2026-10-22 00:49:15.753992+00	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	4447ad07-fe64-4d41-8c96-7a278d167be2	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	LAB	GLU	2026-10-22T00:49:15.753991600Z
31142159-4304-44d2-9e21-acae9bd325b3	c49301f4-75fa-49d9-a327-7fd17164bcb8	35268df52c20a052bc8f2b15c27a1311d8cef2604a816e5a2abaa4a7f5fc5adc	9432ae58-7a17-4420-b0f4-18ed6849e40e	2026-10-22 10:57:04.372528+00	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	4447ad07-fe64-4d41-8c96-7a278d167be2	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	LAB	CRP theo dõi Review3	2026-10-22T10:57:04.372528200Z
\.


--
-- Data for Name: lab_test_type; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_test_type (lab_id, lab_type, recorded_at) FROM stdin;
\.


--
-- Data for Name: ledger_refund_evidence; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.ledger_refund_evidence (refund_transaction_id, original_transaction_id, reason, created_at) FROM stdin;
\.


--
-- Data for Name: outpatient_cancellation_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.outpatient_cancellation_delivery (event_id, source_type, source_id, delivery_fingerprint, received_at) FROM stdin;
\.


--
-- Data for Name: outpatient_cancellation_refund_due; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.outpatient_cancellation_refund_due (source_type, source_id, original_transaction_id, charge_id, account_id, amount, currency) FROM stdin;
\.


--
-- Data for Name: outpatient_cancellation_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.outpatient_cancellation_source (source_type, source_id, event_type, source_fingerprint, first_event_id, correlation_id, source_order_id, patient_id, department_id, record_id, care_episode_type, care_episode_id, price_code, lab_type, cancelled_by, reason, cancelled_at_iso, status, rejection_code, applied_at) FROM stdin;
\.


--
-- Data for Name: outpatient_execution_start; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.outpatient_execution_start (operation_id, clearance_id, account_id, source_type, source_id, patient_id, care_episode_type, care_episode_id, state, started_at) FROM stdin;
11935f4f-6428-4051-ba9e-8e8a1b7d9f11	86de69c5-4b2e-4458-83e0-6677efc159aa	2b0753ea-f892-4484-81c6-644b08832cc2	EXAM	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	c36277c4-347f-480d-8473-bcf9f98c8a44	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	STARTED	2026-10-22 00:40:17.652671+00
71626749-ef62-42f4-9d40-35097f05dfb9	49f69633-3e1f-4ef3-ab1e-c7b344ab174a	e4463013-b866-45d6-aeeb-20d5e6c07197	EXAM	71626749-ef62-42f4-9d40-35097f05dfb9	30820325-711c-4d7b-b364-be73c78a43b0	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	STARTED	2026-10-22 00:40:40.670578+00
eab4cb0a-de3f-495c-9304-73ff1f18b5d4	b9ec057b-c3a9-45b5-9e95-36109f530bc1	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	EXAM	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	STARTED	2026-10-22 00:44:58.123691+00
037fcbd9-127b-44d6-96ee-73c571763c5e	db1a5449-6c73-4772-8791-54039cb8111e	8f3e4603-9e61-481a-8cb5-3b485b4c8560	EXAM	037fcbd9-127b-44d6-96ee-73c571763c5e	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	STARTED	2026-10-22 00:45:40.07465+00
250352fd-6ae9-4108-a147-2d76e9b49eb9	33e1d0e6-f85f-450b-9813-41a6e5b56cf9	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	EXAM	250352fd-6ae9-4108-a147-2d76e9b49eb9	6f75b635-a491-4191-99b3-1df16c1a3832	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	STARTED	2026-10-22 00:45:48.959369+00
\.


--
-- Data for Name: payment_allocation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_allocation (allocation_id, transaction_id, charge_id, amount, created_at) FROM stdin;
a68f3c41-680b-484e-bd93-d066729938dd	c4830cfc-8783-4fe7-93a6-834d90b31eba	96224989-bf0d-43fd-b8ab-10e9398d5a28	100000.00	2026-10-22 00:40:16.905072+00
3885eb78-db09-4fb6-838d-bd5e5edcb3d7	2ba1e00e-ccb0-47ea-b27e-e81042f78ce2	535a41e6-f862-4b81-baf4-f4221f45c388	100000.00	2026-10-22 00:40:39.513554+00
f340b58f-9e5a-43de-8537-8b35308ced86	cc0c7042-4358-4039-81dc-19d81ae7b14a	6566a9c1-9bcd-45be-83dc-79d006f9bdad	150000.00	2026-10-22 00:40:44.572895+00
9b9b6c22-8c67-4dff-9261-d7f6f5eccef1	e1cffe9a-9228-45bf-9f63-511d4c6f550c	b20cb508-4ac7-4be2-b306-165983dae7c8	100000.00	2026-10-22 00:44:56.984815+00
d5d1e61b-c5ce-4f96-a21f-968f54264f27	cf4b6b6a-d59d-444c-92fd-f4905bf85509	bef6f14e-f3b1-4a17-92f1-264b1becba6a	150000.00	2026-10-22 00:45:02.485025+00
27501dd0-830a-4031-9227-70ec7359bbdd	55a1ae72-572d-4daa-a23a-0176b0834aa0	1ab24bed-3bac-40f3-bdba-16c5a8099dda	9000.00	2026-10-22 00:45:32.196472+00
6e48d2ef-476a-4862-9a5b-d1db52715f0a	7649919b-93b2-4aea-906c-6e69d5cc3afb	a8123829-9a69-4681-b3af-2f8899dfda2a	9000.00	2026-10-22 00:45:35.734652+00
e618ea63-b643-4d0a-b092-215b9ccf633b	e55fe586-5ddb-48d7-9338-1836225c52c5	075f291a-6e18-4bee-92ae-947c1a001741	100000.00	2026-10-22 00:45:38.923668+00
2bd24d73-7a59-4d8d-8217-3df11d380f2a	b52f3117-223d-4432-ac82-4e7f06ccee62	c24ceadc-cf39-4535-b829-f8d8e6478e05	150000.00	2026-10-22 00:45:44.429168+00
0ee86146-48bf-44cf-a394-de68a9bd2a3e	add16c27-3994-4ef8-a7a6-80c3a01f5090	03fc1127-625e-4c64-8fc5-e9c43fb10700	100000.00	2026-10-22 00:45:47.833124+00
75fb2bc6-8b49-4894-bc4b-f176e6b49813	bced71a5-0b2f-45d2-87d2-9b41bf72d8b3	db6e6f4a-9d10-4686-8937-628089ea5d01	100000.00	2026-10-22 00:45:52.488902+00
80f2fe1a-f3b3-46df-a18e-9307c6d1d100	a33cb5ed-d048-4908-a7ec-2ae3f8416d77	1f8f920f-f6c0-48e8-96b2-36c62b21335a	100000.00	2026-10-22 00:52:50.995058+00
9875a2f5-fcd7-4896-827d-81c13e15d338	65284733-97d8-4108-9d85-6fa449d75310	17627d7b-ea5d-406e-92c8-d3bf644fffec	100000.00	2026-10-22 00:56:55.529997+00
7f7cbca8-21b6-41fa-bcfe-decc7e3872c7	c3802b07-5f4f-47e8-afa2-0d08b2d9fe2e	149bb948-f905-48a2-963f-476caa1c8e7c	100000.00	2026-10-22 10:53:22.765133+00
7248c0ee-1598-4621-b031-ae97b01e155b	2dc1ba9c-e65f-4396-be2e-f94e5d0f4a16	7eac2be7-f638-491e-8171-340be47b746f	100000.00	2026-10-22 11:41:34.158021+00
\.


--
-- Data for Name: payment_request; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_request (payment_request_id, invoice_id, account_id, purpose, status, requested_amount, currency, expires_at, created_by, created_at, completed_at) FROM stdin;
5e891e9f-3f19-447f-87dd-a4d1dbff1f54	48bdd0f9-a9c4-45bc-9987-40df9770e1b0	2b0753ea-f892-4484-81c6-644b08832cc2	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:40:16.340778+00	2026-10-22 00:40:16.917046+00
cfab1271-de78-43bd-8b3d-d4425096c5db	eb118cfb-934a-47ff-aed1-38fff5ef32a9	2b0753ea-f892-4484-81c6-644b08832cc2	PRESCRIPTION	CANCELLED	30000.00	VND	\N	\N	2026-10-22 00:40:21.46499+00	\N
615649a6-dcfb-4c4e-87cf-44a747595daa	92244ff4-319a-4ccc-8bcd-cb76aa714e08	e4463013-b866-45d6-aeeb-20d5e6c07197	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:40:38.521027+00	2026-10-22 00:40:39.521339+00
cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	cb97113f-6b14-469f-8439-ce19ae2aa207	e4463013-b866-45d6-aeeb-20d5e6c07197	LAB_TEST	PAID	150000.00	VND	\N	\N	2026-10-22 00:40:43.998292+00	2026-10-22 00:40:44.583111+00
c626fe0e-332f-4ec6-820c-462757683d5c	1f14c914-9c13-4e48-9003-dd73422859fa	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:44:56.008084+00	2026-10-22 00:44:56.990696+00
17047f8c-0ddf-443f-8e19-b056667afbe5	4541152d-f447-4a88-ba0d-b63ee9244d34	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	LAB_TEST	PAID	150000.00	VND	\N	\N	2026-10-22 00:45:01.462161+00	2026-10-22 00:45:02.489562+00
0cff9bd3-381b-4b73-970f-b66e657e4435	eb37200c-eb91-4c71-b45e-732a9ba773c8	e4463013-b866-45d6-aeeb-20d5e6c07197	PRESCRIPTION	PAID	9000.00	VND	\N	\N	2026-10-22 00:45:31.288281+00	2026-10-22 00:45:32.201666+00
1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	88e40197-ac94-4a80-be9b-f94934eea7ff	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	PRESCRIPTION	PAID	9000.00	VND	\N	\N	2026-10-22 00:45:35.51058+00	2026-10-22 00:45:35.739516+00
ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	2659a221-7b81-4a18-be74-2c0a873edad7	8f3e4603-9e61-481a-8cb5-3b485b4c8560	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:45:37.888384+00	2026-10-22 00:45:38.928545+00
4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	e4c1681c-a2ac-40a9-9a63-e871388725fe	8f3e4603-9e61-481a-8cb5-3b485b4c8560	LAB_TEST	PAID	150000.00	VND	\N	\N	2026-10-22 00:45:43.362269+00	2026-10-22 00:45:44.434798+00
c80ea7fa-076a-4a07-98bc-8fe4b08982c9	4a69d756-b855-4c6f-9eae-a4560ce74904	8f3e4603-9e61-481a-8cb5-3b485b4c8560	PRESCRIPTION	PENDING	9000.00	VND	\N	\N	2026-10-22 00:45:46.712577+00	\N
cc950a37-4205-444f-a620-352b617b09fe	70cfd0e8-1d1d-41a3-93c2-b7e0d9eccbb4	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:45:46.77355+00	2026-10-22 00:45:47.836519+00
cba26ee1-8c27-4fc1-9b74-1083946a1db0	48e6e851-6b2c-4bee-ba91-fe906ad737aa	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	PRESCRIPTION	PENDING	9000.00	VND	\N	\N	2026-10-22 00:45:52.242506+00	\N
e9af22b5-ef29-41f9-bb49-8eca6f3bd792	eb8b3c99-6ddd-437e-9f43-ee4714beecf5	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	LAB_TEST	PENDING	150000.00	VND	\N	\N	2026-10-22 00:45:52.255675+00	\N
e62ca364-6afa-4a41-a7cb-e1654821950d	6a11c00e-afd8-4eb9-9a6f-6c26ffa86d92	86874f73-ccd3-492e-ad59-d064e2520f62	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:45:52.227013+00	2026-10-22 00:45:52.492536+00
c9614526-fddd-41ef-ab85-4e8be1204adb	ad21ca34-63f8-44d4-8204-a6a09c644dc4	e4463013-b866-45d6-aeeb-20d5e6c07197	LAB_TEST	PENDING	150000.00	VND	\N	\N	2026-10-22 00:49:16.592778+00	\N
1d51b9bf-3af2-4263-8b04-b868a82bffa9	fbbb7a7c-7227-4118-a21c-66f30fb3ad72	a806fda3-0b26-4bc1-96fd-85f6a74ca294	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:50:46.657987+00	2026-10-22 00:52:51.000335+00
ea2b7648-09a0-4906-9ee4-ca1224c29368	663b13cc-9758-4615-a7a1-d401ddbe6e21	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 00:45:54.810977+00	2026-10-22 00:56:55.535559+00
33467dbe-11a2-4c7a-8742-fa27f9fb2641	8e705b7f-804d-48d8-a0f4-10a8a4df1e9d	5c325e27-b74d-49d7-b8ef-2c763630967b	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 10:49:29.360719+00	2026-10-22 10:53:22.794782+00
9432ae58-7a17-4420-b0f4-18ed6849e40e	d4b0c07e-1d0c-4198-8912-eeeb7338a74c	e4463013-b866-45d6-aeeb-20d5e6c07197	LAB_TEST	PENDING	150000.00	VND	\N	\N	2026-10-22 10:57:05.424467+00	\N
8f673d22-4cef-4eb7-8a29-134fdc8ba990	a5d788e6-3ae0-4ed2-bb3d-0383e921a628	6941b792-a948-4acb-b52f-6cdcb26b5956	EXAM	PAID	100000.00	VND	\N	\N	2026-10-22 11:37:33.793302+00	2026-10-22 11:41:34.165632+00
faa86de8-488d-415b-b02a-a6eb72898929	9e370aba-9a8e-401a-afea-5cf68b97ed3f	0e6d5b39-82f4-4631-8851-60a1191ed085	EXAM	PENDING	100000.00	VND	\N	\N	2026-10-22 11:56:40.771836+00	\N
\.


--
-- Data for Name: payment_request_charge; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_request_charge (payment_request_id, charge_id, requested_amount) FROM stdin;
5e891e9f-3f19-447f-87dd-a4d1dbff1f54	96224989-bf0d-43fd-b8ab-10e9398d5a28	100000.00
cfab1271-de78-43bd-8b3d-d4425096c5db	6549563e-4097-439c-80e4-bf172df66a18	30000.00
615649a6-dcfb-4c4e-87cf-44a747595daa	535a41e6-f862-4b81-baf4-f4221f45c388	100000.00
cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	6566a9c1-9bcd-45be-83dc-79d006f9bdad	150000.00
c626fe0e-332f-4ec6-820c-462757683d5c	b20cb508-4ac7-4be2-b306-165983dae7c8	100000.00
17047f8c-0ddf-443f-8e19-b056667afbe5	bef6f14e-f3b1-4a17-92f1-264b1becba6a	150000.00
0cff9bd3-381b-4b73-970f-b66e657e4435	1ab24bed-3bac-40f3-bdba-16c5a8099dda	9000.00
1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	a8123829-9a69-4681-b3af-2f8899dfda2a	9000.00
ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	075f291a-6e18-4bee-92ae-947c1a001741	100000.00
4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	c24ceadc-cf39-4535-b829-f8d8e6478e05	150000.00
c80ea7fa-076a-4a07-98bc-8fe4b08982c9	df36b6e0-3679-43ad-9f60-5ec204596f9d	9000.00
cc950a37-4205-444f-a620-352b617b09fe	03fc1127-625e-4c64-8fc5-e9c43fb10700	100000.00
e62ca364-6afa-4a41-a7cb-e1654821950d	db6e6f4a-9d10-4686-8937-628089ea5d01	100000.00
cba26ee1-8c27-4fc1-9b74-1083946a1db0	3e0fb6a6-da75-472a-bde9-8052d0074770	9000.00
e9af22b5-ef29-41f9-bb49-8eca6f3bd792	b3b62ff8-9966-4bd6-8e29-ba1c4475f9dc	150000.00
ea2b7648-09a0-4906-9ee4-ca1224c29368	17627d7b-ea5d-406e-92c8-d3bf644fffec	100000.00
c9614526-fddd-41ef-ab85-4e8be1204adb	f03f290b-f182-4f27-bf18-b445ec7a13a9	150000.00
1d51b9bf-3af2-4263-8b04-b868a82bffa9	1f8f920f-f6c0-48e8-96b2-36c62b21335a	100000.00
33467dbe-11a2-4c7a-8742-fa27f9fb2641	149bb948-f905-48a2-963f-476caa1c8e7c	100000.00
9432ae58-7a17-4420-b0f4-18ed6849e40e	7eb54b4c-60af-45bc-8113-934570a7da73	150000.00
8f673d22-4cef-4eb7-8a29-134fdc8ba990	7eac2be7-f638-491e-8171-340be47b746f	100000.00
faa86de8-488d-415b-b02a-a6eb72898929	16024a61-3635-41d8-a0ae-40be3fb37969	100000.00
\.


--
-- Data for Name: payment_request_target; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_request_target (payment_request_id, appointment_id, record_id, lab_test_ids, prescription_id, admission_id, surgery_case_id) FROM stdin;
5e891e9f-3f19-447f-87dd-a4d1dbff1f54	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	\N	{}	\N	\N	\N
cfab1271-de78-43bd-8b3d-d4425096c5db	\N	\N	{}	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	\N	\N
615649a6-dcfb-4c4e-87cf-44a747595daa	71626749-ef62-42f4-9d40-35097f05dfb9	\N	{}	\N	\N	\N
cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	\N	4447ad07-fe64-4d41-8c96-7a278d167be2	{4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088}	\N	\N	\N
c626fe0e-332f-4ec6-820c-462757683d5c	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	\N	{}	\N	\N	\N
17047f8c-0ddf-443f-8e19-b056667afbe5	\N	1361a316-8aa3-4ad4-8774-f852d8c64fa1	{a92c9118-e58f-4afb-b666-75030fe10600}	\N	\N	\N
0cff9bd3-381b-4b73-970f-b66e657e4435	\N	\N	{}	84116e5c-b8d2-4afa-a586-0c2af1c350ab	\N	\N
1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	\N	\N	{}	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	\N	\N
ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	037fcbd9-127b-44d6-96ee-73c571763c5e	\N	{}	\N	\N	\N
4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	\N	acc4a82c-6856-469d-80be-8dc343a9e06e	{bbbe2b41-5061-4381-ab46-472b63180bad}	\N	\N	\N
c80ea7fa-076a-4a07-98bc-8fe4b08982c9	\N	\N	{}	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	\N	\N
cc950a37-4205-444f-a620-352b617b09fe	250352fd-6ae9-4108-a147-2d76e9b49eb9	\N	{}	\N	\N	\N
e62ca364-6afa-4a41-a7cb-e1654821950d	a4fde265-3562-4528-8ea6-0db0e835c1d6	\N	{}	\N	\N	\N
cba26ee1-8c27-4fc1-9b74-1083946a1db0	\N	\N	{}	7b8b6d90-897d-4439-8f89-4f6905e825ae	\N	\N
e9af22b5-ef29-41f9-bb49-8eca6f3bd792	\N	1d4e99c7-3572-42be-9520-a43c1eedc030	{ad116d89-b9f6-4cef-a3de-028d541b00ef}	\N	\N	\N
ea2b7648-09a0-4906-9ee4-ca1224c29368	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	\N	{}	\N	\N	\N
c9614526-fddd-41ef-ab85-4e8be1204adb	\N	4447ad07-fe64-4d41-8c96-7a278d167be2	{cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932}	\N	\N	\N
1d51b9bf-3af2-4263-8b04-b868a82bffa9	b264777a-2a76-4793-b4ac-91e6a8779f0d	\N	{}	\N	\N	\N
33467dbe-11a2-4c7a-8742-fa27f9fb2641	b9de10f8-44e8-4e44-8637-09135b5691e8	\N	{}	\N	\N	\N
9432ae58-7a17-4420-b0f4-18ed6849e40e	\N	4447ad07-fe64-4d41-8c96-7a278d167be2	{31142159-4304-44d2-9e21-acae9bd325b3}	\N	\N	\N
8f673d22-4cef-4eb7-8a29-134fdc8ba990	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	\N	{}	\N	\N	\N
faa86de8-488d-415b-b02a-a6eb72898929	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	\N	{}	\N	\N	\N
\.


--
-- Data for Name: payment_transaction; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_transaction (transaction_id, account_id, payment_request_id, transaction_type, classification, status, amount, currency, payment_method, provider_reference, idempotency_key, original_transaction_id, completed_at, created_at, actor_account_id) FROM stdin;
c4830cfc-8783-4fe7-93a6-834d90b31eba	2b0753ea-f892-4484-81c6-644b08832cc2	5e891e9f-3f19-447f-87dd-a4d1dbff1f54	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	paid-exam-11935f4f-6428-4051-ba9e-8e8a1b7d9f11	\N	2026-10-22 00:40:16.917045+00	2026-10-22 00:40:16.917046+00	b7f06379-ddcc-4e1b-87e7-50ac24a7448f
2ba1e00e-ccb0-47ea-b27e-e81042f78ce2	e4463013-b866-45d6-aeeb-20d5e6c07197	615649a6-dcfb-4c4e-87cf-44a747595daa	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	review3-8f4679b2-deb1-4914-a98b-332c7f4c67ed	\N	2026-10-22 00:40:39.521338+00	2026-10-22 00:40:39.521339+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
cc0c7042-4358-4039-81dc-19d81ae7b14a	e4463013-b866-45d6-aeeb-20d5e6c07197	cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	PAYMENT	SERVICE_PAYMENT	COMPLETED	150000.00	VND	CASH	\N	review3-d03f0c52-73cb-4884-ae22-4676dd949f3b	\N	2026-10-22 00:40:44.583111+00	2026-10-22 00:40:44.583111+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
e1cffe9a-9228-45bf-9f63-511d4c6f550c	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	c626fe0e-332f-4ec6-820c-462757683d5c	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	review3-b36c0585-ee7f-4848-b315-03be0db673ab	\N	2026-10-22 00:44:56.990696+00	2026-10-22 00:44:56.990696+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
cf4b6b6a-d59d-444c-92fd-f4905bf85509	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	17047f8c-0ddf-443f-8e19-b056667afbe5	PAYMENT	SERVICE_PAYMENT	COMPLETED	150000.00	VND	CASH	\N	review3-62fa68c9-5cb4-4a98-932b-e2cbad5e6c61	\N	2026-10-22 00:45:02.489562+00	2026-10-22 00:45:02.489562+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
55a1ae72-572d-4daa-a23a-0176b0834aa0	e4463013-b866-45d6-aeeb-20d5e6c07197	0cff9bd3-381b-4b73-970f-b66e657e4435	PAYMENT	SERVICE_PAYMENT	COMPLETED	9000.00	VND	CASH	\N	review3-be0666a2-d3dd-4f0b-af87-667525b78a24	\N	2026-10-22 00:45:32.201665+00	2026-10-22 00:45:32.201666+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
7649919b-93b2-4aea-906c-6e69d5cc3afb	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	PAYMENT	SERVICE_PAYMENT	COMPLETED	9000.00	VND	CASH	\N	review3-7b3663b0-528d-491f-84ce-606e15369df5	\N	2026-10-22 00:45:35.739515+00	2026-10-22 00:45:35.739516+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
e55fe586-5ddb-48d7-9338-1836225c52c5	8f3e4603-9e61-481a-8cb5-3b485b4c8560	ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	review3-53575b61-e1f5-4b74-a54a-c7a7c194d560	\N	2026-10-22 00:45:38.928545+00	2026-10-22 00:45:38.928545+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
b52f3117-223d-4432-ac82-4e7f06ccee62	8f3e4603-9e61-481a-8cb5-3b485b4c8560	4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	PAYMENT	SERVICE_PAYMENT	COMPLETED	150000.00	VND	CASH	\N	review3-a7d8d634-b9a7-42bc-ae59-1589c7cf8095	\N	2026-10-22 00:45:44.434798+00	2026-10-22 00:45:44.434798+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
add16c27-3994-4ef8-a7a6-80c3a01f5090	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	cc950a37-4205-444f-a620-352b617b09fe	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	review3-c19076b6-1c8f-4512-a040-8c98ac844b85	\N	2026-10-22 00:45:47.836519+00	2026-10-22 00:45:47.836519+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
bced71a5-0b2f-45d2-87d2-9b41bf72d8b3	86874f73-ccd3-492e-ad59-d064e2520f62	e62ca364-6afa-4a41-a7cb-e1654821950d	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	review3-d7e5c9b6-c4ff-44df-80b9-be5cc931847a	\N	2026-10-22 00:45:52.492536+00	2026-10-22 00:45:52.492536+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
a33cb5ed-d048-4908-a7ec-2ae3f8416d77	a806fda3-0b26-4bc1-96fd-85f6a74ca294	1d51b9bf-3af2-4263-8b04-b868a82bffa9	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	62f6afa7-4904-47f7-b879-46cd1c30eb08	\N	2026-10-22 00:52:51.000334+00	2026-10-22 00:52:51.000335+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
65284733-97d8-4108-9d85-6fa449d75310	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	ea2b7648-09a0-4906-9ee4-ca1224c29368	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	1ae00ee4-53d8-4f2c-94f4-4dfce3664330	\N	2026-10-22 00:56:55.535559+00	2026-10-22 00:56:55.535559+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
c3802b07-5f4f-47e8-afa2-0d08b2d9fe2e	5c325e27-b74d-49d7-b8ef-2c763630967b	33467dbe-11a2-4c7a-8742-fa27f9fb2641	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	b9a559a3-85ea-4bc6-a09a-edfdc1f547f8	\N	2026-10-22 10:53:22.794782+00	2026-10-22 10:53:22.794782+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
2dc1ba9c-e65f-4396-be2e-f94e5d0f4a16	6941b792-a948-4acb-b52f-6cdcb26b5956	8f673d22-4cef-4eb7-8a29-134fdc8ba990	PAYMENT	SERVICE_PAYMENT	COMPLETED	100000.00	VND	CASH	\N	1609f641-f9bb-4a8b-9472-784ebe347e27	\N	2026-10-22 11:41:34.165631+00	2026-10-22 11:41:34.165632+00	78b0cc56-dd0f-4634-8ed0-be65e0c96229
\.


--
-- Data for Name: prescription_charge_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_charge_delivery (event_id, prescription_id, payload_fingerprint) FROM stdin;
7377fa4b-ea08-4ff1-a24f-5be31546e5a7	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	ac6791a3b4a1c96e0346fb3fde82af1518256c2c503731a1676e94575c92f0d4
8994a20f-f105-4ec4-a185-395c22aab57b	84116e5c-b8d2-4afa-a586-0c2af1c350ab	12c15b02e6b9341251d4b84a6cdab0d0b8add9795d043640f7298165e789c375
1ffa49d2-e974-4c65-9b7d-7c49510aec81	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	c42b774b28f563fdadfccff1aa3016e6c72ea03bab5f03e48b5f1ad039950246
588992b4-d137-4996-af14-313422e07269	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	0f0171c4178758ad279008e450f3ece5d3b99baf5cd9039df0bf0bf43ea53025
3de60d0c-4a2b-4052-9f39-47e93a533100	7b8b6d90-897d-4439-8f89-4f6905e825ae	604b3e79249f6ef38ba56cacd503731622f9213c312c34b0b41b11bdc75275ee
\.


--
-- Data for Name: prescription_charge_item; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_charge_item (prescription_id, drug_id, quantity, unit_price) FROM stdin;
583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	2cfc6e03-07fc-4e53-b740-7e744cb15d60	3	10000.00
84116e5c-b8d2-4afa-a586-0c2af1c350ab	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00
19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00
0a3f84b7-2905-405b-a0f8-c68afd9ca87a	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00
7b8b6d90-897d-4439-8f89-4f6905e825ae	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00
\.


--
-- Data for Name: prescription_charge_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_charge_source (prescription_id, source_fingerprint, account_id, charge_id, payment_request_id, invoice_id, care_context, record_id, created_at_iso, snapshot_fingerprint) FROM stdin;
583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	5d0ae6198c42253d54aaecf30c6ed0ad21d06a8046bdc60e3d1e2db3398931f4	2b0753ea-f892-4484-81c6-644b08832cc2	6549563e-4097-439c-80e4-bf172df66a18	cfab1271-de78-43bd-8b3d-d4425096c5db	eb118cfb-934a-47ff-aed1-38fff5ef32a9	OUTPATIENT	5917d49c-0715-416d-a482-c0b281926b43	2026-10-22T00:40:19.501589Z	de12d25d8899dde2c734dc5cc4486b7515a8c17d6ab783d5897fdf5e36ca82aa
84116e5c-b8d2-4afa-a586-0c2af1c350ab	8c6850096ceffb6c56325749f763068f089f1efbbb73c10ae10e6fe0c4fe08cc	e4463013-b866-45d6-aeeb-20d5e6c07197	1ab24bed-3bac-40f3-bdba-16c5a8099dda	0cff9bd3-381b-4b73-970f-b66e657e4435	eb37200c-eb91-4c71-b45e-732a9ba773c8	OUTPATIENT	4447ad07-fe64-4d41-8c96-7a278d167be2	2026-10-22T00:45:31.109138Z	9ee5aa753188b270c4d18cd53d81591edc1a8f6910d0bd616ac51933853416b3
19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	628e55b7e74d4a9db0f6e835bd51df23645ef86d03e11d97d0d76ff16b79a5e7	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	a8123829-9a69-4681-b3af-2f8899dfda2a	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	88e40197-ac94-4a80-be9b-f94934eea7ff	OUTPATIENT	1361a316-8aa3-4ad4-8774-f852d8c64fa1	2026-10-22T00:45:34.633066Z	7f1cec14104ab6140d20f5caaa4861f4d38991a14cf59c14b7d951c138725ff9
0a3f84b7-2905-405b-a0f8-c68afd9ca87a	60bb0146763591440ac51bceb9069c9b89b8efeb077eabdd8584a5168a84554c	8f3e4603-9e61-481a-8cb5-3b485b4c8560	df36b6e0-3679-43ad-9f60-5ec204596f9d	c80ea7fa-076a-4a07-98bc-8fe4b08982c9	4a69d756-b855-4c6f-9eae-a4560ce74904	OUTPATIENT	acc4a82c-6856-469d-80be-8dc343a9e06e	2026-10-22T00:45:46.632375Z	dfcfcfe2ecca00085b2ef55ae45bb4f9548e79fb10b241d06a418055d63e59a0
7b8b6d90-897d-4439-8f89-4f6905e825ae	ff2e5e5a6555804aaa17abe2bd05260d965c1c88f70d7604e670d6a7db3aa62a	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	3e0fb6a6-da75-472a-bde9-8052d0074770	cba26ee1-8c27-4fc1-9b74-1083946a1db0	48e6e851-6b2c-4bee-ba91-fe906ad737aa	OUTPATIENT	1d4e99c7-3572-42be-9520-a43c1eedc030	2026-10-22T00:45:49.267844Z	a459fb947092df78aba35d24b3a068e712a89f77d03c75ccaf4263450d6a2af7
\.


--
-- Data for Name: prescription_terminal_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_terminal_delivery (event_id, prescription_id, delivery_fingerprint) FROM stdin;
32b9c998-f101-42df-af40-0cd87c41fc40	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	0420cb835d4b7b78628f0b29b11ffb1e5d0dabf7ff643db205ac72931e170b7b
7d3186d9-a2c6-44e1-a89c-4565b164d105	84116e5c-b8d2-4afa-a586-0c2af1c350ab	e2c9eb15bfe67c2911430225d01771042f4ee97d601cefaf8ec234ec23eff8f5
6400782d-fa9c-41f8-a5c3-95b1ad7081ce	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	b9dda7aebee9037c313c4d799c8266ce4ace510f6d2dddd350fdb8896818f2e2
\.


--
-- Data for Name: prescription_terminal_refund_due; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_terminal_refund_due (prescription_id, original_transaction_id, charge_id, amount) FROM stdin;
\.


--
-- Data for Name: prescription_terminal_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_terminal_source (prescription_id, event_type, source_fingerprint, snapshot_fingerprint, terminal_at_iso, status, rejection_code, applied_at) FROM stdin;
583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	prescription.cancelled	e542e8d5513f177dedd52aad3e93aecd35538f3ce8379a9c3ecd7759568ec34c	de12d25d8899dde2c734dc5cc4486b7515a8c17d6ab783d5897fdf5e36ca82aa	2026-10-22T00:40:19.656720600Z	APPLIED	\N	2026-10-22 00:40:22.119117+00
84116e5c-b8d2-4afa-a586-0c2af1c350ab	prescription.filled	4e91a6510e4d41ca1fcd69fdcfbeebca355ea3106b679773813f18ef9f6d87ec	9ee5aa753188b270c4d18cd53d81591edc1a8f6910d0bd616ac51933853416b3	2026-10-22T00:45:34.364279600Z	APPLIED	\N	2026-10-22 00:45:35.364281+00
19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	prescription.filled	5f0c981fe9f1fed3a258359f740e5d66a7413cdee0b0cdd1bda2d9100fe6baf6	7f1cec14104ab6140d20f5caaa4861f4d38991a14cf59c14b7d951c138725ff9	2026-10-22T00:52:17.382604700Z	APPLIED	\N	2026-10-22 00:52:18.019178+00
\.


--
-- Data for Name: processed_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.processed_event (event_id, routing_key, processed_at) FROM stdin;
\.


--
-- Data for Name: settlement; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.settlement (settlement_id, account_id, admission_id, settlement_version, supersedes_settlement_id, gross_amount, insurance_amount, patient_liability, completed_payments, completed_refunds, balance, outcome, completed_at) FROM stdin;
\.


--
-- Data for Name: surgery_cancellation_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_cancellation_delivery (event_id, surgery_case_id, delivery_fingerprint, received_at) FROM stdin;
\.


--
-- Data for Name: surgery_cancellation_refund_due; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_cancellation_refund_due (cancellation_id, original_transaction_id, charge_id, account_id, amount, currency, created_at) FROM stdin;
\.


--
-- Data for Name: surgery_cancellation_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_cancellation_source (surgery_case_id, cancellation_id, surgery_request_id, first_event_id, source_fingerprint, delivery_fingerprint, correlation_id, patient_id, department_id, care_episode_type, care_episode_id, admission_id, record_id, case_revision, cancellation_stage, cancelled_by, cancelled_by_staff_id, cancelled_at_iso, status, rejection_code, retry_at, applied_at) FROM stdin;
\.


--
-- Data for Name: surgery_charge_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_charge_delivery (event_id, surgery_case_id, payload_fingerprint, received_at) FROM stdin;
\.


--
-- Data for Name: surgery_charge_item; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_charge_item (surgery_case_id, item_code, price_code, quantity, charge_id) FROM stdin;
\.


--
-- Data for Name: surgery_charge_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_charge_source (surgery_case_id, surgery_request_id, source_fingerprint, payment_request_id, record_id, requested_at, requested_at_iso) FROM stdin;
\.


--
-- Data for Name: surgery_completion_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_completion_delivery (event_id, surgery_case_id, delivery_fingerprint) FROM stdin;
\.


--
-- Data for Name: surgery_completion_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_completion_source (surgery_case_id, surgery_request_id, account_id, patient_id, department_id, care_episode_type, care_episode_id, result_id, case_revision, source_revision, completed_at_iso, recorded_at_iso, source_fingerprint) FROM stdin;
\.


--
-- Name: admission_charge_close admission_charge_close_account_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close
    ADD CONSTRAINT admission_charge_close_account_id_key UNIQUE (account_id);


--
-- Name: admission_charge_close_delivery admission_charge_close_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close_delivery
    ADD CONSTRAINT admission_charge_close_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: admission_charge_close admission_charge_close_first_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close
    ADD CONSTRAINT admission_charge_close_first_event_id_key UNIQUE (first_event_id);


--
-- Name: admission_charge_close admission_charge_close_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close
    ADD CONSTRAINT admission_charge_close_pkey PRIMARY KEY (admission_id);


--
-- Name: admission_charge_close admission_charge_close_summary_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close
    ADD CONSTRAINT admission_charge_close_summary_id_key UNIQUE (summary_id);


--
-- Name: admission_deposit_delivery admission_deposit_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_deposit_delivery
    ADD CONSTRAINT admission_deposit_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: admission_deposit_source admission_deposit_source_payment_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_deposit_source
    ADD CONSTRAINT admission_deposit_source_payment_request_id_key UNIQUE (payment_request_id);


--
-- Name: admission_deposit_source admission_deposit_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_deposit_source
    ADD CONSTRAINT admission_deposit_source_pkey PRIMARY KEY (admission_id);


--
-- Name: admission_reconciliation admission_reconciliation_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_reconciliation
    ADD CONSTRAINT admission_reconciliation_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: admission_reconciliation admission_reconciliation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_reconciliation
    ADD CONSTRAINT admission_reconciliation_pkey PRIMARY KEY (settlement_id);


--
-- Name: billing_account billing_account_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_account
    ADD CONSTRAINT billing_account_pkey PRIMARY KEY (account_id);


--
-- Name: billing_event_outbox billing_event_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_event_outbox
    ADD CONSTRAINT billing_event_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: billing_outpatient_publication_cohort billing_outpatient_publication_cohort_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_outpatient_publication_cohort
    ADD CONSTRAINT billing_outpatient_publication_cohort_pkey PRIMARY KEY (payment_request_id);


--
-- Name: billing_outpatient_refund_publication billing_outpatient_refund_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_outpatient_refund_publication
    ADD CONSTRAINT billing_outpatient_refund_publication_pkey PRIMARY KEY (refund_transaction_id);


--
-- Name: charge charge_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.charge
    ADD CONSTRAINT charge_pkey PRIMARY KEY (charge_id);


--
-- Name: deposit_allocation_operation deposit_allocation_operation_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.deposit_allocation_operation
    ADD CONSTRAINT deposit_allocation_operation_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: deposit_allocation_operation deposit_allocation_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.deposit_allocation_operation
    ADD CONSTRAINT deposit_allocation_operation_pkey PRIMARY KEY (allocation_operation_id);


--
-- Name: exam_charge_delivery exam_charge_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_charge_delivery
    ADD CONSTRAINT exam_charge_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: exam_charge_source exam_charge_source_payment_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_charge_source
    ADD CONSTRAINT exam_charge_source_payment_request_id_key UNIQUE (payment_request_id);


--
-- Name: exam_charge_source exam_charge_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_charge_source
    ADD CONSTRAINT exam_charge_source_pkey PRIMARY KEY (appointment_id);


--
-- Name: fee fee_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.fee
    ADD CONSTRAINT fee_pkey PRIMARY KEY (fee_id);


--
-- Name: final_admission_settlement_operation final_admission_settlement_operation_account_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.final_admission_settlement_operation
    ADD CONSTRAINT final_admission_settlement_operation_account_id_key UNIQUE (account_id);


--
-- Name: final_admission_settlement_operation final_admission_settlement_operation_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.final_admission_settlement_operation
    ADD CONSTRAINT final_admission_settlement_operation_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: final_admission_settlement_operation final_admission_settlement_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.final_admission_settlement_operation
    ADD CONSTRAINT final_admission_settlement_operation_pkey PRIMARY KEY (recognition_operation_id);


--
-- Name: final_admission_settlement_operation final_admission_settlement_operation_settlement_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.final_admission_settlement_operation
    ADD CONSTRAINT final_admission_settlement_operation_settlement_id_key UNIQUE (settlement_id);


--
-- Name: financial_clearance financial_clearance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_clearance
    ADD CONSTRAINT financial_clearance_pkey PRIMARY KEY (clearance_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: insurance_adjustment insurance_adjustment_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.insurance_adjustment
    ADD CONSTRAINT insurance_adjustment_pkey PRIMARY KEY (adjustment_id);


--
-- Name: invoice invoice_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_pkey PRIMARY KEY (invoice_id);


--
-- Name: lab_test_charge_delivery lab_test_charge_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test_charge_delivery
    ADD CONSTRAINT lab_test_charge_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: lab_test_charge_source lab_test_charge_source_payment_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test_charge_source
    ADD CONSTRAINT lab_test_charge_source_payment_request_id_key UNIQUE (payment_request_id);


--
-- Name: lab_test_charge_source lab_test_charge_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test_charge_source
    ADD CONSTRAINT lab_test_charge_source_pkey PRIMARY KEY (lab_id);


--
-- Name: lab_test_type lab_test_type_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test_type
    ADD CONSTRAINT lab_test_type_pkey PRIMARY KEY (lab_id);


--
-- Name: ledger_refund_evidence ledger_refund_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.ledger_refund_evidence
    ADD CONSTRAINT ledger_refund_evidence_pkey PRIMARY KEY (refund_transaction_id);


--
-- Name: outpatient_cancellation_delivery outpatient_cancellation_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_delivery
    ADD CONSTRAINT outpatient_cancellation_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: outpatient_cancellation_refund_due outpatient_cancellation_refund_due_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_refund_due
    ADD CONSTRAINT outpatient_cancellation_refund_due_pkey PRIMARY KEY (source_type, source_id, original_transaction_id, charge_id);


--
-- Name: outpatient_cancellation_source outpatient_cancellation_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_source
    ADD CONSTRAINT outpatient_cancellation_source_pkey PRIMARY KEY (source_type, source_id);


--
-- Name: outpatient_execution_start outpatient_execution_start_clearance_id_source_type_source__key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_execution_start
    ADD CONSTRAINT outpatient_execution_start_clearance_id_source_type_source__key UNIQUE (clearance_id, source_type, source_id);


--
-- Name: outpatient_execution_start outpatient_execution_start_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_execution_start
    ADD CONSTRAINT outpatient_execution_start_pkey PRIMARY KEY (operation_id);


--
-- Name: outpatient_execution_start outpatient_execution_start_source_type_source_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_execution_start
    ADD CONSTRAINT outpatient_execution_start_source_type_source_id_key UNIQUE (source_type, source_id);


--
-- Name: payment_allocation payment_allocation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_allocation
    ADD CONSTRAINT payment_allocation_pkey PRIMARY KEY (allocation_id);


--
-- Name: payment_request_charge payment_request_charge_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request_charge
    ADD CONSTRAINT payment_request_charge_pkey PRIMARY KEY (payment_request_id, charge_id);


--
-- Name: payment_request payment_request_invoice_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request
    ADD CONSTRAINT payment_request_invoice_id_key UNIQUE (invoice_id);


--
-- Name: payment_request payment_request_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request
    ADD CONSTRAINT payment_request_pkey PRIMARY KEY (payment_request_id);


--
-- Name: payment_request_target payment_request_target_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request_target
    ADD CONSTRAINT payment_request_target_pkey PRIMARY KEY (payment_request_id);


--
-- Name: payment_transaction payment_transaction_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: payment_transaction payment_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_pkey PRIMARY KEY (transaction_id);


--
-- Name: prescription_charge_delivery prescription_charge_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_delivery
    ADD CONSTRAINT prescription_charge_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: prescription_charge_item prescription_charge_item_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_item
    ADD CONSTRAINT prescription_charge_item_pkey PRIMARY KEY (prescription_id, drug_id);


--
-- Name: prescription_charge_source prescription_charge_source_charge_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_charge_id_key UNIQUE (charge_id);


--
-- Name: prescription_charge_source prescription_charge_source_invoice_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_invoice_id_key UNIQUE (invoice_id);


--
-- Name: prescription_charge_source prescription_charge_source_payment_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_payment_request_id_key UNIQUE (payment_request_id);


--
-- Name: prescription_charge_source prescription_charge_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_pkey PRIMARY KEY (prescription_id);


--
-- Name: prescription_terminal_delivery prescription_terminal_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_delivery
    ADD CONSTRAINT prescription_terminal_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: prescription_terminal_refund_due prescription_terminal_refund_due_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_refund_due
    ADD CONSTRAINT prescription_terminal_refund_due_pkey PRIMARY KEY (prescription_id, original_transaction_id, charge_id);


--
-- Name: prescription_terminal_source prescription_terminal_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_source
    ADD CONSTRAINT prescription_terminal_source_pkey PRIMARY KEY (prescription_id);


--
-- Name: processed_event processed_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id);


--
-- Name: settlement settlement_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_pkey PRIMARY KEY (settlement_id);


--
-- Name: surgery_cancellation_delivery surgery_cancellation_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_delivery
    ADD CONSTRAINT surgery_cancellation_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: surgery_cancellation_refund_due surgery_cancellation_refund_due_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_refund_due
    ADD CONSTRAINT surgery_cancellation_refund_due_pkey PRIMARY KEY (cancellation_id, original_transaction_id, charge_id);


--
-- Name: surgery_cancellation_source surgery_cancellation_source_cancellation_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_source
    ADD CONSTRAINT surgery_cancellation_source_cancellation_id_key UNIQUE (cancellation_id);


--
-- Name: surgery_cancellation_source surgery_cancellation_source_first_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_source
    ADD CONSTRAINT surgery_cancellation_source_first_event_id_key UNIQUE (first_event_id);


--
-- Name: surgery_cancellation_source surgery_cancellation_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_source
    ADD CONSTRAINT surgery_cancellation_source_pkey PRIMARY KEY (surgery_case_id);


--
-- Name: surgery_charge_delivery surgery_charge_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_delivery
    ADD CONSTRAINT surgery_charge_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: surgery_charge_item surgery_charge_item_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_item
    ADD CONSTRAINT surgery_charge_item_pkey PRIMARY KEY (surgery_case_id, item_code);


--
-- Name: surgery_charge_source surgery_charge_source_payment_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_source
    ADD CONSTRAINT surgery_charge_source_payment_request_id_key UNIQUE (payment_request_id);


--
-- Name: surgery_charge_source surgery_charge_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_source
    ADD CONSTRAINT surgery_charge_source_pkey PRIMARY KEY (surgery_case_id);


--
-- Name: surgery_charge_source surgery_charge_source_surgery_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_source
    ADD CONSTRAINT surgery_charge_source_surgery_request_id_key UNIQUE (surgery_request_id);


--
-- Name: surgery_completion_delivery surgery_completion_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_delivery
    ADD CONSTRAINT surgery_completion_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: surgery_completion_source surgery_completion_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_source
    ADD CONSTRAINT surgery_completion_source_pkey PRIMARY KEY (surgery_case_id);


--
-- Name: surgery_completion_source surgery_completion_source_result_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_source
    ADD CONSTRAINT surgery_completion_source_result_id_key UNIQUE (result_id);


--
-- Name: surgery_completion_source surgery_completion_source_surgery_request_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_source
    ADD CONSTRAINT surgery_completion_source_surgery_request_id_key UNIQUE (surgery_request_id);


--
-- Name: admission_reconciliation uq_admission_reconciliation_version; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_reconciliation
    ADD CONSTRAINT uq_admission_reconciliation_version UNIQUE (account_id, settlement_version);


--
-- Name: billing_account uq_billing_account_episode; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_account
    ADD CONSTRAINT uq_billing_account_episode UNIQUE (care_episode_type, care_episode_id);


--
-- Name: charge uq_charge_source; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.charge
    ADD CONSTRAINT uq_charge_source UNIQUE (source_type, source_id, price_code);


--
-- Name: financial_clearance uq_financial_clearance_request; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_clearance
    ADD CONSTRAINT uq_financial_clearance_request UNIQUE (payment_request_id);


--
-- Name: insurance_adjustment uq_insurance_decision; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.insurance_adjustment
    ADD CONSTRAINT uq_insurance_decision UNIQUE (account_id, decision_reference, adjustment_type);


--
-- Name: payment_allocation uq_payment_allocation; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_allocation
    ADD CONSTRAINT uq_payment_allocation UNIQUE (transaction_id, charge_id);


--
-- Name: settlement uq_settlement_account_version; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT uq_settlement_account_version UNIQUE (account_id, settlement_version);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_admission_deposit_delivery_admission; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_admission_deposit_delivery_admission ON public.admission_deposit_delivery USING btree (admission_id);


--
-- Name: idx_billing_outbox_aggregate_order; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_billing_outbox_aggregate_order ON public.billing_event_outbox USING btree (aggregate_id, created_at, event_id) WHERE (published_at IS NULL);


--
-- Name: idx_billing_outbox_claimable; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_billing_outbox_claimable ON public.billing_event_outbox USING btree (available_at, created_at, event_id) WHERE ((published_at IS NULL) AND (quarantined_at IS NULL));


--
-- Name: idx_billing_outbox_quarantined; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_billing_outbox_quarantined ON public.billing_event_outbox USING btree (quarantined_at) WHERE ((published_at IS NULL) AND (quarantined_at IS NOT NULL));


--
-- Name: idx_billing_outbox_release; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_billing_outbox_release ON public.billing_event_outbox USING btree (publication_enabled, available_at) WHERE ((published_at IS NULL) AND (quarantined_at IS NULL));


--
-- Name: idx_charge_account; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_charge_account ON public.charge USING btree (account_id, status, incurred_at);


--
-- Name: idx_exam_charge_delivery_appointment; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_exam_charge_delivery_appointment ON public.exam_charge_delivery USING btree (appointment_id);


--
-- Name: idx_fee_dept; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_fee_dept ON public.fee USING btree (department_id, incurred_date);


--
-- Name: idx_fee_invoice; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_fee_invoice ON public.fee USING btree (invoice_id);


--
-- Name: idx_fee_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_fee_patient ON public.fee USING btree (patient_id, is_paid);


--
-- Name: idx_invoice_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_invoice_patient ON public.invoice USING btree (patient_id);


--
-- Name: idx_lab_test_charge_delivery_lab; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_test_charge_delivery_lab ON public.lab_test_charge_delivery USING btree (lab_id);


--
-- Name: idx_ledger_refund_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_ledger_refund_original ON public.ledger_refund_evidence USING btree (original_transaction_id);


--
-- Name: idx_outpatient_cancellation_delivery_source; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_outpatient_cancellation_delivery_source ON public.outpatient_cancellation_delivery USING btree (source_type, source_id);


--
-- Name: idx_outpatient_cancellation_refund_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_outpatient_cancellation_refund_original ON public.outpatient_cancellation_refund_due USING btree (original_transaction_id);


--
-- Name: idx_outpatient_execution_start_account; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_outpatient_execution_start_account ON public.outpatient_execution_start USING btree (account_id, started_at);


--
-- Name: idx_payment_transaction_original_completed; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_payment_transaction_original_completed ON public.payment_transaction USING btree (original_transaction_id, status, transaction_type);


--
-- Name: idx_prescription_terminal_delivery_source; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_terminal_delivery_source ON public.prescription_terminal_delivery USING btree (prescription_id);


--
-- Name: idx_prescription_terminal_refund_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_terminal_refund_original ON public.prescription_terminal_refund_due USING btree (original_transaction_id);


--
-- Name: idx_surgery_cancellation_delivery_case; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_surgery_cancellation_delivery_case ON public.surgery_cancellation_delivery USING btree (surgery_case_id);


--
-- Name: idx_surgery_cancellation_due; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_surgery_cancellation_due ON public.surgery_cancellation_source USING btree (retry_at, surgery_case_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_surgery_cancellation_refund_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_surgery_cancellation_refund_original ON public.surgery_cancellation_refund_due USING btree (original_transaction_id);


--
-- Name: idx_surgery_charge_delivery_case; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_surgery_charge_delivery_case ON public.surgery_charge_delivery USING btree (surgery_case_id);


--
-- Name: uq_fee_source; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_fee_source ON public.fee USING btree (fee_type, source_ref_id) WHERE (source_ref_id IS NOT NULL);


--
-- Name: uq_invoice_prescription; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_invoice_prescription ON public.invoice USING btree (prescription_id) WHERE (prescription_id IS NOT NULL);


--
-- Name: billing_event_outbox trg_billing_outbox_event_facts_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_outbox_event_facts_immutable BEFORE UPDATE ON public.billing_event_outbox FOR EACH ROW EXECUTE FUNCTION public.billing_outbox_event_facts_immutable();


--
-- Name: billing_event_outbox trg_billing_outbox_outpatient_fence; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_outbox_outpatient_fence BEFORE INSERT ON public.billing_event_outbox FOR EACH ROW EXECUTE FUNCTION public.billing_outbox_outpatient_fence();


--
-- Name: billing_outpatient_publication_cohort trg_billing_outpatient_cohort_no_delete; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_outpatient_cohort_no_delete BEFORE DELETE ON public.billing_outpatient_publication_cohort FOR EACH ROW EXECUTE FUNCTION public.billing_outpatient_cohort_immutable();


--
-- Name: billing_outpatient_publication_cohort trg_billing_outpatient_cohort_no_update; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_outpatient_cohort_no_update BEFORE UPDATE ON public.billing_outpatient_publication_cohort FOR EACH ROW EXECUTE FUNCTION public.billing_outpatient_cohort_immutable();


--
-- Name: billing_outpatient_refund_publication trg_billing_refund_publication_new_only; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_refund_publication_new_only BEFORE INSERT ON public.billing_outpatient_refund_publication FOR EACH ROW EXECUTE FUNCTION public.billing_refund_publication_new_fact_only();


--
-- Name: billing_outpatient_refund_publication trg_billing_refund_publication_no_delete; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_refund_publication_no_delete BEFORE DELETE ON public.billing_outpatient_refund_publication FOR EACH ROW EXECUTE FUNCTION public.billing_outpatient_cohort_immutable();


--
-- Name: billing_outpatient_refund_publication trg_billing_refund_publication_no_update; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER trg_billing_refund_publication_no_update BEFORE UPDATE ON public.billing_outpatient_refund_publication FOR EACH ROW EXECUTE FUNCTION public.billing_outpatient_cohort_immutable();


--
-- Name: admission_charge_close admission_charge_close_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close
    ADD CONSTRAINT admission_charge_close_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: admission_charge_close_delivery admission_charge_close_delivery_admission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_charge_close_delivery
    ADD CONSTRAINT admission_charge_close_delivery_admission_id_fkey FOREIGN KEY (admission_id) REFERENCES public.admission_charge_close(admission_id);


--
-- Name: admission_deposit_source admission_deposit_source_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_deposit_source
    ADD CONSTRAINT admission_deposit_source_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: admission_reconciliation admission_reconciliation_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_reconciliation
    ADD CONSTRAINT admission_reconciliation_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: admission_reconciliation admission_reconciliation_supersedes_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_reconciliation
    ADD CONSTRAINT admission_reconciliation_supersedes_settlement_id_fkey FOREIGN KEY (supersedes_settlement_id) REFERENCES public.admission_reconciliation(settlement_id);


--
-- Name: billing_event_outbox billing_event_outbox_outpatient_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_event_outbox
    ADD CONSTRAINT billing_event_outbox_outpatient_payment_request_id_fkey FOREIGN KEY (outpatient_payment_request_id) REFERENCES public.billing_outpatient_publication_cohort(payment_request_id);


--
-- Name: billing_outpatient_publication_cohort billing_outpatient_publication_cohort_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_outpatient_publication_cohort
    ADD CONSTRAINT billing_outpatient_publication_cohort_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: billing_outpatient_publication_cohort billing_outpatient_publication_cohort_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_outpatient_publication_cohort
    ADD CONSTRAINT billing_outpatient_publication_cohort_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: billing_outpatient_refund_publication billing_outpatient_refund_publicatio_refund_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_outpatient_refund_publication
    ADD CONSTRAINT billing_outpatient_refund_publicatio_refund_transaction_id_fkey FOREIGN KEY (refund_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: billing_outpatient_refund_publication billing_outpatient_refund_publication_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.billing_outpatient_refund_publication
    ADD CONSTRAINT billing_outpatient_refund_publication_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.billing_outpatient_publication_cohort(payment_request_id);


--
-- Name: charge charge_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.charge
    ADD CONSTRAINT charge_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: deposit_allocation_operation deposit_allocation_operation_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.deposit_allocation_operation
    ADD CONSTRAINT deposit_allocation_operation_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: deposit_allocation_operation deposit_allocation_operation_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.deposit_allocation_operation
    ADD CONSTRAINT deposit_allocation_operation_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: exam_charge_source exam_charge_source_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_charge_source
    ADD CONSTRAINT exam_charge_source_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: final_admission_settlement_operation final_admission_settlement_operation_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.final_admission_settlement_operation
    ADD CONSTRAINT final_admission_settlement_operation_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: final_admission_settlement_operation final_admission_settlement_operation_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.final_admission_settlement_operation
    ADD CONSTRAINT final_admission_settlement_operation_settlement_id_fkey FOREIGN KEY (settlement_id) REFERENCES public.settlement(settlement_id);


--
-- Name: financial_clearance financial_clearance_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_clearance
    ADD CONSTRAINT financial_clearance_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: financial_clearance financial_clearance_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_clearance
    ADD CONSTRAINT financial_clearance_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: admission_deposit_delivery fk_admission_deposit_delivery_admission; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_deposit_delivery
    ADD CONSTRAINT fk_admission_deposit_delivery_admission FOREIGN KEY (admission_id) REFERENCES public.admission_deposit_source(admission_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: exam_charge_delivery fk_exam_charge_delivery_appointment; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_charge_delivery
    ADD CONSTRAINT fk_exam_charge_delivery_appointment FOREIGN KEY (appointment_id) REFERENCES public.exam_charge_source(appointment_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: lab_test_charge_delivery fk_lab_test_charge_delivery_lab; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test_charge_delivery
    ADD CONSTRAINT fk_lab_test_charge_delivery_lab FOREIGN KEY (lab_id) REFERENCES public.lab_test_charge_source(lab_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: surgery_charge_delivery fk_surgery_charge_delivery_case; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_delivery
    ADD CONSTRAINT fk_surgery_charge_delivery_case FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_charge_source(surgery_case_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: insurance_adjustment insurance_adjustment_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.insurance_adjustment
    ADD CONSTRAINT insurance_adjustment_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: insurance_adjustment insurance_adjustment_original_adjustment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.insurance_adjustment
    ADD CONSTRAINT insurance_adjustment_original_adjustment_id_fkey FOREIGN KEY (original_adjustment_id) REFERENCES public.insurance_adjustment(adjustment_id);


--
-- Name: lab_test_charge_source lab_test_charge_source_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test_charge_source
    ADD CONSTRAINT lab_test_charge_source_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: ledger_refund_evidence ledger_refund_evidence_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.ledger_refund_evidence
    ADD CONSTRAINT ledger_refund_evidence_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: ledger_refund_evidence ledger_refund_evidence_refund_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.ledger_refund_evidence
    ADD CONSTRAINT ledger_refund_evidence_refund_transaction_id_fkey FOREIGN KEY (refund_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: outpatient_cancellation_delivery outpatient_cancellation_delivery_source_type_source_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_delivery
    ADD CONSTRAINT outpatient_cancellation_delivery_source_type_source_id_fkey FOREIGN KEY (source_type, source_id) REFERENCES public.outpatient_cancellation_source(source_type, source_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: outpatient_cancellation_refund_due outpatient_cancellation_refund_due_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_refund_due
    ADD CONSTRAINT outpatient_cancellation_refund_due_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: outpatient_cancellation_refund_due outpatient_cancellation_refund_due_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_refund_due
    ADD CONSTRAINT outpatient_cancellation_refund_due_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: outpatient_cancellation_refund_due outpatient_cancellation_refund_due_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_refund_due
    ADD CONSTRAINT outpatient_cancellation_refund_due_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: outpatient_cancellation_refund_due outpatient_cancellation_refund_due_source_type_source_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_cancellation_refund_due
    ADD CONSTRAINT outpatient_cancellation_refund_due_source_type_source_id_fkey FOREIGN KEY (source_type, source_id) REFERENCES public.outpatient_cancellation_source(source_type, source_id);


--
-- Name: outpatient_execution_start outpatient_execution_start_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_execution_start
    ADD CONSTRAINT outpatient_execution_start_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: outpatient_execution_start outpatient_execution_start_clearance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.outpatient_execution_start
    ADD CONSTRAINT outpatient_execution_start_clearance_id_fkey FOREIGN KEY (clearance_id) REFERENCES public.financial_clearance(clearance_id);


--
-- Name: payment_allocation payment_allocation_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_allocation
    ADD CONSTRAINT payment_allocation_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: payment_allocation payment_allocation_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_allocation
    ADD CONSTRAINT payment_allocation_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: payment_request payment_request_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request
    ADD CONSTRAINT payment_request_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: payment_request_charge payment_request_charge_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request_charge
    ADD CONSTRAINT payment_request_charge_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: payment_request_charge payment_request_charge_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request_charge
    ADD CONSTRAINT payment_request_charge_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: payment_request_target payment_request_target_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_request_target
    ADD CONSTRAINT payment_request_target_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: payment_transaction payment_transaction_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: payment_transaction payment_transaction_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: payment_transaction payment_transaction_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: prescription_charge_delivery prescription_charge_delivery_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_delivery
    ADD CONSTRAINT prescription_charge_delivery_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription_charge_source(prescription_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: prescription_charge_item prescription_charge_item_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_item
    ADD CONSTRAINT prescription_charge_item_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription_charge_source(prescription_id);


--
-- Name: prescription_charge_source prescription_charge_source_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: prescription_charge_source prescription_charge_source_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: prescription_charge_source prescription_charge_source_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_charge_source
    ADD CONSTRAINT prescription_charge_source_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: prescription_terminal_delivery prescription_terminal_delivery_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_delivery
    ADD CONSTRAINT prescription_terminal_delivery_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription_terminal_source(prescription_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: prescription_terminal_refund_due prescription_terminal_refund_due_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_refund_due
    ADD CONSTRAINT prescription_terminal_refund_due_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: prescription_terminal_refund_due prescription_terminal_refund_due_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_refund_due
    ADD CONSTRAINT prescription_terminal_refund_due_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: prescription_terminal_refund_due prescription_terminal_refund_due_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_terminal_refund_due
    ADD CONSTRAINT prescription_terminal_refund_due_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription_terminal_source(prescription_id);


--
-- Name: settlement settlement_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: settlement settlement_supersedes_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_supersedes_settlement_id_fkey FOREIGN KEY (supersedes_settlement_id) REFERENCES public.settlement(settlement_id);


--
-- Name: surgery_cancellation_delivery surgery_cancellation_delivery_surgery_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_delivery
    ADD CONSTRAINT surgery_cancellation_delivery_surgery_case_id_fkey FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_cancellation_source(surgery_case_id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: surgery_cancellation_refund_due surgery_cancellation_refund_due_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_refund_due
    ADD CONSTRAINT surgery_cancellation_refund_due_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: surgery_cancellation_refund_due surgery_cancellation_refund_due_cancellation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_refund_due
    ADD CONSTRAINT surgery_cancellation_refund_due_cancellation_id_fkey FOREIGN KEY (cancellation_id) REFERENCES public.surgery_cancellation_source(cancellation_id);


--
-- Name: surgery_cancellation_refund_due surgery_cancellation_refund_due_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_refund_due
    ADD CONSTRAINT surgery_cancellation_refund_due_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: surgery_cancellation_refund_due surgery_cancellation_refund_due_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_cancellation_refund_due
    ADD CONSTRAINT surgery_cancellation_refund_due_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.payment_transaction(transaction_id);


--
-- Name: surgery_charge_item surgery_charge_item_charge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_item
    ADD CONSTRAINT surgery_charge_item_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES public.charge(charge_id);


--
-- Name: surgery_charge_item surgery_charge_item_surgery_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_item
    ADD CONSTRAINT surgery_charge_item_surgery_case_id_fkey FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_charge_source(surgery_case_id);


--
-- Name: surgery_charge_source surgery_charge_source_payment_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_charge_source
    ADD CONSTRAINT surgery_charge_source_payment_request_id_fkey FOREIGN KEY (payment_request_id) REFERENCES public.payment_request(payment_request_id);


--
-- Name: surgery_completion_delivery surgery_completion_delivery_surgery_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_delivery
    ADD CONSTRAINT surgery_completion_delivery_surgery_case_id_fkey FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_completion_source(surgery_case_id);


--
-- Name: surgery_completion_source surgery_completion_source_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_source
    ADD CONSTRAINT surgery_completion_source_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.billing_account(account_id);


--
-- Name: surgery_completion_source surgery_completion_source_surgery_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_completion_source
    ADD CONSTRAINT surgery_completion_source_surgery_case_id_fkey FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_charge_source(surgery_case_id);


--
-- PostgreSQL database dump complete
--

\unrestrict D7gjs5DlGaFPkXVVtj0p7Zo3xQa8WUR9fvmcGmqsOG85CugiVhLhocO7SjqVHoW

--
-- Database "mediflow_clinical" dump
--

--
-- PostgreSQL database dump
--

\restrict gzbUtCryXbWYh8f8TpQuDlK0oAxJAWIXxsqrcETw2YuWQrSMlXbtfGd2eHy7N8B

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_clinical; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_clinical WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_clinical OWNER TO test;

\unrestrict gzbUtCryXbWYh8f8TpQuDlK0oAxJAWIXxsqrcETw2YuWQrSMlXbtfGd2eHy7N8B
\connect mediflow_clinical
\restrict gzbUtCryXbWYh8f8TpQuDlK0oAxJAWIXxsqrcETw2YuWQrSMlXbtfGd2eHy7N8B

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admission_referral; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_referral (
    admission_request_id uuid NOT NULL,
    record_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    diagnosis_summary text NOT NULL,
    priority character varying(20) NOT NULL,
    emergency boolean DEFAULT false NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.admission_referral OWNER TO test;

--
-- Name: appointment; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.appointment (
    appointment_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    department_id uuid NOT NULL,
    appointment_date date NOT NULL,
    appointment_time time without time zone NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    care_contract_version smallint DEFAULT 0 NOT NULL,
    exam_clearance_id uuid,
    exam_clearance_at timestamp with time zone,
    emergency_override_id uuid,
    exam_price_code character varying(64),
    checked_in_at timestamp with time zone,
    examination_started_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_by uuid,
    cancellation_reason text,
    cancelled_at timestamp with time zone,
    CONSTRAINT ck_appointment_contract_version CHECK ((care_contract_version = ANY (ARRAY[0, 1]))),
    CONSTRAINT ck_appointment_exam_gate CHECK (((care_contract_version = 0) OR ((status)::text <> ALL (ARRAY[('READY_FOR_EXAM'::character varying)::text, ('IN_EXAM'::character varying)::text, ('COMPLETED'::character varying)::text])) OR (exam_clearance_id IS NOT NULL) OR (emergency_override_id IS NOT NULL))),
    CONSTRAINT ck_appointment_v1_cancellation_audit CHECK ((((care_contract_version = 1) AND ((status)::text = 'CANCELLED'::text) AND (cancelled_by IS NOT NULL) AND (cancellation_reason IS NOT NULL) AND (btrim(cancellation_reason) <> ''::text) AND (char_length(cancellation_reason) <= 500) AND (cancelled_at IS NOT NULL) AND (cancelled_at >= created_at)) OR (((care_contract_version <> 1) OR ((status)::text <> 'CANCELLED'::text)) AND (cancelled_by IS NULL) AND (cancellation_reason IS NULL) AND (cancelled_at IS NULL)))),
    CONSTRAINT ck_appointment_v1_price CHECK (((care_contract_version = 0) OR (exam_price_code IS NOT NULL)))
);


ALTER TABLE public.appointment OWNER TO test;

--
-- Name: attached_result; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.attached_result (
    record_id uuid NOT NULL,
    type character varying(20) NOT NULL,
    reference_id uuid NOT NULL,
    summary text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT attached_result_type_check CHECK (((type)::text = ANY (ARRAY[('LAB'::character varying)::text, ('PRESCRIPTION'::character varying)::text])))
);


ALTER TABLE public.attached_result OWNER TO test;

--
-- Name: clinical_emergency_override; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.clinical_emergency_override (
    override_id uuid NOT NULL,
    appointment_id uuid,
    record_id uuid,
    patient_id uuid NOT NULL,
    care_episode_id uuid NOT NULL,
    approved_by uuid NOT NULL,
    approver_role character varying(32) NOT NULL,
    reason text NOT NULL,
    approved_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_clinical_override_target CHECK (((appointment_id IS NOT NULL) OR (record_id IS NOT NULL)))
);


ALTER TABLE public.clinical_emergency_override OWNER TO test;

--
-- Name: clinical_lab_order; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.clinical_lab_order (
    source_order_id uuid NOT NULL,
    record_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    requesting_doctor_id uuid NOT NULL,
    requesting_department_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    lab_type character varying(50) NOT NULL,
    requested_date date NOT NULL,
    price_code character varying(64) NOT NULL,
    ordered_at timestamp with time zone NOT NULL,
    CONSTRAINT clinical_lab_order_care_episode_type_check CHECK (((care_episode_type)::text = 'OUTPATIENT_VISIT'::text)),
    CONSTRAINT clinical_lab_order_lab_type_check CHECK ((length(btrim((lab_type)::text)) > 0)),
    CONSTRAINT clinical_lab_order_price_code_check CHECK ((length(btrim((price_code)::text)) > 0))
);


ALTER TABLE public.clinical_lab_order OWNER TO test;

--
-- Name: clinical_outbox_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.clinical_outbox_event (
    event_id uuid NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type character varying(100) NOT NULL,
    event_version integer NOT NULL,
    correlation_id character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    retry_count integer DEFAULT 0 NOT NULL,
    payload_bytes bytea,
    held_at timestamp with time zone,
    hold_reason character varying(255),
    quarantined_at timestamp with time zone,
    quarantine_reason character varying(255)
);


ALTER TABLE public.clinical_outbox_event OWNER TO test;

--
-- Name: consumer_inbox_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.consumer_inbox_delivery (
    consumer_name character varying(64) NOT NULL,
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    operation_key character varying(512) NOT NULL,
    raw_body bytea NOT NULL,
    raw_sha256 character varying(64) NOT NULL,
    canonical_business_bytes bytea NOT NULL,
    canonical_sha256 character varying(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT consumer_inbox_delivery_canonical_business_bytes_check CHECK (((octet_length(canonical_business_bytes) >= 1) AND (octet_length(canonical_business_bytes) <= 1048576))),
    CONSTRAINT consumer_inbox_delivery_canonical_sha256_check CHECK (((canonical_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT consumer_inbox_delivery_raw_body_check CHECK (((octet_length(raw_body) >= 1) AND (octet_length(raw_body) <= 1048576))),
    CONSTRAINT consumer_inbox_delivery_raw_sha256_check CHECK (((raw_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.consumer_inbox_delivery OWNER TO test;

--
-- Name: consumer_inbox_operation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.consumer_inbox_operation (
    consumer_name character varying(64) NOT NULL,
    operation_key character varying(512) NOT NULL,
    first_event_id uuid NOT NULL,
    canonical_business_bytes bytea NOT NULL,
    canonical_sha256 character varying(64) NOT NULL,
    applied_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT consumer_inbox_operation_canonical_business_bytes_check CHECK (((octet_length(canonical_business_bytes) >= 1) AND (octet_length(canonical_business_bytes) <= 1048576))),
    CONSTRAINT consumer_inbox_operation_canonical_sha256_check CHECK (((canonical_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.consumer_inbox_operation OWNER TO test;

--
-- Name: diagnosis; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.diagnosis (
    diagnosis_id uuid NOT NULL,
    record_id uuid NOT NULL,
    diagnosis_name character varying(255) NOT NULL,
    description text,
    icd_code character varying(10),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.diagnosis OWNER TO test;

--
-- Name: exam_clearance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.exam_clearance (
    clearance_id uuid NOT NULL,
    event_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    account_id uuid NOT NULL,
    appointment_id uuid,
    record_id uuid,
    patient_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    expires_at timestamp with time zone,
    emergency_override boolean DEFAULT false NOT NULL,
    granted_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_exam_clearance_target CHECK (((appointment_id IS NOT NULL) OR (record_id IS NOT NULL))),
    CONSTRAINT exam_clearance_amount_check CHECK ((amount >= (0)::numeric)),
    CONSTRAINT exam_clearance_care_episode_type_check CHECK (((care_episode_type)::text = 'OUTPATIENT_VISIT'::text))
);


ALTER TABLE public.exam_clearance OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: medical_record; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.medical_record (
    record_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    department_id uuid NOT NULL,
    examination_date date NOT NULL,
    symptoms text,
    appointment_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    disposition character varying(32),
    disposition_note text,
    completed_at timestamp with time zone,
    CONSTRAINT ck_medical_record_completion CHECK (((((status)::text = 'OPEN'::text) AND (completed_at IS NULL)) OR (((status)::text = 'COMPLETED'::text) AND (disposition IS NOT NULL) AND (completed_at IS NOT NULL)))),
    CONSTRAINT ck_medical_record_status CHECK (((status)::text = ANY (ARRAY[('OPEN'::character varying)::text, ('COMPLETED'::character varying)::text])))
);


ALTER TABLE public.medical_record OWNER TO test;

--
-- Name: processed_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.processed_event OWNER TO test;

--
-- Data for Name: admission_referral; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_referral (admission_request_id, record_id, patient_id, department_id, requested_by, diagnosis_summary, priority, emergency, requested_at, created_at) FROM stdin;
\.


--
-- Data for Name: appointment; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.appointment (appointment_id, patient_id, doctor_id, department_id, appointment_date, appointment_time, status, reason, created_at, updated_at, care_contract_version, exam_clearance_id, exam_clearance_at, emergency_override_id, exam_price_code, checked_in_at, examination_started_at, completed_at, cancelled_by, cancellation_reason, cancelled_at) FROM stdin;
11935f4f-6428-4051-ba9e-8e8a1b7d9f11	c36277c4-347f-480d-8473-bcf9f98c8a44	ddf8f66b-7b09-43e0-8e24-dd24678c3414	b134eebd-d327-4718-8bef-92525164178d	2026-10-23	09:00:00	IN_EXAM	Packaged EXAM acceptance	2026-10-22 00:40:15.690614+00	2026-10-22 00:40:17.82253+00	1	86de69c5-4b2e-4458-83e0-6677efc159aa	2026-10-22 00:40:16.917046+00	\N	OUTPATIENT_EXAM	2026-10-22 00:40:15.984936+00	2026-10-22 00:40:17.82253+00	\N	\N	\N	\N
13be7899-c362-4232-acd9-ad6e751a5296	87674766-119a-46ed-b4db-2059be4cd59c	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	10:30:00	PENDING	Đau vùng thượng vị	2026-10-22 00:40:38.040381+00	\N	0	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
0bd51db1-eda6-432e-a306-790ff7e4ad20	579e6d5b-1f63-43ce-bd2a-609fdf508481	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	10:45:00	PENDING	Kiểm tra đường huyết	2026-10-22 00:40:38.108353+00	\N	0	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
71626749-ef62-42f4-9d40-35097f05dfb9	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	08:00:00	IN_EXAM	Khám sức khỏe định kỳ	2026-10-22 00:40:37.153591+00	2026-10-22 00:40:40.626899+00	1	49f69633-3e1f-4ef3-ab1e-c7b344ab174a	2026-10-22 00:40:39.521339+00	\N	OUTPATIENT_EXAM	2026-10-22 00:40:38.443302+00	2026-10-22 00:40:40.626899+00	\N	\N	\N	\N
eab4cb0a-de3f-495c-9304-73ff1f18b5d4	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	08:15:00	IN_EXAM	Đau đầu và mệt mỏi ba ngày	2026-10-22 00:40:37.227748+00	2026-10-22 00:44:58.104163+00	1	b9ec057b-c3a9-45b5-9e95-36109f530bc1	2026-10-22 00:44:56.990696+00	\N	OUTPATIENT_EXAM	2026-10-22 00:44:55.899375+00	2026-10-22 00:44:58.104163+00	\N	\N	\N	\N
037fcbd9-127b-44d6-96ee-73c571763c5e	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	08:30:00	IN_EXAM	Ho và đau họng	2026-10-22 00:40:37.337674+00	2026-10-22 00:45:40.038426+00	1	db1a5449-6c73-4772-8791-54039cb8111e	2026-10-22 00:45:38.928545+00	\N	OUTPATIENT_EXAM	2026-10-22 00:45:37.87203+00	2026-10-22 00:45:40.038426+00	\N	\N	\N	\N
250352fd-6ae9-4108-a147-2d76e9b49eb9	6f75b635-a491-4191-99b3-1df16c1a3832	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	08:45:00	IN_EXAM	Tái khám huyết áp	2026-10-22 00:40:37.416886+00	2026-10-22 00:45:48.936871+00	1	33e1d0e6-f85f-450b-9813-41a6e5b56cf9	2026-10-22 00:45:47.836519+00	\N	OUTPATIENT_EXAM	2026-10-22 00:45:46.741006+00	2026-10-22 00:45:48.936871+00	\N	\N	\N	\N
a4fde265-3562-4528-8ea6-0db0e835c1d6	1270368c-7e70-40eb-852c-b58d7b052cf7	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	09:00:00	READY_FOR_EXAM	Đau vùng thượng vị	2026-10-22 00:40:37.493487+00	2026-10-22 00:45:52.492536+00	1	fa6622b0-d820-4c4e-9bf6-26671394453b	2026-10-22 00:45:52.492536+00	\N	OUTPATIENT_EXAM	2026-10-22 00:45:49.368883+00	\N	\N	\N	\N	\N
b264777a-2a76-4793-b4ac-91e6a8779f0d	3ad6444d-54b2-467a-9e25-beb664d3975f	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	09:30:00	READY_FOR_EXAM	Khám sức khỏe định kỳ	2026-10-22 00:40:37.68966+00	2026-10-22 00:52:51.000335+00	1	76283443-9e57-4e50-b63c-ede3cac9664d	2026-10-22 00:52:51.000335+00	\N	OUTPATIENT_EXAM	2026-10-22 00:50:46.53268+00	\N	\N	\N	\N	\N
74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	83ee7cd5-ccb4-4005-96f3-42abb0261400	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	09:15:00	READY_FOR_EXAM	Kiểm tra đường huyết	2026-10-22 00:40:37.598505+00	2026-10-22 00:56:55.535559+00	1	0c7cc504-61b9-4f1d-ac04-fda127f2cb86	2026-10-22 00:56:55.535559+00	\N	OUTPATIENT_EXAM	2026-10-22 00:45:54.593407+00	\N	\N	\N	\N	\N
b9de10f8-44e8-4e44-8637-09135b5691e8	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	09:45:00	READY_FOR_EXAM	Đau đầu và mệt mỏi ba ngày	2026-10-22 00:40:37.788751+00	2026-10-22 10:53:22.794782+00	1	80f4ed5d-2194-4056-be84-bd056d4e1599	2026-10-22 10:53:22.794782+00	\N	OUTPATIENT_EXAM	2026-10-22 10:49:28.989701+00	\N	\N	\N	\N	\N
f91a4cad-6e7a-4b23-9f3f-08187f1687ec	a2c54b05-3010-4b8d-93ee-67fb06469d77	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	10:00:00	READY_FOR_EXAM	Ho và đau họng	2026-10-22 00:40:37.855624+00	2026-10-22 11:41:34.165632+00	1	fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	2026-10-22 11:41:34.165632+00	\N	OUTPATIENT_EXAM	2026-10-22 11:37:33.301207+00	\N	\N	\N	\N	\N
52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	10:15:00	AWAITING_PAYMENT	Tái khám huyết áp	2026-10-22 00:40:37.946383+00	2026-10-22 11:56:40.509471+00	1	\N	\N	\N	OUTPATIENT_EXAM	2026-10-22 11:56:40.509471+00	\N	\N	\N	\N	\N
\.


--
-- Data for Name: attached_result; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.attached_result (record_id, type, reference_id, summary, created_at) FROM stdin;
4447ad07-fe64-4d41-8c96-7a278d167be2	PRESCRIPTION	84116e5c-b8d2-4afa-a586-0c2af1c350ab	\N	2026-10-22 00:45:35.427995+00
1361a316-8aa3-4ad4-8774-f852d8c64fa1	PRESCRIPTION	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	\N	2026-10-22 00:52:18.003759+00
\.


--
-- Data for Name: clinical_emergency_override; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.clinical_emergency_override (override_id, appointment_id, record_id, patient_id, care_episode_id, approved_by, approver_role, reason, approved_at, created_at) FROM stdin;
\.


--
-- Data for Name: clinical_lab_order; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.clinical_lab_order (source_order_id, record_id, patient_id, requesting_doctor_id, requesting_department_id, care_episode_type, care_episode_id, lab_type, requested_date, price_code, ordered_at) FROM stdin;
520799cf-ade0-4ceb-a7a9-ab82f7e88941	5917d49c-0715-416d-a482-c0b281926b43	c36277c4-347f-480d-8473-bcf9f98c8a44	ddf8f66b-7b09-43e0-8e24-dd24678c3414	b134eebd-d327-4718-8bef-92525164178d	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	CBC	2026-10-22	LAB	2026-10-22 00:40:18.55571+00
bfb6c454-9d0f-4777-9460-eea5721d50f8	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	CBC	2026-10-22	LAB	2026-10-22 00:40:40.882341+00
08f32690-ca20-4c8c-b075-d9a019e281ad	1361a316-8aa3-4ad4-8774-f852d8c64fa1	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	CBC	2026-10-22	LAB	2026-10-22 00:44:58.290045+00
1c25abad-5ea6-4e62-b72a-a9f5176e29f6	acc4a82c-6856-469d-80be-8dc343a9e06e	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	CBC	2026-10-22	LAB	2026-10-22 00:45:40.236448+00
7d0abb3e-4dc3-4f47-956a-07abf29bceeb	1d4e99c7-3572-42be-9520-a43c1eedc030	6f75b635-a491-4191-99b3-1df16c1a3832	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	CBC	2026-10-22	LAB	2026-10-22 00:45:49.102418+00
53ed2165-8991-493e-bc9b-0067d26283b9	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	GLU	2026-10-22	LAB	2026-10-22 00:49:04.888566+00
949b80c3-9e1d-4db3-96d8-7deab833a587	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	Công thức máu kiểm tra Review3	2026-10-22	LAB_CBC	2026-10-22 10:44:09.526253+00
c49301f4-75fa-49d9-a327-7fd17164bcb8	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	CRP theo dõi Review3	2026-10-22	LAB	2026-10-22 10:57:03.003037+00
\.


--
-- Data for Name: clinical_outbox_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.clinical_outbox_event (event_id, aggregate_type, aggregate_id, event_type, event_version, correlation_id, payload, occurred_at, published_at, retry_count, payload_bytes, held_at, hold_reason, quarantined_at, quarantine_reason) FROM stdin;
61d7d6fc-83f0-4f36-a1ad-3802e35a40de	APPOINTMENT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	appointment.created	0	68002a8d-064c-42a7-8571-254bd4062e64	{"eventId": "61d7d6fc-83f0-4f36-a1ad-3802e35a40de", "doctorId": "ddf8f66b-7b09-43e0-8e24-dd24678c3414", "patientId": "c36277c4-347f-480d-8473-bcf9f98c8a44", "occurredAt": 1792629615.751547, "departmentId": "b134eebd-d327-4718-8bef-92525164178d", "appointmentId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "correlationId": "68002a8d-064c-42a7-8571-254bd4062e64", "appointmentDate": [2026, 10, 23], "appointmentTime": "09:00"}	2026-10-22 00:40:15.751547+00	2026-10-22 00:40:16.027141+00	0	\\x7b226576656e744964223a2236316437643666632d383366302d346633362d613161642d333830326533356134306465222c226f636375727265644174223a313739323632393631352e3735313534373230302c22636f7272656c6174696f6e4964223a2236383030326138642d303634632d343261372d383537312d323534626434303632653634222c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c22646f63746f724964223a2264646638663636622d376230392d343365302d386532342d646432343637386333343134222c226465706172746d656e744964223a2262313334656562642d643332372d343731382d386265662d393235323531363431373864222c226170706f696e746d656e7444617465223a5b323032362c31302c32335d2c226170706f696e746d656e7454696d65223a2230393a3030227d	\N	\N	\N	\N
86cf892c-01ee-4f3d-a21a-8423b53d7156	APPOINTMENT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	appointment.status.changed	1	c4db6604-4691-44ba-8e3c-665fe61048f0	{"eventId": "86cf892c-01ee-4f3d-a21a-8423b53d7156", "payload": {"recordId": null, "sourceId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "changedAt": "2026-10-22T00:40:15.984936300Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "c36277c4-347f-480d-8473-bcf9f98c8a44", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "b134eebd-d327-4718-8bef-92525164178d", "appointmentId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:40:15.984936300Z", "correlationId": "c4db6604-4691-44ba-8e3c-665fe61048f0"}	2026-10-22 00:40:15.984936+00	2026-10-22 00:40:16.309082+00	0	\N	\N	\N	\N	\N
413e95da-185e-4d06-b14a-ef7fe38d9e9f	APPOINTMENT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "413e95da-185e-4d06-b14a-ef7fe38d9e9f", "payload": {"recordId": null, "sourceId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "changedAt": "2026-10-22T00:40:17.378874600Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "c36277c4-347f-480d-8473-bcf9f98c8a44", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "b134eebd-d327-4718-8bef-92525164178d", "appointmentId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:40:17.378874600Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:40:17.378875+00	2026-10-22 00:40:17.83496+00	0	\N	\N	\N	\N	\N
005b9f98-3f8c-4698-ac48-d7b62ab056ec	MEDICAL_RECORD	5917d49c-0715-416d-a482-c0b281926b43	medicalrecord.created	1	73251b16-f096-446b-8326-3f4f8af7c960	{"eventId": "005b9f98-3f8c-4698-ac48-d7b62ab056ec", "payload": {"doctorId": "ddf8f66b-7b09-43e0-8e24-dd24678c3414", "recordId": "5917d49c-0715-416d-a482-c0b281926b43", "sourceId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "patientId": "c36277c4-347f-480d-8473-bcf9f98c8a44", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "b134eebd-d327-4718-8bef-92525164178d", "appointmentId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeType": "OUTPATIENT_VISIT", "examinationDate": "2026-10-22"}, "version": 1, "producer": "clinical-service", "eventType": "medicalrecord.created", "occurredAt": "2026-10-22T00:40:17.822529700Z", "correlationId": "73251b16-f096-446b-8326-3f4f8af7c960"}	2026-10-22 00:40:17.82253+00	2026-10-22 00:40:18.217409+00	0	\N	\N	\N	\N	\N
4c00089a-93a6-43a0-a2f9-40452b748bb8	APPOINTMENT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	appointment.status.changed	1	73251b16-f096-446b-8326-3f4f8af7c960	{"eventId": "4c00089a-93a6-43a0-a2f9-40452b748bb8", "payload": {"recordId": "5917d49c-0715-416d-a482-c0b281926b43", "sourceId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "changedAt": "2026-10-22T00:40:17.822529700Z", "newStatus": "IN_EXAM", "oldStatus": "READY_FOR_EXAM", "patientId": "c36277c4-347f-480d-8473-bcf9f98c8a44", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "b134eebd-d327-4718-8bef-92525164178d", "appointmentId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeId": "11935f4f-6428-4051-ba9e-8e8a1b7d9f11", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:40:17.822529700Z", "correlationId": "73251b16-f096-446b-8326-3f4f8af7c960"}	2026-10-22 00:40:17.82253+00	2026-10-22 00:40:18.217932+00	0	\N	\N	\N	\N	\N
e2350514-840e-4234-9c2a-09c21138ca73	APPOINTMENT	71626749-ef62-42f4-9d40-35097f05dfb9	appointment.created	0	ce71c1b3-a7db-4065-bb05-4d8ddffae0c2	{"eventId": "e2350514-840e-4234-9c2a-09c21138ca73", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "occurredAt": 1792629637.1578388, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "71626749-ef62-42f4-9d40-35097f05dfb9", "correlationId": "ce71c1b3-a7db-4065-bb05-4d8ddffae0c2", "appointmentDate": [2026, 10, 22], "appointmentTime": "08:00"}	2026-10-22 00:40:37.157839+00	2026-10-22 00:40:37.343637+00	0	\\x7b226576656e744964223a2265323335303531342d383430652d343233342d396332612d303963323131333863613733222c226f636375727265644174223a313739323632393633372e3135373833383930302c22636f7272656c6174696f6e4964223a2263653731633162332d613764622d343036352d626230352d346438646466666165306332222c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230383a3030227d	\N	\N	\N	\N
fa0b5171-f12b-4b54-a168-1c3c8726aff2	APPOINTMENT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	appointment.created	0	d3514f8a-bbcc-41bf-b9ef-7db06cca3b6a	{"eventId": "fa0b5171-f12b-4b54-a168-1c3c8726aff2", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "8dd25d75-6d29-4967-ac6e-d54c6a8dd490", "occurredAt": 1792629637.2324598, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "correlationId": "d3514f8a-bbcc-41bf-b9ef-7db06cca3b6a", "appointmentDate": [2026, 10, 22], "appointmentTime": "08:15"}	2026-10-22 00:40:37.23246+00	2026-10-22 00:40:37.344176+00	0	\\x7b226576656e744964223a2266613062353137312d663132622d346235342d613136382d316333633837323661666632222c226f636375727265644174223a313739323632393633372e3233323435393830302c22636f7272656c6174696f6e4964223a2264333531346638612d626263632d343162662d623965662d376462303663636133623661222c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230383a3135227d	\N	\N	\N	\N
8c340260-4e7c-4c42-86dc-650988320669	APPOINTMENT	037fcbd9-127b-44d6-96ee-73c571763c5e	appointment.created	0	3ad14e0e-47cb-44fa-8990-01c29b76401b	{"eventId": "8c340260-4e7c-4c42-86dc-650988320669", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "8fbf50e4-67ca-42f6-96ac-c15d6aab61c8", "occurredAt": 1792629637.3431137, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "correlationId": "3ad14e0e-47cb-44fa-8990-01c29b76401b", "appointmentDate": [2026, 10, 22], "appointmentTime": "08:30"}	2026-10-22 00:40:37.343114+00	2026-10-22 00:40:37.654798+00	0	\\x7b226576656e744964223a2238633334303236302d346537632d346334322d383664632d363530393838333230363639222c226f636375727265644174223a313739323632393633372e3334333131333730302c22636f7272656c6174696f6e4964223a2233616431346530652d343763622d343466612d383939302d303163323962373634303162222c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230383a3330227d	\N	\N	\N	\N
e8a679a9-b485-443c-bdce-c2671f9441b1	APPOINTMENT	a4fde265-3562-4528-8ea6-0db0e835c1d6	appointment.created	0	91449e57-c1be-42d4-bc94-e625e7bce8fc	{"eventId": "e8a679a9-b485-443c-bdce-c2671f9441b1", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "1270368c-7e70-40eb-852c-b58d7b052cf7", "occurredAt": 1792629637.4991367, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "correlationId": "91449e57-c1be-42d4-bc94-e625e7bce8fc", "appointmentDate": [2026, 10, 22], "appointmentTime": "09:00"}	2026-10-22 00:40:37.499137+00	2026-10-22 00:40:37.655333+00	0	\\x7b226576656e744964223a2265386136373961392d623438352d343433632d626463652d633236373166393434316231222c226f636375727265644174223a313739323632393633372e3439393133363830302c22636f7272656c6174696f6e4964223a2239313434396535372d633162652d343264342d626339342d653632356537626365386663222c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230393a3030227d	\N	\N	\N	\N
914da36b-2591-43a9-b1cd-5f3299b77153	APPOINTMENT	71626749-ef62-42f4-9d40-35097f05dfb9	appointment.status.changed	1	725fd934-5e1a-4dc3-9a7c-16496daadd6b	{"eventId": "914da36b-2591-43a9-b1cd-5f3299b77153", "payload": {"recordId": "4447ad07-fe64-4d41-8c96-7a278d167be2", "sourceId": "71626749-ef62-42f4-9d40-35097f05dfb9", "changedAt": "2026-10-22T00:40:40.626899400Z", "newStatus": "IN_EXAM", "oldStatus": "READY_FOR_EXAM", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:40:40.626899400Z", "correlationId": "725fd934-5e1a-4dc3-9a7c-16496daadd6b"}	2026-10-22 00:40:40.626899+00	2026-10-22 00:40:40.969399+00	0	\N	\N	\N	\N	\N
e8177c6d-c3e7-469a-b9a5-bcb6affe790c	MEDICAL_RECORD	4447ad07-fe64-4d41-8c96-7a278d167be2	medicalrecord.created	1	725fd934-5e1a-4dc3-9a7c-16496daadd6b	{"eventId": "e8177c6d-c3e7-469a-b9a5-bcb6affe790c", "payload": {"doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "recordId": "4447ad07-fe64-4d41-8c96-7a278d167be2", "sourceId": "71626749-ef62-42f4-9d40-35097f05dfb9", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeType": "OUTPATIENT_VISIT", "examinationDate": "2026-10-22"}, "version": 1, "producer": "clinical-service", "eventType": "medicalrecord.created", "occurredAt": "2026-10-22T00:40:40.626899400Z", "correlationId": "725fd934-5e1a-4dc3-9a7c-16496daadd6b"}	2026-10-22 00:40:40.626899+00	2026-10-22 00:40:40.96994+00	0	\N	\N	\N	\N	\N
475a1403-248a-440e-9dcc-c796ec0cfac1	MEDICAL_RECORD	acc4a82c-6856-469d-80be-8dc343a9e06e	diagnosis.added	0	d3e8d23a-f8b4-4c85-926e-1cc3dc026f55	{"eventId": "475a1403-248a-440e-9dcc-c796ec0cfac1", "recordId": "acc4a82c-6856-469d-80be-8dc343a9e06e", "occurredAt": 1792629940.1967661, "correlationId": "d3e8d23a-f8b4-4c85-926e-1cc3dc026f55", "diagnosisCode": "J06.9", "diagnosisName": "Theo dõi nhiễm khuẩn hô hấp trên"}	2026-10-22 00:45:40.196766+00	\N	5	\\x7b226576656e744964223a2234373561313430332d323438612d343430652d396463632d633739366563306366616331222c226f636375727265644174223a313739323632393934302e3139363736363230302c22636f7272656c6174696f6e4964223a2264336538643233612d663862342d346338352d393236652d316363336463303236663535222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c22646961676e6f736973436f6465223a224a30362e39222c22646961676e6f7369734e616d65223a225468656f2064c3b569206e6869e1bb856d206b6875e1baa96e2068c3b42068e1baa570207472c3aa6e227d	\N	\N	2026-10-22 00:45:41.46973+00	Maximum Clinical outbox publish retries reached
6214f2ae-0a06-4198-bbe0-3006e57b8e14	APPOINTMENT	250352fd-6ae9-4108-a147-2d76e9b49eb9	appointment.status.changed	1	c1a3572e-e03b-4b9e-99ee-ed73c1d97a18	{"eventId": "6214f2ae-0a06-4198-bbe0-3006e57b8e14", "payload": {"recordId": null, "sourceId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "changedAt": "2026-10-22T00:45:46.741005900Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "6f75b635-a491-4191-99b3-1df16c1a3832", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:46.741005900Z", "correlationId": "c1a3572e-e03b-4b9e-99ee-ed73c1d97a18"}	2026-10-22 00:45:46.741006+00	2026-10-22 00:45:46.782247+00	0	\N	\N	\N	\N	\N
af68af8d-bb51-49e7-9504-32c774839d59	APPOINTMENT	250352fd-6ae9-4108-a147-2d76e9b49eb9	appointment.created	0	8809aa84-d023-4001-bcbe-e45153b7a70f	{"eventId": "af68af8d-bb51-49e7-9504-32c774839d59", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "6f75b635-a491-4191-99b3-1df16c1a3832", "occurredAt": 1792629637.4205673, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "correlationId": "8809aa84-d023-4001-bcbe-e45153b7a70f", "appointmentDate": [2026, 10, 22], "appointmentTime": "08:45"}	2026-10-22 00:40:37.420567+00	2026-10-22 00:40:37.655333+00	0	\\x7b226576656e744964223a2261663638616638642d626235312d343965372d393530342d333263373734383339643539222c226f636375727265644174223a313739323632393633372e3432303536373330302c22636f7272656c6174696f6e4964223a2238383039616138342d643032332d343030312d626362652d653435313533623761373066222c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230383a3435227d	\N	\N	\N	\N
43b3152e-7663-41db-9a01-4d8765cb1ebc	APPOINTMENT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	appointment.created	0	8b532c9d-6bfe-4700-a61b-545a59279168	{"eventId": "43b3152e-7663-41db-9a01-4d8765cb1ebc", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "83ee7cd5-ccb4-4005-96f3-42abb0261400", "occurredAt": 1792629637.6038606, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "correlationId": "8b532c9d-6bfe-4700-a61b-545a59279168", "appointmentDate": [2026, 10, 22], "appointmentTime": "09:15"}	2026-10-22 00:40:37.603861+00	2026-10-22 00:40:37.655866+00	0	\\x7b226576656e744964223a2234336233313532652d373636332d343164622d396130312d346438373635636231656263222c226f636375727265644174223a313739323632393633372e3630333836303630302c22636f7272656c6174696f6e4964223a2238623533326339642d366266652d343730302d613631622d353435613539323739313638222c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230393a3135227d	\N	\N	\N	\N
82002f94-612d-4036-8ba2-17b56423a022	APPOINTMENT	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	appointment.created	0	8fd8fa48-0051-4c38-91fa-578ac2a981bd	{"eventId": "82002f94-612d-4036-8ba2-17b56423a022", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8", "occurredAt": 1792629637.9516926, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "52c8e6d2-4a81-4677-838d-ea6d7c1c63d9", "correlationId": "8fd8fa48-0051-4c38-91fa-578ac2a981bd", "appointmentDate": [2026, 10, 22], "appointmentTime": "10:15"}	2026-10-22 00:40:37.951693+00	2026-10-22 00:40:38.246331+00	0	\\x7b226576656e744964223a2238323030326639342d363132642d343033362d386261322d313762353634323361303232222c226f636375727265644174223a313739323632393633372e3935313639323630302c22636f7272656c6174696f6e4964223a2238666438666134382d303035312d346333382d393166612d353738616332613938316264222c226170706f696e746d656e744964223a2235326338653664322d346138312d343637372d383338642d656136643763316336336439222c2270617469656e744964223a2262336332623663312d613862652d346238632d383362362d636365636563383064386238222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2231303a3135227d	\N	\N	\N	\N
b23c40cf-32c6-41f5-9b5a-b230aaa0775f	APPOINTMENT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "b23c40cf-32c6-41f5-9b5a-b230aaa0775f", "payload": {"recordId": null, "sourceId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "changedAt": "2026-10-22T00:44:57.900923500Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "8dd25d75-6d29-4967-ac6e-d54c6a8dd490", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:44:57.900923500Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:44:57.900924+00	2026-10-22 00:44:58.150499+00	0	\N	\N	\N	\N	\N
8d0245d5-8e7d-400b-b50f-f866f45e6cb4	APPOINTMENT	250352fd-6ae9-4108-a147-2d76e9b49eb9	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "8d0245d5-8e7d-400b-b50f-f866f45e6cb4", "payload": {"recordId": null, "sourceId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "changedAt": "2026-10-22T00:45:47.973127300Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "6f75b635-a491-4191-99b3-1df16c1a3832", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:47.973127300Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:45:47.973127+00	2026-10-22 00:45:48.119682+00	0	\N	\N	\N	\N	\N
2b4d1827-b801-40fd-aa05-6c6ca5f4fdbc	MEDICAL_RECORD	1d4e99c7-3572-42be-9520-a43c1eedc030	medicalrecord.created	1	c79233af-3bd5-482a-8226-b1cade7cbb8c	{"eventId": "2b4d1827-b801-40fd-aa05-6c6ca5f4fdbc", "payload": {"doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "recordId": "1d4e99c7-3572-42be-9520-a43c1eedc030", "sourceId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "patientId": "6f75b635-a491-4191-99b3-1df16c1a3832", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeType": "OUTPATIENT_VISIT", "examinationDate": "2026-10-22"}, "version": 1, "producer": "clinical-service", "eventType": "medicalrecord.created", "occurredAt": "2026-10-22T00:45:48.936871200Z", "correlationId": "c79233af-3bd5-482a-8226-b1cade7cbb8c"}	2026-10-22 00:45:48.936871+00	2026-10-22 00:45:49.21423+00	0	\N	\N	\N	\N	\N
72b042a6-d785-42fc-a741-c63199df05dd	APPOINTMENT	b264777a-2a76-4793-b4ac-91e6a8779f0d	appointment.created	0	2cb3407a-fdf1-447f-b558-bdcea692ac4e	{"eventId": "72b042a6-d785-42fc-a741-c63199df05dd", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "3ad6444d-54b2-467a-9e25-beb664d3975f", "occurredAt": 1792629637.6951811, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "correlationId": "2cb3407a-fdf1-447f-b558-bdcea692ac4e", "appointmentDate": [2026, 10, 22], "appointmentTime": "09:30"}	2026-10-22 00:40:37.695181+00	2026-10-22 00:40:37.951693+00	0	\\x7b226576656e744964223a2237326230343261362d643738352d343266632d613734312d633633313939646630356464222c226f636375727265644174223a313739323632393633372e3639353138313230302c22636f7272656c6174696f6e4964223a2232636233343037612d666466312d343437662d623535382d626463656136393261633465222c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230393a3330227d	\N	\N	\N	\N
84c3333d-c61d-4e5f-92af-c0b9fbf4557b	APPOINTMENT	b9de10f8-44e8-4e44-8637-09135b5691e8	appointment.created	0	11c066f9-2d46-4104-8cd4-d302455cc33c	{"eventId": "84c3333d-c61d-4e5f-92af-c0b9fbf4557b", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "fc4fe9c4-a34c-4252-bd0b-3e171604d3b5", "occurredAt": 1792629637.7923882, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "correlationId": "11c066f9-2d46-4104-8cd4-d302455cc33c", "appointmentDate": [2026, 10, 22], "appointmentTime": "09:45"}	2026-10-22 00:40:37.792388+00	2026-10-22 00:40:37.952219+00	0	\\x7b226576656e744964223a2238346333333333642d633631642d346535662d393261662d633062396662663435353762222c226f636375727265644174223a313739323632393633372e3739323338383230302c22636f7272656c6174696f6e4964223a2231316330363666392d326434362d343130342d386364342d643330323435356363333363222c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2230393a3435227d	\N	\N	\N	\N
9c13c851-17df-4814-92aa-5f1f080b7ba0	APPOINTMENT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	appointment.created	0	7eb99f4c-cc82-48f3-ae2a-bf29c4e6e644	{"eventId": "9c13c851-17df-4814-92aa-5f1f080b7ba0", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "a2c54b05-3010-4b8d-93ee-67fb06469d77", "occurredAt": 1792629637.859819, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "correlationId": "7eb99f4c-cc82-48f3-ae2a-bf29c4e6e644", "appointmentDate": [2026, 10, 22], "appointmentTime": "10:00"}	2026-10-22 00:40:37.859819+00	2026-10-22 00:40:37.952219+00	0	\\x7b226576656e744964223a2239633133633835312d313764662d343831342d393261612d356631663038306237626130222c226f636375727265644174223a313739323632393633372e3835393831393030302c22636f7272656c6174696f6e4964223a2237656239396634632d636338322d343866332d616532612d626632396334653665363434222c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2231303a3030227d	\N	\N	\N	\N
8cb07ec0-79d4-4e15-80d6-432c8f418021	APPOINTMENT	a4fde265-3562-4528-8ea6-0db0e835c1d6	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "8cb07ec0-79d4-4e15-80d6-432c8f418021", "payload": {"recordId": null, "sourceId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "changedAt": "2026-10-22T00:45:54.145399100Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "1270368c-7e70-40eb-852c-b58d7b052cf7", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "careEpisodeId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:54.145399100Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:45:54.145399+00	2026-10-22 00:45:54.285764+00	0	\N	\N	\N	\N	\N
dd427405-c979-4eb9-8abf-abca38267e81	MEDICAL_RECORD	1d4e99c7-3572-42be-9520-a43c1eedc030	diagnosis.added	0	510e20d2-4aa2-42c9-9f12-90e29197b376	{"eventId": "dd427405-c979-4eb9-8abf-abca38267e81", "recordId": "1d4e99c7-3572-42be-9520-a43c1eedc030", "occurredAt": 1792629949.0676868, "correlationId": "510e20d2-4aa2-42c9-9f12-90e29197b376", "diagnosisCode": "J06.9", "diagnosisName": "Theo dõi nhiễm khuẩn hô hấp trên"}	2026-10-22 00:45:49.067687+00	\N	5	\\x7b226576656e744964223a2264643432373430352d633937392d346562392d386162662d616263613338323637653831222c226f636375727265644174223a313739323632393934392e3036373638363830302c22636f7272656c6174696f6e4964223a2235313065323064322d346161322d343263392d396631322d393065323931393762333736222c227265636f72644964223a2231643465393963372d333537322d343262652d393532302d613433633165656463303330222c22646961676e6f736973436f6465223a224a30362e39222c22646961676e6f7369734e616d65223a225468656f2064c3b569206e6869e1bb856d206b6875e1baa96e2068c3b42068e1baa570207472c3aa6e227d	\N	\N	2026-10-22 00:45:50.281653+00	Maximum Clinical outbox publish retries reached
de44f999-c373-4502-9b15-6688b0facbfb	APPOINTMENT	13be7899-c362-4232-acd9-ad6e751a5296	appointment.created	0	66118dce-2063-4d86-a857-fff307abaad5	{"eventId": "de44f999-c373-4502-9b15-6688b0facbfb", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "87674766-119a-46ed-b4db-2059be4cd59c", "occurredAt": 1792629638.047204, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "13be7899-c362-4232-acd9-ad6e751a5296", "correlationId": "66118dce-2063-4d86-a857-fff307abaad5", "appointmentDate": [2026, 10, 22], "appointmentTime": "10:30"}	2026-10-22 00:40:38.047204+00	2026-10-22 00:40:38.24686+00	0	\\x7b226576656e744964223a2264653434663939392d633337332d343530322d396231352d363638386230666163626662222c226f636375727265644174223a313739323632393633382e3034373230333930302c22636f7272656c6174696f6e4964223a2236363131386463652d323036332d346438362d613835372d666666333037616261616435222c226170706f696e746d656e744964223a2231336265373839392d633336322d343233322d616364392d616436653735316135323936222c2270617469656e744964223a2238373637343736362d313139612d343665642d623464622d323035396265346364353963222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2231303a3330227d	\N	\N	\N	\N
30440a63-6e39-45d6-b772-0298265f732c	APPOINTMENT	0bd51db1-eda6-432e-a306-790ff7e4ad20	appointment.created	0	a6a9c2a8-b81f-41eb-ae59-cb7099814264	{"eventId": "30440a63-6e39-45d6-b772-0298265f732c", "doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "patientId": "579e6d5b-1f63-43ce-bd2a-609fdf508481", "occurredAt": 1792629638.113271, "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "0bd51db1-eda6-432e-a306-790ff7e4ad20", "correlationId": "a6a9c2a8-b81f-41eb-ae59-cb7099814264", "appointmentDate": [2026, 10, 22], "appointmentTime": "10:45"}	2026-10-22 00:40:38.113271+00	2026-10-22 00:40:38.247395+00	0	\\x7b226576656e744964223a2233303434306136332d366533392d343564362d623737322d303239383236356637333263222c226f636375727265644174223a313739323632393633382e3131333237303930302c22636f7272656c6174696f6e4964223a2261366139633261382d623831662d343165622d616535392d636237303939383134323634222c226170706f696e746d656e744964223a2230626435316462312d656461362d343332652d613330362d373930666637653461643230222c2270617469656e744964223a2235373965366435622d316636332d343363652d626432612d363039666466353038343831222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e7444617465223a5b323032362c31302c32325d2c226170706f696e746d656e7454696d65223a2231303a3435227d	\N	\N	\N	\N
37a45f81-b95b-41b1-9efc-b43ffb9f1f83	APPOINTMENT	71626749-ef62-42f4-9d40-35097f05dfb9	appointment.status.changed	1	3f3cb928-3c66-4555-9134-1ddc620fac52	{"eventId": "37a45f81-b95b-41b1-9efc-b43ffb9f1f83", "payload": {"recordId": null, "sourceId": "71626749-ef62-42f4-9d40-35097f05dfb9", "changedAt": "2026-10-22T00:40:38.443302Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:40:38.443302Z", "correlationId": "3f3cb928-3c66-4555-9134-1ddc620fac52"}	2026-10-22 00:40:38.443302+00	2026-10-22 00:40:38.527302+00	0	\N	\N	\N	\N	\N
865b3835-47c3-447d-971f-2acd2b5b30b1	APPOINTMENT	71626749-ef62-42f4-9d40-35097f05dfb9	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "865b3835-47c3-447d-971f-2acd2b5b30b1", "payload": {"recordId": null, "sourceId": "71626749-ef62-42f4-9d40-35097f05dfb9", "changedAt": "2026-10-22T00:40:40.252429700Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:40:40.252429700Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:40:40.25243+00	2026-10-22 00:40:40.405975+00	0	\N	\N	\N	\N	\N
86e61417-82f2-4875-b8d8-e52dd0cfe804	MEDICAL_RECORD	4447ad07-fe64-4d41-8c96-7a278d167be2	diagnosis.added	0	4f330ca3-3ac6-4616-81c5-823af343e069	{"eventId": "86e61417-82f2-4875-b8d8-e52dd0cfe804", "recordId": "4447ad07-fe64-4d41-8c96-7a278d167be2", "occurredAt": 1792629640.8450139, "correlationId": "4f330ca3-3ac6-4616-81c5-823af343e069", "diagnosisCode": "J06.9", "diagnosisName": "Theo dõi nhiễm khuẩn hô hấp trên"}	2026-10-22 00:40:40.845014+00	\N	5	\\x7b226576656e744964223a2238366536313431372d383266322d343837352d623864382d653532646430636665383034222c226f636375727265644174223a313739323632393634302e3834353031333830302c22636f7272656c6174696f6e4964223a2234663333306361332d336163362d343631362d383163352d383233616633343365303639222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c22646961676e6f736973436f6465223a224a30362e39222c22646961676e6f7369734e616d65223a225468656f2064c3b569206e6869e1bb856d206b6875e1baa96e2068c3b42068e1baa570207472c3aa6e227d	\N	\N	2026-10-22 00:40:42.055655+00	Maximum Clinical outbox publish retries reached
44e26346-c657-4b9e-80ae-1e1a3f5ef77f	APPOINTMENT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	appointment.status.changed	1	2a7054d4-f678-4f35-bfe3-68c7b4a0cc6d	{"eventId": "44e26346-c657-4b9e-80ae-1e1a3f5ef77f", "payload": {"recordId": null, "sourceId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "changedAt": "2026-10-22T00:44:55.899375100Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "8dd25d75-6d29-4967-ac6e-d54c6a8dd490", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:44:55.899375100Z", "correlationId": "2a7054d4-f678-4f35-bfe3-68c7b4a0cc6d"}	2026-10-22 00:44:55.899375+00	2026-10-22 00:44:56.022968+00	0	\N	\N	\N	\N	\N
6ce6bcb7-ed30-4adf-8c58-d14bb363cb3f	MEDICAL_RECORD	1361a316-8aa3-4ad4-8774-f852d8c64fa1	medicalrecord.created	1	28a9f6b6-abfc-40f0-a1d3-fa6e6d4a1d70	{"eventId": "6ce6bcb7-ed30-4adf-8c58-d14bb363cb3f", "payload": {"doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "recordId": "1361a316-8aa3-4ad4-8774-f852d8c64fa1", "sourceId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "patientId": "8dd25d75-6d29-4967-ac6e-d54c6a8dd490", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeType": "OUTPATIENT_VISIT", "examinationDate": "2026-10-22"}, "version": 1, "producer": "clinical-service", "eventType": "medicalrecord.created", "occurredAt": "2026-10-22T00:44:58.104162800Z", "correlationId": "28a9f6b6-abfc-40f0-a1d3-fa6e6d4a1d70"}	2026-10-22 00:44:58.104163+00	2026-10-22 00:44:58.435428+00	0	\N	\N	\N	\N	\N
dcaaa6e3-ba34-419f-917e-744f573d2d6f	APPOINTMENT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	appointment.status.changed	1	28a9f6b6-abfc-40f0-a1d3-fa6e6d4a1d70	{"eventId": "dcaaa6e3-ba34-419f-917e-744f573d2d6f", "payload": {"recordId": "1361a316-8aa3-4ad4-8774-f852d8c64fa1", "sourceId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "changedAt": "2026-10-22T00:44:58.104162800Z", "newStatus": "IN_EXAM", "oldStatus": "READY_FOR_EXAM", "patientId": "8dd25d75-6d29-4967-ac6e-d54c6a8dd490", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:44:58.104162800Z", "correlationId": "28a9f6b6-abfc-40f0-a1d3-fa6e6d4a1d70"}	2026-10-22 00:44:58.104163+00	2026-10-22 00:44:58.435428+00	0	\N	\N	\N	\N	\N
49d4b575-7d49-4098-b1ba-9d975abf8cd6	APPOINTMENT	037fcbd9-127b-44d6-96ee-73c571763c5e	appointment.status.changed	1	127b7387-bc2e-4f6c-b7ba-a6e2c00721d9	{"eventId": "49d4b575-7d49-4098-b1ba-9d975abf8cd6", "payload": {"recordId": null, "sourceId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "changedAt": "2026-10-22T00:45:37.872029600Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "8fbf50e4-67ca-42f6-96ac-c15d6aab61c8", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:37.872029600Z", "correlationId": "127b7387-bc2e-4f6c-b7ba-a6e2c00721d9"}	2026-10-22 00:45:37.87203+00	2026-10-22 00:45:37.895325+00	0	\N	\N	\N	\N	\N
9da67684-b48e-4c52-b2e4-64f957d99290	APPOINTMENT	037fcbd9-127b-44d6-96ee-73c571763c5e	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "9da67684-b48e-4c52-b2e4-64f957d99290", "payload": {"recordId": null, "sourceId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "changedAt": "2026-10-22T00:45:39.623104Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "8fbf50e4-67ca-42f6-96ac-c15d6aab61c8", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:39.623104Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:45:39.623104+00	2026-10-22 00:45:39.777383+00	0	\N	\N	\N	\N	\N
c97fb9d2-486b-4c27-88b1-7a7f0a1caaff	MEDICAL_RECORD	1361a316-8aa3-4ad4-8774-f852d8c64fa1	diagnosis.added	0	478cc86d-62c5-44b5-ba79-287556dfafd9	{"eventId": "c97fb9d2-486b-4c27-88b1-7a7f0a1caaff", "recordId": "1361a316-8aa3-4ad4-8774-f852d8c64fa1", "occurredAt": 1792629898.2444236, "correlationId": "478cc86d-62c5-44b5-ba79-287556dfafd9", "diagnosisCode": "J06.9", "diagnosisName": "Theo dõi nhiễm khuẩn hô hấp trên"}	2026-10-22 00:44:58.244424+00	\N	5	\\x7b226576656e744964223a2263393766623964322d343836622d346332372d383862312d376137663061316361616666222c226f636375727265644174223a313739323632393839382e3234343432333630302c22636f7272656c6174696f6e4964223a2234373863633836642d363263352d343462352d626137392d323837353536646661666439222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c22646961676e6f736973436f6465223a224a30362e39222c22646961676e6f7369734e616d65223a225468656f2064c3b569206e6869e1bb856d206b6875e1baa96e2068c3b42068e1baa570207472c3aa6e227d	\N	\N	2026-10-22 00:44:59.499702+00	Maximum Clinical outbox publish retries reached
cc458670-802b-46b5-bd67-a1fc24cab197	MEDICAL_RECORD	acc4a82c-6856-469d-80be-8dc343a9e06e	medicalrecord.created	1	aaeff276-1cc9-480d-8c65-aa958af19ef4	{"eventId": "cc458670-802b-46b5-bd67-a1fc24cab197", "payload": {"doctorId": "e524185a-adb6-48d3-a867-b39b259940f2", "recordId": "acc4a82c-6856-469d-80be-8dc343a9e06e", "sourceId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "patientId": "8fbf50e4-67ca-42f6-96ac-c15d6aab61c8", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeType": "OUTPATIENT_VISIT", "examinationDate": "2026-10-22"}, "version": 1, "producer": "clinical-service", "eventType": "medicalrecord.created", "occurredAt": "2026-10-22T00:45:40.038425600Z", "correlationId": "aaeff276-1cc9-480d-8c65-aa958af19ef4"}	2026-10-22 00:45:40.038426+00	2026-10-22 00:45:40.401496+00	0	\N	\N	\N	\N	\N
e536f1bb-5cad-40bf-b412-70cd4368c61d	APPOINTMENT	037fcbd9-127b-44d6-96ee-73c571763c5e	appointment.status.changed	1	aaeff276-1cc9-480d-8c65-aa958af19ef4	{"eventId": "e536f1bb-5cad-40bf-b412-70cd4368c61d", "payload": {"recordId": "acc4a82c-6856-469d-80be-8dc343a9e06e", "sourceId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "changedAt": "2026-10-22T00:45:40.038425600Z", "newStatus": "IN_EXAM", "oldStatus": "READY_FOR_EXAM", "patientId": "8fbf50e4-67ca-42f6-96ac-c15d6aab61c8", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:40.038425600Z", "correlationId": "aaeff276-1cc9-480d-8c65-aa958af19ef4"}	2026-10-22 00:45:40.038426+00	2026-10-22 00:45:40.401999+00	0	\N	\N	\N	\N	\N
1633b5f3-6807-4afe-acc4-6191b81eceb7	APPOINTMENT	250352fd-6ae9-4108-a147-2d76e9b49eb9	appointment.status.changed	1	c79233af-3bd5-482a-8226-b1cade7cbb8c	{"eventId": "1633b5f3-6807-4afe-acc4-6191b81eceb7", "payload": {"recordId": "1d4e99c7-3572-42be-9520-a43c1eedc030", "sourceId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "changedAt": "2026-10-22T00:45:48.936871200Z", "newStatus": "IN_EXAM", "oldStatus": "READY_FOR_EXAM", "patientId": "6f75b635-a491-4191-99b3-1df16c1a3832", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:48.936871200Z", "correlationId": "c79233af-3bd5-482a-8226-b1cade7cbb8c"}	2026-10-22 00:45:48.936871+00	2026-10-22 00:45:49.21423+00	0	\N	\N	\N	\N	\N
879e9f5d-05d4-4a11-93d5-7864a8e009f8	APPOINTMENT	a4fde265-3562-4528-8ea6-0db0e835c1d6	appointment.status.changed	1	6bf83a1b-79bd-4e0d-a474-01d0a1f2e84b	{"eventId": "879e9f5d-05d4-4a11-93d5-7864a8e009f8", "payload": {"recordId": null, "sourceId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "changedAt": "2026-10-22T00:45:49.368882900Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "1270368c-7e70-40eb-852c-b58d7b052cf7", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "careEpisodeId": "a4fde265-3562-4528-8ea6-0db0e835c1d6", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:49.368882900Z", "correlationId": "6bf83a1b-79bd-4e0d-a474-01d0a1f2e84b"}	2026-10-22 00:45:49.368883+00	2026-10-22 00:45:49.484696+00	0	\N	\N	\N	\N	\N
724aff72-bf5a-4b1d-9dbf-e43a35e5a2d2	APPOINTMENT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	appointment.status.changed	1	e262f638-b318-4577-9bd7-37102902924e	{"eventId": "724aff72-bf5a-4b1d-9dbf-e43a35e5a2d2", "payload": {"recordId": null, "sourceId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "changedAt": "2026-10-22T00:45:54.593407Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "83ee7cd5-ccb4-4005-96f3-42abb0261400", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "careEpisodeId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:45:54.593407Z", "correlationId": "e262f638-b318-4577-9bd7-37102902924e"}	2026-10-22 00:45:54.593407+00	2026-10-22 00:45:54.84983+00	0	\N	\N	\N	\N	\N
1f308854-58a8-4503-90e2-378ea0fab161	APPOINTMENT	b264777a-2a76-4793-b4ac-91e6a8779f0d	appointment.status.changed	1	a047d4bc-709f-48eb-9c3c-bf342d41a286	{"eventId": "1f308854-58a8-4503-90e2-378ea0fab161", "payload": {"recordId": null, "sourceId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "changedAt": "2026-10-22T00:50:46.532680200Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "3ad6444d-54b2-467a-9e25-beb664d3975f", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "careEpisodeId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:50:46.532680200Z", "correlationId": "a047d4bc-709f-48eb-9c3c-bf342d41a286"}	2026-10-22 00:50:46.53268+00	2026-10-22 00:50:46.662496+00	0	\N	\N	\N	\N	\N
156d97aa-75ef-4d33-82ec-5575d45f2977	APPOINTMENT	b264777a-2a76-4793-b4ac-91e6a8779f0d	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "156d97aa-75ef-4d33-82ec-5575d45f2977", "payload": {"recordId": null, "sourceId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "changedAt": "2026-10-22T00:52:52.735709100Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "3ad6444d-54b2-467a-9e25-beb664d3975f", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "careEpisodeId": "b264777a-2a76-4793-b4ac-91e6a8779f0d", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:52:52.735709100Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:52:52.735709+00	2026-10-22 00:52:52.860158+00	0	\N	\N	\N	\N	\N
eec07b46-9974-4f73-8669-4464cd83440f	APPOINTMENT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	appointment.status.changed	1	c49d7234-55c0-4344-a85a-e0615a621dc5	{"eventId": "eec07b46-9974-4f73-8669-4464cd83440f", "payload": {"recordId": null, "sourceId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "changedAt": "2026-10-22T00:56:55.909053500Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "83ee7cd5-ccb4-4005-96f3-42abb0261400", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "careEpisodeId": "74fcae55-0ff2-4f0c-ae64-ea847dc3a66b", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T00:56:55.909053500Z", "correlationId": "c49d7234-55c0-4344-a85a-e0615a621dc5"}	2026-10-22 00:56:55.909054+00	2026-10-22 00:56:55.999686+00	0	\N	\N	\N	\N	\N
0dae3cab-312e-4bb6-8aba-7026d7eb9751	APPOINTMENT	b9de10f8-44e8-4e44-8637-09135b5691e8	appointment.status.changed	1	d68155c5-9126-4295-8a20-acb76aa84d01	{"eventId": "0dae3cab-312e-4bb6-8aba-7026d7eb9751", "payload": {"recordId": null, "sourceId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "changedAt": "2026-10-22T10:49:28.989701100Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "fc4fe9c4-a34c-4252-bd0b-3e171604d3b5", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "careEpisodeId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T10:49:28.989701100Z", "correlationId": "d68155c5-9126-4295-8a20-acb76aa84d01"}	2026-10-22 10:49:28.989701+00	2026-10-22 10:49:29.289421+00	0	\N	\N	\N	\N	\N
d89bb83a-5d43-43b1-b323-205bbb255393	APPOINTMENT	b9de10f8-44e8-4e44-8637-09135b5691e8	appointment.status.changed	1	cbc58b81-4706-4591-b2f6-a150dabdf616	{"eventId": "d89bb83a-5d43-43b1-b323-205bbb255393", "payload": {"recordId": null, "sourceId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "changedAt": "2026-10-22T10:53:23.756842800Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "fc4fe9c4-a34c-4252-bd0b-3e171604d3b5", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "careEpisodeId": "b9de10f8-44e8-4e44-8637-09135b5691e8", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T10:53:23.756842800Z", "correlationId": "cbc58b81-4706-4591-b2f6-a150dabdf616"}	2026-10-22 10:53:23.756843+00	2026-10-22 10:53:23.831077+00	0	\N	\N	\N	\N	\N
d056593d-84f3-44fc-896c-6b5903c12175	APPOINTMENT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	appointment.status.changed	1	848d4b49-c1ce-4524-a47b-fd97acecb85e	{"eventId": "d056593d-84f3-44fc-896c-6b5903c12175", "payload": {"recordId": null, "sourceId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "changedAt": "2026-10-22T11:37:33.301206600Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "a2c54b05-3010-4b8d-93ee-67fb06469d77", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "careEpisodeId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T11:37:33.301206600Z", "correlationId": "848d4b49-c1ce-4524-a47b-fd97acecb85e"}	2026-10-22 11:37:33.301207+00	2026-10-22 11:37:33.776974+00	0	\N	\N	\N	\N	\N
3fdae1a1-495f-4c35-ac9f-f01dfea33acb	APPOINTMENT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	appointment.status.changed	1	cbc58b81-4706-4591-b2f6-a150dabdf616	{"eventId": "3fdae1a1-495f-4c35-ac9f-f01dfea33acb", "payload": {"recordId": null, "sourceId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "changedAt": "2026-10-22T11:41:34.737703200Z", "newStatus": "READY_FOR_EXAM", "oldStatus": "AWAITING_PAYMENT", "patientId": "a2c54b05-3010-4b8d-93ee-67fb06469d77", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "careEpisodeId": "f91a4cad-6e7a-4b23-9f3f-08187f1687ec", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T11:41:34.737703200Z", "correlationId": "cbc58b81-4706-4591-b2f6-a150dabdf616"}	2026-10-22 11:41:34.737703+00	2026-10-22 11:41:34.861102+00	0	\N	\N	\N	\N	\N
9726abd3-fbea-4b34-ac89-8963d0ad6f68	APPOINTMENT	52c8e6d2-4a81-4677-838d-ea6d7c1c63d9	appointment.status.changed	1	21408525-9870-4976-8552-24b025ae30b4	{"eventId": "9726abd3-fbea-4b34-ac89-8963d0ad6f68", "payload": {"recordId": null, "sourceId": "52c8e6d2-4a81-4677-838d-ea6d7c1c63d9", "changedAt": "2026-10-22T11:56:40.509471300Z", "newStatus": "AWAITING_PAYMENT", "oldStatus": "PENDING", "patientId": "b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8", "priceCode": "OUTPATIENT_EXAM", "sourceType": "EXAM", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "appointmentId": "52c8e6d2-4a81-4677-838d-ea6d7c1c63d9", "careEpisodeId": "52c8e6d2-4a81-4677-838d-ea6d7c1c63d9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "clinical-service", "eventType": "appointment.status.changed", "occurredAt": "2026-10-22T11:56:40.509471300Z", "correlationId": "21408525-9870-4976-8552-24b025ae30b4"}	2026-10-22 11:56:40.509471+00	2026-10-22 11:56:40.750886+00	0	\N	\N	\N	\N	\N
\.


--
-- Data for Name: consumer_inbox_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.consumer_inbox_delivery (consumer_name, event_id, routing_key, operation_key, raw_body, raw_sha256, canonical_business_bytes, canonical_sha256, received_at) FROM stdin;
clinical-exam-clearance	99a7d7b4-2942-4dfb-b648-a37002ffe6a7	financial.clearance.granted	clinical:clearance:v1:86de69c5-4b2e-4458-83e0-6677efc159aa	\\x7b226576656e744964223a2239396137643762342d323934322d346466622d623634382d613337303032666665366137222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34303a31362e3931373034353630305a222c22636f7272656c6174696f6e4964223a2234343964353264612d323862352d343464312d613531332d393964623539636665376432222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2238366465363963352d346232652d343435382d383365302d363637376566633135396161222c22696e766f6963654964223a2234386264643066392d613963342d343562632d393938372d343064663937373065316230222c226163636f756e744964223a2232623037353365612d663839322d343438342d383163362d363434623038383332636332222c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	74974495e9e499ab3fb57f0c3de0a5586920775b179533664cb338508811115f	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2232623037353365612d663839322d343438342d383163362d363434623038383332636332222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238366465363963352d346232652d343435382d383365302d363637376566633135396161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234386264643066392d613963342d343562632d393938372d343064663937373065316230222c226c616254657374496473223a5b5d2c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	4ef871dee45a5a6efe0611a8f9047e9d2e8d3a37e42b60573ff034bd18cab548	2026-10-22 00:40:17.364555+00
clinical-exam-clearance	57bdeb4a-1402-44d3-8e9a-fe70eec15d8d	financial.clearance.granted	clinical:clearance:v1:49f69633-3e1f-4ef3-ab1e-c7b344ab174a	\\x7b226576656e744964223a2235376264656234612d313430322d343464332d386539612d666537306565633135643864222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34303a33392e3532313333383830305a222c22636f7272656c6174696f6e4964223a2232616134323763362d613566342d343032342d616333362d386265656431333064303730222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2234396636393633332d336531662d346566332d616231652d633762333434616231373461222c22696e766f6963654964223a2239323234346666342d333139612d346363632d386263642d636237366161373134653038222c226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	9298f14861f41ffba12ab40c2f854f171eb0298014eca614ae00a3fba7d6b83e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234396636393633332d336531662d346566332d616231652d633762333434616231373461222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2239323234346666342d333139612d346363632d386263642d636237366161373134653038222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	699008b881e037c5fd279ec050f409d5d69c792dcfb4794fac755b02cf3d2f13	2026-10-22 00:40:40.246977+00
clinical-exam-clearance	db0ade44-9af5-4b37-9105-3ec7930ad00b	financial.clearance.granted	clinical:clearance:v1:f774ec12-0ace-4926-b47a-1a97274cc814	\\x7b226576656e744964223a2264623061646534342d396166352d346233372d393130352d336563373933306164303062222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34303a34342e3538333131313430305a222c22636f7272656c6174696f6e4964223a2264633435613232652d306537352d343662612d613261302d366166646362313732333364222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2266373734656331322d306163652d343932362d623437612d316139373237346363383134222c22696e766f6963654964223a2263623937313133662d366231342d343639662d383433392d636531396165326161323037222c226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22707572706f7365223a224c41425f54455354222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c226c616254657374496473223a5b2234636466666161302d323065642d346164392d386133662d663039316361386564303838225d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3135303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	76235d1a1060aed9cb497e35ea5842bfc35ad125f23269913aab8948ce16ffc8	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266373734656331322d306163652d343932362d623437612d316139373237346363383134222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2263623937313133662d366231342d343639662d383433392d636531396165326161323037222c226c616254657374496473223a5b2234636466666161302d323065642d346164392d386133662d663039316361386564303838225d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4a14285fe2cf318f685238771561a063eeef6d300686e0a44ede695070a5514	2026-10-22 00:40:46.427325+00
clinical-exam-clearance	ec5b3a7c-269d-42d5-a3b7-fc28391aa064	financial.clearance.granted	clinical:clearance:v1:b9ec057b-c3a9-45b5-9e95-36109f530bc1	\\x7b226576656e744964223a2265633562336137632d323639642d343264352d613362372d666332383339316161303634222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34343a35362e3939303639363430305a222c22636f7272656c6174696f6e4964223a2231346665303062612d623830632d343039332d396661322d306538663262646465636539222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2262396563303537622d633361392d343562352d396539352d333631303966353330626331222c22696e766f6963654964223a2231663134633931342d396331332d346534382d393030332d646437333432323835396661222c226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	6314e109e64ef9ef488d45ed1f1a492041966cd6787379991e8ab927f0c7d711	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396563303537622d633361392d343562352d396539352d333631303966353330626331222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2231663134633931342d396331332d346534382d393030332d646437333432323835396661222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	2b9738565f55b6434732543b8132d0de971c3b91cc8da7ebef30452060315787	2026-10-22 00:44:57.897001+00
clinical-exam-clearance	7db9d05c-3d1c-40ae-84c5-6d427809bf76	financial.clearance.granted	clinical:clearance:v1:b9dc3502-c045-47d3-973d-07f7c296e12d	\\x7b226576656e744964223a2237646239643035632d336431632d343061652d383463352d366434323738303962663736222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a30322e3438393536323430305a222c22636f7272656c6174696f6e4964223a2263303563373136352d653232302d343937372d623166612d646163633830383436663164222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2262396463333530322d633034352d343764332d393733642d303766376332393665313264222c22696e766f6963654964223a2234353431313532642d663434372d346138382d626130642d623633656539323434643334222c226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22707572706f7365223a224c41425f54455354222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c226c616254657374496473223a5b2261393263393131382d653538662d346166622d623636362d373530333066653130363030225d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3135303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	f15b28845ff6d2fc73393184835c1827670b5aa8ad21b17a681d8d6772e94163	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396463333530322d633034352d343764332d393733642d303766376332393665313264222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234353431313532642d663434372d346138382d626130642d623633656539323434643334222c226c616254657374496473223a5b2261393263393131382d653538662d346166622d623636362d373530333066653130363030225d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	168d1b72593729bd8f690d4488c12f05d6b9fe58f4aaedb340ccabf31931f4e0	2026-10-22 00:45:03.007665+00
clinical-exam-clearance	74190a4b-a6b9-419f-8a27-c5882393cf55	financial.clearance.granted	clinical:clearance:v1:595165d5-e8c4-41e1-9203-1cd72c053d30	\\x7b226576656e744964223a2237343139306134622d613662392d343139662d386132372d633538383233393363663535222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33322e3230313636353830305a222c22636f7272656c6174696f6e4964223a2266383366656537352d396237312d343366392d383837632d663637323261363736326162222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2235393531363564352d653863342d343165312d393230332d316364373263303533643330222c22696e766f6963654964223a2265623337323030632d656239312d346337312d623435652d373332613962613737336338222c226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22707572706f7365223a22505245534352495054494f4e222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a393030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	8a50857436a67c57f5f1386c0d2e1f4798d4951bf06bec7f2fd05b8c02dd88b5	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2235393531363564352d653863342d343165312d393230332d316364373263303533643330222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265623337323030632d656239312d346337312d623435652d373332613962613737336338222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	0d7273b9a82233a6fb1fe534583d632d5a0d9aebc75c2bf6b45ab507304b246c	2026-10-22 00:45:33.447224+00
clinical-prescription	7d3186d9-a2c6-44e1-a89c-4565b164d105	prescription.filled	clinical:prescription:v1:d3b54c5d-ea65-48d2-9200-cfae37dae670	\\x7b226576656e744964223a2237643331383664392d613263362d343465312d613839632d343536356231363464313035222c226576656e7454797065223a22707265736372697074696f6e2e66696c6c6564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33342e3336343237393630305a222c22636f7272656c6174696f6e4964223a2235366431333830322d663364642d346334382d613834312d626364323233323035313433222c2270726f6475636572223a22706861726d6163792d73657276696365222c227061796c6f6164223a7b22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c2263617265436f6e74657874223a224f555450415449454e54222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22736f7572636554797065223a22505245534352495054494f4e222c22736f757263654964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c227072696365436f6465223a2244525547222c226974656d73223a5b7b22647275674964223a2237316138616464352d353838322d343466302d383562622d386531336561306430306661222c22647275674e616d65223a2250617261636574616d6f6c20353030206d67222c227175616e74697479223a362c22756e69745072696365223a313530302e30307d5d2c22746f74616c416d6f756e74223a393030302e30302c2264697370656e73654964223a2264336235346335642d656136352d343864322d393230302d636661653337646165363730222c2266696c6c65644174223a22323032362d31302d32325430303a34353a33342e3336343237393630305a227d7d	33c08aec81137060db91b2d8072ce81b811f052bd085e9846755174d38f466cc	\\x7b226576656e7454797065223a22707265736372697074696f6e2e66696c6c6564222c227061796c6f6164223a7b2263617265436f6e74657874223a224f555450415449454e54222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c2264697370656e73654964223a2264336235346335642d656136352d343864322d393230302d636661653337646165363730222c2266696c6c65644174223a22323032362d31302d32325430303a34353a33342e3336343237393630305a222c226974656d73223a5b7b22647275674964223a2237316138616464352d353838322d343466302d383562622d386531336561306430306661222c22647275674e616d65223a2250617261636574616d6f6c20353030206d67222c227175616e74697479223a362c22756e69745072696365223a312e35452b337d5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c227072696365436f6465223a2244525547222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c22736f757263654964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c22736f7572636554797065223a22505245534352495054494f4e222c22746f74616c416d6f756e74223a39452b337d2c2270726f6475636572223a22706861726d6163792d73657276696365222c2276657273696f6e223a317d	bc7376dd0c7243a4fdd11e001a6b05ecb7eb129b7f6a453625b0088e0aaeab76	2026-10-22 00:45:35.427995+00
clinical-exam-clearance	736eea19-daa1-489e-8eda-499a93f4165b	financial.clearance.granted	clinical:clearance:v1:3dc5587a-41d2-4b78-8ea6-cd1fc05e9294	\\x7b226576656e744964223a2237333665656131392d646161312d343839652d386564612d343939613933663431363562222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33352e3733393531353530305a222c22636f7272656c6174696f6e4964223a2238666163613031312d386331302d343933372d393935662d396632643263633539613662222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2233646335353837612d343164322d346237382d386561362d636431666330356539323934222c22696e766f6963654964223a2238386534303139372d616339342d346138302d626539622d663934393334656561376666222c226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22707572706f7365223a22505245534352495054494f4e222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a393030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	9891cd743a3ad82f22ea40f4de19db8f0a340139bdc9f4ac8f53c2e81c3cf56e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233646335353837612d343164322d346237382d386561362d636431666330356539323934222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238386534303139372d616339342d346138302d626539622d663934393334656561376666222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	237d15f81e84f6ed3e2b20a4b936d1ad67a52adf84b639fcbaa4e122392d8a8c	2026-10-22 00:45:37.535738+00
clinical-exam-clearance	9c03fa80-daba-4a73-b1af-9dedd6e60eb2	financial.clearance.granted	clinical:clearance:v1:db1a5449-6c73-4772-8791-54039cb8111e	\\x7b226576656e744964223a2239633033666138302d646162612d346137332d623161662d396465646436653630656232222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33382e3932383534355a222c22636f7272656c6174696f6e4964223a2237363931646637652d616361312d343433632d623130622d376533633335353333616162222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2264623161353434392d366337332d343737322d383739312d353430333963623831313165222c22696e766f6963654964223a2232363539613232312d376238312d346131382d626537342d326330613837336564616437222c226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	85e674244594ff370599eca3c7b758aec91a4d2320b02799892476af4735638b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2264623161353434392d366337332d343737322d383739312d353430333963623831313165222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2232363539613232312d376238312d346131382d626537342d326330613837336564616437222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	47db46dbecbc2872bf6890d9f5ecbb4c64e3a0ad7c17ae1e95f0eb6d30f194d7	2026-10-22 00:45:39.620118+00
clinical-exam-clearance	12e1525e-15f7-426f-ba92-1aa941eab204	financial.clearance.granted	clinical:clearance:v1:432e20d2-1ca3-49ed-8aed-a17948dca7aa	\\x7b226576656e744964223a2231326531353235652d313566372d343236662d626139322d316161393431656162323034222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a34342e3433343739383330305a222c22636f7272656c6174696f6e4964223a2266646435663539392d346662352d346465372d623839652d623465653830366166626161222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2234333265323064322d316361332d343965642d386165642d613137393438646361376161222c22696e766f6963654964223a2265346331363831632d613261632d343061392d396136332d653837313338383732356665222c226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c22707572706f7365223a224c41425f54455354222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c226c616254657374496473223a5b2262626265326234312d353036312d343338312d616234362d343732623633313830626164225d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3135303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	7dd5d78f208a23578735cd44bede0254c6d8de62f1a5ac6098408b577ece527e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234333265323064322d316361332d343965642d386165642d613137393438646361376161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265346331363831632d613261632d343061392d396136332d653837313338383732356665222c226c616254657374496473223a5b2262626265326234312d353036312d343338312d616234362d343732623633313830626164225d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66b3993fe2f04af465752c3e962935daf52314533f9b128daaef4d5cb3f1a0cc	2026-10-22 00:45:45.869495+00
clinical-exam-clearance	9c3e6353-a2b1-4d52-be28-7689b60257d2	financial.clearance.granted	clinical:clearance:v1:33e1d0e6-f85f-450b-9813-41a6e5b56cf9	\\x7b226576656e744964223a2239633365363335332d613262312d346435322d626532382d373638396236303235376432222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a34372e3833363531393230305a222c22636f7272656c6174696f6e4964223a2264316533376539342d633563332d346137312d383434612d373962616330303933616331222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2233336531643065362d663835662d343530622d393831332d343161366535623536636639222c22696e766f6963654964223a2237306366643065382d316431642d343161332d393363322d623765306439656363626234222c226163636f756e744964223a2264613065613865312d656363352d343363662d386631332d643263333235623666626637222c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	a5e5d19604a5b50d16bc3c19da4bd99df3530a04565e3962f97afb9c709dd482	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264613065613865312d656363352d343363662d386631332d643263333235623666626637222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233336531643065362d663835662d343530622d393831332d343161366535623536636639222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2237306366643065382d316431642d343161332d393363322d623765306439656363626234222c226c616254657374496473223a5b5d2c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdb6c697dc6754e042594e51bbe8c229b4074485d86991c4f59dd12571b06054	2026-10-22 00:45:47.967831+00
clinical-exam-clearance	8aa0aec0-16ae-400e-880b-36342e617c0e	financial.clearance.granted	clinical:clearance:v1:fa6622b0-d820-4c4e-9bf6-26671394453b	\\x7b226576656e744964223a2238616130616563302d313661652d343030652d383830622d333633343265363137633065222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a35322e3439323533363330305a222c22636f7272656c6174696f6e4964223a2261373639323930332d376563302d343636332d393432342d333631343064323130373430222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2266613636323262302d643832302d346334652d396266362d323636373133393434353362222c22696e766f6963654964223a2236613131633030652d616664382d346562392d396136662d366332366666613836643932222c226163636f756e744964223a2238363837346637332d636364332d343932652d616435392d643036346532353230663632222c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	b42794a61461e074a3ba31223325eec5cd0acfe8954ab98ed6f749cd2ceec511	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238363837346637332d636364332d343932652d616435392d643036346532353230663632222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f64654964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266613636323262302d643832302d346334652d396266362d323636373133393434353362222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236613131633030652d616664382d346562392d396136662d366332366666613836643932222c226c616254657374496473223a5b5d2c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66fc10a0738c63a07b454b60ef4f5faaa1bd426dd9e7512c89cce4afd2452f05	2026-10-22 00:45:54.141238+00
clinical-prescription	6400782d-fa9c-41f8-a5c3-95b1ad7081ce	prescription.filled	clinical:prescription:v1:bfc025df-98c4-4244-bf55-3287331fc5e9	\\x7b226576656e744964223a2236343030373832642d666139632d343166382d613563332d393562316164373038316365222c226576656e7454797065223a22707265736372697074696f6e2e66696c6c6564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a35323a31372e3338323630343730305a222c22636f7272656c6174696f6e4964223a2261346538613131352d343330372d343262652d626466612d326265366436356433313536222c2270726f6475636572223a22706861726d6163792d73657276696365222c227061796c6f6164223a7b22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c2263617265436f6e74657874223a224f555450415449454e54222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22736f7572636554797065223a22505245534352495054494f4e222c22736f757263654964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c227072696365436f6465223a2244525547222c226974656d73223a5b7b22647275674964223a2237316138616464352d353838322d343466302d383562622d386531336561306430306661222c22647275674e616d65223a2250617261636574616d6f6c20353030206d67222c227175616e74697479223a362c22756e69745072696365223a313530302e30307d5d2c22746f74616c416d6f756e74223a393030302e30302c2264697370656e73654964223a2262666330323564662d393863342d343234342d626635352d333238373333316663356539222c2266696c6c65644174223a22323032362d31302d32325430303a35323a31372e3338323630343730305a227d7d	b93a58364dfc09653ec7431aadfd5b5542bad3f0e17eb1e2bc1f46d6693c1ebe	\\x7b226576656e7454797065223a22707265736372697074696f6e2e66696c6c6564222c227061796c6f6164223a7b2263617265436f6e74657874223a224f555450415449454e54222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c2264697370656e73654964223a2262666330323564662d393863342d343234342d626635352d333238373333316663356539222c2266696c6c65644174223a22323032362d31302d32325430303a35323a31372e3338323630343730305a222c226974656d73223a5b7b22647275674964223a2237316138616464352d353838322d343466302d383562622d386531336561306430306661222c22647275674e616d65223a2250617261636574616d6f6c20353030206d67222c227175616e74697479223a362c22756e69745072696365223a312e35452b337d5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c227072696365436f6465223a2244525547222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c22736f757263654964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c22736f7572636554797065223a22505245534352495054494f4e222c22746f74616c416d6f756e74223a39452b337d2c2270726f6475636572223a22706861726d6163792d73657276696365222c2276657273696f6e223a317d	f82f312fc77b03cdae51769f5b21c46969a072974ad0bb51f62aef9de18f8bbe	2026-10-22 00:52:18.003759+00
clinical-exam-clearance	68d00a96-48cf-4e4d-a88f-b71c0d9a5010	financial.clearance.granted	clinical:clearance:v1:76283443-9e57-4e50-b63c-ede3cac9664d	\\x7b226576656e744964223a2236386430306139362d343863662d346534642d613838662d623731633064396135303130222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a35323a35312e3030303333343930305a222c22636f7272656c6174696f6e4964223a2237333232653235322d396530302d346133382d393833652d373735333431623563656566222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2237363238333434332d396535372d346535302d623633632d656465336361633936363464222c22696e766f6963654964223a2266626262376137632d373232372d343131382d613231632d363666333066623361643732222c226163636f756e744964223a2261383036666461332d306232362d346263312d393666642d383566366137346361323934222c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	0d20d8a0988254697158c70eff118206a34971ddbcfb35e70c8e06a607454859	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2261383036666461332d306232362d346263312d393666642d383566366137346361323934222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f64654964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2237363238333434332d396535372d346535302d623633632d656465336361633936363464222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2266626262376137632d373232372d343131382d613231632d363666333066623361643732222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	82c0ac8c545c2235841a019718fb9149e00875864133eeff942ff0d974f62d5c	2026-10-22 00:52:52.732607+00
clinical-exam-clearance	20a4d671-939e-43a8-b642-62a283362106	financial.clearance.granted	clinical:clearance:v1:0c7cc504-61b9-4f1d-ac04-fda127f2cb86	\\x7b226576656e744964223a2232306134643637312d393339652d343361382d623634322d363261323833333632313036222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a35363a35352e3533353535393430305a222c22636f7272656c6174696f6e4964223a2262386135343337362d333937652d343136362d623165352d663361343534643561613739222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2230633763633530342d363162392d346631642d616330342d666461313237663263623836222c22696e766f6963654964223a2236363362313363632d393735382d343631352d613761312d643430316464626536653231222c226163636f756e744964223a2234626161313666612d326166382d343362302d396231392d316633326337663237356163222c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	a27e35704092ed1713ef9c1d7cfbe0796f19d3b44740805cb71c3b2c9b908a7b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2234626161313666612d326166382d343362302d396231392d316633326337663237356163222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f64654964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2230633763633530342d363162392d346631642d616330342d666461313237663263623836222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236363362313363632d393735382d343631352d613761312d643430316464626536653231222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4d84b66b347ccd01a340dd4b1bc3140df245ef15938abb3b23b112d8d7de143	2026-10-22 00:56:55.905993+00
clinical-exam-clearance	ad4889ed-ca90-4c64-8368-204493565785	financial.clearance.granted	clinical:clearance:v1:80f4ed5d-2194-4056-be84-bd056d4e1599	\\x7b226576656e744964223a2261643438383965642d636139302d346336342d383336382d323034343933353635373835222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325431303a35333a32322e3739343738323330305a222c22636f7272656c6174696f6e4964223a2230313766663536632d323166662d346162302d383834312d363861636532333035393563222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2238306634656435642d323139342d343035362d626538342d626430353664346531353939222c22696e766f6963654964223a2238653730356237662d383034642d343864382d613066342d313061386134646631653964222c226163636f756e744964223a2235633332356532372d623734642d343964372d623865662d326337363336333039363762222c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	f2bb39e10bc8c301f0f6b977c449ab5120ceb0209d60b3d205aa94f289beef0a	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2235633332356532372d623734642d343964372d623865662d326337363336333039363762222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f64654964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238306634656435642d323139342d343035362d626538342d626430353664346531353939222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238653730356237662d383034642d343864382d613066342d313061386134646631653964222c226c616254657374496473223a5b5d2c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdbcabda3e950c883d589e162104af7de4b8443cdfb78492310dfc3ade29d32e	2026-10-22 10:53:23.717267+00
clinical-exam-clearance	0e7ccf12-b2f0-439e-b271-6232e8d66f39	financial.clearance.granted	clinical:clearance:v1:fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	\\x7b226576656e744964223a2230653763636631322d623266302d343339652d623237312d363233326538643636663339222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325431313a34313a33342e3136353633313730305a222c22636f7272656c6174696f6e4964223a2238643565643764612d333830652d346339362d626165392d613464623230636138303831222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2266653861376662652d663632352d346532322d613662652d613266356639393763646465222c22696e766f6963654964223a2261356437383865362d336165302d346564322d626233642d303338336539323161363238222c226163636f756e744964223a2236393431623739322d613934382d346163622d623532662d366364636232366235393536222c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	4249f97ece4509f4e934ca9bd93f61ff3df1e05009c73bcb36f33d07fad07e9d	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2236393431623739322d613934382d346163622d623532662d366364636232366235393536222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f64654964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266653861376662652d663632352d346532322d613662652d613266356639393763646465222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2261356437383865362d336165302d346564322d626233642d303338336539323161363238222c226c616254657374496473223a5b5d2c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	a28b373978142a2202e27cc4c5cdd14a49562dc7194ba75f3a95e73576d8f7fb	2026-10-22 11:41:34.710248+00
\.


--
-- Data for Name: consumer_inbox_operation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.consumer_inbox_operation (consumer_name, operation_key, first_event_id, canonical_business_bytes, canonical_sha256, applied_at) FROM stdin;
clinical-exam-clearance	clinical:clearance:v1:86de69c5-4b2e-4458-83e0-6677efc159aa	99a7d7b4-2942-4dfb-b648-a37002ffe6a7	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2232623037353365612d663839322d343438342d383163362d363434623038383332636332222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238366465363963352d346232652d343435382d383365302d363637376566633135396161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234386264643066392d613963342d343562632d393938372d343064663937373065316230222c226c616254657374496473223a5b5d2c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	4ef871dee45a5a6efe0611a8f9047e9d2e8d3a37e42b60573ff034bd18cab548	2026-10-22 00:40:17.364555+00
clinical-exam-clearance	clinical:clearance:v1:49f69633-3e1f-4ef3-ab1e-c7b344ab174a	57bdeb4a-1402-44d3-8e9a-fe70eec15d8d	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234396636393633332d336531662d346566332d616231652d633762333434616231373461222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2239323234346666342d333139612d346363632d386263642d636237366161373134653038222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	699008b881e037c5fd279ec050f409d5d69c792dcfb4794fac755b02cf3d2f13	2026-10-22 00:40:40.246977+00
clinical-exam-clearance	clinical:clearance:v1:f774ec12-0ace-4926-b47a-1a97274cc814	db0ade44-9af5-4b37-9105-3ec7930ad00b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266373734656331322d306163652d343932362d623437612d316139373237346363383134222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2263623937313133662d366231342d343639662d383433392d636531396165326161323037222c226c616254657374496473223a5b2234636466666161302d323065642d346164392d386133662d663039316361386564303838225d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4a14285fe2cf318f685238771561a063eeef6d300686e0a44ede695070a5514	2026-10-22 00:40:46.427325+00
clinical-exam-clearance	clinical:clearance:v1:b9ec057b-c3a9-45b5-9e95-36109f530bc1	ec5b3a7c-269d-42d5-a3b7-fc28391aa064	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396563303537622d633361392d343562352d396539352d333631303966353330626331222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2231663134633931342d396331332d346534382d393030332d646437333432323835396661222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	2b9738565f55b6434732543b8132d0de971c3b91cc8da7ebef30452060315787	2026-10-22 00:44:57.897001+00
clinical-exam-clearance	clinical:clearance:v1:b9dc3502-c045-47d3-973d-07f7c296e12d	7db9d05c-3d1c-40ae-84c5-6d427809bf76	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396463333530322d633034352d343764332d393733642d303766376332393665313264222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234353431313532642d663434372d346138382d626130642d623633656539323434643334222c226c616254657374496473223a5b2261393263393131382d653538662d346166622d623636362d373530333066653130363030225d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	168d1b72593729bd8f690d4488c12f05d6b9fe58f4aaedb340ccabf31931f4e0	2026-10-22 00:45:03.007665+00
clinical-exam-clearance	clinical:clearance:v1:595165d5-e8c4-41e1-9203-1cd72c053d30	74190a4b-a6b9-419f-8a27-c5882393cf55	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2235393531363564352d653863342d343165312d393230332d316364373263303533643330222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265623337323030632d656239312d346337312d623435652d373332613962613737336338222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	0d7273b9a82233a6fb1fe534583d632d5a0d9aebc75c2bf6b45ab507304b246c	2026-10-22 00:45:33.447224+00
clinical-prescription	clinical:prescription:v1:d3b54c5d-ea65-48d2-9200-cfae37dae670	7d3186d9-a2c6-44e1-a89c-4565b164d105	\\x7b226576656e7454797065223a22707265736372697074696f6e2e66696c6c6564222c227061796c6f6164223a7b2263617265436f6e74657874223a224f555450415449454e54222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c2264697370656e73654964223a2264336235346335642d656136352d343864322d393230302d636661653337646165363730222c2266696c6c65644174223a22323032362d31302d32325430303a34353a33342e3336343237393630305a222c226974656d73223a5b7b22647275674964223a2237316138616464352d353838322d343466302d383562622d386531336561306430306661222c22647275674e616d65223a2250617261636574616d6f6c20353030206d67222c227175616e74697479223a362c22756e69745072696365223a312e35452b337d5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c227072696365436f6465223a2244525547222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c22736f757263654964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c22736f7572636554797065223a22505245534352495054494f4e222c22746f74616c416d6f756e74223a39452b337d2c2270726f6475636572223a22706861726d6163792d73657276696365222c2276657273696f6e223a317d	bc7376dd0c7243a4fdd11e001a6b05ecb7eb129b7f6a453625b0088e0aaeab76	2026-10-22 00:45:35.427995+00
clinical-exam-clearance	clinical:clearance:v1:3dc5587a-41d2-4b78-8ea6-cd1fc05e9294	736eea19-daa1-489e-8eda-499a93f4165b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233646335353837612d343164322d346237382d386561362d636431666330356539323934222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238386534303139372d616339342d346138302d626539622d663934393334656561376666222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	237d15f81e84f6ed3e2b20a4b936d1ad67a52adf84b639fcbaa4e122392d8a8c	2026-10-22 00:45:37.535738+00
clinical-exam-clearance	clinical:clearance:v1:db1a5449-6c73-4772-8791-54039cb8111e	9c03fa80-daba-4a73-b1af-9dedd6e60eb2	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2264623161353434392d366337332d343737322d383739312d353430333963623831313165222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2232363539613232312d376238312d346131382d626537342d326330613837336564616437222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	47db46dbecbc2872bf6890d9f5ecbb4c64e3a0ad7c17ae1e95f0eb6d30f194d7	2026-10-22 00:45:39.620118+00
clinical-exam-clearance	clinical:clearance:v1:432e20d2-1ca3-49ed-8aed-a17948dca7aa	12e1525e-15f7-426f-ba92-1aa941eab204	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234333265323064322d316361332d343965642d386165642d613137393438646361376161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265346331363831632d613261632d343061392d396136332d653837313338383732356665222c226c616254657374496473223a5b2262626265326234312d353036312d343338312d616234362d343732623633313830626164225d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66b3993fe2f04af465752c3e962935daf52314533f9b128daaef4d5cb3f1a0cc	2026-10-22 00:45:45.869495+00
clinical-exam-clearance	clinical:clearance:v1:33e1d0e6-f85f-450b-9813-41a6e5b56cf9	9c3e6353-a2b1-4d52-be28-7689b60257d2	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264613065613865312d656363352d343363662d386631332d643263333235623666626637222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233336531643065362d663835662d343530622d393831332d343161366535623536636639222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2237306366643065382d316431642d343161332d393363322d623765306439656363626234222c226c616254657374496473223a5b5d2c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdb6c697dc6754e042594e51bbe8c229b4074485d86991c4f59dd12571b06054	2026-10-22 00:45:47.967831+00
clinical-exam-clearance	clinical:clearance:v1:fa6622b0-d820-4c4e-9bf6-26671394453b	8aa0aec0-16ae-400e-880b-36342e617c0e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238363837346637332d636364332d343932652d616435392d643036346532353230663632222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f64654964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266613636323262302d643832302d346334652d396266362d323636373133393434353362222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236613131633030652d616664382d346562392d396136662d366332366666613836643932222c226c616254657374496473223a5b5d2c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66fc10a0738c63a07b454b60ef4f5faaa1bd426dd9e7512c89cce4afd2452f05	2026-10-22 00:45:54.141238+00
clinical-prescription	clinical:prescription:v1:bfc025df-98c4-4244-bf55-3287331fc5e9	6400782d-fa9c-41f8-a5c3-95b1ad7081ce	\\x7b226576656e7454797065223a22707265736372697074696f6e2e66696c6c6564222c227061796c6f6164223a7b2263617265436f6e74657874223a224f555450415449454e54222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c2264697370656e73654964223a2262666330323564662d393863342d343234342d626635352d333238373333316663356539222c2266696c6c65644174223a22323032362d31302d32325430303a35323a31372e3338323630343730305a222c226974656d73223a5b7b22647275674964223a2237316138616464352d353838322d343466302d383562622d386531336561306430306661222c22647275674e616d65223a2250617261636574616d6f6c20353030206d67222c227175616e74697479223a362c22756e69745072696365223a312e35452b337d5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c227072696365436f6465223a2244525547222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c22736f757263654964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c22736f7572636554797065223a22505245534352495054494f4e222c22746f74616c416d6f756e74223a39452b337d2c2270726f6475636572223a22706861726d6163792d73657276696365222c2276657273696f6e223a317d	f82f312fc77b03cdae51769f5b21c46969a072974ad0bb51f62aef9de18f8bbe	2026-10-22 00:52:18.003759+00
clinical-exam-clearance	clinical:clearance:v1:76283443-9e57-4e50-b63c-ede3cac9664d	68d00a96-48cf-4e4d-a88f-b71c0d9a5010	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2261383036666461332d306232362d346263312d393666642d383566366137346361323934222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f64654964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2237363238333434332d396535372d346535302d623633632d656465336361633936363464222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2266626262376137632d373232372d343131382d613231632d363666333066623361643732222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	82c0ac8c545c2235841a019718fb9149e00875864133eeff942ff0d974f62d5c	2026-10-22 00:52:52.732607+00
clinical-exam-clearance	clinical:clearance:v1:0c7cc504-61b9-4f1d-ac04-fda127f2cb86	20a4d671-939e-43a8-b642-62a283362106	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2234626161313666612d326166382d343362302d396231392d316633326337663237356163222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f64654964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2230633763633530342d363162392d346631642d616330342d666461313237663263623836222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236363362313363632d393735382d343631352d613761312d643430316464626536653231222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4d84b66b347ccd01a340dd4b1bc3140df245ef15938abb3b23b112d8d7de143	2026-10-22 00:56:55.905993+00
clinical-exam-clearance	clinical:clearance:v1:80f4ed5d-2194-4056-be84-bd056d4e1599	ad4889ed-ca90-4c64-8368-204493565785	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2235633332356532372d623734642d343964372d623865662d326337363336333039363762222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f64654964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238306634656435642d323139342d343035362d626538342d626430353664346531353939222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238653730356237662d383034642d343864382d613066342d313061386134646631653964222c226c616254657374496473223a5b5d2c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdbcabda3e950c883d589e162104af7de4b8443cdfb78492310dfc3ade29d32e	2026-10-22 10:53:23.717267+00
clinical-exam-clearance	clinical:clearance:v1:fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	0e7ccf12-b2f0-439e-b271-6232e8d66f39	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2236393431623739322d613934382d346163622d623532662d366364636232366235393536222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f64654964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266653861376662652d663632352d346532322d613662652d613266356639393763646465222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2261356437383865362d336165302d346564322d626233642d303338336539323161363238222c226c616254657374496473223a5b5d2c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	a28b373978142a2202e27cc4c5cdd14a49562dc7194ba75f3a95e73576d8f7fb	2026-10-22 11:41:34.710248+00
\.


--
-- Data for Name: diagnosis; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.diagnosis (diagnosis_id, record_id, diagnosis_name, description, icd_code, created_at) FROM stdin;
d5266a8d-0bef-47a5-9489-cfc1f77cf17c	4447ad07-fe64-4d41-8c96-7a278d167be2	Theo dõi nhiễm khuẩn hô hấp trên	Theo dõi triệu chứng và tái khám theo hẹn	J06.9	2026-10-22 00:40:40.838753+00
a5f693b5-8eb7-45de-b0f0-c61efef057b0	1361a316-8aa3-4ad4-8774-f852d8c64fa1	Theo dõi nhiễm khuẩn hô hấp trên	Theo dõi triệu chứng và tái khám theo hẹn	J06.9	2026-10-22 00:44:58.242289+00
c4e44f79-dbf2-4ca9-8922-79748ee9a8c5	acc4a82c-6856-469d-80be-8dc343a9e06e	Theo dõi nhiễm khuẩn hô hấp trên	Theo dõi triệu chứng và tái khám theo hẹn	J06.9	2026-10-22 00:45:40.19454+00
7e0d3ca4-ae79-49f0-977c-6129d836a5fd	1d4e99c7-3572-42be-9520-a43c1eedc030	Theo dõi nhiễm khuẩn hô hấp trên	Theo dõi triệu chứng và tái khám theo hẹn	J06.9	2026-10-22 00:45:49.065476+00
\.


--
-- Data for Name: exam_clearance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.exam_clearance (clearance_id, event_id, invoice_id, account_id, appointment_id, record_id, patient_id, care_episode_type, care_episode_id, amount, currency, expires_at, emergency_override, granted_at, created_at) FROM stdin;
86de69c5-4b2e-4458-83e0-6677efc159aa	99a7d7b4-2942-4dfb-b648-a37002ffe6a7	48bdd0f9-a9c4-45bc-9987-40df9770e1b0	2b0753ea-f892-4484-81c6-644b08832cc2	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	\N	c36277c4-347f-480d-8473-bcf9f98c8a44	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	100000.00	VND	\N	f	2026-10-22 00:40:16.917046+00	2026-10-22 00:40:17.364555+00
49f69633-3e1f-4ef3-ab1e-c7b344ab174a	57bdeb4a-1402-44d3-8e9a-fe70eec15d8d	92244ff4-319a-4ccc-8bcd-cb76aa714e08	e4463013-b866-45d6-aeeb-20d5e6c07197	71626749-ef62-42f4-9d40-35097f05dfb9	\N	30820325-711c-4d7b-b364-be73c78a43b0	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	100000.00	VND	\N	f	2026-10-22 00:40:39.521339+00	2026-10-22 00:40:40.246977+00
b9ec057b-c3a9-45b5-9e95-36109f530bc1	ec5b3a7c-269d-42d5-a3b7-fc28391aa064	1f14c914-9c13-4e48-9003-dd73422859fa	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	\N	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	100000.00	VND	\N	f	2026-10-22 00:44:56.990696+00	2026-10-22 00:44:57.897001+00
db1a5449-6c73-4772-8791-54039cb8111e	9c03fa80-daba-4a73-b1af-9dedd6e60eb2	2659a221-7b81-4a18-be74-2c0a873edad7	8f3e4603-9e61-481a-8cb5-3b485b4c8560	037fcbd9-127b-44d6-96ee-73c571763c5e	\N	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	100000.00	VND	\N	f	2026-10-22 00:45:38.928545+00	2026-10-22 00:45:39.620118+00
33e1d0e6-f85f-450b-9813-41a6e5b56cf9	9c3e6353-a2b1-4d52-be28-7689b60257d2	70cfd0e8-1d1d-41a3-93c2-b7e0d9eccbb4	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	250352fd-6ae9-4108-a147-2d76e9b49eb9	\N	6f75b635-a491-4191-99b3-1df16c1a3832	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	100000.00	VND	\N	f	2026-10-22 00:45:47.836519+00	2026-10-22 00:45:47.967831+00
fa6622b0-d820-4c4e-9bf6-26671394453b	8aa0aec0-16ae-400e-880b-36342e617c0e	6a11c00e-afd8-4eb9-9a6f-6c26ffa86d92	86874f73-ccd3-492e-ad59-d064e2520f62	a4fde265-3562-4528-8ea6-0db0e835c1d6	\N	1270368c-7e70-40eb-852c-b58d7b052cf7	OUTPATIENT_VISIT	a4fde265-3562-4528-8ea6-0db0e835c1d6	100000.00	VND	\N	f	2026-10-22 00:45:52.492536+00	2026-10-22 00:45:54.141238+00
76283443-9e57-4e50-b63c-ede3cac9664d	68d00a96-48cf-4e4d-a88f-b71c0d9a5010	fbbb7a7c-7227-4118-a21c-66f30fb3ad72	a806fda3-0b26-4bc1-96fd-85f6a74ca294	b264777a-2a76-4793-b4ac-91e6a8779f0d	\N	3ad6444d-54b2-467a-9e25-beb664d3975f	OUTPATIENT_VISIT	b264777a-2a76-4793-b4ac-91e6a8779f0d	100000.00	VND	\N	f	2026-10-22 00:52:51.000335+00	2026-10-22 00:52:52.732607+00
0c7cc504-61b9-4f1d-ac04-fda127f2cb86	20a4d671-939e-43a8-b642-62a283362106	663b13cc-9758-4615-a7a1-d401ddbe6e21	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	\N	83ee7cd5-ccb4-4005-96f3-42abb0261400	OUTPATIENT_VISIT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	100000.00	VND	\N	f	2026-10-22 00:56:55.535559+00	2026-10-22 00:56:55.905993+00
80f4ed5d-2194-4056-be84-bd056d4e1599	ad4889ed-ca90-4c64-8368-204493565785	8e705b7f-804d-48d8-a0f4-10a8a4df1e9d	5c325e27-b74d-49d7-b8ef-2c763630967b	b9de10f8-44e8-4e44-8637-09135b5691e8	\N	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	OUTPATIENT_VISIT	b9de10f8-44e8-4e44-8637-09135b5691e8	100000.00	VND	\N	f	2026-10-22 10:53:22.794782+00	2026-10-22 10:53:23.717267+00
fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	0e7ccf12-b2f0-439e-b271-6232e8d66f39	a5d788e6-3ae0-4ed2-bb3d-0383e921a628	6941b792-a948-4acb-b52f-6cdcb26b5956	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	\N	a2c54b05-3010-4b8d-93ee-67fb06469d77	OUTPATIENT_VISIT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	100000.00	VND	\N	f	2026-10-22 11:41:34.165632+00	2026-10-22 11:41:34.710248+00
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	1650751748	test	2026-10-22 07:39:12.379784	33	t
2	2	attached result	SQL	V2__attached_result.sql	641984771	test	2026-10-22 07:39:12.445345	11	t
3	3	processed event	SQL	V3__processed_event.sql	-127232650	test	2026-10-22 07:39:12.473735	5	t
4	4	care finance v2	SQL	V4__care_finance_v2.sql	-374638920	test	2026-10-22 07:39:12.490841	33	t
5	5	clinical outbox delivery state	SQL	V5__clinical_outbox_delivery_state.sql	-865481143	test	2026-10-22 07:39:12.539483	5	t
6	6	clinical lab orders	SQL	V6__clinical_lab_orders.sql	-727271659	test	2026-10-22 07:39:12.554338	9	t
7	7	consumer inbox	SQL	V7__consumer_inbox.sql	-874372880	test	2026-10-22 07:39:12.574533	12	t
8	8	appointment exam cancellation audit	SQL	V8__appointment_exam_cancellation_audit.sql	-1715343909	test	2026-10-22 07:39:12.5977	7	t
\.


--
-- Data for Name: medical_record; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.medical_record (record_id, patient_id, doctor_id, department_id, examination_date, symptoms, appointment_id, created_at, updated_at, status, disposition, disposition_note, completed_at) FROM stdin;
5917d49c-0715-416d-a482-c0b281926b43	c36277c4-347f-480d-8473-bcf9f98c8a44	ddf8f66b-7b09-43e0-8e24-dd24678c3414	b134eebd-d327-4718-8bef-92525164178d	2026-10-22	\N	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	2026-10-22 00:40:17.82253+00	\N	OPEN	\N	\N	\N
4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	Khám sức khỏe định kỳ. Tỉnh táo, tiếp xúc tốt; mạch 76 lần/phút.	71626749-ef62-42f4-9d40-35097f05dfb9	2026-10-22 00:40:40.626899+00	2026-10-22 00:40:40.831304+00	OPEN	\N	\N	\N
1361a316-8aa3-4ad4-8774-f852d8c64fa1	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	Đau đầu và mệt mỏi ba ngày. Tỉnh táo, tiếp xúc tốt; mạch 76 lần/phút.	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	2026-10-22 00:44:58.104163+00	2026-10-22 00:44:58.240151+00	OPEN	\N	\N	\N
acc4a82c-6856-469d-80be-8dc343a9e06e	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	Ho và đau họng. Tỉnh táo, tiếp xúc tốt; mạch 76 lần/phút.	037fcbd9-127b-44d6-96ee-73c571763c5e	2026-10-22 00:45:40.038426+00	2026-10-22 00:45:40.19196+00	OPEN	\N	\N	\N
1d4e99c7-3572-42be-9520-a43c1eedc030	6f75b635-a491-4191-99b3-1df16c1a3832	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	Tái khám huyết áp. Tỉnh táo, tiếp xúc tốt; mạch 76 lần/phút.	250352fd-6ae9-4108-a147-2d76e9b49eb9	2026-10-22 00:45:48.936871+00	2026-10-22 00:45:49.063385+00	OPEN	\N	\N	\N
\.


--
-- Data for Name: processed_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.processed_event (event_id, routing_key, processed_at) FROM stdin;
\.


--
-- Name: admission_referral admission_referral_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_referral
    ADD CONSTRAINT admission_referral_pkey PRIMARY KEY (admission_request_id);


--
-- Name: appointment appointment_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT appointment_pkey PRIMARY KEY (appointment_id);


--
-- Name: attached_result attached_result_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.attached_result
    ADD CONSTRAINT attached_result_pkey PRIMARY KEY (record_id, type, reference_id);


--
-- Name: clinical_emergency_override clinical_emergency_override_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_emergency_override
    ADD CONSTRAINT clinical_emergency_override_pkey PRIMARY KEY (override_id);


--
-- Name: clinical_lab_order clinical_lab_order_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_lab_order
    ADD CONSTRAINT clinical_lab_order_pkey PRIMARY KEY (source_order_id);


--
-- Name: clinical_outbox_event clinical_outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_outbox_event
    ADD CONSTRAINT clinical_outbox_event_pkey PRIMARY KEY (event_id);


--
-- Name: consumer_inbox_delivery consumer_inbox_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_delivery
    ADD CONSTRAINT consumer_inbox_delivery_pkey PRIMARY KEY (consumer_name, event_id);


--
-- Name: consumer_inbox_operation consumer_inbox_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_operation
    ADD CONSTRAINT consumer_inbox_operation_pkey PRIMARY KEY (consumer_name, operation_key);


--
-- Name: diagnosis diagnosis_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.diagnosis
    ADD CONSTRAINT diagnosis_pkey PRIMARY KEY (diagnosis_id);


--
-- Name: exam_clearance exam_clearance_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_clearance
    ADD CONSTRAINT exam_clearance_event_id_key UNIQUE (event_id);


--
-- Name: exam_clearance exam_clearance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.exam_clearance
    ADD CONSTRAINT exam_clearance_pkey PRIMARY KEY (clearance_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: medical_record medical_record_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT medical_record_pkey PRIMARY KEY (record_id);


--
-- Name: processed_event processed_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id);


--
-- Name: admission_referral uq_admission_referral_record; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_referral
    ADD CONSTRAINT uq_admission_referral_record UNIQUE (record_id);


--
-- Name: consumer_inbox_delivery uq_consumer_inbox_delivery_global_event; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_delivery
    ADD CONSTRAINT uq_consumer_inbox_delivery_global_event UNIQUE (event_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_appointment_department_date; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_appointment_department_date ON public.appointment USING btree (department_id, appointment_date);


--
-- Name: idx_appointment_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_appointment_patient ON public.appointment USING btree (patient_id);


--
-- Name: idx_appointment_patient_date_status; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_appointment_patient_date_status ON public.appointment USING btree (patient_id, appointment_date, status);


--
-- Name: idx_attached_result_reference; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_attached_result_reference ON public.attached_result USING btree (type, reference_id);


--
-- Name: idx_clinical_lab_order_record; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_clinical_lab_order_record ON public.clinical_lab_order USING btree (record_id, ordered_at);


--
-- Name: idx_clinical_outbox_dispatchable; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_clinical_outbox_dispatchable ON public.clinical_outbox_event USING btree (occurred_at, event_id) WHERE ((published_at IS NULL) AND (held_at IS NULL) AND (quarantined_at IS NULL));


--
-- Name: idx_clinical_outbox_unpublished; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_clinical_outbox_unpublished ON public.clinical_outbox_event USING btree (occurred_at) WHERE (published_at IS NULL);


--
-- Name: idx_diagnosis_record; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_diagnosis_record ON public.diagnosis USING btree (record_id);


--
-- Name: idx_exam_clearance_appointment; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_exam_clearance_appointment ON public.exam_clearance USING btree (appointment_id) WHERE (appointment_id IS NOT NULL);


--
-- Name: idx_exam_clearance_record; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_exam_clearance_record ON public.exam_clearance USING btree (record_id) WHERE ((appointment_id IS NULL) AND (record_id IS NOT NULL));


--
-- Name: idx_medical_record_department_date; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_medical_record_department_date ON public.medical_record USING btree (department_id, examination_date);


--
-- Name: idx_medical_record_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_medical_record_patient ON public.medical_record USING btree (patient_id);


--
-- Name: uq_appointment_pending_patient_date; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_appointment_pending_patient_date ON public.appointment USING btree (patient_id, appointment_date) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_medical_record_appointment; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_medical_record_appointment ON public.medical_record USING btree (appointment_id) WHERE (appointment_id IS NOT NULL);


--
-- Name: admission_referral admission_referral_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_referral
    ADD CONSTRAINT admission_referral_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.medical_record(record_id);


--
-- Name: attached_result attached_result_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.attached_result
    ADD CONSTRAINT attached_result_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.medical_record(record_id) ON DELETE CASCADE;


--
-- Name: clinical_emergency_override clinical_emergency_override_appointment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_emergency_override
    ADD CONSTRAINT clinical_emergency_override_appointment_id_fkey FOREIGN KEY (appointment_id) REFERENCES public.appointment(appointment_id);


--
-- Name: clinical_emergency_override clinical_emergency_override_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_emergency_override
    ADD CONSTRAINT clinical_emergency_override_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.medical_record(record_id);


--
-- Name: clinical_lab_order clinical_lab_order_care_episode_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_lab_order
    ADD CONSTRAINT clinical_lab_order_care_episode_id_fkey FOREIGN KEY (care_episode_id) REFERENCES public.appointment(appointment_id);


--
-- Name: clinical_lab_order clinical_lab_order_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.clinical_lab_order
    ADD CONSTRAINT clinical_lab_order_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.medical_record(record_id);


--
-- Name: diagnosis diagnosis_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.diagnosis
    ADD CONSTRAINT diagnosis_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.medical_record(record_id) ON DELETE CASCADE;


--
-- Name: consumer_inbox_operation fk_consumer_inbox_operation_delivery; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_operation
    ADD CONSTRAINT fk_consumer_inbox_operation_delivery FOREIGN KEY (consumer_name, first_event_id) REFERENCES public.consumer_inbox_delivery(consumer_name, event_id);


--
-- Name: medical_record medical_record_appointment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT medical_record_appointment_id_fkey FOREIGN KEY (appointment_id) REFERENCES public.appointment(appointment_id);


--
-- PostgreSQL database dump complete
--

\unrestrict gzbUtCryXbWYh8f8TpQuDlK0oAxJAWIXxsqrcETw2YuWQrSMlXbtfGd2eHy7N8B

--
-- Database "mediflow_lab" dump
--

--
-- PostgreSQL database dump
--

\restrict h9Af6u7kuZKpeAKWC8rpgOGGTDUrfQGPGeKwrHQHL3Mwwskf2lRIm8yJ6PYFnIF

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_lab; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_lab WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_lab OWNER TO test;

\unrestrict h9Af6u7kuZKpeAKWC8rpgOGGTDUrfQGPGeKwrHQHL3Mwwskf2lRIm8yJ6PYFnIF
\connect mediflow_lab
\restrict h9Af6u7kuZKpeAKWC8rpgOGGTDUrfQGPGeKwrHQHL3Mwwskf2lRIm8yJ6PYFnIF

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: consumer_inbox_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.consumer_inbox_delivery (
    consumer_name character varying(64) NOT NULL,
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    operation_key character varying(512) NOT NULL,
    raw_body bytea NOT NULL,
    raw_sha256 character varying(64) NOT NULL,
    canonical_business_bytes bytea NOT NULL,
    canonical_sha256 character varying(64) NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT consumer_inbox_delivery_canonical_business_bytes_check CHECK (((octet_length(canonical_business_bytes) >= 1) AND (octet_length(canonical_business_bytes) <= 1048576))),
    CONSTRAINT consumer_inbox_delivery_canonical_sha256_check CHECK (((canonical_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT consumer_inbox_delivery_raw_body_check CHECK (((octet_length(raw_body) >= 1) AND (octet_length(raw_body) <= 1048576))),
    CONSTRAINT consumer_inbox_delivery_raw_sha256_check CHECK (((raw_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.consumer_inbox_delivery OWNER TO test;

--
-- Name: consumer_inbox_operation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.consumer_inbox_operation (
    consumer_name character varying(64) NOT NULL,
    operation_key character varying(512) NOT NULL,
    first_event_id uuid NOT NULL,
    canonical_business_bytes bytea NOT NULL,
    canonical_sha256 character varying(64) NOT NULL,
    applied_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT consumer_inbox_operation_canonical_business_bytes_check CHECK (((octet_length(canonical_business_bytes) >= 1) AND (octet_length(canonical_business_bytes) <= 1048576))),
    CONSTRAINT consumer_inbox_operation_canonical_sha256_check CHECK (((canonical_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.consumer_inbox_operation OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: lab_emergency_override; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_emergency_override (
    override_id uuid NOT NULL,
    test_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    approved_by uuid NOT NULL,
    approver_role character varying(32) NOT NULL,
    reason text NOT NULL,
    approved_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.lab_emergency_override OWNER TO test;

--
-- Name: lab_financial_clearance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_financial_clearance (
    clearance_target_id uuid NOT NULL,
    clearance_id uuid NOT NULL,
    event_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    account_id uuid NOT NULL,
    test_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    expires_at timestamp with time zone,
    emergency_override boolean DEFAULT false NOT NULL,
    granted_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT lab_financial_clearance_amount_check CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.lab_financial_clearance OWNER TO test;

--
-- Name: lab_outbox_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_outbox_event (
    event_id uuid NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type character varying(100) NOT NULL,
    event_version integer NOT NULL,
    correlation_id character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    retry_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    quarantined_at timestamp with time zone,
    last_failure_code character varying(32)
);


ALTER TABLE public.lab_outbox_event OWNER TO test;

--
-- Name: lab_result; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_result (
    result_id uuid NOT NULL,
    test_id uuid NOT NULL,
    indicator character varying(100) NOT NULL,
    value character varying(50) NOT NULL,
    unit character varying(20),
    reference_range character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.lab_result OWNER TO test;

--
-- Name: lab_test; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.lab_test (
    test_id uuid NOT NULL,
    record_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    requesting_department_id uuid NOT NULL,
    test_type character varying(50) NOT NULL,
    requested_date date NOT NULL,
    performed_date date,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    conclusion text,
    is_paid boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    care_contract_version smallint DEFAULT 0 NOT NULL,
    care_episode_type character varying(32),
    care_episode_id uuid,
    source_order_id uuid,
    price_code character varying(64),
    clearance_id uuid,
    clearance_granted_at timestamp with time zone,
    emergency_override_id uuid,
    result_version integer DEFAULT 0 NOT NULL,
    cancelled_by uuid,
    cancellation_reason character varying(1000),
    cancelled_at timestamp with time zone,
    CONSTRAINT ck_lab_cancellation_audit CHECK ((((cancelled_by IS NULL) AND (cancellation_reason IS NULL) AND (cancelled_at IS NULL)) OR (((status)::text = 'CANCELLED'::text) AND (cancelled_by IS NOT NULL) AND (cancellation_reason IS NOT NULL) AND (length(TRIM(BOTH FROM cancellation_reason)) > 0) AND (cancelled_at IS NOT NULL)))),
    CONSTRAINT ck_lab_contract_version CHECK ((care_contract_version = ANY (ARRAY[0, 1]))),
    CONSTRAINT ck_lab_execution_gate CHECK (((care_contract_version = 0) OR ((status)::text <> ALL (ARRAY[('IN_PROGRESS'::character varying)::text, ('COMPLETED'::character varying)::text])) OR (clearance_id IS NOT NULL) OR (emergency_override_id IS NOT NULL))),
    CONSTRAINT ck_lab_v1_episode CHECK (((care_contract_version = 0) OR ((care_episode_type IS NOT NULL) AND ((care_episode_type)::text = ANY (ARRAY[('OUTPATIENT_VISIT'::character varying)::text, ('ADMISSION'::character varying)::text])) AND (care_episode_id IS NOT NULL) AND (price_code IS NOT NULL) AND (length(TRIM(BOTH FROM price_code)) > 0))))
);


ALTER TABLE public.lab_test OWNER TO test;

--
-- Name: processed_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.processed_event OWNER TO test;

--
-- Data for Name: consumer_inbox_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.consumer_inbox_delivery (consumer_name, event_id, routing_key, operation_key, raw_body, raw_sha256, canonical_business_bytes, canonical_sha256, received_at) FROM stdin;
lab-test-clearance	99a7d7b4-2942-4dfb-b648-a37002ffe6a7	financial.clearance.granted	lab:financial-clearance:v1:86de69c5-4b2e-4458-83e0-6677efc159aa	\\x7b226576656e744964223a2239396137643762342d323934322d346466622d623634382d613337303032666665366137222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34303a31362e3931373034353630305a222c22636f7272656c6174696f6e4964223a2234343964353264612d323862352d343464312d613531332d393964623539636665376432222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2238366465363963352d346232652d343435382d383365302d363637376566633135396161222c22696e766f6963654964223a2234386264643066392d613963342d343562632d393938372d343064663937373065316230222c226163636f756e744964223a2232623037353365612d663839322d343438342d383163362d363434623038383332636332222c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	74974495e9e499ab3fb57f0c3de0a5586920775b179533664cb338508811115f	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2232623037353365612d663839322d343438342d383163362d363434623038383332636332222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238366465363963352d346232652d343435382d383365302d363637376566633135396161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234386264643066392d613963342d343562632d393938372d343064663937373065316230222c226c616254657374496473223a5b5d2c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	4ef871dee45a5a6efe0611a8f9047e9d2e8d3a37e42b60573ff034bd18cab548	2026-10-22 00:40:17.384363+00
lab-medical-record	005b9f98-3f8c-4698-ac48-d7b62ab056ec	medicalrecord.created	lab:medical-record:v1:5917d49c-0715-416d-a482-c0b281926b43	\\x7b226576656e744964223a2230303562396639382d336638632d343639382d616334382d643762363261623035366563222c227061796c6f6164223a7b22646f63746f724964223a2264646638663636622d376230392d343365302d386532342d646432343637386333343134222c227265636f72644964223a2235393137643439632d303731352d343136642d613438322d633062323831393236623433222c22736f757263654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227072696365436f6465223a224f555450415449454e545f4558414d222c22736f7572636554797065223a224558414d222c226465706172746d656e744964223a2262313334656562642d643332372d343731382d386265662d393235323531363431373864222c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226578616d696e6174696f6e44617465223a22323032362d31302d3232227d2c2276657273696f6e223a312c2270726f6475636572223a22636c696e6963616c2d73657276696365222c226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c226f636375727265644174223a22323032362d31302d32325430303a34303a31372e3832323532393730305a222c22636f7272656c6174696f6e4964223a2237333235316231362d663039362d343436622d383332362d336634663861663763393630227d	6976c9bb905e394aa6c49c4a604d1f8696545a857767617e69ef16ec1fbd5742	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2262313334656562642d643332372d343731382d386265662d393235323531363431373864222c22646f63746f724964223a2264646638663636622d376230392d343365302d386532342d646432343637386333343134222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2235393137643439632d303731352d343136642d613438322d633062323831393236623433222c22736f757263654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	3438a6e01ce1878c1085ab66089295505db92d7ce97cc3cb303bd6db47605e26	2026-10-22 00:40:18.122731+00
lab-test-clearance	57bdeb4a-1402-44d3-8e9a-fe70eec15d8d	financial.clearance.granted	lab:financial-clearance:v1:49f69633-3e1f-4ef3-ab1e-c7b344ab174a	\\x7b226576656e744964223a2235376264656234612d313430322d343464332d386539612d666537306565633135643864222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34303a33392e3532313333383830305a222c22636f7272656c6174696f6e4964223a2232616134323763362d613566342d343032342d616333362d386265656431333064303730222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2234396636393633332d336531662d346566332d616231652d633762333434616231373461222c22696e766f6963654964223a2239323234346666342d333139612d346363632d386263642d636237366161373134653038222c226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	9298f14861f41ffba12ab40c2f854f171eb0298014eca614ae00a3fba7d6b83e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234396636393633332d336531662d346566332d616231652d633762333434616231373461222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2239323234346666342d333139612d346363632d386263642d636237366161373134653038222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	699008b881e037c5fd279ec050f409d5d69c792dcfb4794fac755b02cf3d2f13	2026-10-22 00:40:40.247791+00
lab-medical-record	e8177c6d-c3e7-469a-b9a5-bcb6affe790c	medicalrecord.created	lab:medical-record:v1:4447ad07-fe64-4d41-8c96-7a278d167be2	\\x7b226576656e744964223a2265383137376336642d633365372d343639612d623961352d626362366166666537393063222c227061796c6f6164223a7b22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c22736f757263654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227072696365436f6465223a224f555450415449454e545f4558414d222c22736f7572636554797065223a224558414d222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226578616d696e6174696f6e44617465223a22323032362d31302d3232227d2c2276657273696f6e223a312c2270726f6475636572223a22636c696e6963616c2d73657276696365222c226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c226f636375727265644174223a22323032362d31302d32325430303a34303a34302e3632363839393430305a222c22636f7272656c6174696f6e4964223a2237323566643933342d356531612d346463332d396137632d313634393664616164643662227d	50adaa46d41a84c02eaaa103cb88770a34fc0bfeb3318b0381bd600efdb19529	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c22736f757263654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	9ae6994a1b029a5ffacb454c359034ecd3184549a7c56a4771fcfe730cf9e7e9	2026-10-22 00:40:40.942041+00
lab-test-clearance	db0ade44-9af5-4b37-9105-3ec7930ad00b	financial.clearance.granted	lab:financial-clearance:v1:f774ec12-0ace-4926-b47a-1a97274cc814	\\x7b226576656e744964223a2264623061646534342d396166352d346233372d393130352d336563373933306164303062222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34303a34342e3538333131313430305a222c22636f7272656c6174696f6e4964223a2264633435613232652d306537352d343662612d613261302d366166646362313732333364222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2266373734656331322d306163652d343932362d623437612d316139373237346363383134222c22696e766f6963654964223a2263623937313133662d366231342d343639662d383433392d636531396165326161323037222c226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22707572706f7365223a224c41425f54455354222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c226c616254657374496473223a5b2234636466666161302d323065642d346164392d386133662d663039316361386564303838225d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3135303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	76235d1a1060aed9cb497e35ea5842bfc35ad125f23269913aab8948ce16ffc8	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266373734656331322d306163652d343932362d623437612d316139373237346363383134222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2263623937313133662d366231342d343639662d383433392d636531396165326161323037222c226c616254657374496473223a5b2234636466666161302d323065642d346164392d386133662d663039316361386564303838225d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4a14285fe2cf318f685238771561a063eeef6d300686e0a44ede695070a5514	2026-10-22 00:40:46.428228+00
lab-test-clearance	ec5b3a7c-269d-42d5-a3b7-fc28391aa064	financial.clearance.granted	lab:financial-clearance:v1:b9ec057b-c3a9-45b5-9e95-36109f530bc1	\\x7b226576656e744964223a2265633562336137632d323639642d343264352d613362372d666332383339316161303634222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34343a35362e3939303639363430305a222c22636f7272656c6174696f6e4964223a2231346665303062612d623830632d343039332d396661322d306538663262646465636539222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2262396563303537622d633361392d343562352d396539352d333631303966353330626331222c22696e766f6963654964223a2231663134633931342d396331332d346534382d393030332d646437333432323835396661222c226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	6314e109e64ef9ef488d45ed1f1a492041966cd6787379991e8ab927f0c7d711	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396563303537622d633361392d343562352d396539352d333631303966353330626331222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2231663134633931342d396331332d346534382d393030332d646437333432323835396661222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	2b9738565f55b6434732543b8132d0de971c3b91cc8da7ebef30452060315787	2026-10-22 00:44:57.896984+00
lab-medical-record	6ce6bcb7-ed30-4adf-8c58-d14bb363cb3f	medicalrecord.created	lab:medical-record:v1:1361a316-8aa3-4ad4-8774-f852d8c64fa1	\\x7b226576656e744964223a2236636536626362372d656433302d346164662d386335382d643134626233363363623366222c227061796c6f6164223a7b22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c22736f757263654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227072696365436f6465223a224f555450415449454e545f4558414d222c22736f7572636554797065223a224558414d222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226578616d696e6174696f6e44617465223a22323032362d31302d3232227d2c2276657273696f6e223a312c2270726f6475636572223a22636c696e6963616c2d73657276696365222c226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c226f636375727265644174223a22323032362d31302d32325430303a34343a35382e3130343136323830305a222c22636f7272656c6174696f6e4964223a2232386139663662362d616266632d343066302d613164332d666136653664346131643730227d	ca67207be1055fad73036f96b936f4ca87b55ab54eea0d7c3a5bcb6487fb8cb3	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c22736f757263654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	f33385c3945c414884761b9ff134f74c310ba452bceb58bbfae8d946cc118091	2026-10-22 00:44:58.417285+00
lab-test-clearance	7db9d05c-3d1c-40ae-84c5-6d427809bf76	financial.clearance.granted	lab:financial-clearance:v1:b9dc3502-c045-47d3-973d-07f7c296e12d	\\x7b226576656e744964223a2237646239643035632d336431632d343061652d383463352d366434323738303962663736222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a30322e3438393536323430305a222c22636f7272656c6174696f6e4964223a2263303563373136352d653232302d343937372d623166612d646163633830383436663164222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2262396463333530322d633034352d343764332d393733642d303766376332393665313264222c22696e766f6963654964223a2234353431313532642d663434372d346138382d626130642d623633656539323434643334222c226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22707572706f7365223a224c41425f54455354222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c226c616254657374496473223a5b2261393263393131382d653538662d346166622d623636362d373530333066653130363030225d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3135303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	f15b28845ff6d2fc73393184835c1827670b5aa8ad21b17a681d8d6772e94163	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396463333530322d633034352d343764332d393733642d303766376332393665313264222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234353431313532642d663434372d346138382d626130642d623633656539323434643334222c226c616254657374496473223a5b2261393263393131382d653538662d346166622d623636362d373530333066653130363030225d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	168d1b72593729bd8f690d4488c12f05d6b9fe58f4aaedb340ccabf31931f4e0	2026-10-22 00:45:03.007202+00
lab-test-clearance	74190a4b-a6b9-419f-8a27-c5882393cf55	financial.clearance.granted	lab:financial-clearance:v1:595165d5-e8c4-41e1-9203-1cd72c053d30	\\x7b226576656e744964223a2237343139306134622d613662392d343139662d386132372d633538383233393363663535222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33322e3230313636353830305a222c22636f7272656c6174696f6e4964223a2266383366656537352d396237312d343366392d383837632d663637323261363736326162222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2235393531363564352d653863342d343165312d393230332d316364373263303533643330222c22696e766f6963654964223a2265623337323030632d656239312d346337312d623435652d373332613962613737336338222c226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22707572706f7365223a22505245534352495054494f4e222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a393030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	8a50857436a67c57f5f1386c0d2e1f4798d4951bf06bec7f2fd05b8c02dd88b5	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2235393531363564352d653863342d343165312d393230332d316364373263303533643330222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265623337323030632d656239312d346337312d623435652d373332613962613737336338222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	0d7273b9a82233a6fb1fe534583d632d5a0d9aebc75c2bf6b45ab507304b246c	2026-10-22 00:45:33.44732+00
lab-test-clearance	736eea19-daa1-489e-8eda-499a93f4165b	financial.clearance.granted	lab:financial-clearance:v1:3dc5587a-41d2-4b78-8ea6-cd1fc05e9294	\\x7b226576656e744964223a2237333665656131392d646161312d343839652d386564612d343939613933663431363562222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33352e3733393531353530305a222c22636f7272656c6174696f6e4964223a2238666163613031312d386331302d343933372d393935662d396632643263633539613662222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2233646335353837612d343164322d346237382d386561362d636431666330356539323934222c22696e766f6963654964223a2238386534303139372d616339342d346138302d626539622d663934393334656561376666222c226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22707572706f7365223a22505245534352495054494f4e222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a393030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	9891cd743a3ad82f22ea40f4de19db8f0a340139bdc9f4ac8f53c2e81c3cf56e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233646335353837612d343164322d346237382d386561362d636431666330356539323934222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238386534303139372d616339342d346138302d626539622d663934393334656561376666222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	237d15f81e84f6ed3e2b20a4b936d1ad67a52adf84b639fcbaa4e122392d8a8c	2026-10-22 00:45:37.535812+00
lab-test-clearance	9c03fa80-daba-4a73-b1af-9dedd6e60eb2	financial.clearance.granted	lab:financial-clearance:v1:db1a5449-6c73-4772-8791-54039cb8111e	\\x7b226576656e744964223a2239633033666138302d646162612d346137332d623161662d396465646436653630656232222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a33382e3932383534355a222c22636f7272656c6174696f6e4964223a2237363931646637652d616361312d343433632d623130622d376533633335353333616162222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2264623161353434392d366337332d343737322d383739312d353430333963623831313165222c22696e766f6963654964223a2232363539613232312d376238312d346131382d626537342d326330613837336564616437222c226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	85e674244594ff370599eca3c7b758aec91a4d2320b02799892476af4735638b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2264623161353434392d366337332d343737322d383739312d353430333963623831313165222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2232363539613232312d376238312d346131382d626537342d326330613837336564616437222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	47db46dbecbc2872bf6890d9f5ecbb4c64e3a0ad7c17ae1e95f0eb6d30f194d7	2026-10-22 00:45:39.62011+00
lab-medical-record	cc458670-802b-46b5-bd67-a1fc24cab197	medicalrecord.created	lab:medical-record:v1:acc4a82c-6856-469d-80be-8dc343a9e06e	\\x7b226576656e744964223a2263633435383637302d383032622d343662352d626436372d613166633234636162313937222c227061796c6f6164223a7b22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c22736f757263654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227072696365436f6465223a224f555450415449454e545f4558414d222c22736f7572636554797065223a224558414d222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226578616d696e6174696f6e44617465223a22323032362d31302d3232227d2c2276657273696f6e223a312c2270726f6475636572223a22636c696e6963616c2d73657276696365222c226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c226f636375727265644174223a22323032362d31302d32325430303a34353a34302e3033383432353630305a222c22636f7272656c6174696f6e4964223a2261616566663237362d316363392d343830642d386336352d616139353861663139656634227d	a96ffaea638ca0d797f3495e0d217905fca3a2c8b6efc174ada00db08bd21228	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c22736f757263654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	aefc1cb220adb376ff4587c0356f6c7bb90539a5c09696454420b71d8824bb11	2026-10-22 00:45:40.318539+00
lab-test-clearance	12e1525e-15f7-426f-ba92-1aa941eab204	financial.clearance.granted	lab:financial-clearance:v1:432e20d2-1ca3-49ed-8aed-a17948dca7aa	\\x7b226576656e744964223a2231326531353235652d313566372d343236662d626139322d316161393431656162323034222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a34342e3433343739383330305a222c22636f7272656c6174696f6e4964223a2266646435663539392d346662352d346465372d623839652d623465653830366166626161222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2234333265323064322d316361332d343965642d386165642d613137393438646361376161222c22696e766f6963654964223a2265346331363831632d613261632d343061392d396136332d653837313338383732356665222c226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c22707572706f7365223a224c41425f54455354222c226170706f696e746d656e744964223a6e756c6c2c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c226c616254657374496473223a5b2262626265326234312d353036312d343338312d616234362d343732623633313830626164225d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3135303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	7dd5d78f208a23578735cd44bede0254c6d8de62f1a5ac6098408b577ece527e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234333265323064322d316361332d343965642d386165642d613137393438646361376161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265346331363831632d613261632d343061392d396136332d653837313338383732356665222c226c616254657374496473223a5b2262626265326234312d353036312d343338312d616234362d343732623633313830626164225d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66b3993fe2f04af465752c3e962935daf52314533f9b128daaef4d5cb3f1a0cc	2026-10-22 00:45:45.868384+00
lab-test-clearance	9c3e6353-a2b1-4d52-be28-7689b60257d2	financial.clearance.granted	lab:financial-clearance:v1:33e1d0e6-f85f-450b-9813-41a6e5b56cf9	\\x7b226576656e744964223a2239633365363335332d613262312d346435322d626532382d373638396236303235376432222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a34372e3833363531393230305a222c22636f7272656c6174696f6e4964223a2264316533376539342d633563332d346137312d383434612d373962616330303933616331222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2233336531643065362d663835662d343530622d393831332d343161366535623536636639222c22696e766f6963654964223a2237306366643065382d316431642d343161332d393363322d623765306439656363626234222c226163636f756e744964223a2264613065613865312d656363352d343363662d386631332d643263333235623666626637222c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	a5e5d19604a5b50d16bc3c19da4bd99df3530a04565e3962f97afb9c709dd482	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264613065613865312d656363352d343363662d386631332d643263333235623666626637222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233336531643065362d663835662d343530622d393831332d343161366535623536636639222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2237306366643065382d316431642d343161332d393363322d623765306439656363626234222c226c616254657374496473223a5b5d2c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdb6c697dc6754e042594e51bbe8c229b4074485d86991c4f59dd12571b06054	2026-10-22 00:45:47.967811+00
lab-medical-record	2b4d1827-b801-40fd-aa05-6c6ca5f4fdbc	medicalrecord.created	lab:medical-record:v1:1d4e99c7-3572-42be-9520-a43c1eedc030	\\x7b226576656e744964223a2232623464313832372d623830312d343066642d616130352d366336636135663466646263222c227061796c6f6164223a7b22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c227265636f72644964223a2231643465393963372d333537322d343262652d393532302d613433633165656463303330222c22736f757263654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227072696365436f6465223a224f555450415449454e545f4558414d222c22736f7572636554797065223a224558414d222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226578616d696e6174696f6e44617465223a22323032362d31302d3232227d2c2276657273696f6e223a312c2270726f6475636572223a22636c696e6963616c2d73657276696365222c226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c226f636375727265644174223a22323032362d31302d32325430303a34353a34382e3933363837313230305a222c22636f7272656c6174696f6e4964223a2263373932333361662d336264352d343832612d383232362d623163616465376362623863227d	336c432078068e3f71da79b6d81ae0334a571aa6f9992baa2b56b79299d38f8c	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2231643465393963372d333537322d343262652d393532302d613433633165656463303330222c22736f757263654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	aef08b8264c1dee06cbc23b9bc1b23c3b363cd62082bcd005b99fe0d990cd176	2026-10-22 00:45:49.770607+00
lab-test-clearance	8aa0aec0-16ae-400e-880b-36342e617c0e	financial.clearance.granted	lab:financial-clearance:v1:fa6622b0-d820-4c4e-9bf6-26671394453b	\\x7b226576656e744964223a2238616130616563302d313661652d343030652d383830622d333633343265363137633065222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a34353a35322e3439323533363330305a222c22636f7272656c6174696f6e4964223a2261373639323930332d376563302d343636332d393432342d333631343064323130373430222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2266613636323262302d643832302d346334652d396266362d323636373133393434353362222c22696e766f6963654964223a2236613131633030652d616664382d346562392d396136662d366332366666613836643932222c226163636f756e744964223a2238363837346637332d636364332d343932652d616435392d643036346532353230663632222c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	b42794a61461e074a3ba31223325eec5cd0acfe8954ab98ed6f749cd2ceec511	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238363837346637332d636364332d343932652d616435392d643036346532353230663632222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f64654964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266613636323262302d643832302d346334652d396266362d323636373133393434353362222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236613131633030652d616664382d346562392d396136662d366332366666613836643932222c226c616254657374496473223a5b5d2c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66fc10a0738c63a07b454b60ef4f5faaa1bd426dd9e7512c89cce4afd2452f05	2026-10-22 00:45:54.141344+00
lab-test-clearance	68d00a96-48cf-4e4d-a88f-b71c0d9a5010	financial.clearance.granted	lab:financial-clearance:v1:76283443-9e57-4e50-b63c-ede3cac9664d	\\x7b226576656e744964223a2236386430306139362d343863662d346534642d613838662d623731633064396135303130222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a35323a35312e3030303333343930305a222c22636f7272656c6174696f6e4964223a2237333232653235322d396530302d346133382d393833652d373735333431623563656566222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2237363238333434332d396535372d346535302d623633632d656465336361633936363464222c22696e766f6963654964223a2266626262376137632d373232372d343131382d613231632d363666333066623361643732222c226163636f756e744964223a2261383036666461332d306232362d346263312d393666642d383566366137346361323934222c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	0d20d8a0988254697158c70eff118206a34971ddbcfb35e70c8e06a607454859	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2261383036666461332d306232362d346263312d393666642d383566366137346361323934222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f64654964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2237363238333434332d396535372d346535302d623633632d656465336361633936363464222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2266626262376137632d373232372d343131382d613231632d363666333066623361643732222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	82c0ac8c545c2235841a019718fb9149e00875864133eeff942ff0d974f62d5c	2026-10-22 00:52:52.732374+00
lab-test-clearance	20a4d671-939e-43a8-b642-62a283362106	financial.clearance.granted	lab:financial-clearance:v1:0c7cc504-61b9-4f1d-ac04-fda127f2cb86	\\x7b226576656e744964223a2232306134643637312d393339652d343361382d623634322d363261323833333632313036222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325430303a35363a35352e3533353535393430305a222c22636f7272656c6174696f6e4964223a2262386135343337362d333937652d343136362d623165352d663361343534643561613739222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2230633763633530342d363162392d346631642d616330342d666461313237663263623836222c22696e766f6963654964223a2236363362313363632d393735382d343631352d613761312d643430316464626536653231222c226163636f756e744964223a2234626161313666612d326166382d343362302d396231392d316633326337663237356163222c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	a27e35704092ed1713ef9c1d7cfbe0796f19d3b44740805cb71c3b2c9b908a7b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2234626161313666612d326166382d343362302d396231392d316633326337663237356163222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f64654964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2230633763633530342d363162392d346631642d616330342d666461313237663263623836222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236363362313363632d393735382d343631352d613761312d643430316464626536653231222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4d84b66b347ccd01a340dd4b1bc3140df245ef15938abb3b23b112d8d7de143	2026-10-22 00:56:55.905994+00
lab-test-clearance	ad4889ed-ca90-4c64-8368-204493565785	financial.clearance.granted	lab:financial-clearance:v1:80f4ed5d-2194-4056-be84-bd056d4e1599	\\x7b226576656e744964223a2261643438383965642d636139302d346336342d383336382d323034343933353635373835222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325431303a35333a32322e3739343738323330305a222c22636f7272656c6174696f6e4964223a2230313766663536632d323166662d346162302d383834312d363861636532333035393563222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2238306634656435642d323139342d343035362d626538342d626430353664346531353939222c22696e766f6963654964223a2238653730356237662d383034642d343864382d613066342d313061386134646631653964222c226163636f756e744964223a2235633332356532372d623734642d343964372d623865662d326337363336333039363762222c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	f2bb39e10bc8c301f0f6b977c449ab5120ceb0209d60b3d205aa94f289beef0a	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2235633332356532372d623734642d343964372d623865662d326337363336333039363762222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f64654964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238306634656435642d323139342d343035362d626538342d626430353664346531353939222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238653730356237662d383034642d343864382d613066342d313061386134646631653964222c226c616254657374496473223a5b5d2c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdbcabda3e950c883d589e162104af7de4b8443cdfb78492310dfc3ade29d32e	2026-10-22 10:53:23.793356+00
lab-test-clearance	0e7ccf12-b2f0-439e-b271-6232e8d66f39	financial.clearance.granted	lab:financial-clearance:v1:fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	\\x7b226576656e744964223a2230653763636631322d623266302d343339652d623237312d363233326538643636663339222c226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c2276657273696f6e223a312c226f636375727265644174223a22323032362d31302d32325431313a34313a33342e3136353633313730305a222c22636f7272656c6174696f6e4964223a2238643565643764612d333830652d346339362d626165392d613464623230636138303831222c2270726f6475636572223a2262696c6c696e672d73657276696365222c227061796c6f6164223a7b22636c656172616e63654964223a2266653861376662652d663632352d346532322d613662652d613266356639393763646465222c22696e766f6963654964223a2261356437383865362d336165302d346564322d626233642d303338336539323161363238222c226163636f756e744964223a2236393431623739322d613934382d346163622d623532662d366364636232366235393536222c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c2263617265457069736f64654964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c22707572706f7365223a224558414d222c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c227265636f72644964223a6e756c6c2c226c616254657374496473223a5b5d2c22707265736372697074696f6e4964223a6e756c6c2c2261646d697373696f6e4964223a6e756c6c2c2273757267657279436173654964223a6e756c6c2c22616d6f756e74223a3130303030302e30302c2263757272656e6379223a22564e44222c227061796d656e744d6574686f64223a2243415348222c22657870697265734174223a6e756c6c2c22656d657267656e63794f76657272696465223a66616c73657d7d	4249f97ece4509f4e934ca9bd93f61ff3df1e05009c73bcb36f33d07fad07e9d	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2236393431623739322d613934382d346163622d623532662d366364636232366235393536222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f64654964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266653861376662652d663632352d346532322d613662652d613266356639393763646465222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2261356437383865362d336165302d346564322d626233642d303338336539323161363238222c226c616254657374496473223a5b5d2c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	a28b373978142a2202e27cc4c5cdd14a49562dc7194ba75f3a95e73576d8f7fb	2026-10-22 11:41:34.713695+00
\.


--
-- Data for Name: consumer_inbox_operation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.consumer_inbox_operation (consumer_name, operation_key, first_event_id, canonical_business_bytes, canonical_sha256, applied_at) FROM stdin;
lab-test-clearance	lab:financial-clearance:v1:86de69c5-4b2e-4458-83e0-6677efc159aa	99a7d7b4-2942-4dfb-b648-a37002ffe6a7	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2232623037353365612d663839322d343438342d383163362d363434623038383332636332222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238366465363963352d346232652d343435382d383365302d363637376566633135396161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234386264643066392d613963342d343562632d393938372d343064663937373065316230222c226c616254657374496473223a5b5d2c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	4ef871dee45a5a6efe0611a8f9047e9d2e8d3a37e42b60573ff034bd18cab548	2026-10-22 00:40:17.384363+00
lab-medical-record	lab:medical-record:v1:5917d49c-0715-416d-a482-c0b281926b43	005b9f98-3f8c-4698-ac48-d7b62ab056ec	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f64654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2262313334656562642d643332372d343731382d386265662d393235323531363431373864222c22646f63746f724964223a2264646638663636622d376230392d343365302d386532342d646432343637386333343134222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2263333632373763342d333437662d343830642d383437332d626366396639386338613434222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2235393137643439632d303731352d343136642d613438322d633062323831393236623433222c22736f757263654964223a2231313933356634662d363432382d343035312d626139652d386538613162376439663131222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	3438a6e01ce1878c1085ab66089295505db92d7ce97cc3cb303bd6db47605e26	2026-10-22 00:40:18.122731+00
lab-test-clearance	lab:financial-clearance:v1:49f69633-3e1f-4ef3-ab1e-c7b344ab174a	57bdeb4a-1402-44d3-8e9a-fe70eec15d8d	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234396636393633332d336531662d346566332d616231652d633762333434616231373461222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2239323234346666342d333139612d346363632d386263642d636237366161373134653038222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	699008b881e037c5fd279ec050f409d5d69c792dcfb4794fac755b02cf3d2f13	2026-10-22 00:40:40.247791+00
lab-medical-record	lab:medical-record:v1:4447ad07-fe64-4d41-8c96-7a278d167be2	e8177c6d-c3e7-469a-b9a5-bcb6affe790c	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c22736f757263654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	9ae6994a1b029a5ffacb454c359034ecd3184549a7c56a4771fcfe730cf9e7e9	2026-10-22 00:40:40.942041+00
lab-test-clearance	lab:financial-clearance:v1:f774ec12-0ace-4926-b47a-1a97274cc814	db0ade44-9af5-4b37-9105-3ec7930ad00b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266373734656331322d306163652d343932362d623437612d316139373237346363383134222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2263623937313133662d366231342d343639662d383433392d636531396165326161323037222c226c616254657374496473223a5b2234636466666161302d323065642d346164392d386133662d663039316361386564303838225d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2234343437616430372d666536342d346434312d386339362d376132373864313637626532222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4a14285fe2cf318f685238771561a063eeef6d300686e0a44ede695070a5514	2026-10-22 00:40:46.428228+00
lab-test-clearance	lab:financial-clearance:v1:b9ec057b-c3a9-45b5-9e95-36109f530bc1	ec5b3a7c-269d-42d5-a3b7-fc28391aa064	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396563303537622d633361392d343562352d396539352d333631303966353330626331222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2231663134633931342d396331332d346534382d393030332d646437333432323835396661222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	2b9738565f55b6434732543b8132d0de971c3b91cc8da7ebef30452060315787	2026-10-22 00:44:57.896984+00
lab-medical-record	lab:medical-record:v1:1361a316-8aa3-4ad4-8774-f852d8c64fa1	6ce6bcb7-ed30-4adf-8c58-d14bb363cb3f	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c22736f757263654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	f33385c3945c414884761b9ff134f74c310ba452bceb58bbfae8d946cc118091	2026-10-22 00:44:58.417285+00
lab-test-clearance	lab:financial-clearance:v1:b9dc3502-c045-47d3-973d-07f7c296e12d	7db9d05c-3d1c-40ae-84c5-6d427809bf76	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2262396463333530322d633034352d343764332d393733642d303766376332393665313264222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2234353431313532642d663434372d346138382d626130642d623633656539323434643334222c226c616254657374496473223a5b2261393263393131382d653538662d346166622d623636362d373530333066653130363030225d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2231333631613331362d386161332d346164342d383737342d663835326438633634666131222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	168d1b72593729bd8f690d4488c12f05d6b9fe58f4aaedb340ccabf31931f4e0	2026-10-22 00:45:03.007202+00
lab-test-clearance	lab:financial-clearance:v1:595165d5-e8c4-41e1-9203-1cd72c053d30	74190a4b-a6b9-419f-8a27-c5882393cf55	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2265343436333031332d623836362d343564362d616565622d323064356536633037313937222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2237313632363734392d656636322d343266342d396434302d333530393766303564666239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2235393531363564352d653863342d343165312d393230332d316364373263303533643330222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265623337323030632d656239312d346337312d623435652d373332613962613737336338222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233303832303332352d373131632d346437622d623336342d626537336337386134336230222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2238343131366535632d623864322d346166612d613538362d306332616631633335306162222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	0d7273b9a82233a6fb1fe534583d632d5a0d9aebc75c2bf6b45ab507304b246c	2026-10-22 00:45:33.44732+00
lab-test-clearance	lab:financial-clearance:v1:3dc5587a-41d2-4b78-8ea6-cd1fc05e9294	736eea19-daa1-489e-8eda-499a93f4165b	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264376337653365612d326131392d346465662d386365392d616166376234393039646235222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a393030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2265616234636230612d646533662d343935632d393330342d373366663166313862356434222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233646335353837612d343164322d346237382d386561362d636431666330356539323934222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238386534303139372d616339342d346138302d626539622d663934393334656561376666222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238646432356437352d366432392d343936372d616336652d643534633661386464343930222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a2231393936376463382d363262642d346264642d623431332d336633623461633166363961222c22707572706f7365223a22505245534352495054494f4e222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	237d15f81e84f6ed3e2b20a4b936d1ad67a52adf84b639fcbaa4e122392d8a8c	2026-10-22 00:45:37.535812+00
lab-test-clearance	lab:financial-clearance:v1:db1a5449-6c73-4772-8791-54039cb8111e	9c03fa80-daba-4a73-b1af-9dedd6e60eb2	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2264623161353434392d366337332d343737322d383739312d353430333963623831313165222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2232363539613232312d376238312d346131382d626537342d326330613837336564616437222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	47db46dbecbc2872bf6890d9f5ecbb4c64e3a0ad7c17ae1e95f0eb6d30f194d7	2026-10-22 00:45:39.62011+00
lab-medical-record	lab:medical-record:v1:acc4a82c-6856-469d-80be-8dc343a9e06e	cc458670-802b-46b5-bd67-a1fc24cab197	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c22736f757263654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	aefc1cb220adb376ff4587c0356f6c7bb90539a5c09696454420b71d8824bb11	2026-10-22 00:45:40.318539+00
lab-test-clearance	lab:financial-clearance:v1:432e20d2-1ca3-49ed-8aed-a17948dca7aa	12e1525e-15f7-426f-ba92-1aa941eab204	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238663365343630332d396536312d343831612d386362352d336234383562346338353630222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3135303030302e302c226170706f696e746d656e744964223a6e756c6c2c2263617265457069736f64654964223a2230333766636264392d313237622d343464362d393665652d373363353731373633633565222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2234333265323064322d316361332d343965642d386165642d613137393438646361376161222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2265346331363831632d613261632d343061392d396136332d653837313338383732356665222c226c616254657374496473223a5b2262626265326234312d353036312d343338312d616234362d343732623633313830626164225d2c2270617469656e744964223a2238666266353065342d363763612d343266362d393661632d633135643661616236316338222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224c41425f54455354222c227265636f72644964223a2261636334613832632d363835362d343639642d383062652d386463333433613965303665222c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66b3993fe2f04af465752c3e962935daf52314533f9b128daaef4d5cb3f1a0cc	2026-10-22 00:45:45.868384+00
lab-test-clearance	lab:financial-clearance:v1:33e1d0e6-f85f-450b-9813-41a6e5b56cf9	9c3e6353-a2b1-4d52-be28-7689b60257d2	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2264613065613865312d656363352d343363662d386631332d643263333235623666626637222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2233336531643065362d663835662d343530622d393831332d343161366535623536636639222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2237306366643065382d316431642d343161332d393363322d623765306439656363626234222c226c616254657374496473223a5b5d2c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdb6c697dc6754e042594e51bbe8c229b4074485d86991c4f59dd12571b06054	2026-10-22 00:45:47.967811+00
lab-medical-record	lab:medical-record:v1:1d4e99c7-3572-42be-9520-a43c1eedc030	2b4d1827-b801-40fd-aa05-6c6ca5f4fdbc	\\x7b226576656e7454797065223a226d65646963616c7265636f72642e63726561746564222c227061796c6f6164223a7b226170706f696e746d656e744964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f64654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c226465706172746d656e744964223a2236633536386430622d646562642d343461652d393939642d333934326531656366653862222c22646f63746f724964223a2265353234313835612d616462362d343864332d613836372d623339623235393934306632222c226578616d696e6174696f6e44617465223a22323032362d31302d3232222c2270617469656e744964223a2236663735623633352d613439312d343139312d393962332d316466313663316133383332222c227072696365436f6465223a224f555450415449454e545f4558414d222c227265636f72644964223a2231643465393963372d333537322d343262652d393532302d613433633165656463303330222c22736f757263654964223a2232353033353266642d366165392d343130382d613134372d326437366539623439656239222c22736f7572636554797065223a224558414d227d2c2270726f6475636572223a22636c696e6963616c2d73657276696365222c2276657273696f6e223a317d	aef08b8264c1dee06cbc23b9bc1b23c3b363cd62082bcd005b99fe0d990cd176	2026-10-22 00:45:49.770607+00
lab-test-clearance	lab:financial-clearance:v1:fa6622b0-d820-4c4e-9bf6-26671394453b	8aa0aec0-16ae-400e-880b-36342e617c0e	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2238363837346637332d636364332d343932652d616435392d643036346532353230663632222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f64654964223a2261346664653236352d333536322d343532382d386561362d306462306538333563316436222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266613636323262302d643832302d346334652d396266362d323636373133393434353362222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236613131633030652d616664382d346562392d396136662d366332366666613836643932222c226c616254657374496473223a5b5d2c2270617469656e744964223a2231323730333638632d376537302d343065622d383532632d623538643762303532636637222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	66fc10a0738c63a07b454b60ef4f5faaa1bd426dd9e7512c89cce4afd2452f05	2026-10-22 00:45:54.141344+00
lab-test-clearance	lab:financial-clearance:v1:76283443-9e57-4e50-b63c-ede3cac9664d	68d00a96-48cf-4e4d-a88f-b71c0d9a5010	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2261383036666461332d306232362d346263312d393666642d383566366137346361323934222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f64654964223a2262323634373737612d326137362d343739332d623461632d393165366138373739663064222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2237363238333434332d396535372d346535302d623633632d656465336361633936363464222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2266626262376137632d373232372d343131382d613231632d363666333066623361643732222c226c616254657374496473223a5b5d2c2270617469656e744964223a2233616436343434642d353462322d343637612d396532352d626562363634643339373566222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	82c0ac8c545c2235841a019718fb9149e00875864133eeff942ff0d974f62d5c	2026-10-22 00:52:52.732374+00
lab-test-clearance	lab:financial-clearance:v1:0c7cc504-61b9-4f1d-ac04-fda127f2cb86	20a4d671-939e-43a8-b642-62a283362106	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2234626161313666612d326166382d343362302d396231392d316633326337663237356163222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f64654964223a2237346663616535352d306666322d346630632d616536342d656138343764633361363662222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2230633763633530342d363162392d346631642d616330342d666461313237663263623836222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2236363362313363632d393735382d343631352d613761312d643430316464626536653231222c226c616254657374496473223a5b5d2c2270617469656e744964223a2238336565376364352d636362342d343030352d393666332d343261626230323631343030222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	c4d84b66b347ccd01a340dd4b1bc3140df245ef15938abb3b23b112d8d7de143	2026-10-22 00:56:55.905994+00
lab-test-clearance	lab:financial-clearance:v1:80f4ed5d-2194-4056-be84-bd056d4e1599	ad4889ed-ca90-4c64-8368-204493565785	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2235633332356532372d623734642d343964372d623865662d326337363336333039363762222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f64654964223a2262396465313066382d343465382d346534342d383633372d303931333562353639316538222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2238306634656435642d323139342d343035362d626538342d626430353664346531353939222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2238653730356237662d383034642d343864382d613066342d313061386134646631653964222c226c616254657374496473223a5b5d2c2270617469656e744964223a2266633466653963342d613334632d343235322d626430622d336531373136303464336235222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	bdbcabda3e950c883d589e162104af7de4b8443cdfb78492310dfc3ade29d32e	2026-10-22 10:53:23.793356+00
lab-test-clearance	lab:financial-clearance:v1:fe8a7fbe-f625-4e22-a6be-a2f5f997cdde	0e7ccf12-b2f0-439e-b271-6232e8d66f39	\\x7b226576656e7454797065223a2266696e616e6369616c2e636c656172616e63652e6772616e746564222c227061796c6f6164223a7b226163636f756e744964223a2236393431623739322d613934382d346163622d623532662d366364636232366235393536222c2261646d697373696f6e4964223a6e756c6c2c22616d6f756e74223a3130303030302e302c226170706f696e746d656e744964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f64654964223a2266393161346361642d366537612d346232332d396633662d303831383766313638376563222c2263617265457069736f646554797065223a224f555450415449454e545f5649534954222c22636c656172616e63654964223a2266653861376662652d663632352d346532322d613662652d613266356639393763646465222c2263757272656e6379223a22564e44222c22656d657267656e63794f76657272696465223a66616c73652c22657870697265734174223a6e756c6c2c22696e766f6963654964223a2261356437383865362d336165302d346564322d626233642d303338336539323161363238222c226c616254657374496473223a5b5d2c2270617469656e744964223a2261326335346230352d333031302d346238642d393365652d363766623036343639643737222c227061796d656e744d6574686f64223a2243415348222c22707265736372697074696f6e4964223a6e756c6c2c22707572706f7365223a224558414d222c227265636f72644964223a6e756c6c2c2273757267657279436173654964223a6e756c6c7d2c2270726f6475636572223a2262696c6c696e672d73657276696365222c2276657273696f6e223a317d	a28b373978142a2202e27cc4c5cdd14a49562dc7194ba75f3a95e73576d8f7fb	2026-10-22 11:41:34.713695+00
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	-468522968	test	2026-10-22 07:39:46.511857	31	t
2	2	care finance v2	SQL	V2__care_finance_v2.sql	1538292794	test	2026-10-22 07:39:46.588962	57	t
3	3	consumer inbox	SQL	V3__consumer_inbox.sql	-874372880	test	2026-10-22 07:39:46.679157	18	t
4	4	outbox retry quarantine	SQL	V4__outbox_retry_quarantine.sql	1609093745	test	2026-10-22 07:39:46.718322	10	t
5	5	lab cancellation audit	SQL	V5__lab_cancellation_audit.sql	1047269604	test	2026-10-22 07:39:46.749969	16	t
\.


--
-- Data for Name: lab_emergency_override; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_emergency_override (override_id, test_id, patient_id, care_episode_type, care_episode_id, approved_by, approver_role, reason, approved_at, created_at) FROM stdin;
\.


--
-- Data for Name: lab_financial_clearance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_financial_clearance (clearance_target_id, clearance_id, event_id, invoice_id, account_id, test_id, patient_id, care_episode_type, care_episode_id, amount, currency, expires_at, emergency_override, granted_at, created_at) FROM stdin;
ce454943-64f1-434f-95da-ca68616bb6d6	f774ec12-0ace-4926-b47a-1a97274cc814	db0ade44-9af5-4b37-9105-3ec7930ad00b	cb97113f-6b14-469f-8439-ce19ae2aa207	e4463013-b866-45d6-aeeb-20d5e6c07197	4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	30820325-711c-4d7b-b364-be73c78a43b0	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	150000.00	VND	\N	f	2026-10-22 00:40:44.583111+00	2026-10-22 00:40:46.486932+00
3e0d35c0-ca88-49a9-ac35-627e3a44fbfe	b9dc3502-c045-47d3-973d-07f7c296e12d	7db9d05c-3d1c-40ae-84c5-6d427809bf76	4541152d-f447-4a88-ba0d-b63ee9244d34	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	a92c9118-e58f-4afb-b666-75030fe10600	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	150000.00	VND	\N	f	2026-10-22 00:45:02.489562+00	2026-10-22 00:45:03.018295+00
cb63c5a4-7eea-4f3b-8023-6c2cd32eaa51	432e20d2-1ca3-49ed-8aed-a17948dca7aa	12e1525e-15f7-426f-ba92-1aa941eab204	e4c1681c-a2ac-40a9-9a63-e871388725fe	8f3e4603-9e61-481a-8cb5-3b485b4c8560	bbbe2b41-5061-4381-ab46-472b63180bad	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	150000.00	VND	\N	f	2026-10-22 00:45:44.434798+00	2026-10-22 00:45:45.890581+00
\.


--
-- Data for Name: lab_outbox_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_outbox_event (event_id, aggregate_type, aggregate_id, event_type, event_version, correlation_id, payload, occurred_at, published_at, retry_count, next_attempt_at, quarantined_at, last_failure_code) FROM stdin;
0bf095e0-3af3-49d5-953c-c32248a202f9	LAB_TEST	4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	lab.request.created	1	c9515b8a-7cfa-47e1-97a1-73f9320d568e	{"eventId": "0bf095e0-3af3-49d5-953c-c32248a202f9", "payload": {"labId": "4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088", "labType": "CBC", "recordId": "4447ad07-fe64-4d41-8c96-7a278d167be2", "sourceId": "4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "LAB", "sourceType": "LAB_TEST", "requestedAt": "2026-10-22T00:40:41.435904600Z", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "sourceOrderId": "bfb6c454-9d0f-4777-9460-eea5721d50f8", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "lab-service", "eventType": "lab.request.created", "occurredAt": "2026-10-22T00:40:41.435904600Z", "correlationId": "c9515b8a-7cfa-47e1-97a1-73f9320d568e"}	2026-10-22 00:40:41.435905+00	2026-10-22 00:40:41.776979+00	0	\N	\N	\N
9fc128f4-247e-4ca3-a85b-e7c5b56b481b	LAB_TEST	a92c9118-e58f-4afb-b666-75030fe10600	lab.request.created	1	cb39abf9-bc8e-4f87-999e-7213965dba73	{"eventId": "9fc128f4-247e-4ca3-a85b-e7c5b56b481b", "payload": {"labId": "a92c9118-e58f-4afb-b666-75030fe10600", "labType": "CBC", "recordId": "1361a316-8aa3-4ad4-8774-f852d8c64fa1", "sourceId": "a92c9118-e58f-4afb-b666-75030fe10600", "patientId": "8dd25d75-6d29-4967-ac6e-d54c6a8dd490", "priceCode": "LAB", "sourceType": "LAB_TEST", "requestedAt": "2026-10-22T00:44:58.336530400Z", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "sourceOrderId": "08f32690-ca20-4c8c-b075-d9a019e281ad", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "lab-service", "eventType": "lab.request.created", "occurredAt": "2026-10-22T00:44:58.336530400Z", "correlationId": "cb39abf9-bc8e-4f87-999e-7213965dba73"}	2026-10-22 00:44:58.33653+00	2026-10-22 00:44:59.22771+00	0	\N	\N	\N
4bed985d-04ff-4eee-87a2-7e24dd3ec44d	LAB_TEST	bbbe2b41-5061-4381-ab46-472b63180bad	lab.request.created	1	c7df6f25-0329-41d5-91a5-8480c311f83c	{"eventId": "4bed985d-04ff-4eee-87a2-7e24dd3ec44d", "payload": {"labId": "bbbe2b41-5061-4381-ab46-472b63180bad", "labType": "CBC", "recordId": "acc4a82c-6856-469d-80be-8dc343a9e06e", "sourceId": "bbbe2b41-5061-4381-ab46-472b63180bad", "patientId": "8fbf50e4-67ca-42f6-96ac-c15d6aab61c8", "priceCode": "LAB", "sourceType": "LAB_TEST", "requestedAt": "2026-10-22T00:45:40.287795300Z", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "037fcbd9-127b-44d6-96ee-73c571763c5e", "sourceOrderId": "1c25abad-5ea6-4e62-b72a-a9f5176e29f6", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "lab-service", "eventType": "lab.request.created", "occurredAt": "2026-10-22T00:45:40.287795300Z", "correlationId": "c7df6f25-0329-41d5-91a5-8480c311f83c"}	2026-10-22 00:45:40.287795+00	2026-10-22 00:45:40.827685+00	0	\N	\N	\N
3f1d13bb-a8f6-461c-99e3-99d34c9e1049	LAB_TEST	ad116d89-b9f6-4cef-a3de-028d541b00ef	lab.request.created	1	44f37b1a-7e95-48bb-b03d-b8d3da93e73d	{"eventId": "3f1d13bb-a8f6-461c-99e3-99d34c9e1049", "payload": {"labId": "ad116d89-b9f6-4cef-a3de-028d541b00ef", "labType": "CBC", "recordId": "1d4e99c7-3572-42be-9520-a43c1eedc030", "sourceId": "ad116d89-b9f6-4cef-a3de-028d541b00ef", "patientId": "6f75b635-a491-4191-99b3-1df16c1a3832", "priceCode": "LAB", "sourceType": "LAB_TEST", "requestedAt": "2026-10-22T00:45:49.151243300Z", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "250352fd-6ae9-4108-a147-2d76e9b49eb9", "sourceOrderId": "7d0abb3e-4dc3-4f47-956a-07abf29bceeb", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "lab-service", "eventType": "lab.request.created", "occurredAt": "2026-10-22T00:45:49.151243300Z", "correlationId": "44f37b1a-7e95-48bb-b03d-b8d3da93e73d"}	2026-10-22 00:45:49.151243+00	2026-10-22 00:45:49.996932+00	0	\N	\N	\N
53d52fd0-74cb-4b31-853b-d2793e83af2d	LAB_TEST	cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932	lab.request.created	1	07fab716-d019-4a32-81e5-f2140872af4a	{"eventId": "53d52fd0-74cb-4b31-853b-d2793e83af2d", "payload": {"labId": "cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932", "labType": "GLU", "recordId": "4447ad07-fe64-4d41-8c96-7a278d167be2", "sourceId": "cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "LAB", "sourceType": "LAB_TEST", "requestedAt": "2026-10-22T00:49:15.753991600Z", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "sourceOrderId": "53ed2165-8991-493e-bc9b-0067d26283b9", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "lab-service", "eventType": "lab.request.created", "occurredAt": "2026-10-22T00:49:15.753991600Z", "correlationId": "07fab716-d019-4a32-81e5-f2140872af4a"}	2026-10-22 00:49:15.753992+00	2026-10-22 00:49:16.597746+00	0	\N	\N	\N
8ffb5648-45e2-4e02-9130-dd21134bacde	LAB_TEST	31142159-4304-44d2-9e21-acae9bd325b3	lab.request.created	1	560045ee-af74-4af6-b232-341809359429	{"eventId": "8ffb5648-45e2-4e02-9130-dd21134bacde", "payload": {"labId": "31142159-4304-44d2-9e21-acae9bd325b3", "labType": "CRP theo dõi Review3", "recordId": "4447ad07-fe64-4d41-8c96-7a278d167be2", "sourceId": "31142159-4304-44d2-9e21-acae9bd325b3", "patientId": "30820325-711c-4d7b-b364-be73c78a43b0", "priceCode": "LAB", "sourceType": "LAB_TEST", "requestedAt": "2026-10-22T10:57:04.372528200Z", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "sourceOrderId": "c49301f4-75fa-49d9-a327-7fd17164bcb8", "careEpisodeType": "OUTPATIENT_VISIT", "emergencyOverrideId": null}, "version": 1, "producer": "lab-service", "eventType": "lab.request.created", "occurredAt": "2026-10-22T10:57:04.372528200Z", "correlationId": "560045ee-af74-4af6-b232-341809359429"}	2026-10-22 10:57:04.372528+00	2026-10-22 10:57:05.414154+00	0	\N	\N	\N
\.


--
-- Data for Name: lab_result; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_result (result_id, test_id, indicator, value, unit, reference_range, created_at) FROM stdin;
\.


--
-- Data for Name: lab_test; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.lab_test (test_id, record_id, patient_id, requesting_department_id, test_type, requested_date, performed_date, status, conclusion, is_paid, created_at, updated_at, care_contract_version, care_episode_type, care_episode_id, source_order_id, price_code, clearance_id, clearance_granted_at, emergency_override_id, result_version, cancelled_by, cancellation_reason, cancelled_at) FROM stdin;
4cdffaa0-20ed-4ad9-8a3f-f091ca8ed088	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	CBC	2026-10-22	\N	READY	\N	f	2026-10-22 00:40:41.345353+00	2026-10-22 00:40:46.491663+00	1	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	bfb6c454-9d0f-4777-9460-eea5721d50f8	LAB	f774ec12-0ace-4926-b47a-1a97274cc814	2026-10-22 00:40:44.583111+00	\N	0	\N	\N	\N
a92c9118-e58f-4afb-b666-75030fe10600	1361a316-8aa3-4ad4-8774-f852d8c64fa1	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	CBC	2026-10-22	\N	READY	\N	f	2026-10-22 00:44:58.332833+00	2026-10-22 00:45:03.019963+00	1	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	08f32690-ca20-4c8c-b075-d9a019e281ad	LAB	b9dc3502-c045-47d3-973d-07f7c296e12d	2026-10-22 00:45:02.489562+00	\N	0	\N	\N	\N
bbbe2b41-5061-4381-ab46-472b63180bad	acc4a82c-6856-469d-80be-8dc343a9e06e	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	CBC	2026-10-22	\N	READY	\N	f	2026-10-22 00:45:40.284079+00	2026-10-22 00:45:45.892814+00	1	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	1c25abad-5ea6-4e62-b72a-a9f5176e29f6	LAB	432e20d2-1ca3-49ed-8aed-a17948dca7aa	2026-10-22 00:45:44.434798+00	\N	0	\N	\N	\N
ad116d89-b9f6-4cef-a3de-028d541b00ef	1d4e99c7-3572-42be-9520-a43c1eedc030	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	CBC	2026-10-22	\N	AWAITING_PAYMENT	\N	f	2026-10-22 00:45:49.148081+00	2026-10-22 00:45:49.148081+00	1	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	7d0abb3e-4dc3-4f47-956a-07abf29bceeb	LAB	\N	\N	\N	0	\N	\N	\N
cb4d3ada-c563-4bf1-a0e3-ab7ec1d93932	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	GLU	2026-10-22	\N	AWAITING_PAYMENT	\N	f	2026-10-22 00:49:15.738784+00	2026-10-22 00:49:15.738784+00	1	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	53ed2165-8991-493e-bc9b-0067d26283b9	LAB	\N	\N	\N	0	\N	\N	\N
31142159-4304-44d2-9e21-acae9bd325b3	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	CRP theo dõi Review3	2026-10-22	\N	AWAITING_PAYMENT	\N	f	2026-10-22 10:57:04.291728+00	2026-10-22 10:57:04.291728+00	1	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	c49301f4-75fa-49d9-a327-7fd17164bcb8	LAB	\N	\N	\N	0	\N	\N	\N
\.


--
-- Data for Name: processed_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.processed_event (event_id, routing_key, processed_at) FROM stdin;
db0ade44-9af5-4b37-9105-3ec7930ad00b	financial.clearance.granted	2026-10-22 00:40:46.428228+00
7db9d05c-3d1c-40ae-84c5-6d427809bf76	financial.clearance.granted	2026-10-22 00:45:03.007202+00
12e1525e-15f7-426f-ba92-1aa941eab204	financial.clearance.granted	2026-10-22 00:45:45.868384+00
\.


--
-- Name: consumer_inbox_delivery consumer_inbox_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_delivery
    ADD CONSTRAINT consumer_inbox_delivery_pkey PRIMARY KEY (consumer_name, event_id);


--
-- Name: consumer_inbox_operation consumer_inbox_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_operation
    ADD CONSTRAINT consumer_inbox_operation_pkey PRIMARY KEY (consumer_name, operation_key);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: lab_emergency_override lab_emergency_override_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_emergency_override
    ADD CONSTRAINT lab_emergency_override_pkey PRIMARY KEY (override_id);


--
-- Name: lab_financial_clearance lab_financial_clearance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_financial_clearance
    ADD CONSTRAINT lab_financial_clearance_pkey PRIMARY KEY (clearance_target_id);


--
-- Name: lab_outbox_event lab_outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_outbox_event
    ADD CONSTRAINT lab_outbox_event_pkey PRIMARY KEY (event_id);


--
-- Name: lab_result lab_result_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_result
    ADD CONSTRAINT lab_result_pkey PRIMARY KEY (result_id);


--
-- Name: lab_test lab_test_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_test
    ADD CONSTRAINT lab_test_pkey PRIMARY KEY (test_id);


--
-- Name: processed_event processed_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id);


--
-- Name: consumer_inbox_delivery uq_consumer_inbox_delivery_global_event; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_delivery
    ADD CONSTRAINT uq_consumer_inbox_delivery_global_event UNIQUE (event_id);


--
-- Name: lab_financial_clearance uq_lab_clearance_event_target; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_financial_clearance
    ADD CONSTRAINT uq_lab_clearance_event_target UNIQUE (event_id, test_id);


--
-- Name: lab_financial_clearance uq_lab_clearance_target; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_financial_clearance
    ADD CONSTRAINT uq_lab_clearance_target UNIQUE (clearance_id, test_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_lab_clearance_test_expiry; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_clearance_test_expiry ON public.lab_financial_clearance USING btree (test_id, expires_at, granted_at DESC);


--
-- Name: idx_lab_episode; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_episode ON public.lab_test USING btree (care_episode_type, care_episode_id);


--
-- Name: idx_lab_outbox_dispatchable; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_outbox_dispatchable ON public.lab_outbox_event USING btree (occurred_at, event_id) WHERE ((published_at IS NULL) AND (quarantined_at IS NULL));


--
-- Name: idx_lab_outbox_unpublished; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_outbox_unpublished ON public.lab_outbox_event USING btree (occurred_at) WHERE (published_at IS NULL);


--
-- Name: idx_lab_override_test; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_override_test ON public.lab_emergency_override USING btree (test_id);


--
-- Name: idx_lab_result_test; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_result_test ON public.lab_result USING btree (test_id);


--
-- Name: idx_lab_test_department; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_test_department ON public.lab_test USING btree (requesting_department_id);


--
-- Name: idx_lab_test_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_test_patient ON public.lab_test USING btree (patient_id);


--
-- Name: idx_lab_test_record; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_lab_test_record ON public.lab_test USING btree (record_id);


--
-- Name: uq_lab_source_order; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_lab_source_order ON public.lab_test USING btree (source_order_id) WHERE (source_order_id IS NOT NULL);


--
-- Name: consumer_inbox_operation fk_consumer_inbox_operation_delivery; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.consumer_inbox_operation
    ADD CONSTRAINT fk_consumer_inbox_operation_delivery FOREIGN KEY (consumer_name, first_event_id) REFERENCES public.consumer_inbox_delivery(consumer_name, event_id);


--
-- Name: lab_emergency_override lab_emergency_override_test_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_emergency_override
    ADD CONSTRAINT lab_emergency_override_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.lab_test(test_id);


--
-- Name: lab_financial_clearance lab_financial_clearance_test_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_financial_clearance
    ADD CONSTRAINT lab_financial_clearance_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.lab_test(test_id);


--
-- Name: lab_result lab_result_test_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.lab_result
    ADD CONSTRAINT lab_result_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.lab_test(test_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict h9Af6u7kuZKpeAKWC8rpgOGGTDUrfQGPGeKwrHQHL3Mwwskf2lRIm8yJ6PYFnIF

--
-- Database "mediflow_notification" dump
--

--
-- PostgreSQL database dump
--

\restrict bjthm0mTMVqJsx66Blo489CaPaoasMytVdqiKECUU2EAdASSLTItowchVFJ3cV8

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_notification; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_notification WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_notification OWNER TO test;

\unrestrict bjthm0mTMVqJsx66Blo489CaPaoasMytVdqiKECUU2EAdASSLTItowchVFJ3cV8
\connect mediflow_notification
\restrict bjthm0mTMVqJsx66Blo489CaPaoasMytVdqiKECUU2EAdASSLTItowchVFJ3cV8

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: care_notification_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.care_notification_source (
    event_type character varying(100) NOT NULL,
    source_id uuid NOT NULL,
    payload_fingerprint character(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT care_notification_source_payload_fingerprint_check CHECK ((payload_fingerprint ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.care_notification_source OWNER TO test;

--
-- Name: care_receipt_byte_backfill; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.care_receipt_byte_backfill (
    event_id uuid NOT NULL,
    legacy_hash character(64) NOT NULL,
    raw_hash character(64) NOT NULL,
    verified_at timestamp with time zone DEFAULT now() NOT NULL,
    verified_by text DEFAULT CURRENT_USER NOT NULL,
    CONSTRAINT care_receipt_byte_backfill_legacy_hash_check CHECK ((legacy_hash ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT care_receipt_byte_backfill_raw_hash_check CHECK ((raw_hash ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.care_receipt_byte_backfill OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: notification; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.notification (
    notification_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    content text NOT NULL,
    channel character varying(10) NOT NULL,
    recipient_address character varying(150),
    status character varying(10) DEFAULT 'PENDING'::character varying NOT NULL,
    failure_reason character varying(255),
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    sent_at timestamp with time zone,
    template_key character varying(64),
    source_event_id uuid,
    source_event_type character varying(100),
    source_id uuid,
    correlation_id character varying(120),
    sensitivity character varying(32) DEFAULT 'STANDARD'::character varying NOT NULL
);


ALTER TABLE public.notification OWNER TO test;

--
-- Name: notification_event_outbox; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.notification_event_outbox (
    event_id uuid NOT NULL,
    notification_id uuid NOT NULL,
    payload text NOT NULL,
    publication_enabled boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    CONSTRAINT notification_event_outbox_publication_enabled_check CHECK ((publication_enabled = false))
);


ALTER TABLE public.notification_event_outbox OWNER TO test;

--
-- Name: processed_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL,
    payload_hash character(64),
    receipt_raw_hash character(64),
    CONSTRAINT processed_event_receipt_raw_hash_check CHECK (((receipt_raw_hash IS NULL) OR (receipt_raw_hash ~ '^[0-9a-f]{64}$'::text)))
);


ALTER TABLE public.processed_event OWNER TO test;

--
-- Name: surgery_notice_case; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_notice_case (
    surgery_case_id uuid NOT NULL,
    surgery_request_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    admission_id uuid,
    record_id uuid,
    terminal_event_type character varying(32),
    terminal_source_id uuid,
    CONSTRAINT surgery_notice_case_care_episode_type_check CHECK (((care_episode_type)::text = ANY (ARRAY[('ADMISSION'::character varying)::text, ('OUTPATIENT_VISIT'::character varying)::text]))),
    CONSTRAINT surgery_notice_case_check CHECK (((terminal_event_type IS NULL) = (terminal_source_id IS NULL))),
    CONSTRAINT surgery_notice_case_check1 CHECK (((((care_episode_type)::text = 'ADMISSION'::text) AND (admission_id IS NOT NULL) AND (admission_id = care_episode_id)) OR (((care_episode_type)::text = 'OUTPATIENT_VISIT'::text) AND (admission_id IS NULL)))),
    CONSTRAINT surgery_notice_case_terminal_event_type_check CHECK (((terminal_event_type)::text = ANY (ARRAY[('surgery.cancelled'::character varying)::text, ('surgery.completed'::character varying)::text])))
);


ALTER TABLE public.surgery_notice_case OWNER TO test;

--
-- Name: surgery_notice_snapshot; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_notice_snapshot (
    snapshot_id uuid NOT NULL,
    surgery_case_id uuid NOT NULL,
    schedule_id uuid NOT NULL,
    schedule_revision bigint NOT NULL,
    invalidated boolean DEFAULT false NOT NULL,
    CONSTRAINT surgery_notice_snapshot_schedule_revision_check CHECK ((schedule_revision > 0))
);


ALTER TABLE public.surgery_notice_snapshot OWNER TO test;

--
-- Name: surgery_notice_source; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_notice_source (
    event_type character varying(40) NOT NULL,
    source_id uuid NOT NULL,
    surgery_case_id uuid NOT NULL,
    payload_fingerprint character(64) NOT NULL,
    CONSTRAINT surgery_notice_source_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('surgery.ready'::character varying)::text, ('surgery.readiness.invalidated'::character varying)::text, ('surgery.cancelled'::character varying)::text, ('surgery.completed'::character varying)::text]))),
    CONSTRAINT surgery_notice_source_payload_fingerprint_check CHECK ((payload_fingerprint ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.surgery_notice_source OWNER TO test;

--
-- Data for Name: care_notification_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.care_notification_source (event_type, source_id, payload_fingerprint, created_at) FROM stdin;
prescription.created	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	5d0ae6198c42253d54aaecf30c6ed0ad21d06a8046bdc60e3d1e2db3398931f4	2026-10-22 00:40:19.709489+00
prescription.cancelled	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	e542e8d5513f177dedd52aad3e93aecd35538f3ce8379a9c3ecd7759568ec34c	2026-10-22 00:40:20.895529+00
invoice.created	cfab1271-de78-43bd-8b3d-d4425096c5db	bc76c22c8a907bfafdbabff9906331a52b9230998a355f0ae0cbd74cbc812513	2026-10-22 00:40:21.869803+00
prescription.created	84116e5c-b8d2-4afa-a586-0c2af1c350ab	8c6850096ceffb6c56325749f763068f089f1efbbb73c10ae10e6fe0c4fe08cc	2026-10-22 00:45:31.283674+00
invoice.created	0cff9bd3-381b-4b73-970f-b66e657e4435	d785a7094618ce6effb14791662164522d3b5c8a4bfe856ea5ebe23db66f51a7	2026-10-22 00:45:31.390672+00
prescription.filled	84116e5c-b8d2-4afa-a586-0c2af1c350ab	4e91a6510e4d41ca1fcd69fdcfbeebca355ea3106b679773813f18ef9f6d87ec	2026-10-22 00:45:35.351868+00
prescription.created	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	628e55b7e74d4a9db0f6e835bd51df23645ef86d03e11d97d0d76ff16b79a5e7	2026-10-22 00:45:35.504702+00
invoice.created	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	26f1b11af020cf943134273e01e2b2bccda76ea8705e286cfd8faccb18f02091	2026-10-22 00:45:36.505106+00
prescription.created	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	60bb0146763591440ac51bceb9069c9b89b8efeb077eabdd8584a5168a84554c	2026-10-22 00:45:46.703295+00
invoice.created	c80ea7fa-076a-4a07-98bc-8fe4b08982c9	362c832e7d7fd8ff7c252a7e8c41b1f27db4f4983cbfb106247aa0fa1da55963	2026-10-22 00:45:46.901801+00
prescription.created	7b8b6d90-897d-4439-8f89-4f6905e825ae	ff2e5e5a6555804aaa17abe2bd05260d965c1c88f70d7604e670d6a7db3aa62a	2026-10-22 00:45:49.765506+00
invoice.created	cba26ee1-8c27-4fc1-9b74-1083946a1db0	65d84171fec3119376f66d6bf10dbb55d661665722a3b174ba296a3bf7bedfa3	2026-10-22 00:45:53.089723+00
prescription.filled	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	5f0c981fe9f1fed3a258359f740e5d66a7413cdee0b0cdd1bda2d9100fe6baf6	2026-10-22 00:52:18.002303+00
\.


--
-- Data for Name: care_receipt_byte_backfill; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.care_receipt_byte_backfill (event_id, legacy_hash, raw_hash, verified_at, verified_by) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	1152831751	test	2026-10-22 07:39:47.510456	21	t
2	2	care receipt delivery	SQL	V2__care_receipt_delivery.sql	532990565	test	2026-10-22 07:39:47.554542	28	t
3	3	surgery reminder evidence	SQL	V3__surgery_reminder_evidence.sql	-8886266	test	2026-10-22 07:39:47.604446	34	t
4	4	care notification source	SQL	V4__care_notification_source.sql	1132493567	test	2026-10-22 07:39:47.657704	9	t
5	5	care receipt raw identity	SQL	V5__care_receipt_raw_identity.sql	-1616084308	test	2026-10-22 07:39:47.692406	24	t
\.


--
-- Data for Name: notification; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.notification (notification_id, patient_id, title, content, channel, recipient_address, status, failure_reason, retry_count, created_at, sent_at, template_key, source_event_id, source_event_type, source_id, correlation_id, sensitivity) FROM stdin;
607c138d-1e5e-4ff1-853e-9ddffadd79ed	c36277c4-347f-480d-8473-bcf9f98c8a44	Chào mừng đến với MediFlow	Xin chào Acceptance Patient c94387bd7e37, hồ sơ của bạn đã được tạo.	EMAIL	patient-c94387bd7e37@example.test	SENT	\N	1	2026-10-22 00:40:15.857507+00	2026-10-22 00:40:15.904525+00	\N	\N	\N	\N	\N	STANDARD
bc155f73-2617-4bec-8063-c153cfd6375f	c36277c4-347f-480d-8473-bcf9f98c8a44	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-23 lúc 09:00.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:16.028725+00	2026-10-22 00:40:16.033498+00	\N	\N	\N	\N	\N	STANDARD
9c15989b-b297-47ce-970d-65122fb7baf8	c36277c4-347f-480d-8473-bcf9f98c8a44	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:40:18.484693+00	2026-10-22 00:40:18.484693+00	PAYMENT_RECEIPT	f5f81d95-ffb2-476c-a67a-d5eaff2beaf2	payment.completed	c4830cfc-8783-4fe7-93a6-834d90b31eba	449d52da-28b5-44d1-a513-99db59cfe7d2	PRIVATE_IN_APP_ONLY
cfa6d50e-6826-48ed-8db6-d3bf94eb8b7a	c36277c4-347f-480d-8473-bcf9f98c8a44	Đã ghi nhận đơn thuốc	Đơn thuốc đã được ghi nhận. Vui lòng xem viện phí và hướng dẫn nhận thuốc.	IN_APP	\N	SENT	\N	0	2026-10-22 00:40:19.709489+00	2026-10-22 00:40:19.709489+00	OUTPATIENT_PRESCRIPTION_CREATED	7377fa4b-ea08-4ff1-a24f-5be31546e5a7	prescription.created	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	0acb8270-c779-4db9-befb-ff2a4fd77b6e	PRIVATE_IN_APP_ONLY
41c50a5b-9f4c-4272-ac51-bb1e1de2264b	c36277c4-347f-480d-8473-bcf9f98c8a44	Đơn thuốc đã hủy	Đơn thuốc đã được hủy. Vui lòng liên hệ nhà thuốc nếu cần hỗ trợ.	IN_APP	\N	SENT	\N	0	2026-10-22 00:40:20.895529+00	2026-10-22 00:40:20.895529+00	OUTPATIENT_PRESCRIPTION_CANCELLED	32b9c998-f101-42df-af40-0cd87c41fc40	prescription.cancelled	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	b21f51ec-be1c-4e83-987b-5b9f5ca32df7	PRIVATE_IN_APP_ONLY
c0050e46-1e98-4f0b-8bad-377eb3025cd6	c36277c4-347f-480d-8473-bcf9f98c8a44	Yêu cầu thanh toán đơn thuốc	Khoản thanh toán được yêu cầu: 30000 VND. Vui lòng liên hệ quầy thu ngân. Đây không phải biên nhận đã thanh toán và không xác nhận thuốc đã được cấp.	IN_APP	\N	SENT	\N	0	2026-10-22 00:40:21.869803+00	2026-10-22 00:40:21.869803+00	PRESCRIPTION_PAYMENT_REQUEST	3db56074-a1a2-40a2-a984-a0ab45e1d443	invoice.created	cfab1271-de78-43bd-8b3d-d4425096c5db	0acb8270-c779-4db9-befb-ff2a4fd77b6e	PRIVATE_IN_APP_ONLY
75362cb1-c7ad-4b8a-9c17-8d125ddf068a	30820325-711c-4d7b-b364-be73c78a43b0	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 08:00.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.331171+00	2026-10-22 00:40:37.337674+00	\N	\N	\N	\N	\N	STANDARD
6c54da6b-6b8e-4775-bbc1-0ce9b121ba28	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 08:15.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.350223+00	2026-10-22 00:40:37.356793+00	\N	\N	\N	\N	\N	STANDARD
404d3638-3ede-4cd4-9150-a3dd8e60c0fc	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 08:30.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.634654+00	2026-10-22 00:40:37.642058+00	\N	\N	\N	\N	\N	STANDARD
8fa7a0a8-8e63-43dc-971d-cb14d00efaa3	6f75b635-a491-4191-99b3-1df16c1a3832	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 08:45.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.661223+00	2026-10-22 00:40:37.667345+00	\N	\N	\N	\N	\N	STANDARD
7a1d38f0-39ee-4249-93d4-ab7dd455aa4d	1270368c-7e70-40eb-852c-b58d7b052cf7	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 09:00.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.681655+00	2026-10-22 00:40:37.687015+00	\N	\N	\N	\N	\N	STANDARD
45925d97-9002-43d3-9ef6-4818f3493e5a	83ee7cd5-ccb4-4005-96f3-42abb0261400	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 09:15.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.698384+00	2026-10-22 00:40:37.703317+00	\N	\N	\N	\N	\N	STANDARD
f1a676bd-2dfa-4cde-8f98-63c1e4bd80d9	3ad6444d-54b2-467a-9e25-beb664d3975f	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 09:30.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.939648+00	2026-10-22 00:40:37.943947+00	\N	\N	\N	\N	\N	STANDARD
7a52b17e-3739-470e-a1cb-8aa050c2d3df	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 09:45.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.955385+00	2026-10-22 00:40:37.959715+00	\N	\N	\N	\N	\N	STANDARD
37da3f0c-c9d8-484a-a7cc-712475f97f3f	a2c54b05-3010-4b8d-93ee-67fb06469d77	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 10:00.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:37.96826+00	2026-10-22 00:40:37.972021+00	\N	\N	\N	\N	\N	STANDARD
51092a6c-1f2d-448c-b878-2bd8e9b64498	b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 10:15.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:38.229689+00	2026-10-22 00:40:38.232818+00	\N	\N	\N	\N	\N	STANDARD
013b3bb7-5df3-495f-a73a-a552bb7c7bbc	87674766-119a-46ed-b4db-2059be4cd59c	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 10:30.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:38.242139+00	2026-10-22 00:40:38.245287+00	\N	\N	\N	\N	\N	STANDARD
1eb1ec34-1601-4675-91ed-f58f7d2ae828	579e6d5b-1f63-43ce-bd2a-609fdf508481	Nhắc lịch khám	Bạn có lịch khám ngày 2026-10-22 lúc 10:45.	IN_APP	\N	SENT	\N	1	2026-10-22 00:40:38.254393+00	2026-10-22 00:40:38.256988+00	\N	\N	\N	\N	\N	STANDARD
e42ee0ab-a6a1-4408-9f0d-c832e13ac35e	30820325-711c-4d7b-b364-be73c78a43b0	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:40:41.289726+00	2026-10-22 00:40:41.289726+00	PAYMENT_RECEIPT	a7a1957a-cad4-479f-8f77-d2106b1945a0	payment.completed	2ba1e00e-ccb0-47ea-b27e-e81042f78ce2	2aa427c6-a5f4-4024-ac36-8beed130d070	PRIVATE_IN_APP_ONLY
81b20d68-353f-41cd-b0d1-c0b4313430a5	30820325-711c-4d7b-b364-be73c78a43b0	Chào mừng đến với MediFlow	Xin chào Nguyễn Văn An, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient0@example.test	SENT	\N	1	2026-10-22 00:40:41.765868+00	2026-10-22 00:40:41.769531+00	\N	\N	\N	\N	\N	STANDARD
467033d3-8608-4697-995e-4b203af1c7b8	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Chào mừng đến với MediFlow	Xin chào Trần Thị Hương, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient1@example.test	SENT	\N	1	2026-10-22 00:40:41.78066+00	2026-10-22 00:40:41.784497+00	\N	\N	\N	\N	\N	STANDARD
2e2e0a8d-e1fa-41c8-8419-136c61c0e8c8	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Chào mừng đến với MediFlow	Xin chào Lê Minh Khang, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient2@example.test	SENT	\N	1	2026-10-22 00:40:41.79581+00	2026-10-22 00:40:41.79832+00	\N	\N	\N	\N	\N	STANDARD
41e126f5-2126-4f69-9228-537f2ae518d2	6f75b635-a491-4191-99b3-1df16c1a3832	Chào mừng đến với MediFlow	Xin chào Phạm Ngọc Anh, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient3@example.test	SENT	\N	1	2026-10-22 00:40:41.80865+00	2026-10-22 00:40:41.81236+00	\N	\N	\N	\N	\N	STANDARD
7e435c08-6536-41ce-8ef4-a641c431c398	1270368c-7e70-40eb-852c-b58d7b052cf7	Chào mừng đến với MediFlow	Xin chào Võ Quốc Bảo, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient4@example.test	SENT	\N	1	2026-10-22 00:40:41.822143+00	2026-10-22 00:40:41.8253+00	\N	\N	\N	\N	\N	STANDARD
757617e7-602c-4799-b905-48bd6b77b713	83ee7cd5-ccb4-4005-96f3-42abb0261400	Chào mừng đến với MediFlow	Xin chào Đặng Thanh Trúc, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient5@example.test	SENT	\N	1	2026-10-22 00:40:41.834906+00	2026-10-22 00:40:41.838057+00	\N	\N	\N	\N	\N	STANDARD
e5ca302e-34d8-4c89-9f88-b5ff94078af9	3ad6444d-54b2-467a-9e25-beb664d3975f	Chào mừng đến với MediFlow	Xin chào Bùi Gia Huy, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient6@example.test	SENT	\N	1	2026-10-22 00:40:41.852062+00	2026-10-22 00:40:41.855226+00	\N	\N	\N	\N	\N	STANDARD
6b091512-c0e8-4347-94ad-aa6859808f80	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	Chào mừng đến với MediFlow	Xin chào Đỗ Thị Lan, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient7@example.test	SENT	\N	1	2026-10-22 00:40:41.8653+00	2026-10-22 00:40:41.86953+00	\N	\N	\N	\N	\N	STANDARD
ce179750-5237-4291-a75d-e4c43de173c5	a2c54b05-3010-4b8d-93ee-67fb06469d77	Chào mừng đến với MediFlow	Xin chào Hồ Minh Đức, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient8@example.test	SENT	\N	1	2026-10-22 00:40:41.880072+00	2026-10-22 00:40:41.883285+00	\N	\N	\N	\N	\N	STANDARD
13ca7a7d-3e93-4391-ab8d-5ac4d2331d74	b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	Chào mừng đến với MediFlow	Xin chào Ngô Thảo Vy, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient9@example.test	SENT	\N	1	2026-10-22 00:40:41.894871+00	2026-10-22 00:40:41.899067+00	\N	\N	\N	\N	\N	STANDARD
5a47664d-f244-4ea8-8bfd-dcbf27dbee7e	87674766-119a-46ed-b4db-2059be4cd59c	Chào mừng đến với MediFlow	Xin chào Dương Tuấn Kiệt, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient10@example.test	SENT	\N	1	2026-10-22 00:40:41.911046+00	2026-10-22 00:40:41.914677+00	\N	\N	\N	\N	\N	STANDARD
b4baed5d-6c69-4bdd-b6d7-de471006c2fe	579e6d5b-1f63-43ce-bd2a-609fdf508481	Chào mừng đến với MediFlow	Xin chào Vũ Ngọc Hà, hồ sơ của bạn đã được tạo.	EMAIL	review3.patient11@example.test	SENT	\N	1	2026-10-22 00:40:41.926929+00	2026-10-22 00:40:41.931607+00	\N	\N	\N	\N	\N	STANDARD
4cbeb2ce-1180-4e8b-8296-be66af541c44	30820325-711c-4d7b-b364-be73c78a43b0	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 150000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:40:45.380746+00	2026-10-22 00:40:45.380746+00	PAYMENT_RECEIPT	1330fe0c-c39d-435f-91b6-f044567dc77f	payment.completed	cc0c7042-4358-4039-81dc-19d81ae7b14a	dc45a22e-0e75-46ba-a2a0-6afdcb17233d	PRIVATE_IN_APP_ONLY
744150d9-1eab-4574-94af-3dae3a0ef8b6	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:44:58.93177+00	2026-10-22 00:44:58.93177+00	PAYMENT_RECEIPT	f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2	payment.completed	e1cffe9a-9228-45bf-9f63-511d4c6f550c	14fe00ba-b80c-4093-9fa2-0e8f2bddece9	PRIVATE_IN_APP_ONLY
9c4733e5-ae36-4bd3-b1c2-5b4dd94b4aee	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 150000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:04.035593+00	2026-10-22 00:45:04.035593+00	PAYMENT_RECEIPT	9099c95d-829c-4360-910f-f52198801584	payment.completed	cf4b6b6a-d59d-444c-92fd-f4905bf85509	c05c7165-e220-4977-b1fa-dacc80846f1d	PRIVATE_IN_APP_ONLY
ea180665-4673-4ab1-80ba-e2b754451762	30820325-711c-4d7b-b364-be73c78a43b0	Đã ghi nhận đơn thuốc	Đơn thuốc đã được ghi nhận. Vui lòng xem viện phí và hướng dẫn nhận thuốc.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:31.283674+00	2026-10-22 00:45:31.283674+00	OUTPATIENT_PRESCRIPTION_CREATED	8994a20f-f105-4ec4-a185-395c22aab57b	prescription.created	84116e5c-b8d2-4afa-a586-0c2af1c350ab	9ee4d903-7954-4826-b1df-a47d706b0e22	PRIVATE_IN_APP_ONLY
ce93aa3c-baa8-45a3-ae8d-06ef5918aeb6	30820325-711c-4d7b-b364-be73c78a43b0	Yêu cầu thanh toán đơn thuốc	Khoản thanh toán được yêu cầu: 9000 VND. Vui lòng liên hệ quầy thu ngân. Đây không phải biên nhận đã thanh toán và không xác nhận thuốc đã được cấp.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:31.390672+00	2026-10-22 00:45:31.390672+00	PRESCRIPTION_PAYMENT_REQUEST	6bf6d013-9e64-412c-ba9c-7a565179f4db	invoice.created	0cff9bd3-381b-4b73-970f-b66e657e4435	9ee4d903-7954-4826-b1df-a47d706b0e22	PRIVATE_IN_APP_ONLY
a28f550c-fabc-4a9d-b96f-6caf2283843b	30820325-711c-4d7b-b364-be73c78a43b0	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 9000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:32.41647+00	2026-10-22 00:45:32.41647+00	PAYMENT_RECEIPT	355f326c-1b48-451e-bda2-779db121bc00	payment.completed	55a1ae72-572d-4daa-a23a-0176b0834aa0	f83fee75-9b71-43f9-887c-f6722a6762ab	PRIVATE_IN_APP_ONLY
dc26da5f-f11e-4d51-8fbe-34ea81d063af	30820325-711c-4d7b-b364-be73c78a43b0	Đã cấp thuốc	Nhà thuốc đã ghi nhận cấp thuốc cho đơn của bạn.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:35.351868+00	2026-10-22 00:45:35.351868+00	OUTPATIENT_PRESCRIPTION_FILLED	7d3186d9-a2c6-44e1-a89c-4565b164d105	prescription.filled	84116e5c-b8d2-4afa-a586-0c2af1c350ab	56d13802-f3dd-4c48-a841-bcd223205143	PRIVATE_IN_APP_ONLY
56aebadf-0aaa-455d-b5c3-c7c3f7e03c50	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Đã ghi nhận đơn thuốc	Đơn thuốc đã được ghi nhận. Vui lòng xem viện phí và hướng dẫn nhận thuốc.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:35.504702+00	2026-10-22 00:45:35.504702+00	OUTPATIENT_PRESCRIPTION_CREATED	1ffa49d2-e974-4c65-9b7d-7c49510aec81	prescription.created	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	d25b9aee-1f1a-4609-a518-0a202019b6e8	PRIVATE_IN_APP_ONLY
82830bad-2f7d-4aed-a316-ae2877d67dca	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Yêu cầu thanh toán đơn thuốc	Khoản thanh toán được yêu cầu: 9000 VND. Vui lòng liên hệ quầy thu ngân. Đây không phải biên nhận đã thanh toán và không xác nhận thuốc đã được cấp.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:36.505106+00	2026-10-22 00:45:36.505106+00	PRESCRIPTION_PAYMENT_REQUEST	5370b35c-27ba-4584-a002-f7a43727cae2	invoice.created	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	d25b9aee-1f1a-4609-a518-0a202019b6e8	PRIVATE_IN_APP_ONLY
127552ba-1546-4a26-aa66-5d321e32dcde	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 9000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:38.575964+00	2026-10-22 00:45:38.575964+00	PAYMENT_RECEIPT	c3d7ad2a-36de-46bd-ba16-bcfba18bc7be	payment.completed	7649919b-93b2-4aea-906c-6e69d5cc3afb	8faca011-8c10-4937-995f-9f2d2cc59a6b	PRIVATE_IN_APP_ONLY
ea6c5cdc-f6a3-49bb-a22e-e8813f094769	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:40.691446+00	2026-10-22 00:45:40.691446+00	PAYMENT_RECEIPT	9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b	payment.completed	e55fe586-5ddb-48d7-9338-1836225c52c5	7691df7e-aca1-443c-b10b-7e3c35533aab	PRIVATE_IN_APP_ONLY
478fe031-6043-4623-a9e9-c848771371c2	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 150000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:44.813028+00	2026-10-22 00:45:44.813028+00	PAYMENT_RECEIPT	0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f	payment.completed	b52f3117-223d-4432-ac82-4e7f06ccee62	fdd5f599-4fb5-4de7-b89e-b4ee806afbaa	PRIVATE_IN_APP_ONLY
62ad991b-32f6-4814-aa11-fb9fd91888c8	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Đã ghi nhận đơn thuốc	Đơn thuốc đã được ghi nhận. Vui lòng xem viện phí và hướng dẫn nhận thuốc.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:46.703295+00	2026-10-22 00:45:46.703295+00	OUTPATIENT_PRESCRIPTION_CREATED	588992b4-d137-4996-af14-313422e07269	prescription.created	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	12989ae3-0597-4983-a637-b6c0c4c8198b	PRIVATE_IN_APP_ONLY
52af7679-e3d5-41c8-a23e-bfe098ced77d	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Yêu cầu thanh toán đơn thuốc	Khoản thanh toán được yêu cầu: 9000 VND. Vui lòng liên hệ quầy thu ngân. Đây không phải biên nhận đã thanh toán và không xác nhận thuốc đã được cấp.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:46.901801+00	2026-10-22 00:45:46.901801+00	PRESCRIPTION_PAYMENT_REQUEST	49d2333c-73de-4226-a373-98b92a58b110	invoice.created	c80ea7fa-076a-4a07-98bc-8fe4b08982c9	12989ae3-0597-4983-a637-b6c0c4c8198b	PRIVATE_IN_APP_ONLY
ff31070b-c0d5-4281-9a8f-598a06466e1a	6f75b635-a491-4191-99b3-1df16c1a3832	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:49.006877+00	2026-10-22 00:45:49.006877+00	PAYMENT_RECEIPT	fa53bcf9-3b37-4ead-9583-7ed10ad93cbe	payment.completed	add16c27-3994-4ef8-a7a6-80c3a01f5090	d1e37e94-c5c3-4a71-844a-79bac0093ac1	PRIVATE_IN_APP_ONLY
8ef67efa-be01-471d-9d96-d355b33e87eb	6f75b635-a491-4191-99b3-1df16c1a3832	Đã ghi nhận đơn thuốc	Đơn thuốc đã được ghi nhận. Vui lòng xem viện phí và hướng dẫn nhận thuốc.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:49.765506+00	2026-10-22 00:45:49.765506+00	OUTPATIENT_PRESCRIPTION_CREATED	3de60d0c-4a2b-4052-9f39-47e93a533100	prescription.created	7b8b6d90-897d-4439-8f89-4f6905e825ae	775e7710-bcab-4da5-a3fb-1fb0e41627ea	PRIVATE_IN_APP_ONLY
87ba700b-206e-4cff-b995-fe0acd3de433	6f75b635-a491-4191-99b3-1df16c1a3832	Yêu cầu thanh toán đơn thuốc	Khoản thanh toán được yêu cầu: 9000 VND. Vui lòng liên hệ quầy thu ngân. Đây không phải biên nhận đã thanh toán và không xác nhận thuốc đã được cấp.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:53.089723+00	2026-10-22 00:45:53.089723+00	PRESCRIPTION_PAYMENT_REQUEST	e3bc9094-e906-4e9f-852e-0b2e1c4c8eaf	invoice.created	cba26ee1-8c27-4fc1-9b74-1083946a1db0	775e7710-bcab-4da5-a3fb-1fb0e41627ea	PRIVATE_IN_APP_ONLY
c8ea2223-d0d1-47a6-8b48-b3585a7f89e9	1270368c-7e70-40eb-852c-b58d7b052cf7	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:45:53.103477+00	2026-10-22 00:45:53.103477+00	PAYMENT_RECEIPT	33f4d6d9-99f8-407c-b685-4f95b4a0ac95	payment.completed	bced71a5-0b2f-45d2-87d2-9b41bf72d8b3	a7692903-7ec0-4663-9424-36140d210740	PRIVATE_IN_APP_ONLY
f93d9f5b-a575-411f-a3c1-4fd85b8b5b3f	30820325-711c-4d7b-b364-be73c78a43b0	Nhắc lịch tái khám	Mời anh Nguyễn Văn An tái khám theo lịch hẹn. Vui lòng mang theo kết quả xét nghiệm.	IN_APP	\N	SENT	\N	1	2026-10-22 00:45:54.866029+00	2026-10-22 00:45:54.86829+00	\N	\N	\N	\N	\N	STANDARD
99274bde-5a74-408e-ae48-ae316b71c2df	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Đã cấp thuốc	Nhà thuốc đã ghi nhận cấp thuốc cho đơn của bạn.	IN_APP	\N	SENT	\N	0	2026-10-22 00:52:18.002303+00	2026-10-22 00:52:18.002303+00	OUTPATIENT_PRESCRIPTION_FILLED	6400782d-fa9c-41f8-a5c3-95b1ad7081ce	prescription.filled	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	a4e8a115-4307-42be-bdfa-2be6d65d3156	PRIVATE_IN_APP_ONLY
b26cdbf1-71cb-4914-bf4b-9d636dc91c63	3ad6444d-54b2-467a-9e25-beb664d3975f	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:52:51.694191+00	2026-10-22 00:52:51.694191+00	PAYMENT_RECEIPT	608193a2-1f8e-493f-a59b-a7d83eb512e7	payment.completed	a33cb5ed-d048-4908-a7ec-2ae3f8416d77	7322e252-9e00-4a38-983e-775341b5ceef	PRIVATE_IN_APP_ONLY
a9861242-3f6e-4d60-bf9c-2109d2924677	83ee7cd5-ccb4-4005-96f3-42abb0261400	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 00:56:56.941206+00	2026-10-22 00:56:56.941206+00	PAYMENT_RECEIPT	b136373c-a447-4817-a839-e28c940fd939	payment.completed	65284733-97d8-4108-9d85-6fa449d75310	b8a54376-397e-4166-b1e5-f3a454d5aa79	PRIVATE_IN_APP_ONLY
075bd56e-f5bd-44d0-9e14-eccde103bbc2	2abcf504-cf70-4933-9eff-5e5b7dfe5d05	Chào mừng đến với MediFlow	Xin chào Nguyễn Văn An Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient01@example.test	SENT	\N	1	2026-10-22 10:20:57.12823+00	2026-10-22 10:20:57.423052+00	\N	\N	\N	\N	\N	STANDARD
10594968-d7e7-431b-8218-cebb7e0f57cd	fac978a8-7abc-470f-830d-9823a8381b70	Chào mừng đến với MediFlow	Xin chào Trần Thị Bình Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient02@example.test	SENT	\N	1	2026-10-22 10:20:57.56165+00	2026-10-22 10:20:57.573354+00	\N	\N	\N	\N	\N	STANDARD
b0dab2cb-41b0-4064-b905-4c1c5c5a2b11	562d84f4-e553-4a17-9d86-b3d900c76a6f	Chào mừng đến với MediFlow	Xin chào Lê Minh Châu Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient03@example.test	SENT	\N	1	2026-10-22 10:20:57.619189+00	2026-10-22 10:20:57.648101+00	\N	\N	\N	\N	\N	STANDARD
2b6d4250-79b1-43a1-96cf-6d9e0c7bc825	7c2da651-e59b-4310-b7e8-1ac4c4bc1be3	Chào mừng đến với MediFlow	Xin chào Phạm Ngọc Dung Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient04@example.test	SENT	\N	1	2026-10-22 10:20:57.706438+00	2026-10-22 10:20:57.721131+00	\N	\N	\N	\N	\N	STANDARD
41fe77bc-c8d0-465e-ad6e-4309d7b07b10	8f8cce22-0ffd-4d79-b55b-90268fc25dd8	Chào mừng đến với MediFlow	Xin chào Võ Quốc Huy Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient05@example.test	SENT	\N	1	2026-10-22 10:20:57.771403+00	2026-10-22 10:20:57.78566+00	\N	\N	\N	\N	\N	STANDARD
07431509-ea61-45cb-a5a6-9ea19e679775	64bb0468-b92e-4c23-86d7-ee9c5a521b9b	Chào mừng đến với MediFlow	Xin chào Đặng Thanh Mai Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient06@example.test	SENT	\N	1	2026-10-22 10:20:57.816839+00	2026-10-22 10:20:57.841336+00	\N	\N	\N	\N	\N	STANDARD
582cd96c-01e2-4b7f-9f9e-a9175acda0b4	c2fc4b28-8ced-4bef-aa67-58fdf38dbcb9	Chào mừng đến với MediFlow	Xin chào Bùi Gia Nam Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient07@example.test	SENT	\N	1	2026-10-22 10:20:57.889031+00	2026-10-22 10:20:57.902259+00	\N	\N	\N	\N	\N	STANDARD
3d00f189-f9e1-4924-86c1-8cc8a4a5d3a2	78d9f918-aa47-41e8-9062-27bfcbd8e1c0	Chào mừng đến với MediFlow	Xin chào Đỗ Thị Phương Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient08@example.test	SENT	\N	1	2026-10-22 10:20:57.933693+00	2026-10-22 10:20:57.946765+00	\N	\N	\N	\N	\N	STANDARD
4117f45c-464b-48f4-b55e-583c0a737144	840486c7-cfe5-44a2-a890-7847425816f0	Chào mừng đến với MediFlow	Xin chào Hồ Minh Quân Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient09@example.test	SENT	\N	1	2026-10-22 10:20:57.973481+00	2026-10-22 10:20:57.984754+00	\N	\N	\N	\N	\N	STANDARD
201532cf-274c-4037-876d-cf05d1930f2a	2cef1e5a-36f0-41ee-a965-28303af23caa	Chào mừng đến với MediFlow	Xin chào Ngô Thảo Vy Demo, hồ sơ của bạn đã được tạo.	EMAIL	demo.patient10@example.test	SENT	\N	1	2026-10-22 10:20:58.014018+00	2026-10-22 10:20:58.028346+00	\N	\N	\N	\N	\N	STANDARD
67dc0bf9-c4c5-4059-8cad-29e81a9c6cf5	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 10:53:24.838473+00	2026-10-22 10:53:24.838473+00	PAYMENT_RECEIPT	dee0992f-cbc0-4af3-aeca-3d1f806333b3	payment.completed	c3802b07-5f4f-47e8-afa2-0d08b2d9fe2e	017ff56c-21ff-4ab0-8841-68ace230595c	PRIVATE_IN_APP_ONLY
bfe587dc-2637-43e2-85cb-9293b827e683	a2c54b05-3010-4b8d-93ee-67fb06469d77	Đã ghi nhận thanh toán	Đã ghi nhận khoản thanh toán 100000 VND. Xem tài khoản viện phí để biết số tiền còn lại.	IN_APP	\N	SENT	\N	0	2026-10-22 11:41:35.841174+00	2026-10-22 11:41:35.841174+00	PAYMENT_RECEIPT	b2531d42-b612-4cb9-a5f0-0781e3d5c106	payment.completed	2dc1ba9c-e65f-4396-be2e-f94e5d0f4a16	8d5ed7da-380e-4c96-bae9-a4db20ca8081	PRIVATE_IN_APP_ONLY
\.


--
-- Data for Name: notification_event_outbox; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.notification_event_outbox (event_id, notification_id, payload, publication_enabled, created_at, published_at) FROM stdin;
acabe28b-3c09-43ca-acc5-96bd555e7609	9c15989b-b297-47ce-970d-65122fb7baf8	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"f5f81d95-ffb2-476c-a67a-d5eaff2beaf2","notificationId":"9c15989b-b297-47ce-970d-65122fb7baf8","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44"},"correlationId":"449d52da-28b5-44d1-a513-99db59cfe7d2","version":1,"eventId":"acabe28b-3c09-43ca-acc5-96bd555e7609","occurredAt":"2026-10-22T00:40:18.487008700Z"}	f	2026-10-22 00:40:18.484693+00	\N
ff8b18cc-aa87-4432-8d0c-e6552aa08dae	cfa6d50e-6826-48ed-8db6-d3bf94eb8b7a	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_CREATED","channel":"IN_APP","status":"SENT","sourceEventId":"7377fa4b-ea08-4ff1-a24f-5be31546e5a7","notificationId":"cfa6d50e-6826-48ed-8db6-d3bf94eb8b7a","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44"},"correlationId":"0acb8270-c779-4db9-befb-ff2a4fd77b6e","version":1,"eventId":"ff8b18cc-aa87-4432-8d0c-e6552aa08dae","occurredAt":"2026-10-22T00:40:19.724605400Z"}	f	2026-10-22 00:40:19.709489+00	\N
115cf791-0fc8-4079-b3cb-fc25c10d2ab6	41c50a5b-9f4c-4272-ac51-bb1e1de2264b	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_CANCELLED","channel":"IN_APP","status":"SENT","sourceEventId":"32b9c998-f101-42df-af40-0cd87c41fc40","notificationId":"41c50a5b-9f4c-4272-ac51-bb1e1de2264b","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44"},"correlationId":"b21f51ec-be1c-4e83-987b-5b9f5ca32df7","version":1,"eventId":"115cf791-0fc8-4079-b3cb-fc25c10d2ab6","occurredAt":"2026-10-22T00:40:20.950773Z"}	f	2026-10-22 00:40:20.895529+00	\N
14212d88-1fd7-4f34-9d96-b98a3cd64042	c0050e46-1e98-4f0b-8bad-377eb3025cd6	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PRESCRIPTION_PAYMENT_REQUEST","channel":"IN_APP","status":"SENT","sourceEventId":"3db56074-a1a2-40a2-a984-a0ab45e1d443","notificationId":"c0050e46-1e98-4f0b-8bad-377eb3025cd6","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44"},"correlationId":"0acb8270-c779-4db9-befb-ff2a4fd77b6e","version":1,"eventId":"14212d88-1fd7-4f34-9d96-b98a3cd64042","occurredAt":"2026-10-22T00:40:21.925215700Z"}	f	2026-10-22 00:40:21.869803+00	\N
2769c18d-66c1-4e9f-9717-9f7113b9326b	e42ee0ab-a6a1-4408-9f0d-c832e13ac35e	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"a7a1957a-cad4-479f-8f77-d2106b1945a0","notificationId":"e42ee0ab-a6a1-4408-9f0d-c832e13ac35e","patientId":"30820325-711c-4d7b-b364-be73c78a43b0"},"correlationId":"2aa427c6-a5f4-4024-ac36-8beed130d070","version":1,"eventId":"2769c18d-66c1-4e9f-9717-9f7113b9326b","occurredAt":"2026-10-22T00:40:41.290955800Z"}	f	2026-10-22 00:40:41.289726+00	\N
086349d0-56e3-4aa4-a257-992b7f626a83	4cbeb2ce-1180-4e8b-8296-be66af541c44	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"1330fe0c-c39d-435f-91b6-f044567dc77f","notificationId":"4cbeb2ce-1180-4e8b-8296-be66af541c44","patientId":"30820325-711c-4d7b-b364-be73c78a43b0"},"correlationId":"dc45a22e-0e75-46ba-a2a0-6afdcb17233d","version":1,"eventId":"086349d0-56e3-4aa4-a257-992b7f626a83","occurredAt":"2026-10-22T00:40:45.382744700Z"}	f	2026-10-22 00:40:45.380746+00	\N
5457b5b7-4de1-49a9-b1ab-cca69f318cd9	744150d9-1eab-4574-94af-3dae3a0ef8b6	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2","notificationId":"744150d9-1eab-4574-94af-3dae3a0ef8b6","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490"},"correlationId":"14fe00ba-b80c-4093-9fa2-0e8f2bddece9","version":1,"eventId":"5457b5b7-4de1-49a9-b1ab-cca69f318cd9","occurredAt":"2026-10-22T00:44:58.933519100Z"}	f	2026-10-22 00:44:58.93177+00	\N
42e1b514-6f5c-4d8f-be07-1e3691077632	9c4733e5-ae36-4bd3-b1c2-5b4dd94b4aee	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"9099c95d-829c-4360-910f-f52198801584","notificationId":"9c4733e5-ae36-4bd3-b1c2-5b4dd94b4aee","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490"},"correlationId":"c05c7165-e220-4977-b1fa-dacc80846f1d","version":1,"eventId":"42e1b514-6f5c-4d8f-be07-1e3691077632","occurredAt":"2026-10-22T00:45:04.036679400Z"}	f	2026-10-22 00:45:04.035593+00	\N
55937ff8-27e1-4f4f-9e7d-ef0b8668f7ae	ea180665-4673-4ab1-80ba-e2b754451762	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_CREATED","channel":"IN_APP","status":"SENT","sourceEventId":"8994a20f-f105-4ec4-a185-395c22aab57b","notificationId":"ea180665-4673-4ab1-80ba-e2b754451762","patientId":"30820325-711c-4d7b-b364-be73c78a43b0"},"correlationId":"9ee4d903-7954-4826-b1df-a47d706b0e22","version":1,"eventId":"55937ff8-27e1-4f4f-9e7d-ef0b8668f7ae","occurredAt":"2026-10-22T00:45:31.286129400Z"}	f	2026-10-22 00:45:31.283674+00	\N
c6adde65-bad6-46e9-ace4-3a36c06cfca1	ce93aa3c-baa8-45a3-ae8d-06ef5918aeb6	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PRESCRIPTION_PAYMENT_REQUEST","channel":"IN_APP","status":"SENT","sourceEventId":"6bf6d013-9e64-412c-ba9c-7a565179f4db","notificationId":"ce93aa3c-baa8-45a3-ae8d-06ef5918aeb6","patientId":"30820325-711c-4d7b-b364-be73c78a43b0"},"correlationId":"9ee4d903-7954-4826-b1df-a47d706b0e22","version":1,"eventId":"c6adde65-bad6-46e9-ace4-3a36c06cfca1","occurredAt":"2026-10-22T00:45:31.393656100Z"}	f	2026-10-22 00:45:31.390672+00	\N
2dc4ca5a-6e4a-4711-bd54-5e3dcaf38e82	a28f550c-fabc-4a9d-b96f-6caf2283843b	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"355f326c-1b48-451e-bda2-779db121bc00","notificationId":"a28f550c-fabc-4a9d-b96f-6caf2283843b","patientId":"30820325-711c-4d7b-b364-be73c78a43b0"},"correlationId":"f83fee75-9b71-43f9-887c-f6722a6762ab","version":1,"eventId":"2dc4ca5a-6e4a-4711-bd54-5e3dcaf38e82","occurredAt":"2026-10-22T00:45:32.417427900Z"}	f	2026-10-22 00:45:32.41647+00	\N
906d2f3c-54c9-4628-8269-4502134194b8	dc26da5f-f11e-4d51-8fbe-34ea81d063af	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_FILLED","channel":"IN_APP","status":"SENT","sourceEventId":"7d3186d9-a2c6-44e1-a89c-4565b164d105","notificationId":"dc26da5f-f11e-4d51-8fbe-34ea81d063af","patientId":"30820325-711c-4d7b-b364-be73c78a43b0"},"correlationId":"56d13802-f3dd-4c48-a841-bcd223205143","version":1,"eventId":"906d2f3c-54c9-4628-8269-4502134194b8","occurredAt":"2026-10-22T00:45:35.353758300Z"}	f	2026-10-22 00:45:35.351868+00	\N
4234cac3-30fd-4ccf-851e-d2d0333028c0	56aebadf-0aaa-455d-b5c3-c7c3f7e03c50	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_CREATED","channel":"IN_APP","status":"SENT","sourceEventId":"1ffa49d2-e974-4c65-9b7d-7c49510aec81","notificationId":"56aebadf-0aaa-455d-b5c3-c7c3f7e03c50","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490"},"correlationId":"d25b9aee-1f1a-4609-a518-0a202019b6e8","version":1,"eventId":"4234cac3-30fd-4ccf-851e-d2d0333028c0","occurredAt":"2026-10-22T00:45:35.507464800Z"}	f	2026-10-22 00:45:35.504702+00	\N
0d6400eb-15c6-4a96-9aa1-282240c2f2d6	82830bad-2f7d-4aed-a316-ae2877d67dca	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PRESCRIPTION_PAYMENT_REQUEST","channel":"IN_APP","status":"SENT","sourceEventId":"5370b35c-27ba-4584-a002-f7a43727cae2","notificationId":"82830bad-2f7d-4aed-a316-ae2877d67dca","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490"},"correlationId":"d25b9aee-1f1a-4609-a518-0a202019b6e8","version":1,"eventId":"0d6400eb-15c6-4a96-9aa1-282240c2f2d6","occurredAt":"2026-10-22T00:45:36.507305500Z"}	f	2026-10-22 00:45:36.505106+00	\N
7c33841a-4a8e-4fd1-ba8b-8622a23fa1d8	127552ba-1546-4a26-aa66-5d321e32dcde	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"c3d7ad2a-36de-46bd-ba16-bcfba18bc7be","notificationId":"127552ba-1546-4a26-aa66-5d321e32dcde","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490"},"correlationId":"8faca011-8c10-4937-995f-9f2d2cc59a6b","version":1,"eventId":"7c33841a-4a8e-4fd1-ba8b-8622a23fa1d8","occurredAt":"2026-10-22T00:45:38.578036100Z"}	f	2026-10-22 00:45:38.575964+00	\N
a7222669-7c45-4fe2-8569-6e9199106b86	ea6c5cdc-f6a3-49bb-a22e-e8813f094769	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b","notificationId":"ea6c5cdc-f6a3-49bb-a22e-e8813f094769","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8"},"correlationId":"7691df7e-aca1-443c-b10b-7e3c35533aab","version":1,"eventId":"a7222669-7c45-4fe2-8569-6e9199106b86","occurredAt":"2026-10-22T00:45:40.692047700Z"}	f	2026-10-22 00:45:40.691446+00	\N
8b38a8ec-edcf-4a7a-abdc-48a5bd3add6b	478fe031-6043-4623-a9e9-c848771371c2	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f","notificationId":"478fe031-6043-4623-a9e9-c848771371c2","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8"},"correlationId":"fdd5f599-4fb5-4de7-b89e-b4ee806afbaa","version":1,"eventId":"8b38a8ec-edcf-4a7a-abdc-48a5bd3add6b","occurredAt":"2026-10-22T00:45:44.814189500Z"}	f	2026-10-22 00:45:44.813028+00	\N
52209141-cd03-4bde-a53b-e69f358d8cbd	62ad991b-32f6-4814-aa11-fb9fd91888c8	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_CREATED","channel":"IN_APP","status":"SENT","sourceEventId":"588992b4-d137-4996-af14-313422e07269","notificationId":"62ad991b-32f6-4814-aa11-fb9fd91888c8","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8"},"correlationId":"12989ae3-0597-4983-a637-b6c0c4c8198b","version":1,"eventId":"52209141-cd03-4bde-a53b-e69f358d8cbd","occurredAt":"2026-10-22T00:45:46.706318800Z"}	f	2026-10-22 00:45:46.703295+00	\N
cdd318ab-1d63-4984-91b1-f62280299f81	52af7679-e3d5-41c8-a23e-bfe098ced77d	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PRESCRIPTION_PAYMENT_REQUEST","channel":"IN_APP","status":"SENT","sourceEventId":"49d2333c-73de-4226-a373-98b92a58b110","notificationId":"52af7679-e3d5-41c8-a23e-bfe098ced77d","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8"},"correlationId":"12989ae3-0597-4983-a637-b6c0c4c8198b","version":1,"eventId":"cdd318ab-1d63-4984-91b1-f62280299f81","occurredAt":"2026-10-22T00:45:46.903682800Z"}	f	2026-10-22 00:45:46.901801+00	\N
eee4a874-69b0-4d04-965d-639ea076fdc5	ff31070b-c0d5-4281-9a8f-598a06466e1a	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"fa53bcf9-3b37-4ead-9583-7ed10ad93cbe","notificationId":"ff31070b-c0d5-4281-9a8f-598a06466e1a","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832"},"correlationId":"d1e37e94-c5c3-4a71-844a-79bac0093ac1","version":1,"eventId":"eee4a874-69b0-4d04-965d-639ea076fdc5","occurredAt":"2026-10-22T00:45:49.007579700Z"}	f	2026-10-22 00:45:49.006877+00	\N
acf7c834-07ee-4891-ad27-447df10a21c3	8ef67efa-be01-471d-9d96-d355b33e87eb	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_CREATED","channel":"IN_APP","status":"SENT","sourceEventId":"3de60d0c-4a2b-4052-9f39-47e93a533100","notificationId":"8ef67efa-be01-471d-9d96-d355b33e87eb","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832"},"correlationId":"775e7710-bcab-4da5-a3fb-1fb0e41627ea","version":1,"eventId":"acf7c834-07ee-4891-ad27-447df10a21c3","occurredAt":"2026-10-22T00:45:49.767067800Z"}	f	2026-10-22 00:45:49.765506+00	\N
d7925486-1dae-4df2-bd4c-9f6ade046677	87ba700b-206e-4cff-b995-fe0acd3de433	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PRESCRIPTION_PAYMENT_REQUEST","channel":"IN_APP","status":"SENT","sourceEventId":"e3bc9094-e906-4e9f-852e-0b2e1c4c8eaf","notificationId":"87ba700b-206e-4cff-b995-fe0acd3de433","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832"},"correlationId":"775e7710-bcab-4da5-a3fb-1fb0e41627ea","version":1,"eventId":"d7925486-1dae-4df2-bd4c-9f6ade046677","occurredAt":"2026-10-22T00:45:53.092149300Z"}	f	2026-10-22 00:45:53.089723+00	\N
6837bd81-d90d-40de-98f9-511387f7f7ee	c8ea2223-d0d1-47a6-8b48-b3585a7f89e9	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"33f4d6d9-99f8-407c-b685-4f95b4a0ac95","notificationId":"c8ea2223-d0d1-47a6-8b48-b3585a7f89e9","patientId":"1270368c-7e70-40eb-852c-b58d7b052cf7"},"correlationId":"a7692903-7ec0-4663-9424-36140d210740","version":1,"eventId":"6837bd81-d90d-40de-98f9-511387f7f7ee","occurredAt":"2026-10-22T00:45:53.104871500Z"}	f	2026-10-22 00:45:53.103477+00	\N
96f4953f-db22-4e8b-97b3-a9f286a74105	99274bde-5a74-408e-ae48-ae316b71c2df	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"OUTPATIENT_PRESCRIPTION_FILLED","channel":"IN_APP","status":"SENT","sourceEventId":"6400782d-fa9c-41f8-a5c3-95b1ad7081ce","notificationId":"99274bde-5a74-408e-ae48-ae316b71c2df","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490"},"correlationId":"a4e8a115-4307-42be-bdfa-2be6d65d3156","version":1,"eventId":"96f4953f-db22-4e8b-97b3-a9f286a74105","occurredAt":"2026-10-22T00:52:18.006647900Z"}	f	2026-10-22 00:52:18.002303+00	\N
e3a940dd-1a5b-4d82-b8c3-721a81e17b01	b26cdbf1-71cb-4914-bf4b-9d636dc91c63	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"608193a2-1f8e-493f-a59b-a7d83eb512e7","notificationId":"b26cdbf1-71cb-4914-bf4b-9d636dc91c63","patientId":"3ad6444d-54b2-467a-9e25-beb664d3975f"},"correlationId":"7322e252-9e00-4a38-983e-775341b5ceef","version":1,"eventId":"e3a940dd-1a5b-4d82-b8c3-721a81e17b01","occurredAt":"2026-10-22T00:52:51.695043300Z"}	f	2026-10-22 00:52:51.694191+00	\N
a372e9b6-8b35-4f56-9842-a7f99f63e433	a9861242-3f6e-4d60-bf9c-2109d2924677	{"eventType":"notification.sent","producer":"notification-service","payload":{"templateKey":"PAYMENT_RECEIPT","channel":"IN_APP","status":"SENT","sourceEventId":"b136373c-a447-4817-a839-e28c940fd939","notificationId":"a9861242-3f6e-4d60-bf9c-2109d2924677","patientId":"83ee7cd5-ccb4-4005-96f3-42abb0261400"},"correlationId":"b8a54376-397e-4166-b1e5-f3a454d5aa79","version":1,"eventId":"a372e9b6-8b35-4f56-9842-a7f99f63e433","occurredAt":"2026-10-22T00:56:56.942798400Z"}	f	2026-10-22 00:56:56.941206+00	\N
9ec9ba12-4935-4415-8f66-35982393a46f	67dc0bf9-c4c5-4059-8cad-29e81a9c6cf5	{"eventType":"notification.sent","producer":"notification-service","payload":{"channel":"IN_APP","status":"SENT","sourceEventId":"dee0992f-cbc0-4af3-aeca-3d1f806333b3","notificationId":"67dc0bf9-c4c5-4059-8cad-29e81a9c6cf5","patientId":"fc4fe9c4-a34c-4252-bd0b-3e171604d3b5","templateKey":"PAYMENT_RECEIPT"},"correlationId":"017ff56c-21ff-4ab0-8841-68ace230595c","version":1,"eventId":"9ec9ba12-4935-4415-8f66-35982393a46f","occurredAt":"2026-10-22T10:53:24.859192700Z"}	f	2026-10-22 10:53:24.838473+00	\N
f5a6ba02-b24b-4603-8013-90917305f49e	bfe587dc-2637-43e2-85cb-9293b827e683	{"eventType":"notification.sent","producer":"notification-service","payload":{"channel":"IN_APP","status":"SENT","sourceEventId":"b2531d42-b612-4cb9-a5f0-0781e3d5c106","notificationId":"bfe587dc-2637-43e2-85cb-9293b827e683","patientId":"a2c54b05-3010-4b8d-93ee-67fb06469d77","templateKey":"PAYMENT_RECEIPT"},"correlationId":"8d5ed7da-380e-4c96-bae9-a4db20ca8081","version":1,"eventId":"f5a6ba02-b24b-4603-8013-90917305f49e","occurredAt":"2026-10-22T11:41:35.851863100Z"}	f	2026-10-22 11:41:35.841174+00	\N
\.


--
-- Data for Name: processed_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.processed_event (event_id, routing_key, processed_at, payload_hash, receipt_raw_hash) FROM stdin;
a85e1afc-f364-4b5b-9cfb-6c972e28c401	patient.created	2026-10-22 00:40:15.823743+00	\N	\N
61d7d6fc-83f0-4f36-a1ad-3802e35a40de	appointment.created	2026-10-22 00:40:16.025722+00	\N	\N
f5f81d95-ffb2-476c-a67a-d5eaff2beaf2	payment.completed	2026-10-22 00:40:18.484693+00	87a623d2ad9099fc4ab330e1a93a2563fe29543d841069c9ae4fcb878a87b2c8	dd10a4e72b8fc2ec8bb90193aecd898494972f9f784a7414a020403be6faabd2
7377fa4b-ea08-4ff1-a24f-5be31546e5a7	prescription.created	2026-10-22 00:40:19.709489+00	ac6791a3b4a1c96e0346fb3fde82af1518256c2c503731a1676e94575c92f0d4	\N
32b9c998-f101-42df-af40-0cd87c41fc40	prescription.cancelled	2026-10-22 00:40:20.895529+00	0420cb835d4b7b78628f0b29b11ffb1e5d0dabf7ff643db205ac72931e170b7b	\N
3db56074-a1a2-40a2-a984-a0ab45e1d443	invoice.created	2026-10-22 00:40:21.869803+00	6fb9e3dc8a8c7d532791ca2f69c5eb752e3231812c1fbdb62b51e9b765158928	\N
e2350514-840e-4234-9c2a-09c21138ca73	appointment.created	2026-10-22 00:40:37.328662+00	\N	\N
fa0b5171-f12b-4b54-a168-1c3c8726aff2	appointment.created	2026-10-22 00:40:37.348015+00	\N	\N
8c340260-4e7c-4c42-86dc-650988320669	appointment.created	2026-10-22 00:40:37.632369+00	\N	\N
af68af8d-bb51-49e7-9504-32c774839d59	appointment.created	2026-10-22 00:40:37.659027+00	\N	\N
e8a679a9-b485-443c-bdce-c2671f9441b1	appointment.created	2026-10-22 00:40:37.679361+00	\N	\N
43b3152e-7663-41db-9a01-4d8765cb1ebc	appointment.created	2026-10-22 00:40:37.696492+00	\N	\N
72b042a6-d785-42fc-a741-c63199df05dd	appointment.created	2026-10-22 00:40:37.93806+00	\N	\N
84c3333d-c61d-4e5f-92af-c0b9fbf4557b	appointment.created	2026-10-22 00:40:37.953668+00	\N	\N
9c13c851-17df-4814-92aa-5f1f080b7ba0	appointment.created	2026-10-22 00:40:37.967376+00	\N	\N
82002f94-612d-4036-8ba2-17b56423a022	appointment.created	2026-10-22 00:40:38.228307+00	\N	\N
de44f999-c373-4502-9b15-6688b0facbfb	appointment.created	2026-10-22 00:40:38.240623+00	\N	\N
30440a63-6e39-45d6-b772-0298265f732c	appointment.created	2026-10-22 00:40:38.253043+00	\N	\N
a7a1957a-cad4-479f-8f77-d2106b1945a0	payment.completed	2026-10-22 00:40:41.289726+00	920860f647823bfe97770723daf4d6f3ff5db8ab31c42751644fd1da0dfa4e79	0978b06c774fcf27e355676fb2958accbf35e730d555ae0ea7cd82355eb97570
7e18530a-ff9f-4afe-a248-b5baf3bacc4e	patient.created	2026-10-22 00:40:41.764197+00	\N	\N
fcae1276-c0ba-44c5-8837-799b59167e60	patient.created	2026-10-22 00:40:41.778732+00	\N	\N
ebd79769-2b7c-4d5d-a97b-b8970ac9fb63	patient.created	2026-10-22 00:40:41.794804+00	\N	\N
37ff17c4-2c7d-4e5f-b5af-7971b02a61aa	patient.created	2026-10-22 00:40:41.807661+00	\N	\N
5ede6981-e220-4e3c-a11f-772345b419ff	patient.created	2026-10-22 00:40:41.821279+00	\N	\N
61e101be-4207-40db-988f-8e83a22fde98	patient.created	2026-10-22 00:40:41.833793+00	\N	\N
e373ea39-3ad4-4b63-8d06-25c689d69714	patient.created	2026-10-22 00:40:41.851278+00	\N	\N
af54a951-d3bc-4258-9327-83f614b5ec8c	patient.created	2026-10-22 00:40:41.864187+00	\N	\N
5e08dc4c-53dc-4f99-956a-2403720ac665	patient.created	2026-10-22 00:40:41.878883+00	\N	\N
a71e9022-74e3-4566-8501-4c1d70edfc3d	patient.created	2026-10-22 00:40:41.892826+00	\N	\N
515bf424-c60e-4542-be90-93de980de3ea	patient.created	2026-10-22 00:40:41.909768+00	\N	\N
fd50e2a9-799c-4367-bc43-da221286c3a6	patient.created	2026-10-22 00:40:41.925485+00	\N	\N
1330fe0c-c39d-435f-91b6-f044567dc77f	payment.completed	2026-10-22 00:40:45.380746+00	fee8dee44ca1e8e850911e6d611e7d3b0bb9b239a14c5cb508a5151077065039	a0e15d16ac3402003ef09296944270e47bbae4b4c113a295d3c7a3573e50c810
f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2	payment.completed	2026-10-22 00:44:58.93177+00	d14604873ddddc4dc2ad3b426c5d4d8c4f8d6cee9cc742e6bbc7162add074247	ef8ac6c3a3f4c9f3c06b61a20851555a9e59da5cf05600e758c62e94681a0d4b
9099c95d-829c-4360-910f-f52198801584	payment.completed	2026-10-22 00:45:04.035593+00	256f1a0cf3e8bc825c15f89972627847110831f99994ce2656fe1a86e81bd3a4	b5df603234ae0826df5410aa07711f0160b03b87776f8aacde2a1dc2ddb72125
8994a20f-f105-4ec4-a185-395c22aab57b	prescription.created	2026-10-22 00:45:31.283674+00	12c15b02e6b9341251d4b84a6cdab0d0b8add9795d043640f7298165e789c375	\N
6bf6d013-9e64-412c-ba9c-7a565179f4db	invoice.created	2026-10-22 00:45:31.390672+00	b8b4fd134afc8004ce43fcffe7462cfb06cefbaa234dd03d885d9911e9d2cd51	\N
355f326c-1b48-451e-bda2-779db121bc00	payment.completed	2026-10-22 00:45:32.41647+00	00ec0a92d9e326b8aba4e899f46501f39921740303b09996d723b12219a2e241	71b328f130d1eeb398220b0af5796e200084f0db7aed22c264a6a573add229fb
7d3186d9-a2c6-44e1-a89c-4565b164d105	prescription.filled	2026-10-22 00:45:35.351868+00	e2c9eb15bfe67c2911430225d01771042f4ee97d601cefaf8ec234ec23eff8f5	\N
1ffa49d2-e974-4c65-9b7d-7c49510aec81	prescription.created	2026-10-22 00:45:35.504702+00	c42b774b28f563fdadfccff1aa3016e6c72ea03bab5f03e48b5f1ad039950246	\N
5370b35c-27ba-4584-a002-f7a43727cae2	invoice.created	2026-10-22 00:45:36.505106+00	ba04e5d8fa8e65638c4470b091e9868a81b9170386eab02a41637a449890d1e6	\N
c3d7ad2a-36de-46bd-ba16-bcfba18bc7be	payment.completed	2026-10-22 00:45:38.575964+00	55d6dcc49a801fff36a90289593821878befecd1e28eaf2308463a28cfb25be1	d158e8029538441d48aaf232d8f375e9e50ade69d1cb888965b7f4af9eb27640
9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b	payment.completed	2026-10-22 00:45:40.691446+00	bd643242e69b33f0bd20b7d968fc1d9f74d10b1b6ebf9283e62e31cbe8265a36	44a29f6680186ceda151cf680c9b1a5a1806c50fea48d7d4af46e3a2be733b2c
0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f	payment.completed	2026-10-22 00:45:44.813028+00	543089a5ab5f7e54927d17efc88d14a50beaf106fa5daf275337e36fe43c994d	99d00e758ba47f4413848634e8129a8cde7095b8182e6c7dba2faeebfb8ebfc6
588992b4-d137-4996-af14-313422e07269	prescription.created	2026-10-22 00:45:46.703295+00	0f0171c4178758ad279008e450f3ece5d3b99baf5cd9039df0bf0bf43ea53025	\N
49d2333c-73de-4226-a373-98b92a58b110	invoice.created	2026-10-22 00:45:46.901801+00	f4d75c5adb41e70299598bc05bce30a11b11e64f2f2fd6611b15de4182ba4274	\N
fa53bcf9-3b37-4ead-9583-7ed10ad93cbe	payment.completed	2026-10-22 00:45:49.006877+00	cff84e774b0cbba36ef7713e074a85e940d1bfe763f665c1428b01a9051660ff	6f58f22004db850554074055e287f6d5c6bbd95d792358fc2ff6006f43241979
3de60d0c-4a2b-4052-9f39-47e93a533100	prescription.created	2026-10-22 00:45:49.765506+00	604b3e79249f6ef38ba56cacd503731622f9213c312c34b0b41b11bdc75275ee	\N
e3bc9094-e906-4e9f-852e-0b2e1c4c8eaf	invoice.created	2026-10-22 00:45:53.089723+00	30262f45a2cff631395f626d5238558bd610e982c58e573308da4d4a8fa2df70	\N
33f4d6d9-99f8-407c-b685-4f95b4a0ac95	payment.completed	2026-10-22 00:45:53.103477+00	2af889163322daa2e2dea763bdeff509c588f9da085a6a08279c6bbd7845de98	a3674ea0dc259cab2c09a1e9d912d3a761c7368478fface2ac645f030e7f5c7c
6400782d-fa9c-41f8-a5c3-95b1ad7081ce	prescription.filled	2026-10-22 00:52:18.002303+00	b9dda7aebee9037c313c4d799c8266ce4ace510f6d2dddd350fdb8896818f2e2	\N
608193a2-1f8e-493f-a59b-a7d83eb512e7	payment.completed	2026-10-22 00:52:51.694191+00	6dc04bdf0549acc77536bd68ba07d1b9c475fa30fe136592196e0a691b725192	1164f1a6a265d654d0454f6a9b7458d3d7c5e55aaae8d30d6f4cff075f8314ff
b136373c-a447-4817-a839-e28c940fd939	payment.completed	2026-10-22 00:56:56.941206+00	722230eba5f27aee54d1c7158a37a9c3ae52380b8348badf94b73d13c51e8263	0a3af0287670f13c95d48bc98f28f1edf3fc247af7574b286329c6e8324980d3
d6572043-4b0b-42bb-b697-2b22281dfd34	patient.created	2026-10-22 10:20:56.963933+00	\N	\N
6dd96a9d-4d43-4522-ae1c-29095240e9cc	patient.created	2026-10-22 10:20:57.556943+00	\N	\N
7509810a-23a8-43cf-8838-cf9cb739ef54	patient.created	2026-10-22 10:20:57.61301+00	\N	\N
a4c64e94-7c4b-4bef-a1ac-85b418e3849f	patient.created	2026-10-22 10:20:57.702006+00	\N	\N
bae7796b-8fee-4d0a-a8e3-ae07a149d714	patient.created	2026-10-22 10:20:57.769127+00	\N	\N
4a943c3a-525a-478c-abe4-2cdf685ae52c	patient.created	2026-10-22 10:20:57.813734+00	\N	\N
b3e59db4-95cf-4ccc-9fdb-9e19b9976d50	patient.created	2026-10-22 10:20:57.884715+00	\N	\N
cb69b4ac-e723-473f-8ef4-db7df984249a	patient.created	2026-10-22 10:20:57.930016+00	\N	\N
b9e0321f-9f28-4c22-ab75-2960fd70641e	patient.created	2026-10-22 10:20:57.968886+00	\N	\N
4e1fadd8-161d-4e9a-928a-7783657ff728	patient.created	2026-10-22 10:20:58.008057+00	\N	\N
dee0992f-cbc0-4af3-aeca-3d1f806333b3	payment.completed	2026-10-22 10:53:24.838473+00	7d38b191c683e30a8c5d5c4315ccdf06f19e31d8a09b625c02c8aa74b07508b9	1f8a1a293bf8164c5e9bcc26e57b91f47780fae92529c346ea21e7fd45063669
b2531d42-b612-4cb9-a5f0-0781e3d5c106	payment.completed	2026-10-22 11:41:35.841174+00	4ab728c9ce7d72f6b1c0dca292ff8f352e1b189c6c61aed5d11a3cf16c39b09b	87bf7e320e85d601c3fee6fb758d1417fb362f41e27ec7186b66fa901151a194
\.


--
-- Data for Name: surgery_notice_case; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_notice_case (surgery_case_id, surgery_request_id, patient_id, department_id, care_episode_type, care_episode_id, admission_id, record_id, terminal_event_type, terminal_source_id) FROM stdin;
\.


--
-- Data for Name: surgery_notice_snapshot; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_notice_snapshot (snapshot_id, surgery_case_id, schedule_id, schedule_revision, invalidated) FROM stdin;
\.


--
-- Data for Name: surgery_notice_source; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_notice_source (event_type, source_id, surgery_case_id, payload_fingerprint) FROM stdin;
\.


--
-- Name: care_notification_source care_notification_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.care_notification_source
    ADD CONSTRAINT care_notification_source_pkey PRIMARY KEY (event_type, source_id);


--
-- Name: care_receipt_byte_backfill care_receipt_byte_backfill_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.care_receipt_byte_backfill
    ADD CONSTRAINT care_receipt_byte_backfill_pkey PRIMARY KEY (event_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: notification_event_outbox notification_event_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.notification_event_outbox
    ADD CONSTRAINT notification_event_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (notification_id);


--
-- Name: processed_event processed_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id);


--
-- Name: surgery_notice_case surgery_notice_case_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_notice_case
    ADD CONSTRAINT surgery_notice_case_pkey PRIMARY KEY (surgery_case_id);


--
-- Name: surgery_notice_snapshot surgery_notice_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_notice_snapshot
    ADD CONSTRAINT surgery_notice_snapshot_pkey PRIMARY KEY (snapshot_id);


--
-- Name: surgery_notice_source surgery_notice_source_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_notice_source
    ADD CONSTRAINT surgery_notice_source_pkey PRIMARY KEY (event_type, source_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_notification_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_notification_patient ON public.notification USING btree (patient_id, created_at DESC);


--
-- Name: idx_notification_status; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_notification_status ON public.notification USING btree (status);


--
-- Name: idx_surgery_notice_snapshot_case; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_surgery_notice_snapshot_case ON public.surgery_notice_snapshot USING btree (surgery_case_id);


--
-- Name: uq_notification_source_template_channel; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_notification_source_template_channel ON public.notification USING btree (source_event_id, template_key, channel) WHERE (source_event_id IS NOT NULL);


--
-- Name: care_receipt_byte_backfill care_receipt_byte_backfill_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.care_receipt_byte_backfill
    ADD CONSTRAINT care_receipt_byte_backfill_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.processed_event(event_id);


--
-- Name: notification_event_outbox notification_event_outbox_notification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.notification_event_outbox
    ADD CONSTRAINT notification_event_outbox_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES public.notification(notification_id);


--
-- Name: surgery_notice_snapshot surgery_notice_snapshot_surgery_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_notice_snapshot
    ADD CONSTRAINT surgery_notice_snapshot_surgery_case_id_fkey FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_notice_case(surgery_case_id);


--
-- Name: surgery_notice_source surgery_notice_source_surgery_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_notice_source
    ADD CONSTRAINT surgery_notice_source_surgery_case_id_fkey FOREIGN KEY (surgery_case_id) REFERENCES public.surgery_notice_case(surgery_case_id);


--
-- PostgreSQL database dump complete
--

\unrestrict bjthm0mTMVqJsx66Blo489CaPaoasMytVdqiKECUU2EAdASSLTItowchVFJ3cV8

--
-- Database "mediflow_organization" dump
--

--
-- PostgreSQL database dump
--

\restrict wDWZeq611C1RRNyLkDHbJSh4KwCtc4r38lNS5rWX67Jm4fP8mGfrq03g5QaFaj2

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_organization; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_organization WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_organization OWNER TO test;

\unrestrict wDWZeq611C1RRNyLkDHbJSh4KwCtc4r38lNS5rWX67Jm4fP8mGfrq03g5QaFaj2
\connect mediflow_organization
\restrict wDWZeq611C1RRNyLkDHbJSh4KwCtc4r38lNS5rWX67Jm4fP8mGfrq03g5QaFaj2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: account; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.account (
    account_id uuid NOT NULL,
    username character varying(50) NOT NULL,
    password_hash character varying(255) NOT NULL,
    staff_id uuid,
    role character varying(20) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    patient_id uuid,
    credential_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_account_credential_version CHECK ((credential_version >= 0)),
    CONSTRAINT ck_account_identity_owner CHECK (((((role)::text = 'PATIENT'::text) AND (patient_id IS NOT NULL) AND (staff_id IS NULL)) OR (((role)::text <> 'PATIENT'::text) AND (patient_id IS NULL))))
);


ALTER TABLE public.account OWNER TO test;

--
-- Name: account_creation_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.account_creation_receipt (
    actor_account_id uuid NOT NULL,
    idempotency_key character varying(160) NOT NULL,
    request_fingerprint character(64) NOT NULL,
    password_hash character varying(255) NOT NULL,
    account_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.account_creation_receipt OWNER TO test;

--
-- Name: department; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.department (
    department_id uuid NOT NULL,
    department_name character varying(100) NOT NULL,
    abbreviation character varying(20) NOT NULL,
    department_type character varying(20) NOT NULL,
    department_head_id uuid,
    location character varying(255),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone
);


ALTER TABLE public.department OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: local_demo_seed_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.local_demo_seed_receipt (
    seed_key character varying(100) NOT NULL,
    entity_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.local_demo_seed_receipt OWNER TO test;

--
-- Name: operating_room; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operating_room (
    room_id uuid NOT NULL,
    room_code character varying(40) NOT NULL,
    room_name character varying(100) NOT NULL,
    department_id uuid NOT NULL,
    is_active boolean NOT NULL,
    revision bigint NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT operating_room_revision_check CHECK ((revision > 0)),
    CONSTRAINT operating_room_room_code_check CHECK (((room_code)::text ~ '^[A-Z0-9_-]{1,40}$'::text)),
    CONSTRAINT operating_room_room_name_check CHECK ((length(TRIM(BOTH FROM room_name)) > 0))
);


ALTER TABLE public.operating_room OWNER TO test;

--
-- Name: organization_event_outbox; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.organization_event_outbox (
    event_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    correlation_id character varying(100) NOT NULL,
    routing_key character varying(150) NOT NULL,
    type_id character varying(255) NOT NULL,
    payload text NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(100),
    CONSTRAINT ck_organization_event_outbox_attempts CHECK ((attempts >= 0)),
    CONSTRAINT ck_organization_event_outbox_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PUBLISHED'::character varying)::text, ('QUARANTINED'::character varying)::text])))
);


ALTER TABLE public.organization_event_outbox OWNER TO test;

--
-- Name: room; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.room (
    room_id uuid NOT NULL,
    department_id uuid NOT NULL,
    room_name character varying(100) NOT NULL,
    room_type character varying(30) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone
);


ALTER TABLE public.room OWNER TO test;

--
-- Name: staff; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.staff (
    staff_id uuid NOT NULL,
    full_name character varying(100) NOT NULL,
    department_id uuid NOT NULL,
    job_title character varying(20) NOT NULL,
    specialization character varying(100),
    license_number character varying(50),
    phone_number character varying(15),
    email character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    identity_number character varying(12),
    CONSTRAINT ck_staff_identity_number CHECK (((identity_number IS NULL) OR ((identity_number)::text ~ '^[0-9]{12}$'::text)))
);


ALTER TABLE public.staff OWNER TO test;

--
-- Name: staff_creation_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.staff_creation_receipt (
    actor_account_id uuid NOT NULL,
    idempotency_key character varying(160) NOT NULL,
    request_fingerprint character(64) NOT NULL,
    staff_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.staff_creation_receipt OWNER TO test;

--
-- Name: surgery_authority_outbox; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgery_authority_outbox (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone
);


ALTER TABLE public.surgery_authority_outbox OWNER TO test;

--
-- Name: surgical_capability; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.surgical_capability (
    staff_id uuid NOT NULL,
    team_role character varying(30) NOT NULL,
    department_id uuid NOT NULL,
    is_active boolean NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_until timestamp with time zone NOT NULL,
    revision bigint NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT surgical_capability_check CHECK ((valid_until > valid_from)),
    CONSTRAINT surgical_capability_revision_check CHECK ((revision > 0)),
    CONSTRAINT surgical_capability_team_role_check CHECK (((team_role)::text = ANY (ARRAY[('PRIMARY_SURGEON'::character varying)::text, ('ASSISTANT_SURGEON'::character varying)::text, ('ANESTHESIOLOGIST'::character varying)::text, ('OR_NURSE'::character varying)::text])))
);


ALTER TABLE public.surgical_capability OWNER TO test;

--
-- Data for Name: account; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.account (account_id, username, password_hash, staff_id, role, is_active, last_login_at, created_at, updated_at, patient_id, credential_version) FROM stdin;
fd5d11b3-a7e5-40ea-ba98-ef24c063dea9	nurse-c94387bd7e37	$2a$10$omLtyVSdFXTT22W/13Lfe.bTkCup7wyV.YuW9jjDrY9HT4FjDMdlO	415793fe-a4b7-4e2c-baf9-b37e1ce1dc63	NURSE	t	2026-10-22 00:40:14.878285+00	2026-10-22 00:40:14.575414+00	2026-10-22 00:40:14.878285+00	\N	0
da50c583-e8e3-452b-9cc2-15f5adcf7cf3	doctor-c94387bd7e37	$2a$10$McgCjd4ohgUQHCbmSCa3OOfmWp49Nrj6mYt0ZqvsHs.zDUTYYwKNG	ddf8f66b-7b09-43e0-8e24-dd24678c3414	DOCTOR	t	2026-10-22 00:40:15.006919+00	2026-10-22 00:40:14.41395+00	2026-10-22 00:40:15.006919+00	\N	0
b7f06379-ddcc-4e1b-87e7-50ac24a7448f	cashier-c94387bd7e37	$2a$10$oVhmnX5nWvB9Q0FGzjMIUuU/BF2BeLgLcE96PAYPItu3FvG1vr1e6	dbb542f0-8396-4431-8a95-af7181c43a30	CASHIER	t	2026-10-22 00:40:15.16236+00	2026-10-22 00:40:14.648249+00	2026-10-22 00:40:15.16236+00	\N	0
374dea5c-6dd9-48b7-8bbb-d82198422e20	pharmacist-c94387bd7e37	$2a$10$6eWbGwW/1bxpNAZV1wWgBuZyw4Ta91wB2UfI3JapPGv93mzj6nHie	04676306-d5a0-4167-ba5f-fabd51df0e2c	PHARMACIST	t	2026-10-22 00:40:18.393248+00	2026-10-22 00:40:18.230016+00	2026-10-22 00:40:18.393248+00	\N	0
62400360-43e9-4c7c-afe2-bb4362dfa806	other-doctor-c94387bd7e37	$2a$10$P2l9v7vIeTYUwwznIPyDteYYkcd4otRg/79vPR/nnyocK6J.GR1jC	377cbe98-4805-4498-ba0a-e5a99e5193b2	DOCTOR	t	2026-10-22 00:40:18.475624+00	2026-10-22 00:40:14.496285+00	2026-10-22 00:40:18.475624+00	\N	0
63f5628c-6b3c-4795-aa4a-872821eafa08	admin-c94387bd7e37	$2a$10$jObHm9ZFRH8TooQoP8EEeO6V95TwIRiZCyPS.9DwIBmkZgRXykLg.	c891b85a-70ff-4363-abd7-11fdcf04989b	ADMIN	t	2026-10-22 00:40:35.320635+00	2026-10-22 00:40:18.140142+00	2026-10-22 00:40:35.320635+00	\N	0
95d6a8b5-c79b-4bc7-a740-fd2517744986	review3.pharmacist	$2a$10$Cs9VLT6Ji4j7dL9EwBL5p.SiDaSN4FjMdSqzKfZ4sA6.K3gg87.fi	c91f2020-617e-4767-8dec-937f2c30cad8	PHARMACIST	t	2026-10-22 11:50:49.348403+00	2026-10-22 00:40:36.771252+00	2026-10-22 11:50:49.348403+00	\N	0
8639641b-7891-4266-aa80-59fe517bf4b0	review3.patient	$2a$10$VOjsAVv7nxd6KyeptuZ.8.8zicICNYR5qlR2wkYMCe7oj.f0Uos/m	\N	PATIENT	t	2026-10-22 11:52:07.175422+00	2026-10-22 00:40:38.198828+00	2026-10-22 11:52:07.175422+00	30820325-711c-4d7b-b364-be73c78a43b0	0
54f93433-b786-4ef5-be62-b028dcaa64ad	review3.admin	$2a$10$QswauViVKCD3VbDPJEshB.VruPdLL3.RAd04mtXBwVRCYA2ehhjru	3d8f509e-fac1-4fcd-bdca-b98dca419491	ADMIN	t	2026-10-22 11:52:33.805685+00	2026-10-22 00:40:35.573053+00	2026-10-22 11:52:33.805685+00	\N	0
91428966-5e67-4cd7-a7da-f51e3b5723e9	review3.nurse	$2a$10$qBg7kerQ8fcgIG5HzEecDu7D.zu9vdA56oOq3h/LWJbTtt9whI2Sm	2dbedff2-d7f5-466a-b79c-df68a7daeff1	NURSE	t	2026-10-22 11:56:12.715696+00	2026-10-22 00:40:36.315376+00	2026-10-22 11:56:12.715696+00	\N	0
0474dc76-6e37-438c-a05e-32e7bb5c2835	review3.manager	$2a$10$imjU4lqxPFJL3//qG6t6rOFTOgKtvXTM6OPPKQNAPyop29cHhQDXG	de2d5e81-e2d0-44a1-81d3-0b2c88c642d6	MANAGER	t	2026-10-22 11:02:15.290836+00	2026-10-22 00:40:35.826439+00	2026-10-22 11:02:15.290836+00	\N	0
74654e38-5e8e-43a3-9186-c6abfc6c4b60	demo.admin	$2a$10$y19Em9gJDYjdxlxjfqmfZu7f2MKKDMcyXlXFxVaGoB8Co6GVtPC2m	f1a0b52a-f493-404f-82b6-b115d8a9fb18	ADMIN	t	\N	2026-10-22 10:20:54.131851+00	2026-10-22 10:20:54.131851+00	\N	0
73b48e03-2c18-46ee-b654-6a31d51505d6	demo.manager	$2a$10$nF3wISwEtmTGXx8mswkg1OBjcgyIWzCtRUuMrhHX.a/4FQHVcO0Sy	df1e1e38-e5c2-4578-b0b5-57806a2ee930	MANAGER	t	\N	2026-10-22 10:20:54.301241+00	2026-10-22 10:20:54.301241+00	\N	0
89c1138b-d621-4456-a00d-4f3a4bcfd4e8	demo.doctor	$2a$10$9CX06GXSG.th67BdzAVDHeKIJe4USFzXOLXgRuK2MVKQ35pdeRge6	be9cf102-f45a-424d-83af-b9e54d03ac06	DOCTOR	t	\N	2026-10-22 10:20:54.451229+00	2026-10-22 10:20:54.451229+00	\N	0
9cb552d5-92f2-4989-b536-3960de083d92	demo.doctor2	$2a$10$hu/j26y/ni5CSk.J/SlTFO5K7T/KdRiQaDJFORc36g26J3XvIP1Pi	a35bf152-732d-4219-a5b0-f794b3ed14ed	DOCTOR	t	\N	2026-10-22 10:20:54.611296+00	2026-10-22 10:20:54.611296+00	\N	0
db4d5e19-80f1-448d-9a7e-f05f6c201ea8	demo.nurse	$2a$10$wlwNsq/UcnbFz2QOJbuBHOswrR.YSFuPweuXw/Y7QwM5tBlIgATje	50be6184-116c-46e9-89a1-241b1d1e12cf	NURSE	t	\N	2026-10-22 10:20:54.757816+00	2026-10-22 10:20:54.757816+00	\N	0
35637b4e-be5c-48c4-a7c4-2c1028679e82	demo.lab	$2a$10$7hMx7ITLxwSiHJCgFp8.COnBZDsTzJKdjZd.ts5P2//uqw5u6ceji	d5dd2dce-a53c-47b5-bd4b-a73b5646809b	LAB_TECH	t	\N	2026-10-22 10:20:54.916719+00	2026-10-22 10:20:54.916719+00	\N	0
1959db60-3636-41f2-b6df-af658e8adc9d	demo.pharmacist	$2a$10$tNquzTcCi4qpG2F35.XKqueDKUJ4pzmRqdRHx2wxB/o05HW5/qgKG	cc08095b-2aa3-47b5-b189-8dbffbc8f3ac	PHARMACIST	t	\N	2026-10-22 10:20:55.104458+00	2026-10-22 10:20:55.104458+00	\N	0
5206c6d7-cf28-4e41-8f2d-eb0e0d4cdab3	demo.cashier	$2a$10$BobB3TJg1duPlJ1EGPxoauCADWZUHVIJC93UIGnCmV5Ip0y.oUrmS	0354d08b-2e07-466a-91d8-4a97495fe37e	CASHIER	t	\N	2026-10-22 10:20:55.274188+00	2026-10-22 10:20:55.274188+00	\N	0
5c38b357-9c06-4925-808c-7021c3fb38ef	demo.patient	$2a$10$kkIykyRIhEL6B628kG.1n.JzBeEh91SwdATsrxU76CZ4s5rm0CUEW	\N	PATIENT	t	\N	2026-10-22 10:20:55.38801+00	2026-10-22 10:20:55.38801+00	2abcf504-cf70-4933-9eff-5e5b7dfe5d05	0
db2e4271-84e3-43a1-b193-0103133db0c0	demo.patient2	$2a$10$NfXtzIJE74QvO3n0rTmayOxuFtWBl8ptqap5nl429vt5IoV3rE6A6	\N	PATIENT	t	\N	2026-10-22 10:20:55.488605+00	2026-10-22 10:20:55.488605+00	fac978a8-7abc-470f-830d-9823a8381b70	0
81432960-3a09-439c-b6bb-9de274648a9d	review3.doctor	$2a$10$wJZ2.vsLtTaKPgmbLB/SVupP9H6C2XkiiBF2dBnJ/aPscWW3viDii	e524185a-adb6-48d3-a867-b39b259940f2	DOCTOR	t	2026-10-22 11:03:21.983808+00	2026-10-22 00:40:36.075338+00	2026-10-22 11:03:21.983808+00	\N	0
78b0cc56-dd0f-4634-8ed0-be65e0c96229	review3.cashier	$2a$10$k9gilzLcLhvytwxP9SjkGuv9tVBWNsZEMNHR2LBDCUZNuHD8EuD2u	f01f79f0-ade2-4f95-b093-94f8c22041ce	CASHIER	t	2026-10-22 11:45:27.261079+00	2026-10-22 00:40:36.976638+00	2026-10-22 11:45:27.261079+00	\N	0
54e02f48-30c7-4249-9cb6-181c0395729c	review3.lab_tech	$2a$10$1OJm7euJKlVkhTlfCau2SeepdfMMsWwVgieLpexorIJnfh2WNsNIm	17fca9c9-39d6-49a7-bd89-5fc1c6138bad	LAB_TECH	t	2026-10-22 11:47:04.733855+00	2026-10-22 00:40:36.520269+00	2026-10-22 11:47:04.733855+00	\N	0
\.


--
-- Data for Name: account_creation_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.account_creation_receipt (actor_account_id, idempotency_key, request_fingerprint, password_hash, account_id, created_at) FROM stdin;
\.


--
-- Data for Name: department; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.department (department_id, department_name, abbreviation, department_type, department_head_id, location, is_active, created_at, updated_at) FROM stdin;
b134eebd-d327-4718-8bef-92525164178d	Acceptance Clinical c94387bd7e37	ACC943	CLINICAL	\N	Acceptance site	t	2026-10-22 00:40:14.126961+00	2026-10-22 00:40:14.126961+00
6c568d0b-debd-44ae-999d-3942e1ecfe8b	Khoa Nội tổng quát	NTQ	CLINICAL	\N	Tầng 1 · Khu khám A	t	2026-10-22 00:40:35.344827+00	2026-10-22 00:40:35.344827+00
2569f289-f271-4acc-8e0a-aa4ab1bbc6ff	Khoa Nhi	NHI	CLINICAL	\N	Tầng 1 · Khu khám B	t	2026-10-22 00:40:35.382017+00	2026-10-22 00:40:35.382017+00
36a19c36-892c-4100-a7b5-f1f2690390f4	Khoa Xét nghiệm	XN	PARACLINICAL	\N	Tầng 2 · Khu cận lâm sàng	t	2026-10-22 00:40:35.40655+00	2026-10-22 00:40:35.40655+00
395c4a4f-aec2-4736-a782-c55e2d874466	Phòng Hành chính	HC	ADMINISTRATIVE	\N	Tầng 3	t	2026-10-22 00:40:35.431249+00	2026-10-22 00:40:35.431249+00
a6817abd-6699-4c96-a138-afd87a2479b4	Khoa Nội tổng quát Demo	DEMO-NOI	CLINICAL	\N	Tầng 1, khu Demo	t	2026-10-22 10:20:50.077694+00	2026-10-22 10:20:50.077694+00
8c413c97-1a7d-4244-b176-c0379a3a1325	Khoa Nhi Demo	DEMO-NHI	CLINICAL	\N	Tầng 1, khu Demo	t	2026-10-22 10:20:50.657845+00	2026-10-22 10:20:50.657845+00
827f8b9c-3d39-48b2-8480-f655a88b4617	Phòng Hành chính Demo	DEMO-HC	ADMINISTRATIVE	\N	Tầng 2, khu Demo	t	2026-10-22 10:20:50.728538+00	2026-10-22 10:20:50.728538+00
b1787ead-9678-422f-a19b-e6c8cfaa144c	Khoa Tim mạch Demo	DEMO-TIM	CLINICAL	\N	Tầng 2, khu Demo	t	2026-10-22 10:20:50.789035+00	2026-10-22 10:20:50.789035+00
34dfa9f0-38d2-4972-97e4-9d945d5ca709	Khoa Tai Mũi Họng Demo	DEMO-TMH	CLINICAL	\N	Tầng 3, khu Demo	t	2026-10-22 10:20:50.874311+00	2026-10-22 10:20:50.874311+00
bcac2ed6-3e85-4e67-9330-61ae88a4d445	Khoa Da liễu Demo	DEMO-DL	CLINICAL	\N	Tầng 3, khu Demo	t	2026-10-22 10:20:50.950606+00	2026-10-22 10:20:50.950606+00
d9623dcd-954c-4f4b-a415-87de1af4ed48	Khoa Mắt Demo	DEMO-MAT	CLINICAL	\N	Tầng 4, khu Demo	t	2026-10-22 10:20:51.000633+00	2026-10-22 10:20:51.000633+00
cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2	Khoa Xét nghiệm Demo	DEMO-XN	PARACLINICAL	\N	Tầng 4, khu Demo	t	2026-10-22 10:20:51.088211+00	2026-10-22 10:20:51.088211+00
4a717d96-b33f-48b2-9a55-071d5742e8e5	Khoa Dược Demo	DEMO-DUOC	PARACLINICAL	\N	Tầng 5, khu Demo	t	2026-10-22 10:20:51.174856+00	2026-10-22 10:20:51.174856+00
5caaab98-3972-4c59-9abf-e1dfc8f0d036	Phòng Tài chính Kế toán Demo	DEMO-TCKT	ADMINISTRATIVE	\N	Tầng 5, khu Demo	t	2026-10-22 10:20:51.2767+00	2026-10-22 10:20:51.2767+00
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	785335221	test	2026-10-22 07:38:59.167027	32	t
2	2	account patient identity	SQL	V2__account_patient_identity.sql	651890207	test	2026-10-22 07:38:59.232758	11	t
3	3	room lookup	SQL	V3__room_lookup.sql	380225474	test	2026-10-22 07:38:59.265089	14	t
4	4	surgery reference authority	SQL	V4__surgery_reference_authority.sql	192298394	test	2026-10-22 07:38:59.298708	30	t
5	5	organization event outbox	SQL	V5__organization_event_outbox.sql	-765666539	test	2026-10-22 07:38:59.350959	15	t
6	6	staff creation idempotency receipts	SQL	V6__staff_creation_idempotency_receipts.sql	992860423	test	2026-10-22 07:38:59.382722	6	t
7	7	account creation idempotency receipts	SQL	V7__account_creation_idempotency_receipts.sql	-889832443	test	2026-10-22 07:38:59.401868	9	t
8	8	staff identity number	SQL	V8__staff_identity_number.sql	728779266	test	2026-10-22 07:38:59.42411	9	t
9	9	account credential version	SQL	V9__account_credential_version.sql	-1250697598	test	2026-10-22 07:38:59.446637	6	t
10	10	local demo seed receipts	SQL	V10__local_demo_seed_receipts.sql	-686894435	test	2026-10-22 17:20:14.270888	307	t
\.


--
-- Data for Name: local_demo_seed_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.local_demo_seed_receipt (seed_key, entity_id, created_at) FROM stdin;
department-01	a6817abd-6699-4c96-a138-afd87a2479b4	2026-10-22 10:20:49.072127+00
department-02	8c413c97-1a7d-4244-b176-c0379a3a1325	2026-10-22 10:20:49.072127+00
department-03	827f8b9c-3d39-48b2-8480-f655a88b4617	2026-10-22 10:20:49.072127+00
department-04	b1787ead-9678-422f-a19b-e6c8cfaa144c	2026-10-22 10:20:49.072127+00
department-05	34dfa9f0-38d2-4972-97e4-9d945d5ca709	2026-10-22 10:20:49.072127+00
department-06	bcac2ed6-3e85-4e67-9330-61ae88a4d445	2026-10-22 10:20:49.072127+00
department-07	d9623dcd-954c-4f4b-a415-87de1af4ed48	2026-10-22 10:20:49.072127+00
department-08	cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2	2026-10-22 10:20:49.072127+00
department-09	4a717d96-b33f-48b2-9a55-071d5742e8e5	2026-10-22 10:20:49.072127+00
department-10	5caaab98-3972-4c59-9abf-e1dfc8f0d036	2026-10-22 10:20:49.072127+00
staff-01	f1a0b52a-f493-404f-82b6-b115d8a9fb18	2026-10-22 10:20:49.072127+00
staff-02	df1e1e38-e5c2-4578-b0b5-57806a2ee930	2026-10-22 10:20:49.072127+00
staff-03	be9cf102-f45a-424d-83af-b9e54d03ac06	2026-10-22 10:20:49.072127+00
staff-04	50be6184-116c-46e9-89a1-241b1d1e12cf	2026-10-22 10:20:49.072127+00
staff-05	a35bf152-732d-4219-a5b0-f794b3ed14ed	2026-10-22 10:20:49.072127+00
staff-06	b0de281a-6901-4a5b-8e0a-a2dc7a3a6511	2026-10-22 10:20:49.072127+00
staff-07	4fd41f3b-d1ac-4090-9f6a-566824649bc6	2026-10-22 10:20:49.072127+00
staff-08	4fe03e80-5a26-4a00-a8d2-f528052a1ece	2026-10-22 10:20:49.072127+00
staff-09	676dc13f-1e6c-49f8-a004-c341b5619cbe	2026-10-22 10:20:49.072127+00
staff-10	f1875dcc-9a11-4c76-b05c-a004ce37ce23	2026-10-22 10:20:49.072127+00
staff-11	341b1aee-0447-40f7-b0c3-8ecea6cb3ee6	2026-10-22 10:20:49.072127+00
staff-12	2de98c18-7ae3-432d-9eaf-3fd03afdc398	2026-10-22 10:20:49.072127+00
staff-13	5da789cf-2cba-4e84-9912-a55deb69b212	2026-10-22 10:20:49.072127+00
staff-14	94c80de8-d0dd-4bf5-a977-27d79725f57b	2026-10-22 10:20:49.072127+00
staff-15	d5dd2dce-a53c-47b5-bd4b-a73b5646809b	2026-10-22 10:20:49.072127+00
staff-16	fa57c45b-a67e-4c9c-bcf5-e703850ffa92	2026-10-22 10:20:49.072127+00
staff-17	cc08095b-2aa3-47b5-b189-8dbffbc8f3ac	2026-10-22 10:20:49.072127+00
staff-18	4015e7ae-7336-4cec-a2c4-a34ddddc0ad2	2026-10-22 10:20:49.072127+00
staff-19	0354d08b-2e07-466a-91d8-4a97495fe37e	2026-10-22 10:20:49.072127+00
staff-20	bfe20ea2-5ff7-475c-89a5-2038756e6e40	2026-10-22 10:20:49.072127+00
account-demo.admin	74654e38-5e8e-43a3-9186-c6abfc6c4b60	2026-10-22 10:20:53.887794+00
account-demo.manager	73b48e03-2c18-46ee-b654-6a31d51505d6	2026-10-22 10:20:53.887794+00
account-demo.doctor	89c1138b-d621-4456-a00d-4f3a4bcfd4e8	2026-10-22 10:20:53.887794+00
account-demo.doctor2	9cb552d5-92f2-4989-b536-3960de083d92	2026-10-22 10:20:53.887794+00
account-demo.nurse	db4d5e19-80f1-448d-9a7e-f05f6c201ea8	2026-10-22 10:20:53.887794+00
account-demo.lab	35637b4e-be5c-48c4-a7c4-2c1028679e82	2026-10-22 10:20:53.887794+00
account-demo.pharmacist	1959db60-3636-41f2-b6df-af658e8adc9d	2026-10-22 10:20:53.887794+00
account-demo.cashier	5206c6d7-cf28-4e41-8f2d-eb0e0d4cdab3	2026-10-22 10:20:53.887794+00
account-demo.patient	5c38b357-9c06-4925-808c-7021c3fb38ef	2026-10-22 10:20:53.887794+00
account-demo.patient2	db2e4271-84e3-43a1-b193-0103133db0c0	2026-10-22 10:20:53.887794+00
\.


--
-- Data for Name: operating_room; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operating_room (room_id, room_code, room_name, department_id, is_active, revision, updated_at) FROM stdin;
\.


--
-- Data for Name: organization_event_outbox; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.organization_event_outbox (event_id, occurred_at, correlation_id, routing_key, type_id, payload, status, attempts, next_attempt_at, published_at, last_error) FROM stdin;
69cc8216-a9b1-4c0a-b647-fe823cb2e1d8	2026-10-22 00:40:14.158299+00	cb3a07ae-b230-4ffc-82f2-4ccd4756834d	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"69cc8216-a9b1-4c0a-b647-fe823cb2e1d8","occurredAt":1792629614.158299400,"correlationId":"cb3a07ae-b230-4ffc-82f2-4ccd4756834d","departmentId":"b134eebd-d327-4718-8bef-92525164178d","departmentName":"Acceptance Clinical c94387bd7e37","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 00:49:04.051867+00	\N	IllegalStateException
5f902c10-94d0-4bda-983c-be7ea46462da	2026-10-22 00:40:14.267336+00	9dd22a1d-fbec-4503-a150-7835f1e096d0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"5f902c10-94d0-4bda-983c-be7ea46462da","occurredAt":1792629614.267336100,"correlationId":"9dd22a1d-fbec-4503-a150-7835f1e096d0","staffId":"ddf8f66b-7b09-43e0-8e24-dd24678c3414","fullName":"Acceptance assigned c94387bd7e37","departmentId":"b134eebd-d327-4718-8bef-92525164178d","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 00:49:04.0556+00	\N	IllegalStateException
46af5ba8-f767-4e61-ae9d-ecec290a956f	2026-10-22 00:40:14.290272+00	eeb6a058-26f2-4d56-aafb-898787466a62	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"46af5ba8-f767-4e61-ae9d-ecec290a956f","occurredAt":1792629614.290271500,"correlationId":"eeb6a058-26f2-4d56-aafb-898787466a62","staffId":"377cbe98-4805-4498-ba0a-e5a99e5193b2","fullName":"Acceptance other c94387bd7e37","departmentId":"b134eebd-d327-4718-8bef-92525164178d","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 00:49:04.058725+00	\N	IllegalStateException
c0c20368-c5dd-44c6-a480-21abfa6af548	2026-10-22 00:40:14.308581+00	f6df95c1-27ed-4dc7-82fc-59b6d851ed3c	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"c0c20368-c5dd-44c6-a480-21abfa6af548","occurredAt":1792629614.308580800,"correlationId":"f6df95c1-27ed-4dc7-82fc-59b6d851ed3c","staffId":"415793fe-a4b7-4e2c-baf9-b37e1ce1dc63","fullName":"Acceptance nurse c94387bd7e37","departmentId":"b134eebd-d327-4718-8bef-92525164178d","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 00:49:04.061306+00	\N	IllegalStateException
1363843e-7616-4131-bc36-8202eae54777	2026-10-22 00:40:14.326947+00	d5d11b4a-4371-4fae-8294-29db4dda5b8d	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"1363843e-7616-4131-bc36-8202eae54777","occurredAt":1792629614.326947100,"correlationId":"d5d11b4a-4371-4fae-8294-29db4dda5b8d","staffId":"dbb542f0-8396-4431-8a95-af7181c43a30","fullName":"Acceptance cashier c94387bd7e37","departmentId":"b134eebd-d327-4718-8bef-92525164178d","jobTitle":"CASHIER"}	QUARANTINED	8	2026-10-22 00:49:04.064985+00	\N	IllegalStateException
7fa6edce-238e-49fd-93a7-ed69dea79bb6	2026-10-22 00:40:18.04441+00	55f0049c-c7ea-4ea7-a79f-ef25cbe5dfbc	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"7fa6edce-238e-49fd-93a7-ed69dea79bb6","occurredAt":1792629618.044409800,"correlationId":"55f0049c-c7ea-4ea7-a79f-ef25cbe5dfbc","staffId":"c891b85a-70ff-4363-abd7-11fdcf04989b","fullName":"Acceptance admin c94387bd7e37","departmentId":"b134eebd-d327-4718-8bef-92525164178d","jobTitle":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 00:49:09.07378+00	\N	IllegalStateException
e1f3891b-ca9a-4f7c-ad95-55d843f52e13	2026-10-22 00:40:18.064933+00	85e24ca6-1aa1-422d-9d5f-534040091fc3	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"e1f3891b-ca9a-4f7c-ad95-55d843f52e13","occurredAt":1792629618.064933300,"correlationId":"85e24ca6-1aa1-422d-9d5f-534040091fc3","staffId":"04676306-d5a0-4167-ba5f-fabd51df0e2c","fullName":"Acceptance pharmacist c94387bd7e37","departmentId":"b134eebd-d327-4718-8bef-92525164178d","jobTitle":"PHARMACIST"}	QUARANTINED	8	2026-10-22 00:49:09.077455+00	\N	IllegalStateException
71099611-a8a4-415a-ac28-07fbfec5c5ed	2026-10-22 00:40:35.347437+00	7645b468-f47e-45cb-89b7-abb1413d6f99	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"71099611-a8a4-415a-ac28-07fbfec5c5ed","occurredAt":1792629635.347436900,"correlationId":"7645b468-f47e-45cb-89b7-abb1413d6f99","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","departmentName":"Khoa Nội tổng quát","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 00:49:24.110172+00	\N	IllegalStateException
2b750925-d400-4376-8434-ed98265ebf29	2026-10-22 00:40:35.384116+00	22c323f6-947b-4bf5-849d-df5eea645a71	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"2b750925-d400-4376-8434-ed98265ebf29","occurredAt":1792629635.384115800,"correlationId":"22c323f6-947b-4bf5-849d-df5eea645a71","departmentId":"2569f289-f271-4acc-8e0a-aa4ab1bbc6ff","departmentName":"Khoa Nhi","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 00:49:24.113907+00	\N	IllegalStateException
a78c3185-a525-42bd-8875-8710e584fe93	2026-10-22 00:40:35.408631+00	873d3a09-5f90-4885-b992-93890959b790	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"a78c3185-a525-42bd-8875-8710e584fe93","occurredAt":1792629635.408630500,"correlationId":"873d3a09-5f90-4885-b992-93890959b790","departmentId":"36a19c36-892c-4100-a7b5-f1f2690390f4","departmentName":"Khoa Xét nghiệm","departmentType":"PARACLINICAL"}	QUARANTINED	8	2026-10-22 00:49:24.116538+00	\N	IllegalStateException
e8954ab1-5524-46b6-8904-b6c182436066	2026-10-22 00:40:35.433879+00	136b5f92-f583-4dc4-9e4e-f34236ef7717	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"e8954ab1-5524-46b6-8904-b6c182436066","occurredAt":1792629635.433879200,"correlationId":"136b5f92-f583-4dc4-9e4e-f34236ef7717","departmentId":"395c4a4f-aec2-4736-a782-c55e2d874466","departmentName":"Phòng Hành chính","departmentType":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 00:49:24.119166+00	\N	IllegalStateException
2c899747-edb4-4d69-b948-12219582f198	2026-10-22 00:40:35.483418+00	40161ab3-4de9-4308-9657-0464543fac79	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"2c899747-edb4-4d69-b948-12219582f198","occurredAt":1792629635.483417700,"correlationId":"40161ab3-4de9-4308-9657-0464543fac79","staffId":"3d8f509e-fac1-4fcd-bdca-b98dca419491","fullName":"Nguyễn Minh Quân","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 00:49:24.12179+00	\N	IllegalStateException
450bd644-f321-4f3e-ba20-41438f399259	2026-10-22 00:40:35.731119+00	08724aa0-0987-442e-972c-a7f2c7fcf532	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"450bd644-f321-4f3e-ba20-41438f399259","occurredAt":1792629635.731119400,"correlationId":"08724aa0-0987-442e-972c-a7f2c7fcf532","staffId":"de2d5e81-e2d0-44a1-81d3-0b2c88c642d6","fullName":"Trần Thu Hà","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"MANAGER"}	QUARANTINED	8	2026-10-22 00:49:24.124713+00	\N	IllegalStateException
c67fe0aa-9563-4905-95a4-81204f92836c	2026-10-22 00:40:35.963388+00	62d95029-f5fc-4dca-9e73-07cb5fcb3f88	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"c67fe0aa-9563-4905-95a4-81204f92836c","occurredAt":1792629635.963388400,"correlationId":"62d95029-f5fc-4dca-9e73-07cb5fcb3f88","staffId":"e524185a-adb6-48d3-a867-b39b259940f2","fullName":"Lê Hoàng Phúc","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 00:49:24.127309+00	\N	IllegalStateException
4fb9e9a7-8f88-4f31-bc86-1f3ace330c2a	2026-10-22 00:40:36.210162+00	b7c36af4-af4d-40d5-baac-77a97c965edf	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"4fb9e9a7-8f88-4f31-bc86-1f3ace330c2a","occurredAt":1792629636.210162400,"correlationId":"b7c36af4-af4d-40d5-baac-77a97c965edf","staffId":"2dbedff2-d7f5-466a-b79c-df68a7daeff1","fullName":"Phạm Ngọc Mai","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 00:49:24.129396+00	\N	IllegalStateException
89151df7-63c8-468e-b119-a034c8053bc4	2026-10-22 00:40:36.437429+00	8f5c2f3b-aea5-41e5-9ed2-2ebcc9e9a478	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"89151df7-63c8-468e-b119-a034c8053bc4","occurredAt":1792629636.437428700,"correlationId":"8f5c2f3b-aea5-41e5-9ed2-2ebcc9e9a478","staffId":"17fca9c9-39d6-49a7-bd89-5fc1c6138bad","fullName":"Võ Thanh Tâm","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"TECHNICIAN"}	QUARANTINED	8	2026-10-22 00:49:24.13249+00	\N	IllegalStateException
c597667d-c2ed-4e73-aeb0-263461448cb5	2026-10-22 00:40:36.665781+00	b3ee0c55-54c5-4b28-b7b1-5e4d9a79eb0a	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"c597667d-c2ed-4e73-aeb0-263461448cb5","occurredAt":1792629636.665780700,"correlationId":"b3ee0c55-54c5-4b28-b7b1-5e4d9a79eb0a","staffId":"c91f2020-617e-4767-8dec-937f2c30cad8","fullName":"Đặng Thảo Nguyên","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"PHARMACIST"}	QUARANTINED	8	2026-10-22 00:49:29.13911+00	\N	IllegalStateException
3b9d72f5-ffb7-440a-ad7d-87a1f3498bd2	2026-10-22 00:40:36.889477+00	f275e4d7-f4d5-4464-9c5c-10f331cbad91	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"3b9d72f5-ffb7-440a-ad7d-87a1f3498bd2","occurredAt":1792629636.889476700,"correlationId":"f275e4d7-f4d5-4464-9c5c-10f331cbad91","staffId":"f01f79f0-ade2-4f95-b093-94f8c22041ce","fullName":"Bùi Khánh Linh","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","jobTitle":"CASHIER"}	QUARANTINED	8	2026-10-22 00:49:29.142279+00	\N	IllegalStateException
8fd1f9cd-2c75-4a99-8f27-da2f1a42d8e5	2026-10-22 10:20:52.137341+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"8fd1f9cd-2c75-4a99-8f27-da2f1a42d8e5","occurredAt":1792664452.137340700,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"676dc13f-1e6c-49f8-a004-c341b5619cbe","fullName":"Võ Quốc Bảo Demo","departmentId":"34dfa9f0-38d2-4972-97e4-9d945d5ca709","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 10:29:51.047917+00	\N	IllegalStateException
fbbb9dec-23da-4d2e-9385-0cd3418f60ca	2026-10-22 10:20:52.19587+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"fbbb9dec-23da-4d2e-9385-0cd3418f60ca","occurredAt":1792664452.195869900,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"f1875dcc-9a11-4c76-b05c-a004ce37ce23","fullName":"Đặng Thảo Nguyên Demo","departmentId":"34dfa9f0-38d2-4972-97e4-9d945d5ca709","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 10:29:51.05755+00	\N	IllegalStateException
1803635d-92ed-4960-bdfb-0bc21cc543b1	2026-10-22 10:20:52.248486+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"1803635d-92ed-4960-bdfb-0bc21cc543b1","occurredAt":1792664452.248486100,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"341b1aee-0447-40f7-b0c3-8ecea6cb3ee6","fullName":"Bùi Minh Châu Demo","departmentId":"bcac2ed6-3e85-4e67-9330-61ae88a4d445","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 10:29:56.080873+00	\N	IllegalStateException
4187b1d5-7955-4b62-a1fa-6db1207267c9	2026-10-22 10:20:52.291585+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"4187b1d5-7955-4b62-a1fa-6db1207267c9","occurredAt":1792664452.291585300,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"2de98c18-7ae3-432d-9eaf-3fd03afdc398","fullName":"Đỗ Ngọc Anh Demo","departmentId":"bcac2ed6-3e85-4e67-9330-61ae88a4d445","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 10:29:56.103502+00	\N	IllegalStateException
39d5a348-70a8-478f-b3ab-4076bed9c294	2026-10-22 10:20:52.32955+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"39d5a348-70a8-478f-b3ab-4076bed9c294","occurredAt":1792664452.329550100,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"5da789cf-2cba-4e84-9912-a55deb69b212","fullName":"Hồ Anh Tuấn Demo","departmentId":"d9623dcd-954c-4f4b-a415-87de1af4ed48","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 10:29:56.132865+00	\N	IllegalStateException
700ff510-ee81-46bd-966b-151444474dec	2026-10-22 10:20:52.373028+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"700ff510-ee81-46bd-966b-151444474dec","occurredAt":1792664452.373028000,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"94c80de8-d0dd-4bf5-a977-27d79725f57b","fullName":"Ngô Thu Hương Demo","departmentId":"d9623dcd-954c-4f4b-a415-87de1af4ed48","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 10:29:56.164476+00	\N	IllegalStateException
0733593a-31bf-484e-815c-d84d3d9ed95b	2026-10-22 10:20:52.432892+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"0733593a-31bf-484e-815c-d84d3d9ed95b","occurredAt":1792664452.432892300,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"d5dd2dce-a53c-47b5-bd4b-a73b5646809b","fullName":"Dương Thanh Tâm Demo","departmentId":"cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2","jobTitle":"TECHNICIAN"}	QUARANTINED	8	2026-10-22 10:29:56.211664+00	\N	IllegalStateException
dfeae821-7392-4fc0-9e61-1f2d95832787	2026-10-22 10:20:52.496555+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"dfeae821-7392-4fc0-9e61-1f2d95832787","occurredAt":1792664452.496555000,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"fa57c45b-a67e-4c9c-bcf5-e703850ffa92","fullName":"Vũ Ngọc Hà Demo","departmentId":"cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2","jobTitle":"TECHNICIAN"}	QUARANTINED	8	2026-10-22 10:29:56.266029+00	\N	IllegalStateException
d1c9e8f0-fc4d-48fd-bdb0-60c861f02cd2	2026-10-22 10:20:52.62581+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"d1c9e8f0-fc4d-48fd-bdb0-60c861f02cd2","occurredAt":1792664452.625810100,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"cc08095b-2aa3-47b5-b189-8dbffbc8f3ac","fullName":"Mai Thảo Vy Demo","departmentId":"4a717d96-b33f-48b2-9a55-071d5742e8e5","jobTitle":"PHARMACIST"}	QUARANTINED	8	2026-10-22 10:29:56.322305+00	\N	IllegalStateException
f5d92bd4-c4c9-4414-b972-05700a427991	2026-10-22 10:20:52.669172+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"f5d92bd4-c4c9-4414-b972-05700a427991","occurredAt":1792664452.669172000,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"4015e7ae-7336-4cec-a2c4-a34ddddc0ad2","fullName":"Đinh Gia Huy Demo","departmentId":"4a717d96-b33f-48b2-9a55-071d5742e8e5","jobTitle":"PHARMACIST"}	QUARANTINED	8	2026-10-22 10:29:56.354626+00	\N	IllegalStateException
fdff8838-d889-499a-bf54-deb829a58564	2026-10-22 10:20:52.726296+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"fdff8838-d889-499a-bf54-deb829a58564","occurredAt":1792664452.726295700,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"0354d08b-2e07-466a-91d8-4a97495fe37e","fullName":"Phan Khánh Linh Demo","departmentId":"5caaab98-3972-4c59-9abf-e1dfc8f0d036","jobTitle":"CASHIER"}	QUARANTINED	8	2026-10-22 10:29:56.389478+00	\N	IllegalStateException
d83a3ec6-8a74-415e-bb7b-99ceed4b64e2	2026-10-22 10:20:50.219078+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"d83a3ec6-8a74-415e-bb7b-99ceed4b64e2","occurredAt":1792664450.219077800,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"a6817abd-6699-4c96-a138-afd87a2479b4","departmentName":"Khoa Nội tổng quát Demo","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.856368+00	\N	IllegalStateException
02cdf10f-058d-40c9-b39b-54f507b977fc	2026-10-22 10:20:50.666993+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"02cdf10f-058d-40c9-b39b-54f507b977fc","occurredAt":1792664450.666993300,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"8c413c97-1a7d-4244-b176-c0379a3a1325","departmentName":"Khoa Nhi Demo","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.867784+00	\N	IllegalStateException
faa33610-e5b4-4fe6-80f9-e1de950e0267	2026-10-22 10:20:50.812564+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"faa33610-e5b4-4fe6-80f9-e1de950e0267","occurredAt":1792664450.812563700,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"b1787ead-9678-422f-a19b-e6c8cfaa144c","departmentName":"Khoa Tim mạch Demo","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.895423+00	\N	IllegalStateException
cff7315f-9e79-48d5-a6d0-4095d54b982c	2026-10-22 10:20:50.887394+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"cff7315f-9e79-48d5-a6d0-4095d54b982c","occurredAt":1792664450.887394100,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"34dfa9f0-38d2-4972-97e4-9d945d5ca709","departmentName":"Khoa Tai Mũi Họng Demo","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.905087+00	\N	IllegalStateException
06d245cf-c9e2-4d0d-91f4-0c4c0613586c	2026-10-22 10:20:50.959122+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"06d245cf-c9e2-4d0d-91f4-0c4c0613586c","occurredAt":1792664450.959121500,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"bcac2ed6-3e85-4e67-9330-61ae88a4d445","departmentName":"Khoa Da liễu Demo","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.917534+00	\N	IllegalStateException
e00808cd-2d32-4654-8c9f-965b594bb395	2026-10-22 10:20:51.010942+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"e00808cd-2d32-4654-8c9f-965b594bb395","occurredAt":1792664451.010942200,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"d9623dcd-954c-4f4b-a415-87de1af4ed48","departmentName":"Khoa Mắt Demo","departmentType":"CLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.928097+00	\N	IllegalStateException
c41fbe22-394c-4535-8d2f-bfc29400e91a	2026-10-22 10:20:51.111342+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"c41fbe22-394c-4535-8d2f-bfc29400e91a","occurredAt":1792664451.111342000,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2","departmentName":"Khoa Xét nghiệm Demo","departmentType":"PARACLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.941513+00	\N	IllegalStateException
1fd64225-1437-4af4-8977-8134b0af6e21	2026-10-22 10:20:51.182031+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"1fd64225-1437-4af4-8977-8134b0af6e21","occurredAt":1792664451.182030600,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"4a717d96-b33f-48b2-9a55-071d5742e8e5","departmentName":"Khoa Dược Demo","departmentType":"PARACLINICAL"}	QUARANTINED	8	2026-10-22 10:29:50.955179+00	\N	IllegalStateException
a3b34698-ca64-4340-9a95-bb859a433df3	2026-10-22 10:20:51.295116+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"a3b34698-ca64-4340-9a95-bb859a433df3","occurredAt":1792664451.295115800,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"5caaab98-3972-4c59-9abf-e1dfc8f0d036","departmentName":"Phòng Tài chính Kế toán Demo","departmentType":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 10:29:50.966981+00	\N	IllegalStateException
5e4deadb-fbf2-4e83-938f-3dee0b298d2e	2026-10-22 10:20:51.552313+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"5e4deadb-fbf2-4e83-938f-3dee0b298d2e","occurredAt":1792664451.552313400,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"f1a0b52a-f493-404f-82b6-b115d8a9fb18","fullName":"Nguyễn Quản Trị Demo","departmentId":"827f8b9c-3d39-48b2-8480-f655a88b4617","jobTitle":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 10:29:50.975039+00	\N	IllegalStateException
33cce265-6f73-4cdd-9384-18e2a7a5ac73	2026-10-22 10:20:51.615615+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"33cce265-6f73-4cdd-9384-18e2a7a5ac73","occurredAt":1792664451.615615200,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"df1e1e38-e5c2-4578-b0b5-57806a2ee930","fullName":"Trần Quản Lý Demo","departmentId":"827f8b9c-3d39-48b2-8480-f655a88b4617","jobTitle":"MANAGER"}	QUARANTINED	8	2026-10-22 10:29:50.98332+00	\N	IllegalStateException
839fd8cf-6234-4612-835b-75239e967778	2026-10-22 10:20:51.67934+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"839fd8cf-6234-4612-835b-75239e967778","occurredAt":1792664451.679340400,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"be9cf102-f45a-424d-83af-b9e54d03ac06","fullName":"Lê Bác Sĩ Demo","departmentId":"a6817abd-6699-4c96-a138-afd87a2479b4","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 10:29:50.991813+00	\N	IllegalStateException
20b95d6f-1bb5-4e19-919c-90b495e74fbb	2026-10-22 10:20:51.759204+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"20b95d6f-1bb5-4e19-919c-90b495e74fbb","occurredAt":1792664451.759204400,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"50be6184-116c-46e9-89a1-241b1d1e12cf","fullName":"Phạm Điều Dưỡng Demo","departmentId":"a6817abd-6699-4c96-a138-afd87a2479b4","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 10:29:51.004543+00	\N	IllegalStateException
70863692-f2fc-4e78-aaa8-98976820204f	2026-10-22 10:20:51.838789+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"70863692-f2fc-4e78-aaa8-98976820204f","occurredAt":1792664451.838789100,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"a35bf152-732d-4219-a5b0-f794b3ed14ed","fullName":"Nguyễn Minh Phúc Demo","departmentId":"8c413c97-1a7d-4244-b176-c0379a3a1325","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 10:29:51.012435+00	\N	IllegalStateException
4b0c312e-7622-4dbc-9896-d0507799a23b	2026-10-22 10:20:50.739354+00	c68c1d7e-4e01-4856-89f2-059618775fb0	department.created	com.mediflow.organization.application.event.DepartmentCreatedEvent	{"eventId":"4b0c312e-7622-4dbc-9896-d0507799a23b","occurredAt":1792664450.739353800,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","departmentId":"827f8b9c-3d39-48b2-8480-f655a88b4617","departmentName":"Phòng Hành chính Demo","departmentType":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 10:29:50.882736+00	\N	IllegalStateException
6cfa2869-98b3-414d-94ff-3b0217805c23	2026-10-22 10:20:51.89552+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"6cfa2869-98b3-414d-94ff-3b0217805c23","occurredAt":1792664451.895519900,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"b0de281a-6901-4a5b-8e0a-a2dc7a3a6511","fullName":"Trần Ngọc Lan Demo","departmentId":"8c413c97-1a7d-4244-b176-c0379a3a1325","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 10:29:51.020956+00	\N	IllegalStateException
fbae952a-fd9d-43ae-a486-194f8cf9daf6	2026-10-22 10:20:51.988673+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"fbae952a-fd9d-43ae-a486-194f8cf9daf6","occurredAt":1792664451.988672500,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"4fd41f3b-d1ac-4090-9f6a-566824649bc6","fullName":"Lê Hoàng Nam Demo","departmentId":"b1787ead-9678-422f-a19b-e6c8cfaa144c","jobTitle":"DOCTOR"}	QUARANTINED	8	2026-10-22 10:29:51.028928+00	\N	IllegalStateException
0fd8aa57-d226-4112-b56f-179a0ecf708f	2026-10-22 10:20:52.076466+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"0fd8aa57-d226-4112-b56f-179a0ecf708f","occurredAt":1792664452.076465800,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"4fe03e80-5a26-4a00-a8d2-f528052a1ece","fullName":"Phạm Thanh Hà Demo","departmentId":"b1787ead-9678-422f-a19b-e6c8cfaa144c","jobTitle":"NURSE"}	QUARANTINED	8	2026-10-22 10:29:51.037472+00	\N	IllegalStateException
3e7e0654-0df0-4ab6-b74d-ada9f435708e	2026-10-22 10:20:52.888889+00	c68c1d7e-4e01-4856-89f2-059618775fb0	staff.created	com.mediflow.organization.application.event.StaffCreatedEvent	{"eventId":"3e7e0654-0df0-4ab6-b74d-ada9f435708e","occurredAt":1792664452.888889400,"correlationId":"c68c1d7e-4e01-4856-89f2-059618775fb0","staffId":"bfe20ea2-5ff7-475c-89a5-2038756e6e40","fullName":"Lý Minh Quân Demo","departmentId":"5caaab98-3972-4c59-9abf-e1dfc8f0d036","jobTitle":"ADMINISTRATIVE"}	QUARANTINED	8	2026-10-22 10:29:56.440656+00	\N	IllegalStateException
\.


--
-- Data for Name: room; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.room (room_id, department_id, room_name, room_type, is_active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: staff; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.staff (staff_id, full_name, department_id, job_title, specialization, license_number, phone_number, email, status, created_at, updated_at, identity_number) FROM stdin;
ddf8f66b-7b09-43e0-8e24-dd24678c3414	Acceptance assigned c94387bd7e37	b134eebd-d327-4718-8bef-92525164178d	DOCTOR	General medicine	LIC-assigned-c94387bd7e37	9706397429	assigned-c94387bd7e37@example.test	ACTIVE	2026-10-22 00:40:14.260288+00	2026-10-22 00:40:14.260288+00	605467503438
377cbe98-4805-4498-ba0a-e5a99e5193b2	Acceptance other c94387bd7e37	b134eebd-d327-4718-8bef-92525164178d	DOCTOR	General medicine	LIC-other-c94387bd7e37	7003229190	other-c94387bd7e37@example.test	ACTIVE	2026-10-22 00:40:14.287591+00	2026-10-22 00:40:14.287591+00	010344311253
415793fe-a4b7-4e2c-baf9-b37e1ce1dc63	Acceptance nurse c94387bd7e37	b134eebd-d327-4718-8bef-92525164178d	NURSE	\N	\N	6013272071	nurse-c94387bd7e37@example.test	ACTIVE	2026-10-22 00:40:14.305959+00	2026-10-22 00:40:14.305959+00	643161642912
dbb542f0-8396-4431-8a95-af7181c43a30	Acceptance cashier c94387bd7e37	b134eebd-d327-4718-8bef-92525164178d	CASHIER	\N	\N	5560358539	cashier-c94387bd7e37@example.test	ACTIVE	2026-10-22 00:40:14.324305+00	2026-10-22 00:40:14.324305+00	437692291074
c891b85a-70ff-4363-abd7-11fdcf04989b	Acceptance admin c94387bd7e37	b134eebd-d327-4718-8bef-92525164178d	ADMINISTRATIVE	\N	\N	2481414603	admin-c94387bd7e37@example.test	ACTIVE	2026-10-22 00:40:18.041251+00	2026-10-22 00:40:18.041251+00	463798334382
04676306-d5a0-4167-ba5f-fabd51df0e2c	Acceptance pharmacist c94387bd7e37	b134eebd-d327-4718-8bef-92525164178d	PHARMACIST	\N	\N	1822777038	pharmacist-c94387bd7e37@example.test	ACTIVE	2026-10-22 00:40:18.062284+00	2026-10-22 00:40:18.062284+00	266961190204
3d8f509e-fac1-4fcd-bdca-b98dca419491	Nguyễn Minh Quân	6c568d0b-debd-44ae-999d-3942e1ecfe8b	ADMINISTRATIVE	\N	\N	0901000001	review3.admin@example.test	ACTIVE	2026-10-22 00:40:35.478183+00	2026-10-22 00:40:35.478183+00	999260000001
de2d5e81-e2d0-44a1-81d3-0b2c88c642d6	Trần Thu Hà	6c568d0b-debd-44ae-999d-3942e1ecfe8b	MANAGER	\N	\N	0901000002	review3.manager@example.test	ACTIVE	2026-10-22 00:40:35.725718+00	2026-10-22 00:40:35.725718+00	999260000002
e524185a-adb6-48d3-a867-b39b259940f2	Lê Hoàng Phúc	6c568d0b-debd-44ae-999d-3942e1ecfe8b	DOCTOR	Nội tổng quát	R3-BS-2026	0901000003	review3.doctor@example.test	ACTIVE	2026-10-22 00:40:35.958889+00	2026-10-22 00:40:35.958889+00	999260000003
2dbedff2-d7f5-466a-b79c-df68a7daeff1	Phạm Ngọc Mai	6c568d0b-debd-44ae-999d-3942e1ecfe8b	NURSE	\N	\N	0901000004	review3.nurse@example.test	ACTIVE	2026-10-22 00:40:36.206374+00	2026-10-22 00:40:36.206374+00	999260000004
17fca9c9-39d6-49a7-bd89-5fc1c6138bad	Võ Thanh Tâm	6c568d0b-debd-44ae-999d-3942e1ecfe8b	TECHNICIAN	\N	\N	0901000005	review3.lab_tech@example.test	ACTIVE	2026-10-22 00:40:36.434264+00	2026-10-22 00:40:36.434264+00	999260000005
c91f2020-617e-4767-8dec-937f2c30cad8	Đặng Thảo Nguyên	6c568d0b-debd-44ae-999d-3942e1ecfe8b	PHARMACIST	\N	\N	0901000006	review3.pharmacist@example.test	ACTIVE	2026-10-22 00:40:36.661335+00	2026-10-22 00:40:36.661335+00	999260000006
f01f79f0-ade2-4f95-b093-94f8c22041ce	Bùi Khánh Linh	6c568d0b-debd-44ae-999d-3942e1ecfe8b	CASHIER	\N	\N	0901000007	review3.cashier@example.test	ACTIVE	2026-10-22 00:40:36.885304+00	2026-10-22 00:40:36.885304+00	999260000007
f1a0b52a-f493-404f-82b6-b115d8a9fb18	Nguyễn Quản Trị Demo	827f8b9c-3d39-48b2-8480-f655a88b4617	ADMINISTRATIVE	\N	\N	0908800001	demo.staff01@example.test	ACTIVE	2026-10-22 10:20:51.519333+00	2026-10-22 10:20:51.519333+00	999880000001
df1e1e38-e5c2-4578-b0b5-57806a2ee930	Trần Quản Lý Demo	827f8b9c-3d39-48b2-8480-f655a88b4617	MANAGER	\N	\N	0908800002	demo.staff02@example.test	ACTIVE	2026-10-22 10:20:51.592049+00	2026-10-22 10:20:51.592049+00	999880000002
be9cf102-f45a-424d-83af-b9e54d03ac06	Lê Bác Sĩ Demo	a6817abd-6699-4c96-a138-afd87a2479b4	DOCTOR	Khoa Nội tổng quát	LOCAL-DEMO-DOCTOR-003	0908800003	demo.staff03@example.test	ACTIVE	2026-10-22 10:20:51.659226+00	2026-10-22 10:20:51.659226+00	999880000003
50be6184-116c-46e9-89a1-241b1d1e12cf	Phạm Điều Dưỡng Demo	a6817abd-6699-4c96-a138-afd87a2479b4	NURSE	\N	\N	0908800004	demo.staff04@example.test	ACTIVE	2026-10-22 10:20:51.732428+00	2026-10-22 10:20:51.732428+00	999880000004
a35bf152-732d-4219-a5b0-f794b3ed14ed	Nguyễn Minh Phúc Demo	8c413c97-1a7d-4244-b176-c0379a3a1325	DOCTOR	Khoa Nhi	LOCAL-DEMO-DOCTOR-005	0908800005	demo.staff05@example.test	ACTIVE	2026-10-22 10:20:51.813815+00	2026-10-22 10:20:51.813815+00	999880000005
b0de281a-6901-4a5b-8e0a-a2dc7a3a6511	Trần Ngọc Lan Demo	8c413c97-1a7d-4244-b176-c0379a3a1325	NURSE	\N	\N	0908800006	demo.staff06@example.test	ACTIVE	2026-10-22 10:20:51.872859+00	2026-10-22 10:20:51.872859+00	999880000006
4fd41f3b-d1ac-4090-9f6a-566824649bc6	Lê Hoàng Nam Demo	b1787ead-9678-422f-a19b-e6c8cfaa144c	DOCTOR	Khoa Tim mạch	LOCAL-DEMO-DOCTOR-007	0908800007	demo.staff07@example.test	ACTIVE	2026-10-22 10:20:51.955069+00	2026-10-22 10:20:51.955069+00	999880000007
4fe03e80-5a26-4a00-a8d2-f528052a1ece	Phạm Thanh Hà Demo	b1787ead-9678-422f-a19b-e6c8cfaa144c	NURSE	\N	\N	0908800008	demo.staff08@example.test	ACTIVE	2026-10-22 10:20:52.058994+00	2026-10-22 10:20:52.058994+00	999880000008
676dc13f-1e6c-49f8-a004-c341b5619cbe	Võ Quốc Bảo Demo	34dfa9f0-38d2-4972-97e4-9d945d5ca709	DOCTOR	Khoa Tai Mũi Họng	LOCAL-DEMO-DOCTOR-009	0908800009	demo.staff09@example.test	ACTIVE	2026-10-22 10:20:52.116496+00	2026-10-22 10:20:52.116496+00	999880000009
f1875dcc-9a11-4c76-b05c-a004ce37ce23	Đặng Thảo Nguyên Demo	34dfa9f0-38d2-4972-97e4-9d945d5ca709	NURSE	\N	\N	0908800010	demo.staff10@example.test	ACTIVE	2026-10-22 10:20:52.180887+00	2026-10-22 10:20:52.180887+00	999880000010
341b1aee-0447-40f7-b0c3-8ecea6cb3ee6	Bùi Minh Châu Demo	bcac2ed6-3e85-4e67-9330-61ae88a4d445	DOCTOR	Khoa Da liễu	LOCAL-DEMO-DOCTOR-011	0908800011	demo.staff11@example.test	ACTIVE	2026-10-22 10:20:52.233151+00	2026-10-22 10:20:52.233151+00	999880000011
2de98c18-7ae3-432d-9eaf-3fd03afdc398	Đỗ Ngọc Anh Demo	bcac2ed6-3e85-4e67-9330-61ae88a4d445	NURSE	\N	\N	0908800012	demo.staff12@example.test	ACTIVE	2026-10-22 10:20:52.279141+00	2026-10-22 10:20:52.279141+00	999880000012
5da789cf-2cba-4e84-9912-a55deb69b212	Hồ Anh Tuấn Demo	d9623dcd-954c-4f4b-a415-87de1af4ed48	DOCTOR	Khoa Mắt	LOCAL-DEMO-DOCTOR-013	0908800013	demo.staff13@example.test	ACTIVE	2026-10-22 10:20:52.317877+00	2026-10-22 10:20:52.317877+00	999880000013
94c80de8-d0dd-4bf5-a977-27d79725f57b	Ngô Thu Hương Demo	d9623dcd-954c-4f4b-a415-87de1af4ed48	NURSE	\N	\N	0908800014	demo.staff14@example.test	ACTIVE	2026-10-22 10:20:52.356018+00	2026-10-22 10:20:52.356018+00	999880000014
d5dd2dce-a53c-47b5-bd4b-a73b5646809b	Dương Thanh Tâm Demo	cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2	TECHNICIAN	\N	\N	0908800015	demo.staff15@example.test	ACTIVE	2026-10-22 10:20:52.405367+00	2026-10-22 10:20:52.405367+00	999880000015
fa57c45b-a67e-4c9c-bcf5-e703850ffa92	Vũ Ngọc Hà Demo	cd9a5bb6-f0a6-4d53-aa63-f27fbc0ac7b2	TECHNICIAN	\N	\N	0908800016	demo.staff16@example.test	ACTIVE	2026-10-22 10:20:52.461264+00	2026-10-22 10:20:52.461264+00	999880000016
cc08095b-2aa3-47b5-b189-8dbffbc8f3ac	Mai Thảo Vy Demo	4a717d96-b33f-48b2-9a55-071d5742e8e5	PHARMACIST	\N	\N	0908800017	demo.staff17@example.test	ACTIVE	2026-10-22 10:20:52.562372+00	2026-10-22 10:20:52.562372+00	999880000017
4015e7ae-7336-4cec-a2c4-a34ddddc0ad2	Đinh Gia Huy Demo	4a717d96-b33f-48b2-9a55-071d5742e8e5	PHARMACIST	\N	\N	0908800018	demo.staff18@example.test	ACTIVE	2026-10-22 10:20:52.649129+00	2026-10-22 10:20:52.649129+00	999880000018
0354d08b-2e07-466a-91d8-4a97495fe37e	Phan Khánh Linh Demo	5caaab98-3972-4c59-9abf-e1dfc8f0d036	CASHIER	\N	\N	0908800019	demo.staff19@example.test	ACTIVE	2026-10-22 10:20:52.706603+00	2026-10-22 10:20:52.706603+00	999880000019
bfe20ea2-5ff7-475c-89a5-2038756e6e40	Lý Minh Quân Demo	5caaab98-3972-4c59-9abf-e1dfc8f0d036	ADMINISTRATIVE	\N	\N	0908800020	demo.staff20@example.test	ACTIVE	2026-10-22 10:20:52.866355+00	2026-10-22 10:20:52.866355+00	999880000020
\.


--
-- Data for Name: staff_creation_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.staff_creation_receipt (actor_account_id, idempotency_key, request_fingerprint, staff_id, created_at) FROM stdin;
\.


--
-- Data for Name: surgery_authority_outbox; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgery_authority_outbox (event_id, routing_key, payload, occurred_at, published_at) FROM stdin;
\.


--
-- Data for Name: surgical_capability; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.surgical_capability (staff_id, team_role, department_id, is_active, valid_from, valid_until, revision, updated_at) FROM stdin;
\.


--
-- Name: account account_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (account_id);


--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT department_pkey PRIMARY KEY (department_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: local_demo_seed_receipt local_demo_seed_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.local_demo_seed_receipt
    ADD CONSTRAINT local_demo_seed_receipt_pkey PRIMARY KEY (seed_key);


--
-- Name: operating_room operating_room_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operating_room
    ADD CONSTRAINT operating_room_pkey PRIMARY KEY (room_id);


--
-- Name: operating_room operating_room_room_code_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operating_room
    ADD CONSTRAINT operating_room_room_code_key UNIQUE (room_code);


--
-- Name: organization_event_outbox organization_event_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.organization_event_outbox
    ADD CONSTRAINT organization_event_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: account_creation_receipt pk_account_creation_receipt; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.account_creation_receipt
    ADD CONSTRAINT pk_account_creation_receipt PRIMARY KEY (actor_account_id, idempotency_key);


--
-- Name: staff_creation_receipt pk_staff_creation_receipt; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.staff_creation_receipt
    ADD CONSTRAINT pk_staff_creation_receipt PRIMARY KEY (actor_account_id, idempotency_key);


--
-- Name: room room_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.room
    ADD CONSTRAINT room_pkey PRIMARY KEY (room_id);


--
-- Name: staff staff_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT staff_pkey PRIMARY KEY (staff_id);


--
-- Name: surgery_authority_outbox surgery_authority_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgery_authority_outbox
    ADD CONSTRAINT surgery_authority_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: surgical_capability surgical_capability_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgical_capability
    ADD CONSTRAINT surgical_capability_pkey PRIMARY KEY (staff_id, team_role);


--
-- Name: account uk_account_username; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT uk_account_username UNIQUE (username);


--
-- Name: department uk_department_abbreviation; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT uk_department_abbreviation UNIQUE (abbreviation);


--
-- Name: staff uk_staff_identity_number; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT uk_staff_identity_number UNIQUE (identity_number);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_operating_room_department; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_operating_room_department ON public.operating_room USING btree (department_id);


--
-- Name: idx_organization_event_outbox_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_organization_event_outbox_pending ON public.organization_event_outbox USING btree (next_attempt_at, occurred_at, event_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_room_department_id; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_room_department_id ON public.room USING btree (department_id);


--
-- Name: idx_staff_department_id; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_staff_department_id ON public.staff USING btree (department_id);


--
-- Name: idx_staff_job_title; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_staff_job_title ON public.staff USING btree (job_title);


--
-- Name: idx_surgery_authority_outbox_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_surgery_authority_outbox_pending ON public.surgery_authority_outbox USING btree (occurred_at) WHERE (published_at IS NULL);


--
-- Name: account_creation_receipt fk_account_creation_receipt_account; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.account_creation_receipt
    ADD CONSTRAINT fk_account_creation_receipt_account FOREIGN KEY (account_id) REFERENCES public.account(account_id);


--
-- Name: account fk_account_staff; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT fk_account_staff FOREIGN KEY (staff_id) REFERENCES public.staff(staff_id);


--
-- Name: department fk_department_head; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT fk_department_head FOREIGN KEY (department_head_id) REFERENCES public.staff(staff_id);


--
-- Name: room fk_room_department; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.room
    ADD CONSTRAINT fk_room_department FOREIGN KEY (department_id) REFERENCES public.department(department_id);


--
-- Name: staff_creation_receipt fk_staff_creation_receipt_staff; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.staff_creation_receipt
    ADD CONSTRAINT fk_staff_creation_receipt_staff FOREIGN KEY (staff_id) REFERENCES public.staff(staff_id);


--
-- Name: staff fk_staff_department; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT fk_staff_department FOREIGN KEY (department_id) REFERENCES public.department(department_id);


--
-- Name: operating_room operating_room_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operating_room
    ADD CONSTRAINT operating_room_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department(department_id);


--
-- Name: surgical_capability surgical_capability_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgical_capability
    ADD CONSTRAINT surgical_capability_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department(department_id);


--
-- Name: surgical_capability surgical_capability_staff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.surgical_capability
    ADD CONSTRAINT surgical_capability_staff_id_fkey FOREIGN KEY (staff_id) REFERENCES public.staff(staff_id);


--
-- PostgreSQL database dump complete
--

\unrestrict wDWZeq611C1RRNyLkDHbJSh4KwCtc4r38lNS5rWX67Jm4fP8mGfrq03g5QaFaj2

--
-- Database "mediflow_patient" dump
--

--
-- PostgreSQL database dump
--

\restrict uXGVG2fXDcXxsvsi6ddYuGKvzaFnCh7M38MLrS0dZhPUfPnPao8rBNG3s4gJRer

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_patient; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_patient WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_patient OWNER TO test;

\unrestrict uXGVG2fXDcXxsvsi6ddYuGKvzaFnCh7M38MLrS0dZhPUfPnPao8rBNG3s4gJRer
\connect mediflow_patient
\restrict uXGVG2fXDcXxsvsi6ddYuGKvzaFnCh7M38MLrS0dZhPUfPnPao8rBNG3s4gJRer

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: local_demo_seed_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.local_demo_seed_receipt (
    seed_key character varying(100) NOT NULL,
    entity_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.local_demo_seed_receipt OWNER TO test;

--
-- Name: patient; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.patient (
    patient_id uuid NOT NULL,
    full_name character varying(100) NOT NULL,
    date_of_birth date NOT NULL,
    gender character varying(1) NOT NULL,
    identity_number character varying(20) NOT NULL,
    address character varying(255),
    phone_number character varying(15),
    email character varying(100),
    health_insurance_number character varying(20),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_patient_gender CHECK (((gender)::text = ANY (ARRAY[('M'::character varying)::text, ('F'::character varying)::text])))
);


ALTER TABLE public.patient OWNER TO test;

--
-- Name: patient_event_outbox; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.patient_event_outbox (
    event_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    correlation_id character varying(255) NOT NULL,
    routing_key character varying(100) NOT NULL,
    payload text NOT NULL,
    status character varying(20) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(100),
    CONSTRAINT patient_event_outbox_attempts_check CHECK ((attempts >= 0)),
    CONSTRAINT patient_event_outbox_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PUBLISHED'::character varying)::text, ('QUARANTINED'::character varying)::text])))
);


ALTER TABLE public.patient_event_outbox OWNER TO test;

--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init patient	SQL	V1__init_patient.sql	-274030574	test	2026-10-22 07:39:01.238067	13	t
2	2	patient event outbox	SQL	V2__patient_event_outbox.sql	-1509723816	test	2026-10-22 07:39:01.264043	9	t
3	3	local demo seed receipts	SQL	V3__local_demo_seed_receipts.sql	369535096	test	2026-10-22 17:20:24.442229	164	t
\.


--
-- Data for Name: local_demo_seed_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.local_demo_seed_receipt (seed_key, entity_id, created_at) FROM stdin;
patient-01	2abcf504-cf70-4933-9eff-5e5b7dfe5d05	2026-10-22 10:20:52.957656+00
patient-02	fac978a8-7abc-470f-830d-9823a8381b70	2026-10-22 10:20:52.957656+00
patient-03	562d84f4-e553-4a17-9d86-b3d900c76a6f	2026-10-22 10:20:52.957656+00
patient-04	7c2da651-e59b-4310-b7e8-1ac4c4bc1be3	2026-10-22 10:20:52.957656+00
patient-05	8f8cce22-0ffd-4d79-b55b-90268fc25dd8	2026-10-22 10:20:52.957656+00
patient-06	64bb0468-b92e-4c23-86d7-ee9c5a521b9b	2026-10-22 10:20:52.957656+00
patient-07	c2fc4b28-8ced-4bef-aa67-58fdf38dbcb9	2026-10-22 10:20:52.957656+00
patient-08	78d9f918-aa47-41e8-9062-27bfcbd8e1c0	2026-10-22 10:20:52.957656+00
patient-09	840486c7-cfe5-44a2-a890-7847425816f0	2026-10-22 10:20:52.957656+00
patient-10	2cef1e5a-36f0-41ee-a965-28303af23caa	2026-10-22 10:20:52.957656+00
\.


--
-- Data for Name: patient; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.patient (patient_id, full_name, date_of_birth, gender, identity_number, address, phone_number, email, health_insurance_number, created_at, updated_at) FROM stdin;
c36277c4-347f-480d-8473-bcf9f98c8a44	Acceptance Patient c94387bd7e37	1988-01-02	M	470269093021	Acceptance address	9323974149	patient-c94387bd7e37@example.test	\N	2026-10-22 00:40:14.692968+00	2026-10-22 00:40:14.692968+00
30820325-711c-4d7b-b364-be73c78a43b0	Nguyễn Văn An	1982-04-12	M	999261000000	12 Đường Hoa Mai, TP. Hồ Chí Minh	0902000000	review3.patient0@example.test	\N	2026-10-22 00:40:37.106054+00	2026-10-22 00:40:37.106054+00
8dd25d75-6d29-4967-ac6e-d54c6a8dd490	Trần Thị Hương	1983-04-12	F	999261000001	13 Đường Hoa Mai, TP. Hồ Chí Minh	0902000001	review3.patient1@example.test	\N	2026-10-22 00:40:37.180132+00	2026-10-22 00:40:37.180132+00
8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	Lê Minh Khang	1984-04-12	M	999261000002	14 Đường Hoa Mai, TP. Hồ Chí Minh	0902000002	review3.patient2@example.test	\N	2026-10-22 00:40:37.259883+00	2026-10-22 00:40:37.259883+00
6f75b635-a491-4191-99b3-1df16c1a3832	Phạm Ngọc Anh	1985-04-12	F	999261000003	15 Đường Hoa Mai, TP. Hồ Chí Minh	0902000003	review3.patient3@example.test	\N	2026-10-22 00:40:37.371912+00	2026-10-22 00:40:37.371912+00
1270368c-7e70-40eb-852c-b58d7b052cf7	Võ Quốc Bảo	1986-04-12	M	999261000004	16 Đường Hoa Mai, TP. Hồ Chí Minh	0902000004	review3.patient4@example.test	\N	2026-10-22 00:40:37.439855+00	2026-10-22 00:40:37.439855+00
83ee7cd5-ccb4-4005-96f3-42abb0261400	Đặng Thanh Trúc	1987-04-12	F	999261000005	17 Đường Hoa Mai, TP. Hồ Chí Minh	0902000005	review3.patient5@example.test	\N	2026-10-22 00:40:37.551639+00	2026-10-22 00:40:37.551639+00
3ad6444d-54b2-467a-9e25-beb664d3975f	Bùi Gia Huy	1988-04-12	M	999261000006	18 Đường Hoa Mai, TP. Hồ Chí Minh	0902000006	review3.patient6@example.test	\N	2026-10-22 00:40:37.629402+00	2026-10-22 00:40:37.629402+00
fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	Đỗ Thị Lan	1989-04-12	F	999261000007	19 Đường Hoa Mai, TP. Hồ Chí Minh	0902000007	review3.patient7@example.test	\N	2026-10-22 00:40:37.721296+00	2026-10-22 00:40:37.721296+00
a2c54b05-3010-4b8d-93ee-67fb06469d77	Hồ Minh Đức	1990-04-12	M	999261000008	20 Đường Hoa Mai, TP. Hồ Chí Minh	0902000008	review3.patient8@example.test	\N	2026-10-22 00:40:37.812246+00	2026-10-22 00:40:37.812246+00
b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8	Ngô Thảo Vy	1991-04-12	F	999261000009	21 Đường Hoa Mai, TP. Hồ Chí Minh	0902000009	review3.patient9@example.test	\N	2026-10-22 00:40:37.87908+00	2026-10-22 00:40:37.87908+00
87674766-119a-46ed-b4db-2059be4cd59c	Dương Tuấn Kiệt	1992-04-12	M	999261000010	22 Đường Hoa Mai, TP. Hồ Chí Minh	0902000010	review3.patient10@example.test	\N	2026-10-22 00:40:37.995813+00	2026-10-22 00:40:37.995813+00
579e6d5b-1f63-43ce-bd2a-609fdf508481	Vũ Ngọc Hà	1993-04-12	F	999261000011	23 Đường Hoa Mai, TP. Hồ Chí Minh	0902000011	review3.patient11@example.test	\N	2026-10-22 00:40:38.068366+00	2026-10-22 00:40:38.068366+00
2abcf504-cf70-4933-9eff-5e5b7dfe5d05	Nguyễn Văn An Demo	1986-04-12	M	999881000001	11 Đường Demo, TP. Hồ Chí Minh	0908810001	demo.patient01@example.test	\N	2026-10-22 10:20:53.106301+00	2026-10-22 10:20:53.106301+00
fac978a8-7abc-470f-830d-9823a8381b70	Trần Thị Bình Demo	1987-04-12	F	999881000002	12 Đường Demo, TP. Hồ Chí Minh	0908810002	demo.patient02@example.test	\N	2026-10-22 10:20:53.183252+00	2026-10-22 10:20:53.183252+00
562d84f4-e553-4a17-9d86-b3d900c76a6f	Lê Minh Châu Demo	1988-04-12	M	999881000003	13 Đường Demo, TP. Hồ Chí Minh	0908810003	demo.patient03@example.test	\N	2026-10-22 10:20:53.242107+00	2026-10-22 10:20:53.242107+00
7c2da651-e59b-4310-b7e8-1ac4c4bc1be3	Phạm Ngọc Dung Demo	1989-04-12	F	999881000004	14 Đường Demo, TP. Hồ Chí Minh	0908810004	demo.patient04@example.test	\N	2026-10-22 10:20:53.309398+00	2026-10-22 10:20:53.309398+00
8f8cce22-0ffd-4d79-b55b-90268fc25dd8	Võ Quốc Huy Demo	1990-04-12	M	999881000005	15 Đường Demo, TP. Hồ Chí Minh	0908810005	demo.patient05@example.test	\N	2026-10-22 10:20:53.399044+00	2026-10-22 10:20:53.399044+00
64bb0468-b92e-4c23-86d7-ee9c5a521b9b	Đặng Thanh Mai Demo	1991-04-12	F	999881000006	16 Đường Demo, TP. Hồ Chí Minh	0908810006	demo.patient06@example.test	\N	2026-10-22 10:20:53.475989+00	2026-10-22 10:20:53.475989+00
c2fc4b28-8ced-4bef-aa67-58fdf38dbcb9	Bùi Gia Nam Demo	1992-04-12	M	999881000007	17 Đường Demo, TP. Hồ Chí Minh	0908810007	demo.patient07@example.test	\N	2026-10-22 10:20:53.548213+00	2026-10-22 10:20:53.548213+00
78d9f918-aa47-41e8-9062-27bfcbd8e1c0	Đỗ Thị Phương Demo	1993-04-12	F	999881000008	18 Đường Demo, TP. Hồ Chí Minh	0908810008	demo.patient08@example.test	\N	2026-10-22 10:20:53.639369+00	2026-10-22 10:20:53.639369+00
840486c7-cfe5-44a2-a890-7847425816f0	Hồ Minh Quân Demo	1994-04-12	M	999881000009	19 Đường Demo, TP. Hồ Chí Minh	0908810009	demo.patient09@example.test	\N	2026-10-22 10:20:53.681242+00	2026-10-22 10:20:53.681242+00
2cef1e5a-36f0-41ee-a965-28303af23caa	Ngô Thảo Vy Demo	1995-04-12	F	999881000010	20 Đường Demo, TP. Hồ Chí Minh	0908810010	demo.patient10@example.test	\N	2026-10-22 10:20:53.768556+00	2026-10-22 10:20:53.768556+00
\.


--
-- Data for Name: patient_event_outbox; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.patient_event_outbox (event_id, occurred_at, correlation_id, routing_key, payload, status, attempts, next_attempt_at, published_at, last_error) FROM stdin;
a85e1afc-f364-4b5b-9cfb-6c972e28c401	2026-10-22 00:40:14.692968+00	0b927dda-dea7-4a27-9501-6c7f425b45d8	patient.created	{"eventId":"a85e1afc-f364-4b5b-9cfb-6c972e28c401","occurredAt":1792629614.692968200,"correlationId":"0b927dda-dea7-4a27-9501-6c7f425b45d8","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44","hoTen":"Acceptance Patient c94387bd7e37","email":"patient-c94387bd7e37@example.test","sdt":"9323974149"}	PUBLISHED	0	2026-10-22 00:40:14.699818+00	2026-10-22 00:40:15.462191+00	\N
7e18530a-ff9f-4afe-a248-b5baf3bacc4e	2026-10-22 00:40:37.106054+00	6a3499e2-7fb0-4835-bf8a-e42acb497353	patient.created	{"eventId":"7e18530a-ff9f-4afe-a248-b5baf3bacc4e","occurredAt":1792629637.106053500,"correlationId":"6a3499e2-7fb0-4835-bf8a-e42acb497353","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","hoTen":"Nguyễn Văn An","email":"review3.patient0@example.test","sdt":"0902000000"}	PUBLISHED	0	2026-10-22 00:40:37.108671+00	2026-10-22 00:40:41.758665+00	\N
fcae1276-c0ba-44c5-8837-799b59167e60	2026-10-22 00:40:37.180132+00	f6f1f518-e6f5-4a46-b178-a3ac4fd9f851	patient.created	{"eventId":"fcae1276-c0ba-44c5-8837-799b59167e60","occurredAt":1792629637.180132100,"correlationId":"f6f1f518-e6f5-4a46-b178-a3ac4fd9f851","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","hoTen":"Trần Thị Hương","email":"review3.patient1@example.test","sdt":"0902000001"}	PUBLISHED	0	2026-10-22 00:40:37.182235+00	2026-10-22 00:40:41.772431+00	\N
ebd79769-2b7c-4d5d-a97b-b8970ac9fb63	2026-10-22 00:40:37.259883+00	5be47b6e-29d9-426d-b67d-4e011f6975b6	patient.created	{"eventId":"ebd79769-2b7c-4d5d-a97b-b8970ac9fb63","occurredAt":1792629637.259883000,"correlationId":"5be47b6e-29d9-426d-b67d-4e011f6975b6","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","hoTen":"Lê Minh Khang","email":"review3.patient2@example.test","sdt":"0902000002"}	PUBLISHED	0	2026-10-22 00:40:37.262019+00	2026-10-22 00:40:41.789884+00	\N
37ff17c4-2c7d-4e5f-b5af-7971b02a61aa	2026-10-22 00:40:37.371912+00	5c1ff41e-1eb3-4028-9b50-4bfbaac7aca5	patient.created	{"eventId":"37ff17c4-2c7d-4e5f-b5af-7971b02a61aa","occurredAt":1792629637.371911800,"correlationId":"5c1ff41e-1eb3-4028-9b50-4bfbaac7aca5","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832","hoTen":"Phạm Ngọc Anh","email":"review3.patient3@example.test","sdt":"0902000003"}	PUBLISHED	0	2026-10-22 00:40:37.375092+00	2026-10-22 00:40:41.803239+00	\N
5ede6981-e220-4e3c-a11f-772345b419ff	2026-10-22 00:40:37.439855+00	2f07db0a-5b51-466b-adf5-c08177087fe9	patient.created	{"eventId":"5ede6981-e220-4e3c-a11f-772345b419ff","occurredAt":1792629637.439854600,"correlationId":"2f07db0a-5b51-466b-adf5-c08177087fe9","patientId":"1270368c-7e70-40eb-852c-b58d7b052cf7","hoTen":"Võ Quốc Bảo","email":"review3.patient4@example.test","sdt":"0902000004"}	PUBLISHED	0	2026-10-22 00:40:37.442469+00	2026-10-22 00:40:41.816909+00	\N
61e101be-4207-40db-988f-8e83a22fde98	2026-10-22 00:40:37.551639+00	d0e5494d-e96a-4fbf-aeb3-7920f96c3d74	patient.created	{"eventId":"61e101be-4207-40db-988f-8e83a22fde98","occurredAt":1792629637.551638800,"correlationId":"d0e5494d-e96a-4fbf-aeb3-7920f96c3d74","patientId":"83ee7cd5-ccb4-4005-96f3-42abb0261400","hoTen":"Đặng Thanh Trúc","email":"review3.patient5@example.test","sdt":"0902000005"}	PUBLISHED	0	2026-10-22 00:40:37.553753+00	2026-10-22 00:40:41.829307+00	\N
e373ea39-3ad4-4b63-8d06-25c689d69714	2026-10-22 00:40:37.629402+00	edd150f4-fb16-4e0e-836f-482593343ff7	patient.created	{"eventId":"e373ea39-3ad4-4b63-8d06-25c689d69714","occurredAt":1792629637.629401800,"correlationId":"edd150f4-fb16-4e0e-836f-482593343ff7","patientId":"3ad6444d-54b2-467a-9e25-beb664d3975f","hoTen":"Bùi Gia Huy","email":"review3.patient6@example.test","sdt":"0902000006"}	PUBLISHED	0	2026-10-22 00:40:37.632436+00	2026-10-22 00:40:41.846542+00	\N
af54a951-d3bc-4258-9327-83f614b5ec8c	2026-10-22 00:40:37.721296+00	63ccc96b-668c-4c57-aa22-3532469c9649	patient.created	{"eventId":"af54a951-d3bc-4258-9327-83f614b5ec8c","occurredAt":1792629637.721296300,"correlationId":"63ccc96b-668c-4c57-aa22-3532469c9649","patientId":"fc4fe9c4-a34c-4252-bd0b-3e171604d3b5","hoTen":"Đỗ Thị Lan","email":"review3.patient7@example.test","sdt":"0902000007"}	PUBLISHED	0	2026-10-22 00:40:37.724036+00	2026-10-22 00:40:41.859133+00	\N
5e08dc4c-53dc-4f99-956a-2403720ac665	2026-10-22 00:40:37.812246+00	c1e470fe-0341-473d-840a-84609aa59de9	patient.created	{"eventId":"5e08dc4c-53dc-4f99-956a-2403720ac665","occurredAt":1792629637.812246300,"correlationId":"c1e470fe-0341-473d-840a-84609aa59de9","patientId":"a2c54b05-3010-4b8d-93ee-67fb06469d77","hoTen":"Hồ Minh Đức","email":"review3.patient8@example.test","sdt":"0902000008"}	PUBLISHED	0	2026-10-22 00:40:37.81487+00	2026-10-22 00:40:41.873282+00	\N
a71e9022-74e3-4566-8501-4c1d70edfc3d	2026-10-22 00:40:37.87908+00	8774aa4d-a1c0-4b44-add5-9d51a3bd6cbe	patient.created	{"eventId":"a71e9022-74e3-4566-8501-4c1d70edfc3d","occurredAt":1792629637.879079600,"correlationId":"8774aa4d-a1c0-4b44-add5-9d51a3bd6cbe","patientId":"b3c2b6c1-a8be-4b8c-83b6-ccecec80d8b8","hoTen":"Ngô Thảo Vy","email":"review3.patient9@example.test","sdt":"0902000009"}	PUBLISHED	0	2026-10-22 00:40:37.881733+00	2026-10-22 00:40:41.888269+00	\N
515bf424-c60e-4542-be90-93de980de3ea	2026-10-22 00:40:37.995813+00	d4d4f83c-bca1-4072-af8e-0d039306f23f	patient.created	{"eventId":"515bf424-c60e-4542-be90-93de980de3ea","occurredAt":1792629637.995813300,"correlationId":"d4d4f83c-bca1-4072-af8e-0d039306f23f","patientId":"87674766-119a-46ed-b4db-2059be4cd59c","hoTen":"Dương Tuấn Kiệt","email":"review3.patient10@example.test","sdt":"0902000010"}	PUBLISHED	0	2026-10-22 00:40:37.998428+00	2026-10-22 00:40:41.901887+00	\N
fd50e2a9-799c-4367-bc43-da221286c3a6	2026-10-22 00:40:38.068366+00	ab155c96-6916-4fe5-9c74-c82a91401501	patient.created	{"eventId":"fd50e2a9-799c-4367-bc43-da221286c3a6","occurredAt":1792629638.068365800,"correlationId":"ab155c96-6916-4fe5-9c74-c82a91401501","patientId":"579e6d5b-1f63-43ce-bd2a-609fdf508481","hoTen":"Vũ Ngọc Hà","email":"review3.patient11@example.test","sdt":"0902000011"}	PUBLISHED	0	2026-10-22 00:40:38.070457+00	2026-10-22 00:40:41.918817+00	\N
6dd96a9d-4d43-4522-ae1c-29095240e9cc	2026-10-22 10:20:53.183252+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"6dd96a9d-4d43-4522-ae1c-29095240e9cc","occurredAt":1792664453.183251500,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"fac978a8-7abc-470f-830d-9823a8381b70","hoTen":"Trần Thị Bình Demo","email":"demo.patient02@example.test","sdt":"0908810002"}	PUBLISHED	0	2026-10-22 10:20:53.194775+00	2026-10-22 10:20:55.773192+00	\N
a4c64e94-7c4b-4bef-a1ac-85b418e3849f	2026-10-22 10:20:53.309398+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"a4c64e94-7c4b-4bef-a1ac-85b418e3849f","occurredAt":1792664453.309397800,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"7c2da651-e59b-4310-b7e8-1ac4c4bc1be3","hoTen":"Phạm Ngọc Dung Demo","email":"demo.patient04@example.test","sdt":"0908810004"}	PUBLISHED	0	2026-10-22 10:20:53.336568+00	2026-10-22 10:20:55.939574+00	\N
bae7796b-8fee-4d0a-a8e3-ae07a149d714	2026-10-22 10:20:53.399044+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"bae7796b-8fee-4d0a-a8e3-ae07a149d714","occurredAt":1792664453.399043500,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"8f8cce22-0ffd-4d79-b55b-90268fc25dd8","hoTen":"Võ Quốc Huy Demo","email":"demo.patient05@example.test","sdt":"0908810005"}	PUBLISHED	0	2026-10-22 10:20:53.41236+00	2026-10-22 10:20:56.025767+00	\N
4a943c3a-525a-478c-abe4-2cdf685ae52c	2026-10-22 10:20:53.475989+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"4a943c3a-525a-478c-abe4-2cdf685ae52c","occurredAt":1792664453.475989300,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"64bb0468-b92e-4c23-86d7-ee9c5a521b9b","hoTen":"Đặng Thanh Mai Demo","email":"demo.patient06@example.test","sdt":"0908810006"}	PUBLISHED	0	2026-10-22 10:20:53.485897+00	2026-10-22 10:20:56.10215+00	\N
d6572043-4b0b-42bb-b697-2b22281dfd34	2026-10-22 10:20:53.106301+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"d6572043-4b0b-42bb-b697-2b22281dfd34","occurredAt":1792664453.106300500,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"2abcf504-cf70-4933-9eff-5e5b7dfe5d05","hoTen":"Nguyễn Văn An Demo","email":"demo.patient01@example.test","sdt":"0908810001"}	PUBLISHED	0	2026-10-22 10:20:53.128512+00	2026-10-22 10:20:55.67416+00	\N
7509810a-23a8-43cf-8838-cf9cb739ef54	2026-10-22 10:20:53.242107+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"7509810a-23a8-43cf-8838-cf9cb739ef54","occurredAt":1792664453.242106800,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"562d84f4-e553-4a17-9d86-b3d900c76a6f","hoTen":"Lê Minh Châu Demo","email":"demo.patient03@example.test","sdt":"0908810003"}	PUBLISHED	0	2026-10-22 10:20:53.250495+00	2026-10-22 10:20:55.877557+00	\N
b3e59db4-95cf-4ccc-9fdb-9e19b9976d50	2026-10-22 10:20:53.548213+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"b3e59db4-95cf-4ccc-9fdb-9e19b9976d50","occurredAt":1792664453.548212600,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"c2fc4b28-8ced-4bef-aa67-58fdf38dbcb9","hoTen":"Bùi Gia Nam Demo","email":"demo.patient07@example.test","sdt":"0908810007"}	PUBLISHED	0	2026-10-22 10:20:53.569383+00	2026-10-22 10:20:56.158878+00	\N
cb69b4ac-e723-473f-8ef4-db7df984249a	2026-10-22 10:20:53.639369+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"cb69b4ac-e723-473f-8ef4-db7df984249a","occurredAt":1792664453.639368900,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"78d9f918-aa47-41e8-9062-27bfcbd8e1c0","hoTen":"Đỗ Thị Phương Demo","email":"demo.patient08@example.test","sdt":"0908810008"}	PUBLISHED	0	2026-10-22 10:20:53.647864+00	2026-10-22 10:20:56.234193+00	\N
b9e0321f-9f28-4c22-ab75-2960fd70641e	2026-10-22 10:20:53.681242+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"b9e0321f-9f28-4c22-ab75-2960fd70641e","occurredAt":1792664453.681241900,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"840486c7-cfe5-44a2-a890-7847425816f0","hoTen":"Hồ Minh Quân Demo","email":"demo.patient09@example.test","sdt":"0908810009"}	PUBLISHED	0	2026-10-22 10:20:53.6905+00	2026-10-22 10:20:56.389282+00	\N
4e1fadd8-161d-4e9a-928a-7783657ff728	2026-10-22 10:20:53.768556+00	56c16b83-82bd-4bfe-95fc-f23cec6863f4	patient.created	{"eventId":"4e1fadd8-161d-4e9a-928a-7783657ff728","occurredAt":1792664453.768555800,"correlationId":"56c16b83-82bd-4bfe-95fc-f23cec6863f4","patientId":"2cef1e5a-36f0-41ee-a965-28303af23caa","hoTen":"Ngô Thảo Vy Demo","email":"demo.patient10@example.test","sdt":"0908810010"}	PUBLISHED	0	2026-10-22 10:20:53.776724+00	2026-10-22 10:20:56.558189+00	\N
\.


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: local_demo_seed_receipt local_demo_seed_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.local_demo_seed_receipt
    ADD CONSTRAINT local_demo_seed_receipt_pkey PRIMARY KEY (seed_key);


--
-- Name: patient_event_outbox patient_event_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.patient_event_outbox
    ADD CONSTRAINT patient_event_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: patient patient_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT patient_pkey PRIMARY KEY (patient_id);


--
-- Name: patient uq_patient_identity_number; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT uq_patient_identity_number UNIQUE (identity_number);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_patient_event_outbox_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_patient_event_outbox_pending ON public.patient_event_outbox USING btree (next_attempt_at, occurred_at, event_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_patient_full_name; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_patient_full_name ON public.patient USING btree (full_name);


--
-- PostgreSQL database dump complete
--

\unrestrict uXGVG2fXDcXxsvsi6ddYuGKvzaFnCh7M38MLrS0dZhPUfPnPao8rBNG3s4gJRer

--
-- Database "mediflow_pharmacy" dump
--

--
-- PostgreSQL database dump
--

\restrict vNIlYtp0xeR3XcKVONOCFsFCTb35E8I6Vnx8DuHChLWklMjearcTwhUN8vmkW38

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_pharmacy; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_pharmacy WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_pharmacy OWNER TO test;

\unrestrict vNIlYtp0xeR3XcKVONOCFsFCTb35E8I6Vnx8DuHChLWklMjearcTwhUN8vmkW38
\connect mediflow_pharmacy
\restrict vNIlYtp0xeR3XcKVONOCFsFCTb35E8I6Vnx8DuHChLWklMjearcTwhUN8vmkW38

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: prevent_historical_care_delivery_activation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.prevent_historical_care_delivery_activation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.care_contract_version=1 AND (NEW.care_delivery_cohort_id IS DISTINCT FROM OLD.care_delivery_cohort_id
        OR NEW.delivery_enabled IS DISTINCT FROM OLD.delivery_enabled) THEN
        RAISE EXCEPTION 'V1 delivery cohort is immutable; historical HELD needs a separate audited migration';
    END IF;
    RETURN NEW;
END $$;


ALTER FUNCTION public.prevent_historical_care_delivery_activation() OWNER TO test;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admission_medication_context; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_medication_context (
    admission_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid,
    started_at timestamp with time zone,
    source_started_at character varying(40),
    started_fingerprint character varying(64),
    closed_at timestamp with time zone,
    source_closed_at character varying(40),
    closed_fingerprint character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    medically_discharged_at timestamp with time zone,
    source_medically_discharged_at character varying(40),
    medical_discharge_fingerprint character varying(64),
    CONSTRAINT admission_medication_context_version_check CHECK ((version >= 0)),
    CONSTRAINT ck_admission_medication_close CHECK ((((closed_at IS NULL) AND (source_closed_at IS NULL) AND (closed_fingerprint IS NULL)) OR ((closed_at IS NOT NULL) AND (source_closed_at IS NOT NULL) AND (closed_fingerprint IS NOT NULL)))),
    CONSTRAINT ck_admission_medication_hash CHECK ((((started_fingerprint IS NULL) OR ((started_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)) AND ((closed_fingerprint IS NULL) OR ((closed_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)))),
    CONSTRAINT ck_admission_medication_medical_discharge CHECK ((((medically_discharged_at IS NULL) AND (source_medically_discharged_at IS NULL) AND (medical_discharge_fingerprint IS NULL)) OR ((medically_discharged_at IS NOT NULL) AND (source_medically_discharged_at IS NOT NULL) AND (medical_discharge_fingerprint IS NOT NULL)))),
    CONSTRAINT ck_admission_medication_medical_discharge_hash CHECK (((medical_discharge_fingerprint IS NULL) OR ((medical_discharge_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))),
    CONSTRAINT ck_admission_medication_medical_discharge_order CHECK ((((started_at IS NULL) OR (medically_discharged_at IS NULL) OR (medically_discharged_at >= started_at)) AND ((medically_discharged_at IS NULL) OR (closed_at IS NULL) OR (closed_at >= medically_discharged_at)))),
    CONSTRAINT ck_admission_medication_medical_discharge_source_time CHECK ((NOT ((source_medically_discharged_at)::timestamp with time zone IS DISTINCT FROM medically_discharged_at))),
    CONSTRAINT ck_admission_medication_source_time CHECK (((NOT ((source_started_at)::timestamp with time zone IS DISTINCT FROM started_at)) AND (NOT ((source_closed_at)::timestamp with time zone IS DISTINCT FROM closed_at)))),
    CONSTRAINT ck_admission_medication_start CHECK ((((started_at IS NULL) AND (source_started_at IS NULL) AND (started_fingerprint IS NULL) AND (department_id IS NULL)) OR ((started_at IS NOT NULL) AND (source_started_at IS NOT NULL) AND (started_fingerprint IS NOT NULL) AND (department_id IS NOT NULL)))),
    CONSTRAINT ck_admission_medication_time CHECK (((started_at IS NULL) OR (closed_at IS NULL) OR (closed_at >= started_at)))
);


ALTER TABLE public.admission_medication_context OWNER TO test;

--
-- Name: admission_medication_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.admission_medication_event (
    event_id uuid NOT NULL,
    event_fingerprint character varying(64) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT admission_medication_event_event_fingerprint_check CHECK (((event_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.admission_medication_event OWNER TO test;

--
-- Name: care_prescription_creation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.care_prescription_creation (
    command_id uuid NOT NULL,
    account_id uuid NOT NULL,
    intent_fingerprint character(64) NOT NULL,
    prescription_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT care_prescription_creation_intent_fingerprint_check CHECK ((intent_fingerprint ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.care_prescription_creation OWNER TO test;

--
-- Name: dispense_slip; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.dispense_slip (
    dispense_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    dispensed_at timestamp with time zone,
    dispensed_by uuid,
    failure_reason character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    dispensed_actor_type character varying(30),
    lifecycle_at_iso character varying(35),
    CONSTRAINT ck_dispense_actor_audit CHECK ((((status)::text <> 'DISPENSED'::text) OR ((dispensed_actor_type)::text = 'LEGACY_UNKNOWN'::text) OR (((dispensed_actor_type)::text = 'SYSTEM'::text) AND (dispensed_by IS NULL)) OR (((dispensed_actor_type)::text = ANY (ARRAY[('STAFF'::character varying)::text, ('ACCOUNT'::character varying)::text])) AND (dispensed_by IS NOT NULL)))),
    CONSTRAINT ck_dispense_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DISPENSED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELLED'::character varying)::text, ('EXPIRED'::character varying)::text])))
);


ALTER TABLE public.dispense_slip OWNER TO test;

--
-- Name: drug; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.drug (
    drug_id uuid NOT NULL,
    drug_name character varying(150) NOT NULL,
    active_ingredient character varying(150),
    unit character varying(20) NOT NULL,
    price numeric(15,2) NOT NULL,
    stock_quantity integer DEFAULT 0 NOT NULL,
    expiry_date date NOT NULL,
    manufacturer character varying(150),
    low_stock_threshold integer DEFAULT 10 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_drug_price_non_negative CHECK ((price >= (0)::numeric)),
    CONSTRAINT ck_drug_stock_non_negative CHECK ((stock_quantity >= 0))
);


ALTER TABLE public.drug OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: payment_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_receipt (
    receipt_id uuid NOT NULL,
    event_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    payment_method character varying(50),
    payment_occurred_at timestamp with time zone NOT NULL,
    correlation_id character varying(200) NOT NULL,
    payload_fingerprint character varying(128),
    status character varying(20) DEFAULT 'RECEIVED'::character varying NOT NULL,
    failure_code character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_payment_receipt_amount_non_negative CHECK ((total_amount >= (0)::numeric)),
    CONSTRAINT ck_payment_receipt_status CHECK (((status)::text = ANY (ARRAY[('RECEIVED'::character varying)::text, ('DISPENSED'::character varying)::text, ('COMPENSATED'::character varying)::text])))
);


ALTER TABLE public.payment_receipt OWNER TO test;

--
-- Name: pharmacy_event_outbox; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.pharmacy_event_outbox (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    payload text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    last_error character varying(500),
    available_at timestamp with time zone DEFAULT now() NOT NULL,
    locked_at timestamp with time zone,
    locked_by character varying(100),
    quarantined_at timestamp with time zone,
    aggregate_id uuid,
    delivery_enabled boolean DEFAULT true NOT NULL,
    care_contract_version smallint DEFAULT 0 NOT NULL,
    care_lifecycle_order smallint,
    care_delivery_cohort_id uuid,
    CONSTRAINT ck_outbox_care_contract CHECK ((((care_contract_version = 0) AND (care_lifecycle_order IS NULL) AND (care_delivery_cohort_id IS NULL)) OR ((care_contract_version = 1) AND (aggregate_id IS NOT NULL) AND (care_lifecycle_order = ANY (ARRAY[0, 1])) AND (care_lifecycle_order IS NOT NULL) AND (((NOT delivery_enabled) AND (care_delivery_cohort_id IS NULL)) OR (delivery_enabled AND (care_delivery_cohort_id = aggregate_id) AND (care_delivery_cohort_id IS NOT NULL))) AND (((care_lifecycle_order = 0) AND ((routing_key)::text = 'prescription.created'::text)) OR ((care_lifecycle_order = 1) AND ((routing_key)::text = ANY (ARRAY[('prescription.filled'::character varying)::text, ('prescription.dispense.failed'::character varying)::text, ('prescription.cancelled'::character varying)::text, ('prescription.expired'::character varying)::text])))))))
);


ALTER TABLE public.pharmacy_event_outbox OWNER TO test;

--
-- Name: pharmacy_outpatient_delivery_cohort; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.pharmacy_outpatient_delivery_cohort (
    prescription_id uuid NOT NULL,
    acceptance_reference character varying(255) NOT NULL,
    approved_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT pharmacy_outpatient_delivery_cohort_acceptance_reference_check CHECK (((length(btrim((acceptance_reference)::text)) >= 1) AND (length(btrim((acceptance_reference)::text)) <= 255)))
);


ALTER TABLE public.pharmacy_outpatient_delivery_cohort OWNER TO test;

--
-- Name: pharmacy_scheduler_lease; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.pharmacy_scheduler_lease (
    job_name character varying(100) NOT NULL,
    cursor_id uuid,
    lease_owner character varying(100),
    lease_until timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    lease_token uuid NOT NULL
);


ALTER TABLE public.pharmacy_scheduler_lease OWNER TO test;

--
-- Name: prescription; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription (
    prescription_id uuid NOT NULL,
    record_id uuid,
    patient_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    department_id uuid NOT NULL,
    prescribed_date date NOT NULL,
    total_amount numeric(15,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    cancelled_at timestamp with time zone,
    cancelled_by uuid,
    cancellation_reason character varying(500),
    care_contract_version smallint DEFAULT 0 NOT NULL,
    care_context character varying(20) DEFAULT 'OUTPATIENT'::character varying NOT NULL,
    care_episode_type character varying(32),
    care_episode_id uuid,
    admission_id uuid,
    price_code character varying(64),
    lifecycle_at_iso character varying(35),
    CONSTRAINT ck_prescription_cancellation_audit CHECK ((((status)::text <> 'CANCELLED'::text) OR ((cancelled_at IS NOT NULL) AND (cancelled_by IS NOT NULL) AND (cancellation_reason IS NOT NULL) AND (length(TRIM(BOTH FROM cancellation_reason)) > 0)))),
    CONSTRAINT ck_prescription_care_context CHECK (((care_context)::text = ANY (ARRAY[('OUTPATIENT'::character varying)::text, ('ADMISSION'::character varying)::text]))),
    CONSTRAINT ck_prescription_care_metadata CHECK ((((care_contract_version = 0) AND (record_id IS NOT NULL) AND ((care_context)::text = 'OUTPATIENT'::text) AND (care_episode_type IS NULL) AND (care_episode_id IS NULL) AND (admission_id IS NULL) AND (price_code IS NULL)) OR ((care_contract_version = 1) AND (care_episode_type IS NOT NULL) AND ((care_episode_type)::text = ANY (ARRAY[('OUTPATIENT_VISIT'::character varying)::text, ('ADMISSION'::character varying)::text])) AND (care_episode_id IS NOT NULL) AND (price_code IS NOT NULL) AND (btrim((price_code)::text) <> ''::text) AND ((((care_context)::text = 'OUTPATIENT'::text) AND ((care_episode_type)::text = 'OUTPATIENT_VISIT'::text) AND (admission_id IS NULL)) OR (((care_context)::text = 'ADMISSION'::text) AND ((care_episode_type)::text = 'ADMISSION'::text) AND (admission_id IS NOT NULL) AND (admission_id = care_episode_id)))))),
    CONSTRAINT ck_prescription_contract_version CHECK ((care_contract_version = ANY (ARRAY[0, 1]))),
    CONSTRAINT ck_prescription_status CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('FULFILLED'::character varying)::text, ('CANCELLED'::character varying)::text, ('EXPIRED'::character varying)::text, ('DISPENSE_FAILED'::character varying)::text])))
);


ALTER TABLE public.prescription OWNER TO test;

--
-- Name: prescription_clearance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_clearance (
    clearance_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    account_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    purpose character varying(32) NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    payment_method character varying(32) NOT NULL,
    granted_at timestamp with time zone NOT NULL,
    source_granted_at character varying(40) NOT NULL,
    expires_at timestamp with time zone,
    source_expires_at character varying(40),
    payload_fingerprint character varying(64) NOT NULL,
    target_status character varying(16) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_clearance_source_times CHECK ((((source_granted_at)::timestamp with time zone = granted_at) AND (((expires_at IS NULL) AND (source_expires_at IS NULL)) OR ((expires_at IS NOT NULL) AND (source_expires_at IS NOT NULL) AND ((source_expires_at)::timestamp with time zone = expires_at) AND (expires_at >= granted_at))))),
    CONSTRAINT prescription_clearance_amount_check CHECK ((amount >= (0)::numeric)),
    CONSTRAINT prescription_clearance_care_episode_type_check CHECK (((care_episode_type)::text = 'OUTPATIENT_VISIT'::text)),
    CONSTRAINT prescription_clearance_currency_check CHECK (((currency)::text = 'VND'::text)),
    CONSTRAINT prescription_clearance_payload_fingerprint_check CHECK (((payload_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT prescription_clearance_payment_method_check CHECK (((payment_method)::text ~ '^[A-Z][A-Z0-9_]{0,31}$'::text)),
    CONSTRAINT prescription_clearance_purpose_check CHECK (((purpose)::text = 'PRESCRIPTION'::text)),
    CONSTRAINT prescription_clearance_target_status_check CHECK (((target_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('VERIFIED'::character varying)::text])))
);


ALTER TABLE public.prescription_clearance OWNER TO test;

--
-- Name: prescription_clearance_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_clearance_event (
    event_id uuid NOT NULL,
    event_fingerprint character varying(64) NOT NULL,
    snapshot_fingerprint character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT prescription_clearance_event_event_fingerprint_check CHECK (((event_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT prescription_clearance_event_snapshot_fingerprint_check CHECK (((snapshot_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.prescription_clearance_event OWNER TO test;

--
-- Name: prescription_clearance_target; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_clearance_target (
    prescription_id uuid NOT NULL
);


ALTER TABLE public.prescription_clearance_target OWNER TO test;

--
-- Name: prescription_line; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_line (
    line_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    drug_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    dosage character varying(255),
    line_total numeric(15,2) NOT NULL,
    drug_name_snapshot character varying(150),
    CONSTRAINT ck_line_quantity_positive CHECK ((quantity > 0)),
    CONSTRAINT ck_prescription_line_name_snapshot CHECK (((drug_name_snapshot IS NULL) OR (length(btrim((drug_name_snapshot)::text)) > 0)))
);


ALTER TABLE public.prescription_line OWNER TO test;

--
-- Name: processed_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.processed_event OWNER TO test;

--
-- Name: stock_adjustment; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.stock_adjustment (
    adjustment_id uuid NOT NULL,
    drug_id uuid NOT NULL,
    before_stock integer NOT NULL,
    delta integer NOT NULL,
    after_stock integer NOT NULL,
    reason character varying(255) NOT NULL,
    actor_id uuid,
    correlation_id character varying(100),
    occurred_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_stock_adjustment_reason CHECK (((delta >= 0) OR (length(TRIM(BOTH FROM reason)) > 0))),
    CONSTRAINT ck_stock_adjustment_snapshot CHECK (((after_stock - before_stock) = delta)),
    CONSTRAINT stock_adjustment_after_stock_check CHECK ((after_stock >= 0)),
    CONSTRAINT stock_adjustment_before_stock_check CHECK ((before_stock >= 0))
);


ALTER TABLE public.stock_adjustment OWNER TO test;

--
-- Name: stock_reservation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.stock_reservation (
    reservation_id uuid NOT NULL,
    drug_id uuid NOT NULL,
    prescription_id uuid NOT NULL,
    quantity integer NOT NULL,
    status character varying(20) DEFAULT 'RESERVED'::character varying NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    release_reason character varying(50),
    released_at timestamp with time zone,
    released_by uuid,
    CONSTRAINT ck_reservation_quantity_positive CHECK ((quantity > 0)),
    CONSTRAINT ck_reservation_release_audit CHECK (((((status)::text = ANY (ARRAY[('RELEASED'::character varying)::text, ('EXPIRED'::character varying)::text])) AND (release_reason IS NOT NULL) AND (released_at IS NOT NULL)) OR (((status)::text = ANY (ARRAY[('RESERVED'::character varying)::text, ('FULFILLED'::character varying)::text])) AND (release_reason IS NULL) AND (released_at IS NULL) AND (released_by IS NULL)))),
    CONSTRAINT ck_reservation_release_reason CHECK (((release_reason IS NULL) OR ((release_reason)::text = ANY (ARRAY[('PRESCRIPTION_CANCELLED'::character varying)::text, ('PAYMENT_FAILED'::character varying)::text, ('DISPENSE_FAILED'::character varying)::text, ('ADMIN_OVERRIDE'::character varying)::text, ('TTL_EXPIRED'::character varying)::text, ('LEGACY_MIGRATION'::character varying)::text])))),
    CONSTRAINT ck_reservation_status CHECK (((status)::text = ANY (ARRAY[('RESERVED'::character varying)::text, ('FULFILLED'::character varying)::text, ('RELEASED'::character varying)::text, ('EXPIRED'::character varying)::text])))
);


ALTER TABLE public.stock_reservation OWNER TO test;

--
-- Data for Name: admission_medication_context; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_medication_context (admission_id, patient_id, department_id, started_at, source_started_at, started_fingerprint, closed_at, source_closed_at, closed_fingerprint, version, medically_discharged_at, source_medically_discharged_at, medical_discharge_fingerprint) FROM stdin;
\.


--
-- Data for Name: admission_medication_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.admission_medication_event (event_id, event_fingerprint, processed_at) FROM stdin;
\.


--
-- Data for Name: care_prescription_creation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.care_prescription_creation (command_id, account_id, intent_fingerprint, prescription_id, created_at) FROM stdin;
a081dd65-6676-4ede-ae7e-6fa5a54878b3	da50c583-e8e3-452b-9cc2-15f5adcf7cf3	0c8cab8ec494287371f39458e23de7b144e62a8d879f74dba54ae654ebbe1a18	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	2026-10-22 00:40:19.44017+00
c9bc0113-1671-44e9-a636-f1a9a62cc3f7	81432960-3a09-439c-b6bb-9de274648a9d	359d474c02de4881c1daa3d70f0a1cb00ff1803d89e932f16976047499741d92	84116e5c-b8d2-4afa-a586-0c2af1c350ab	2026-10-22 00:45:31.102591+00
66e4f4c3-88b6-41b4-8cfc-d38029769e1d	81432960-3a09-439c-b6bb-9de274648a9d	c386edb57097bdfaabfc322d0b22355e504049cc2e3a9833ee0d9e736f77c17f	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	2026-10-22 00:45:34.626816+00
484d5df5-d242-49dc-9d5f-5a152a617f91	81432960-3a09-439c-b6bb-9de274648a9d	a22f50ceb29a7e096f9791b37445d72ab8425de8d4dc85fedaec8c2bf3b75163	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	2026-10-22 00:45:46.626084+00
b11d507c-5695-420c-82a1-1fa5f7700004	81432960-3a09-439c-b6bb-9de274648a9d	e1fe10bccaf468328b8d3aedaae77d23aef48024b2d371830d37489df204fe5f	7b8b6d90-897d-4439-8f89-4f6905e825ae	2026-10-22 00:45:49.258169+00
\.


--
-- Data for Name: dispense_slip; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.dispense_slip (dispense_id, prescription_id, status, dispensed_at, dispensed_by, failure_reason, created_at, updated_at, dispensed_actor_type, lifecycle_at_iso) FROM stdin;
011d2eba-acfb-4ff4-bd88-38cc2eee1e91	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	CANCELLED	\N	\N	Isolated FE acceptance cancellation	2026-10-22 00:40:19.541262+00	2026-10-22 00:40:19.674869+00	\N	2026-10-22T00:40:19.656720600Z
d3b54c5d-ea65-48d2-9200-cfae37dae670	84116e5c-b8d2-4afa-a586-0c2af1c350ab	DISPENSED	2026-10-22 00:45:34.36428+00	c91f2020-617e-4767-8dec-937f2c30cad8	\N	2026-10-22 00:45:31.12026+00	2026-10-22 00:45:34.371063+00	STAFF	2026-10-22T00:45:34.364279600Z
d29ebf10-67c0-426c-b5dd-4f848ee0e03b	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	PENDING	\N	\N	\N	2026-10-22 00:45:46.64373+00	2026-10-22 00:45:46.64373+00	\N	\N
d090d1dc-85f3-4f7f-9f42-908e651077a9	7b8b6d90-897d-4439-8f89-4f6905e825ae	PENDING	\N	\N	\N	2026-10-22 00:45:49.282569+00	2026-10-22 00:45:49.282569+00	\N	\N
bfc025df-98c4-4244-bf55-3287331fc5e9	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	DISPENSED	2026-10-22 00:52:17.382605+00	c91f2020-617e-4767-8dec-937f2c30cad8	\N	2026-10-22 00:45:34.645115+00	2026-10-22 00:52:17.38797+00	STAFF	2026-10-22T00:52:17.382604700Z
\.


--
-- Data for Name: drug; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.drug (drug_id, drug_name, active_ingredient, unit, price, stock_quantity, expiry_date, manufacturer, low_stock_threshold, created_at, updated_at) FROM stdin;
2cfc6e03-07fc-4e53-b740-7e744cb15d60	FE acceptance c94387bd7e37	\N	tablet	10000.00	40	2027-10-22	\N	0	2026-10-22 00:40:18.919406+00	2026-10-22 00:40:18.919406+00
f1b7bfcc-b971-4b87-aa3e-378c816ea545	Vitamin C 500 mg	Ascorbic acid	viên	2000.00	180	2028-06-30	MediFlow Demo	10	2026-10-22 00:40:38.33508+00	2026-10-22 00:40:38.33508+00
054e2567-33df-46fe-a0ea-4945999f0892	Nước muối sinh lý 0,9%	Natri clorid	chai	12000.00	65	2028-06-30	MediFlow Demo	10	2026-10-22 00:40:38.350209+00	2026-10-22 00:40:38.350209+00
7cf8d849-c2d4-4ccc-9bb4-2247107df262	Oresol	Electrolytes	viên	5500.00	90	2028-06-30	MediFlow Demo	10	2026-10-22 00:40:38.380168+00	2026-10-22 00:40:38.380168+00
2d9b1cb1-bf0d-4402-841d-ef5497dfa29b	Omeprazol 20 mg	Omeprazol	viên	3500.00	120	2028-06-30	MediFlow Demo	10	2026-10-22 00:40:38.410105+00	2026-10-22 00:40:38.410105+00
71a8add5-5882-44f0-85bb-8e13ea0d00fa	Paracetamol 500 mg	Paracetamol	viên	1500.00	238	2028-06-30	MediFlow Demo	10	2026-10-22 00:40:38.307676+00	2026-10-22 00:52:17.384763+00
4c7bbb61-6d47-4c23-8122-bc5e039c8958	Loratadin 10 mg	Loratadin	viên	2500.00	38	2028-06-30	MediFlow Demo	10	2026-10-22 00:40:38.395167+00	2026-10-22 10:57:49.089321+00
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	-1415538558	test	2026-10-22 07:39:46.851467	63	t
2	2	stock reservation	SQL	V2__stock_reservation.sql	522481446	test	2026-10-22 07:39:46.972713	26	t
3	3	unique prescription drug reservation	SQL	V3__unique_prescription_drug_reservation.sql	2081143890	test	2026-10-22 07:39:47.024604	6	t
4	4	prescription lifecycle and reservation audit	SQL	V4__prescription_lifecycle_and_reservation_audit.sql	1376006284	test	2026-10-22 07:39:47.046193	33	t
5	5	pharmacy event outbox	SQL	V5__pharmacy_event_outbox.sql	-723693571	test	2026-10-22 07:39:47.099062	14	t
6	6	payment receipt	SQL	V6__payment_receipt.sql	-1239944617	test	2026-10-22 07:39:47.130769	22	t
7	7	pharmacy event outbox lease	SQL	V7__pharmacy_event_outbox_lease.sql	1049860167	test	2026-10-22 07:39:47.171362	9	t
8	8	stock adjustment audit	SQL	V8__stock_adjustment_audit.sql	977191808	test	2026-10-22 07:39:47.194257	10	t
9	9	pharmacy event outbox quarantine	SQL	V9__pharmacy_event_outbox_quarantine.sql	1756270578	test	2026-10-22 07:39:47.217083	8	t
10	10	pharmacy scheduler cursor lease	SQL	V10__pharmacy_scheduler_cursor_lease.sql	679666114	test	2026-10-22 07:39:47.236798	8	t
11	11	pharmacy scheduler fencing token	SQL	V11__pharmacy_scheduler_fencing_token.sql	1062555552	test	2026-10-22 07:39:47.256401	6	t
12	12	pharmacy outbox causal order	SQL	V12__pharmacy_outbox_causal_order.sql	-64854726	test	2026-10-22 07:39:47.272249	7	t
13	13	dispense actor audit type	SQL	V13__dispense_actor_audit_type.sql	-1440300319	test	2026-10-22 07:39:47.291374	7	t
14	14	prescription care context	SQL	V14__prescription_care_context.sql	-1899883625	test	2026-10-22 07:39:47.309081	16	t
15	15	admission medication context	SQL	V15__admission_medication_context.sql	-78566964	test	2026-10-22 07:39:47.337607	12	t
16	16	prescription clearance	SQL	V16__prescription_clearance.sql	-1456908199	test	2026-10-22 07:39:47.364793	32	t
17	17	held prescription care events	SQL	V17__held_prescription_care_events.sql	-1792288551	test	2026-10-22 07:39:47.414265	20	t
18	18	held care creation receipt	SQL	V18__held_care_creation_receipt.sql	313206577	test	2026-10-22 07:39:47.449749	14	t
19	19	admission medical discharge	SQL	V19__admission_medical_discharge.sql	-790637106	test	2026-10-22 07:39:47.479962	9	t
20	20	selective outpatient delivery cohort	SQL	V20__selective_outpatient_delivery_cohort.sql	-352134367	test	2026-10-22 07:39:47.501634	20	t
\.


--
-- Data for Name: payment_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_receipt (receipt_id, event_id, invoice_id, prescription_id, patient_id, department_id, total_amount, payment_method, payment_occurred_at, correlation_id, payload_fingerprint, status, failure_code, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: pharmacy_event_outbox; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.pharmacy_event_outbox (event_id, routing_key, payload, created_at, published_at, attempts, last_error, available_at, locked_at, locked_by, quarantined_at, aggregate_id, delivery_enabled, care_contract_version, care_lifecycle_order, care_delivery_cohort_id) FROM stdin;
7377fa4b-ea08-4ff1-a24f-5be31546e5a7	prescription.created	{"eventId":"7377fa4b-ea08-4ff1-a24f-5be31546e5a7","eventType":"prescription.created","version":1,"occurredAt":"2026-10-22T00:40:19.501589Z","correlationId":"0acb8270-c779-4db9-befb-ff2a4fd77b6e","producer":"pharmacy-service","payload":{"prescriptionId":"583fb28a-4e20-4e2a-9bc4-9ec9acd2363b","recordId":"5917d49c-0715-416d-a482-c0b281926b43","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44","departmentId":"b134eebd-d327-4718-8bef-92525164178d","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"11935f4f-6428-4051-ba9e-8e8a1b7d9f11","sourceType":"PRESCRIPTION","sourceId":"583fb28a-4e20-4e2a-9bc4-9ec9acd2363b","priceCode":"DRUG","items":[{"drugId":"2cfc6e03-07fc-4e53-b740-7e744cb15d60","drugName":"FE acceptance c94387bd7e37","quantity":3,"unitPrice":10000.00}],"totalAmount":30000.00,"createdAt":"2026-10-22T00:40:19.501589Z"}}	2026-10-22 00:40:19.536294+00	2026-10-22 00:40:19.700574+00	0	\N	2026-10-22 00:40:19.536294+00	\N	\N	\N	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	t	1	0	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b
8994a20f-f105-4ec4-a185-395c22aab57b	prescription.created	{"eventId":"8994a20f-f105-4ec4-a185-395c22aab57b","eventType":"prescription.created","version":1,"occurredAt":"2026-10-22T00:45:31.109138Z","correlationId":"9ee4d903-7954-4826-b1df-a47d706b0e22","producer":"pharmacy-service","payload":{"prescriptionId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","recordId":"4447ad07-fe64-4d41-8c96-7a278d167be2","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","sourceType":"PRESCRIPTION","sourceId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","priceCode":"DRUG","items":[{"drugId":"71a8add5-5882-44f0-85bb-8e13ea0d00fa","drugName":"Paracetamol 500 mg","quantity":6,"unitPrice":1500.00}],"totalAmount":9000.00,"createdAt":"2026-10-22T00:45:31.109138Z"}}	2026-10-22 00:45:31.117251+00	2026-10-22 00:45:31.288281+00	0	\N	2026-10-22 00:45:31.117251+00	\N	\N	\N	84116e5c-b8d2-4afa-a586-0c2af1c350ab	t	1	0	84116e5c-b8d2-4afa-a586-0c2af1c350ab
32b9c998-f101-42df-af40-0cd87c41fc40	prescription.cancelled	{"eventId":"32b9c998-f101-42df-af40-0cd87c41fc40","eventType":"prescription.cancelled","version":1,"occurredAt":"2026-10-22T00:40:19.656720600Z","correlationId":"b21f51ec-be1c-4e83-987b-5b9f5ca32df7","producer":"pharmacy-service","payload":{"prescriptionId":"583fb28a-4e20-4e2a-9bc4-9ec9acd2363b","recordId":"5917d49c-0715-416d-a482-c0b281926b43","patientId":"c36277c4-347f-480d-8473-bcf9f98c8a44","departmentId":"b134eebd-d327-4718-8bef-92525164178d","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"11935f4f-6428-4051-ba9e-8e8a1b7d9f11","sourceType":"PRESCRIPTION","sourceId":"583fb28a-4e20-4e2a-9bc4-9ec9acd2363b","priceCode":"DRUG","items":[{"drugId":"2cfc6e03-07fc-4e53-b740-7e744cb15d60","drugName":"FE acceptance c94387bd7e37","quantity":3,"unitPrice":10000.00}],"totalAmount":30000.00,"cancelledAt":"2026-10-22T00:40:19.656720600Z","reason":"Isolated FE acceptance cancellation"}}	2026-10-22 00:40:19.707347+00	2026-10-22 00:40:20.975917+00	0	\N	2026-10-22 00:40:19.707347+00	\N	\N	\N	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	t	1	1	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b
1ffa49d2-e974-4c65-9b7d-7c49510aec81	prescription.created	{"eventId":"1ffa49d2-e974-4c65-9b7d-7c49510aec81","eventType":"prescription.created","version":1,"occurredAt":"2026-10-22T00:45:34.633066Z","correlationId":"d25b9aee-1f1a-4609-a518-0a202019b6e8","producer":"pharmacy-service","payload":{"prescriptionId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","recordId":"1361a316-8aa3-4ad4-8774-f852d8c64fa1","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","sourceType":"PRESCRIPTION","sourceId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","priceCode":"DRUG","items":[{"drugId":"71a8add5-5882-44f0-85bb-8e13ea0d00fa","drugName":"Paracetamol 500 mg","quantity":6,"unitPrice":1500.00}],"totalAmount":9000.00,"createdAt":"2026-10-22T00:45:34.633066Z"}}	2026-10-22 00:45:34.641833+00	2026-10-22 00:45:35.551437+00	0	\N	2026-10-22 00:45:34.641833+00	\N	\N	\N	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	t	1	0	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a
7d3186d9-a2c6-44e1-a89c-4565b164d105	prescription.filled	{"eventId":"7d3186d9-a2c6-44e1-a89c-4565b164d105","eventType":"prescription.filled","version":1,"occurredAt":"2026-10-22T00:45:34.364279600Z","correlationId":"56d13802-f3dd-4c48-a841-bcd223205143","producer":"pharmacy-service","payload":{"prescriptionId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","recordId":"4447ad07-fe64-4d41-8c96-7a278d167be2","patientId":"30820325-711c-4d7b-b364-be73c78a43b0","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"71626749-ef62-42f4-9d40-35097f05dfb9","sourceType":"PRESCRIPTION","sourceId":"84116e5c-b8d2-4afa-a586-0c2af1c350ab","priceCode":"DRUG","items":[{"drugId":"71a8add5-5882-44f0-85bb-8e13ea0d00fa","drugName":"Paracetamol 500 mg","quantity":6,"unitPrice":1500.00}],"totalAmount":9000.00,"dispenseId":"d3b54c5d-ea65-48d2-9200-cfae37dae670","filledAt":"2026-10-22T00:45:34.364279600Z"}}	2026-10-22 00:45:34.380202+00	2026-10-22 00:45:35.497438+00	0	\N	2026-10-22 00:45:34.380202+00	\N	\N	\N	84116e5c-b8d2-4afa-a586-0c2af1c350ab	t	1	1	84116e5c-b8d2-4afa-a586-0c2af1c350ab
588992b4-d137-4996-af14-313422e07269	prescription.created	{"eventId":"588992b4-d137-4996-af14-313422e07269","eventType":"prescription.created","version":1,"occurredAt":"2026-10-22T00:45:46.632375Z","correlationId":"12989ae3-0597-4983-a637-b6c0c4c8198b","producer":"pharmacy-service","payload":{"prescriptionId":"0a3f84b7-2905-405b-a0f8-c68afd9ca87a","recordId":"acc4a82c-6856-469d-80be-8dc343a9e06e","patientId":"8fbf50e4-67ca-42f6-96ac-c15d6aab61c8","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"037fcbd9-127b-44d6-96ee-73c571763c5e","sourceType":"PRESCRIPTION","sourceId":"0a3f84b7-2905-405b-a0f8-c68afd9ca87a","priceCode":"DRUG","items":[{"drugId":"71a8add5-5882-44f0-85bb-8e13ea0d00fa","drugName":"Paracetamol 500 mg","quantity":6,"unitPrice":1500.00}],"totalAmount":9000.00,"createdAt":"2026-10-22T00:45:46.632375Z"}}	2026-10-22 00:45:46.640903+00	2026-10-22 00:45:46.70893+00	0	\N	2026-10-22 00:45:46.640903+00	\N	\N	\N	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	t	1	0	0a3f84b7-2905-405b-a0f8-c68afd9ca87a
3de60d0c-4a2b-4052-9f39-47e93a533100	prescription.created	{"eventId":"3de60d0c-4a2b-4052-9f39-47e93a533100","eventType":"prescription.created","version":1,"occurredAt":"2026-10-22T00:45:49.267844Z","correlationId":"775e7710-bcab-4da5-a3fb-1fb0e41627ea","producer":"pharmacy-service","payload":{"prescriptionId":"7b8b6d90-897d-4439-8f89-4f6905e825ae","recordId":"1d4e99c7-3572-42be-9520-a43c1eedc030","patientId":"6f75b635-a491-4191-99b3-1df16c1a3832","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"250352fd-6ae9-4108-a147-2d76e9b49eb9","sourceType":"PRESCRIPTION","sourceId":"7b8b6d90-897d-4439-8f89-4f6905e825ae","priceCode":"DRUG","items":[{"drugId":"71a8add5-5882-44f0-85bb-8e13ea0d00fa","drugName":"Paracetamol 500 mg","quantity":6,"unitPrice":1500.00}],"totalAmount":9000.00,"createdAt":"2026-10-22T00:45:49.267844Z"}}	2026-10-22 00:45:49.278274+00	2026-10-22 00:45:49.78472+00	0	\N	2026-10-22 00:45:49.278274+00	\N	\N	\N	7b8b6d90-897d-4439-8f89-4f6905e825ae	t	1	0	7b8b6d90-897d-4439-8f89-4f6905e825ae
6400782d-fa9c-41f8-a5c3-95b1ad7081ce	prescription.filled	{"eventId":"6400782d-fa9c-41f8-a5c3-95b1ad7081ce","eventType":"prescription.filled","version":1,"occurredAt":"2026-10-22T00:52:17.382604700Z","correlationId":"a4e8a115-4307-42be-bdfa-2be6d65d3156","producer":"pharmacy-service","payload":{"prescriptionId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","recordId":"1361a316-8aa3-4ad4-8774-f852d8c64fa1","patientId":"8dd25d75-6d29-4967-ac6e-d54c6a8dd490","departmentId":"6c568d0b-debd-44ae-999d-3942e1ecfe8b","careContext":"OUTPATIENT","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"eab4cb0a-de3f-495c-9304-73ff1f18b5d4","sourceType":"PRESCRIPTION","sourceId":"19967dc8-62bd-4bdd-b413-3f3b4ac1f69a","priceCode":"DRUG","items":[{"drugId":"71a8add5-5882-44f0-85bb-8e13ea0d00fa","drugName":"Paracetamol 500 mg","quantity":6,"unitPrice":1500.00}],"totalAmount":9000.00,"dispenseId":"bfc025df-98c4-4244-bf55-3287331fc5e9","filledAt":"2026-10-22T00:52:17.382604700Z"}}	2026-10-22 00:52:17.39709+00	2026-10-22 00:52:18.015839+00	0	\N	2026-10-22 00:52:17.39709+00	\N	\N	\N	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	t	1	1	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a
abd243bd-0080-46f0-97f4-4cfd8644b8c3	stock.adjusted	{"eventId":"abd243bd-0080-46f0-97f4-4cfd8644b8c3","occurredAt":"2026-10-22T00:51:53.472211300Z","correlationId":"5cd5386b-ce27-4875-9139-1d01ea1b674a","actorId":"c91f2020-617e-4767-8dec-937f2c30cad8","drugId":"4c7bbb61-6d47-4c23-8122-bc5e039c8958","beforeStock":8,"afterStock":28,"delta":20,"reason":"Nhập bổ sung lô thuốc demo Review 3"}	2026-10-22 00:51:53.498094+00	\N	5	RabbitMQ returned unroutable event: NO_ROUTE	2026-10-22 00:52:24.887996+00	\N	\N	2026-10-22 00:52:08.889101+00	4c7bbb61-6d47-4c23-8122-bc5e039c8958	t	0	\N	\N
c6db9318-0621-4ac2-9900-443b6608de99	stock.adjusted	{"eventId":"c6db9318-0621-4ac2-9900-443b6608de99","occurredAt":"2026-10-22T10:57:49.059162300Z","correlationId":"f6905464-db11-45ff-be33-09c9b76471cc","actorId":"c91f2020-617e-4767-8dec-937f2c30cad8","drugId":"4c7bbb61-6d47-4c23-8122-bc5e039c8958","beforeStock":28,"afterStock":38,"delta":10,"reason":"Bổ sung tồn kho demo Review3"}	2026-10-22 10:57:49.161268+00	\N	5	RabbitMQ returned unroutable event: NO_ROUTE	2026-10-22 10:58:21.228871+00	\N	\N	2026-10-22 10:58:05.231268+00	4c7bbb61-6d47-4c23-8122-bc5e039c8958	t	0	\N	\N
\.


--
-- Data for Name: pharmacy_outpatient_delivery_cohort; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.pharmacy_outpatient_delivery_cohort (prescription_id, acceptance_reference, approved_at) FROM stdin;
583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	isolated-six-service-acceptance	2026-10-22 00:40:19.534379+00
84116e5c-b8d2-4afa-a586-0c2af1c350ab	isolated-six-service-acceptance	2026-10-22 00:45:31.115991+00
19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	isolated-six-service-acceptance	2026-10-22 00:45:34.640431+00
0a3f84b7-2905-405b-a0f8-c68afd9ca87a	isolated-six-service-acceptance	2026-10-22 00:45:46.639594+00
7b8b6d90-897d-4439-8f89-4f6905e825ae	isolated-six-service-acceptance	2026-10-22 00:45:49.276979+00
\.


--
-- Data for Name: pharmacy_scheduler_lease; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.pharmacy_scheduler_lease (job_name, cursor_id, lease_owner, lease_until, updated_at, lease_token) FROM stdin;
reservation-expiry	\N	\N	\N	2026-10-22 12:00:00.024102+00	b83d095e-af51-4402-b988-7d64812d49c2
\.


--
-- Data for Name: prescription; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription (prescription_id, record_id, patient_id, doctor_id, department_id, prescribed_date, total_amount, created_at, updated_at, status, cancelled_at, cancelled_by, cancellation_reason, care_contract_version, care_context, care_episode_type, care_episode_id, admission_id, price_code, lifecycle_at_iso) FROM stdin;
583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	5917d49c-0715-416d-a482-c0b281926b43	c36277c4-347f-480d-8473-bcf9f98c8a44	ddf8f66b-7b09-43e0-8e24-dd24678c3414	b134eebd-d327-4718-8bef-92525164178d	2026-10-22	30000.00	2026-10-22 00:40:19.501589+00	2026-10-22 00:40:19.660395+00	CANCELLED	2026-10-22 00:40:19.656721+00	ddf8f66b-7b09-43e0-8e24-dd24678c3414	Isolated FE acceptance cancellation	1	OUTPATIENT	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	\N	DRUG	2026-10-22T00:40:19.656720600Z
84116e5c-b8d2-4afa-a586-0c2af1c350ab	4447ad07-fe64-4d41-8c96-7a278d167be2	30820325-711c-4d7b-b364-be73c78a43b0	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	9000.00	2026-10-22 00:45:31.109138+00	2026-10-22 00:45:34.369491+00	FULFILLED	\N	\N	\N	1	OUTPATIENT	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	\N	DRUG	2026-10-22T00:45:34.364279600Z
0a3f84b7-2905-405b-a0f8-c68afd9ca87a	acc4a82c-6856-469d-80be-8dc343a9e06e	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	9000.00	2026-10-22 00:45:46.632375+00	2026-10-22 00:45:46.632375+00	ACTIVE	\N	\N	\N	1	OUTPATIENT	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	\N	DRUG	\N
7b8b6d90-897d-4439-8f89-4f6905e825ae	1d4e99c7-3572-42be-9520-a43c1eedc030	6f75b635-a491-4191-99b3-1df16c1a3832	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	9000.00	2026-10-22 00:45:49.267844+00	2026-10-22 00:45:49.267844+00	ACTIVE	\N	\N	\N	1	OUTPATIENT	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	\N	DRUG	\N
19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	1361a316-8aa3-4ad4-8774-f852d8c64fa1	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	e524185a-adb6-48d3-a867-b39b259940f2	6c568d0b-debd-44ae-999d-3942e1ecfe8b	2026-10-22	9000.00	2026-10-22 00:45:34.633066+00	2026-10-22 00:52:17.386363+00	FULFILLED	\N	\N	\N	1	OUTPATIENT	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	\N	DRUG	2026-10-22T00:52:17.382604700Z
\.


--
-- Data for Name: prescription_clearance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_clearance (clearance_id, invoice_id, account_id, prescription_id, patient_id, purpose, care_episode_type, care_episode_id, amount, currency, payment_method, granted_at, source_granted_at, expires_at, source_expires_at, payload_fingerprint, target_status, created_at) FROM stdin;
595165d5-e8c4-41e1-9203-1cd72c053d30	eb37200c-eb91-4c71-b45e-732a9ba773c8	e4463013-b866-45d6-aeeb-20d5e6c07197	84116e5c-b8d2-4afa-a586-0c2af1c350ab	30820325-711c-4d7b-b364-be73c78a43b0	PRESCRIPTION	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	9000.00	VND	CASH	2026-10-22 00:45:32.201666+00	2026-10-22T00:45:32.201665800Z	\N	\N	56e18d414f17e30361f5925014eab8ecc206aeb514b371463433670592cb86e8	VERIFIED	2026-10-22 00:45:33.459945+00
3dc5587a-41d2-4b78-8ea6-cd1fc05e9294	88e40197-ac94-4a80-be9b-f94934eea7ff	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	PRESCRIPTION	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	9000.00	VND	CASH	2026-10-22 00:45:35.739516+00	2026-10-22T00:45:35.739515500Z	\N	\N	0e2e200b31a71bc056ee6eb89e94cc776285b359f97b9a43cb65be6cfb0f84e6	VERIFIED	2026-10-22 00:45:37.535836+00
\.


--
-- Data for Name: prescription_clearance_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_clearance_event (event_id, event_fingerprint, snapshot_fingerprint, created_at) FROM stdin;
74190a4b-a6b9-419f-8a27-c5882393cf55	e31f3bb215bf5a2e45215fb5ea506f11fe87518c8d78415f51cee89384952428	8448f6512076844cf8392ca113761b5a3be7172a9e016c5765b225c75bd16204	2026-10-22 00:45:33.459945+00
736eea19-daa1-489e-8eda-499a93f4165b	7f65f7a7ba7b2ff9ff2a8fcc9a836c27606de0f4e8150609cc97b8988320a5f0	f21d23bc04b4b0332b0159406ecd3903e72e56ac73c778d25e149f4b3f63fbff	2026-10-22 00:45:37.535836+00
\.


--
-- Data for Name: prescription_clearance_target; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_clearance_target (prescription_id) FROM stdin;
84116e5c-b8d2-4afa-a586-0c2af1c350ab
19967dc8-62bd-4bdd-b413-3f3b4ac1f69a
\.


--
-- Data for Name: prescription_line; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_line (line_id, prescription_id, drug_id, quantity, unit_price, dosage, line_total, drug_name_snapshot) FROM stdin;
4d838fff-5066-4aef-b868-99a64f57de06	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	2cfc6e03-07fc-4e53-b740-7e744cb15d60	3	10000.00	Acceptance only	30000.00	FE acceptance c94387bd7e37
75939539-705b-4757-bd05-53ab002f29a5	84116e5c-b8d2-4afa-a586-0c2af1c350ab	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00	Theo hướng dẫn của bác sĩ	9000.00	Paracetamol 500 mg
a97c4440-8e78-402b-bab5-b2230147294a	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00	Theo hướng dẫn của bác sĩ	9000.00	Paracetamol 500 mg
c0c631ee-62bb-4cdf-ad8c-f1dc3941c0cc	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00	Theo hướng dẫn của bác sĩ	9000.00	Paracetamol 500 mg
d848cfe3-197b-4af2-a3e6-ba68ea946e74	7b8b6d90-897d-4439-8f89-4f6905e825ae	71a8add5-5882-44f0-85bb-8e13ea0d00fa	6	1500.00	Theo hướng dẫn của bác sĩ	9000.00	Paracetamol 500 mg
\.


--
-- Data for Name: processed_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.processed_event (event_id, routing_key, processed_at) FROM stdin;
\.


--
-- Data for Name: stock_adjustment; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.stock_adjustment (adjustment_id, drug_id, before_stock, delta, after_stock, reason, actor_id, correlation_id, occurred_at) FROM stdin;
8c219cf6-9da4-4f29-83d2-f9e5a19d8f22	4c7bbb61-6d47-4c23-8122-bc5e039c8958	8	20	28	Nhập bổ sung lô thuốc demo Review 3	c91f2020-617e-4767-8dec-937f2c30cad8	5cd5386b-ce27-4875-9139-1d01ea1b674a	2026-10-22 00:51:53.472211+00
46d3b54a-c2a5-49a5-a5b8-8b04c42f5a6c	4c7bbb61-6d47-4c23-8122-bc5e039c8958	28	10	38	Bổ sung tồn kho demo Review3	c91f2020-617e-4767-8dec-937f2c30cad8	f6905464-db11-45ff-be33-09c9b76471cc	2026-10-22 10:57:49.059162+00
\.


--
-- Data for Name: stock_reservation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.stock_reservation (reservation_id, drug_id, prescription_id, quantity, status, expires_at, created_at, updated_at, release_reason, released_at, released_by) FROM stdin;
6dae2b63-810c-4222-9913-8d41041f14f2	2cfc6e03-07fc-4e53-b740-7e744cb15d60	583fb28a-4e20-4e2a-9bc4-9ec9acd2363b	3	RELEASED	2026-10-23 00:40:19.501589+00	2026-10-22 00:40:19.539689+00	2026-10-22 00:40:19.66359+00	PRESCRIPTION_CANCELLED	2026-10-22 00:40:19.656721+00	ddf8f66b-7b09-43e0-8e24-dd24678c3414
b0d2478e-1c28-4fc1-9712-8771554002fd	71a8add5-5882-44f0-85bb-8e13ea0d00fa	84116e5c-b8d2-4afa-a586-0c2af1c350ab	6	FULFILLED	2026-10-23 00:45:31.109138+00	2026-10-22 00:45:31.119183+00	2026-10-22 00:45:34.365314+00	\N	\N	\N
bf9c9617-f7c9-41ca-8535-6f810679cf2e	71a8add5-5882-44f0-85bb-8e13ea0d00fa	0a3f84b7-2905-405b-a0f8-c68afd9ca87a	6	RESERVED	2026-10-23 00:45:46.632375+00	2026-10-22 00:45:46.642104+00	2026-10-22 00:45:46.642104+00	\N	\N	\N
d5bdc494-4b80-4db4-86fd-297a52817558	71a8add5-5882-44f0-85bb-8e13ea0d00fa	7b8b6d90-897d-4439-8f89-4f6905e825ae	6	RESERVED	2026-10-23 00:45:49.267844+00	2026-10-22 00:45:49.280593+00	2026-10-22 00:45:49.280593+00	\N	\N	\N
0420908f-1901-407c-998d-a2635c7b05f6	71a8add5-5882-44f0-85bb-8e13ea0d00fa	19967dc8-62bd-4bdd-b413-3f3b4ac1f69a	6	FULFILLED	2026-10-23 00:45:34.633066+00	2026-10-22 00:45:34.644088+00	2026-10-22 00:52:17.383152+00	\N	\N	\N
\.


--
-- Name: admission_medication_context admission_medication_context_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_medication_context
    ADD CONSTRAINT admission_medication_context_pkey PRIMARY KEY (admission_id);


--
-- Name: admission_medication_event admission_medication_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.admission_medication_event
    ADD CONSTRAINT admission_medication_event_pkey PRIMARY KEY (event_id);


--
-- Name: care_prescription_creation care_prescription_creation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.care_prescription_creation
    ADD CONSTRAINT care_prescription_creation_pkey PRIMARY KEY (command_id);


--
-- Name: care_prescription_creation care_prescription_creation_prescription_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.care_prescription_creation
    ADD CONSTRAINT care_prescription_creation_prescription_id_key UNIQUE (prescription_id);


--
-- Name: dispense_slip dispense_slip_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.dispense_slip
    ADD CONSTRAINT dispense_slip_pkey PRIMARY KEY (dispense_id);


--
-- Name: dispense_slip dispense_slip_prescription_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.dispense_slip
    ADD CONSTRAINT dispense_slip_prescription_id_key UNIQUE (prescription_id);


--
-- Name: drug drug_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.drug
    ADD CONSTRAINT drug_pkey PRIMARY KEY (drug_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: payment_receipt payment_receipt_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_receipt
    ADD CONSTRAINT payment_receipt_event_id_key UNIQUE (event_id);


--
-- Name: payment_receipt payment_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_receipt
    ADD CONSTRAINT payment_receipt_pkey PRIMARY KEY (receipt_id);


--
-- Name: pharmacy_event_outbox pharmacy_event_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.pharmacy_event_outbox
    ADD CONSTRAINT pharmacy_event_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: pharmacy_outpatient_delivery_cohort pharmacy_outpatient_delivery_cohort_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.pharmacy_outpatient_delivery_cohort
    ADD CONSTRAINT pharmacy_outpatient_delivery_cohort_pkey PRIMARY KEY (prescription_id);


--
-- Name: pharmacy_scheduler_lease pharmacy_scheduler_lease_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.pharmacy_scheduler_lease
    ADD CONSTRAINT pharmacy_scheduler_lease_pkey PRIMARY KEY (job_name);


--
-- Name: prescription_clearance_event prescription_clearance_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_clearance_event
    ADD CONSTRAINT prescription_clearance_event_pkey PRIMARY KEY (event_id);


--
-- Name: prescription_clearance prescription_clearance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_clearance
    ADD CONSTRAINT prescription_clearance_pkey PRIMARY KEY (clearance_id);


--
-- Name: prescription_clearance_target prescription_clearance_target_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_clearance_target
    ADD CONSTRAINT prescription_clearance_target_pkey PRIMARY KEY (prescription_id);


--
-- Name: prescription_line prescription_line_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_line
    ADD CONSTRAINT prescription_line_pkey PRIMARY KEY (line_id);


--
-- Name: prescription prescription_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT prescription_pkey PRIMARY KEY (prescription_id);


--
-- Name: processed_event processed_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id);


--
-- Name: stock_adjustment stock_adjustment_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.stock_adjustment
    ADD CONSTRAINT stock_adjustment_pkey PRIMARY KEY (adjustment_id);


--
-- Name: stock_reservation stock_reservation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.stock_reservation
    ADD CONSTRAINT stock_reservation_pkey PRIMARY KEY (reservation_id);


--
-- Name: stock_reservation uk_reservation_prescription_drug; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.stock_reservation
    ADD CONSTRAINT uk_reservation_prescription_drug UNIQUE (prescription_id, drug_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_clearance_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_clearance_pending ON public.prescription_clearance USING btree (prescription_id) WHERE ((target_status)::text = 'PENDING'::text);


--
-- Name: idx_clearance_target; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_clearance_target ON public.prescription_clearance USING btree (prescription_id, clearance_id);


--
-- Name: idx_dispense_status; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_dispense_status ON public.dispense_slip USING btree (status);


--
-- Name: idx_drug_name; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_drug_name ON public.drug USING btree (drug_name);


--
-- Name: idx_line_prescription; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_line_prescription ON public.prescription_line USING btree (prescription_id);


--
-- Name: idx_payment_receipt_prescription_status; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_payment_receipt_prescription_status ON public.payment_receipt USING btree (prescription_id, status);


--
-- Name: idx_pharmacy_outbox_aggregate_order; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_pharmacy_outbox_aggregate_order ON public.pharmacy_event_outbox USING btree (aggregate_id, created_at, event_id) WHERE ((published_at IS NULL) AND (quarantined_at IS NULL));


--
-- Name: idx_pharmacy_outbox_claimable; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_pharmacy_outbox_claimable ON public.pharmacy_event_outbox USING btree (available_at, created_at) WHERE (published_at IS NULL);


--
-- Name: idx_pharmacy_outbox_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_pharmacy_outbox_pending ON public.pharmacy_event_outbox USING btree (created_at) WHERE (published_at IS NULL);


--
-- Name: idx_pharmacy_outbox_quarantined; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_pharmacy_outbox_quarantined ON public.pharmacy_event_outbox USING btree (created_at) WHERE ((published_at IS NULL) AND (quarantined_at IS NULL));


--
-- Name: idx_pharmacy_scheduler_lease_until; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_pharmacy_scheduler_lease_until ON public.pharmacy_scheduler_lease USING btree (lease_until);


--
-- Name: idx_prescription_admission; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_admission ON public.prescription USING btree (admission_id) WHERE (admission_id IS NOT NULL);


--
-- Name: idx_prescription_dept; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_dept ON public.prescription USING btree (department_id);


--
-- Name: idx_prescription_episode; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_episode ON public.prescription USING btree (care_episode_type, care_episode_id);


--
-- Name: idx_prescription_patient; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_patient ON public.prescription USING btree (patient_id);


--
-- Name: idx_prescription_status; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_prescription_status ON public.prescription USING btree (status);


--
-- Name: idx_reservation_drug; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_reservation_drug ON public.stock_reservation USING btree (drug_id);


--
-- Name: idx_reservation_prescription; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_reservation_prescription ON public.stock_reservation USING btree (prescription_id);


--
-- Name: idx_reservation_status_expiry; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_reservation_status_expiry ON public.stock_reservation USING btree (status, expires_at);


--
-- Name: idx_stock_adjustment_drug_time; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_stock_adjustment_drug_time ON public.stock_adjustment USING btree (drug_id, occurred_at DESC);


--
-- Name: uq_outbox_care_lifecycle; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_outbox_care_lifecycle ON public.pharmacy_event_outbox USING btree (aggregate_id, care_lifecycle_order) WHERE (care_contract_version = 1);


--
-- Name: pharmacy_event_outbox pharmacy_care_delivery_cohort_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER pharmacy_care_delivery_cohort_immutable BEFORE UPDATE ON public.pharmacy_event_outbox FOR EACH ROW EXECUTE FUNCTION public.prevent_historical_care_delivery_activation();


--
-- Name: care_prescription_creation care_prescription_creation_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.care_prescription_creation
    ADD CONSTRAINT care_prescription_creation_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription(prescription_id);


--
-- Name: dispense_slip dispense_slip_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.dispense_slip
    ADD CONSTRAINT dispense_slip_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription(prescription_id);


--
-- Name: pharmacy_event_outbox pharmacy_event_outbox_care_delivery_cohort_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.pharmacy_event_outbox
    ADD CONSTRAINT pharmacy_event_outbox_care_delivery_cohort_id_fkey FOREIGN KEY (care_delivery_cohort_id) REFERENCES public.pharmacy_outpatient_delivery_cohort(prescription_id);


--
-- Name: pharmacy_outpatient_delivery_cohort pharmacy_outpatient_delivery_cohort_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.pharmacy_outpatient_delivery_cohort
    ADD CONSTRAINT pharmacy_outpatient_delivery_cohort_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription(prescription_id);


--
-- Name: prescription_clearance prescription_clearance_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_clearance
    ADD CONSTRAINT prescription_clearance_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription_clearance_target(prescription_id);


--
-- Name: prescription_line prescription_line_drug_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_line
    ADD CONSTRAINT prescription_line_drug_id_fkey FOREIGN KEY (drug_id) REFERENCES public.drug(drug_id);


--
-- Name: prescription_line prescription_line_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_line
    ADD CONSTRAINT prescription_line_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription(prescription_id) ON DELETE CASCADE;


--
-- Name: stock_reservation stock_reservation_drug_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.stock_reservation
    ADD CONSTRAINT stock_reservation_drug_id_fkey FOREIGN KEY (drug_id) REFERENCES public.drug(drug_id);


--
-- Name: stock_reservation stock_reservation_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.stock_reservation
    ADD CONSTRAINT stock_reservation_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescription(prescription_id);


--
-- PostgreSQL database dump complete
--

\unrestrict vNIlYtp0xeR3XcKVONOCFsFCTb35E8I6Vnx8DuHChLWklMjearcTwhUN8vmkW38

--
-- Database "mediflow_report" dump
--

--
-- PostgreSQL database dump
--

\restrict Gyri9NoKGnnyWjLlWYYO2REOWMioidFV1oZu4FMzGWfArIcEFol1HWhrvLQAZay

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mediflow_report; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE mediflow_report WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE mediflow_report OWNER TO test;

\unrestrict Gyri9NoKGnnyWjLlWYYO2REOWMioidFV1oZu4FMzGWfArIcEFol1HWhrvLQAZay
\connect mediflow_report
\restrict Gyri9NoKGnnyWjLlWYYO2REOWMioidFV1oZu4FMzGWfArIcEFol1HWhrvLQAZay

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: fence_financial_source_rebuild_mutation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.fence_financial_source_rebuild_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE target_generation UUID;
BEGIN
    IF TG_TABLE_NAME='financial_source_rebuild' THEN
        IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Frozen financial generation cannot be deleted'; END IF;
        IF OLD.status<>'BUILDING' THEN RAISE EXCEPTION 'Terminal financial rebuild is immutable'; END IF;
        IF OLD.manifest_sealed AND NOT NEW.manifest_sealed THEN RAISE EXCEPTION 'Financial manifest cannot be reopened'; END IF;
        IF ROW(OLD.generation_id,OLD.snapshot_version,OLD.projector_version,OLD.covered_from,OLD.covered_to,
            OLD.report_zone,OLD.source_observed_at,OLD.source_inputs,OLD.unresolved_inputs,OLD.manifest_fingerprint)
            IS DISTINCT FROM ROW(NEW.generation_id,NEW.snapshot_version,NEW.projector_version,NEW.covered_from,NEW.covered_to,
            NEW.report_zone,NEW.source_observed_at,NEW.source_inputs,NEW.unresolved_inputs,NEW.manifest_fingerprint) THEN
            RAISE EXCEPTION 'Frozen financial manifest metadata is immutable';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_TABLE_NAME='financial_source_rebuild_input' AND TG_OP<>'INSERT' THEN
        RAISE EXCEPTION 'Frozen financial source is immutable';
    END IF;
    IF TG_OP<>'INSERT' THEN
        target_generation=OLD.generation_id;
        PERFORM 1 FROM financial_source_rebuild WHERE generation_id=target_generation AND status='BUILDING' FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Terminal financial rebuild is immutable'; END IF;
    END IF;
    IF TG_OP<>'DELETE' THEN
        PERFORM 1 FROM financial_source_rebuild WHERE generation_id=NEW.generation_id AND status='BUILDING'
            AND (TG_TABLE_NAME<>'financial_source_rebuild_input' OR NOT manifest_sealed) FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Terminal financial rebuild is immutable'; END IF;
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;


ALTER FUNCTION public.fence_financial_source_rebuild_mutation() OWNER TO test;

--
-- Name: guard_accepted_operational_generation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.guard_accepted_operational_generation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM operational_generation_acceptance WHERE generation_id=
            CASE WHEN TG_OP='DELETE' THEN OLD.generation_id ELSE NEW.generation_id END)
        OR (TG_OP='UPDATE' AND EXISTS(SELECT 1 FROM operational_generation_acceptance WHERE generation_id=OLD.generation_id)) THEN
        RAISE EXCEPTION 'Accepted operational generation is immutable';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.guard_accepted_operational_generation() OWNER TO test;

--
-- Name: reject_financial_archive_restore_mutation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.reject_financial_archive_restore_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Financial archive restore provenance is immutable';
END;
$$;


ALTER FUNCTION public.reject_financial_archive_restore_mutation() OWNER TO test;

--
-- Name: reject_financial_publication_audit_mutation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.reject_financial_publication_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Financial publication evidence is immutable'; END;
$$;


ALTER FUNCTION public.reject_financial_publication_audit_mutation() OWNER TO test;

--
-- Name: reject_operational_publication_evidence_mutation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.reject_operational_publication_evidence_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Operational publication evidence is immutable'; END;
$$;


ALTER FUNCTION public.reject_operational_publication_evidence_mutation() OWNER TO test;

--
-- Name: reject_verified_financial_snapshot_mutation(); Type: FUNCTION; Schema: public; Owner: test
--

CREATE FUNCTION public.reject_verified_financial_snapshot_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE target_generation UUID;
BEGIN
    IF TG_TABLE_NAME='financial_report_generation' THEN
        IF OLD.status='VERIFIED' THEN RAISE EXCEPTION 'Verified financial generation is immutable'; END IF;
        IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
    END IF;
    IF TG_OP<>'INSERT' THEN
        target_generation=OLD.generation_id;
        PERFORM 1 FROM financial_report_generation WHERE generation_id=target_generation AND status<>'VERIFIED' FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Verified financial snapshot is immutable'; END IF;
    END IF;
    IF TG_OP<>'DELETE' THEN
        PERFORM 1 FROM financial_report_generation WHERE generation_id=NEW.generation_id AND status<>'VERIFIED' FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Verified financial snapshot is immutable'; END IF;
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;


ALTER FUNCTION public.reject_verified_financial_snapshot_mutation() OWNER TO test;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: cash_replay_generation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.cash_replay_generation (
    generation_id uuid NOT NULL,
    status character varying(12) NOT NULL,
    snapshot_version integer DEFAULT 1 NOT NULL,
    projector_version integer DEFAULT 1 NOT NULL,
    source_receipts bigint DEFAULT 0 NOT NULL,
    applied_receipts bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT cash_replay_generation_check CHECK (((applied_receipts >= 0) AND (applied_receipts <= source_receipts))),
    CONSTRAINT cash_replay_generation_check1 CHECK ((((status)::text = 'BUILDING'::text) = (completed_at IS NULL))),
    CONSTRAINT cash_replay_generation_check2 CHECK ((((status)::text <> 'VERIFIED'::text) OR (applied_receipts = source_receipts))),
    CONSTRAINT cash_replay_generation_projector_version_check CHECK ((projector_version > 0)),
    CONSTRAINT cash_replay_generation_snapshot_version_check CHECK ((snapshot_version > 0)),
    CONSTRAINT cash_replay_generation_source_receipts_check CHECK ((source_receipts >= 0)),
    CONSTRAINT cash_replay_generation_status_check CHECK (((status)::text = ANY (ARRAY[('BUILDING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text])))
);


ALTER TABLE public.cash_replay_generation OWNER TO test;

--
-- Name: cash_replay_input; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.cash_replay_input (
    generation_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    fact_snapshot jsonb NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    payload_fingerprint character varying(64) NOT NULL,
    first_envelope_fingerprint character varying(64) NOT NULL,
    snapshot_version integer NOT NULL,
    projector_version integer NOT NULL,
    applied boolean DEFAULT false NOT NULL,
    CONSTRAINT cash_replay_input_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT cash_replay_input_fact_snapshot_check CHECK ((jsonb_typeof(fact_snapshot) = 'object'::text)),
    CONSTRAINT cash_replay_input_first_envelope_fingerprint_check CHECK (((first_envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT cash_replay_input_payload_fingerprint_check CHECK (((payload_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT cash_replay_input_projector_version_check CHECK ((projector_version > 0)),
    CONSTRAINT cash_replay_input_snapshot_version_check CHECK ((snapshot_version > 0))
);


ALTER TABLE public.cash_replay_input OWNER TO test;

--
-- Name: cash_replay_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.cash_replay_receipt (
    generation_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    fact_snapshot jsonb NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    payload_fingerprint character varying(64) NOT NULL,
    first_envelope_fingerprint character varying(64) NOT NULL,
    CONSTRAINT cash_replay_receipt_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT cash_replay_receipt_fact_snapshot_check CHECK ((jsonb_typeof(fact_snapshot) = 'object'::text)),
    CONSTRAINT cash_replay_receipt_first_envelope_fingerprint_check CHECK (((first_envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT cash_replay_receipt_payload_fingerprint_check CHECK (((payload_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.cash_replay_receipt OWNER TO test;

--
-- Name: cash_replay_scope; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.cash_replay_scope (
    generation_id uuid NOT NULL,
    business_date date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    classification character varying(32) NOT NULL,
    gross_receipts numeric(19,2) NOT NULL,
    receipt_count bigint NOT NULL,
    CONSTRAINT cash_replay_scope_classification_check CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text]))),
    CONSTRAINT cash_replay_scope_currency_check CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT cash_replay_scope_gross_receipts_check CHECK ((gross_receipts > (0)::numeric)),
    CONSTRAINT cash_replay_scope_receipt_count_check CHECK ((receipt_count > 0)),
    CONSTRAINT cash_replay_scope_report_zone_check CHECK ((btrim((report_zone)::text) <> ''::text))
);


ALTER TABLE public.cash_replay_scope OWNER TO test;

--
-- Name: daily_financial_report; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.daily_financial_report (
    report_id uuid NOT NULL,
    report_date date NOT NULL,
    department_id uuid,
    cash_received numeric(19,2) DEFAULT 0 NOT NULL,
    deposit_liability numeric(19,2) DEFAULT 0 NOT NULL,
    earned_revenue numeric(19,2) DEFAULT 0 NOT NULL,
    refunds numeric(19,2) DEFAULT 0 NOT NULL,
    outstanding_receivable numeric(19,2) DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.daily_financial_report OWNER TO test;

--
-- Name: daily_operational_report; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.daily_operational_report (
    report_id uuid NOT NULL,
    report_date date NOT NULL,
    department_id uuid,
    completed_visits bigint DEFAULT 0 NOT NULL,
    admissions bigint DEFAULT 0 NOT NULL,
    discharges bigint DEFAULT 0 NOT NULL,
    inpatient_days bigint DEFAULT 0 NOT NULL,
    surgeries_completed bigint DEFAULT 0 NOT NULL,
    surgeries_cancelled bigint DEFAULT 0 NOT NULL,
    surgery_duration_minutes bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lab_tests bigint DEFAULT 0 NOT NULL,
    dispensed_prescriptions bigint DEFAULT 0 NOT NULL,
    dispensed_units bigint DEFAULT 0 NOT NULL,
    medical_los_minutes bigint DEFAULT 0 NOT NULL,
    medical_discharges bigint DEFAULT 0 NOT NULL,
    completed_stay_days bigint DEFAULT 0 NOT NULL,
    CONSTRAINT daily_operational_report_completed_stay_days_check CHECK ((completed_stay_days >= 0)),
    CONSTRAINT daily_operational_report_medical_discharges_check CHECK ((medical_discharges >= 0)),
    CONSTRAINT daily_operational_report_medical_los_minutes_check CHECK ((medical_los_minutes >= 0))
);


ALTER TABLE public.daily_operational_report OWNER TO test;

--
-- Name: daily_visit_report; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.daily_visit_report (
    report_id uuid NOT NULL,
    report_date date NOT NULL,
    department_id uuid,
    visit_count integer DEFAULT 0 NOT NULL,
    lab_count integer DEFAULT 0 NOT NULL,
    prescription_count integer DEFAULT 0 NOT NULL,
    revenue numeric(15,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_daily_lab_count_non_negative CHECK ((lab_count >= 0)),
    CONSTRAINT ck_daily_prescription_count_non_negative CHECK ((prescription_count >= 0)),
    CONSTRAINT ck_daily_revenue_non_negative CHECK ((revenue >= (0)::numeric)),
    CONSTRAINT ck_daily_visit_count_non_negative CHECK ((visit_count >= 0))
);


ALTER TABLE public.daily_visit_report OWNER TO test;

--
-- Name: drug_statistic; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.drug_statistic (
    statistic_id uuid NOT NULL,
    drug_id uuid NOT NULL,
    drug_name character varying(150) NOT NULL,
    report_date date NOT NULL,
    department_id uuid,
    dispensed_quantity integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_drug_name_not_blank CHECK ((btrim((drug_name)::text) <> ''::text)),
    CONSTRAINT ck_drug_quantity_non_negative CHECK ((dispensed_quantity >= 0))
);


ALTER TABLE public.drug_statistic OWNER TO test;

--
-- Name: financial_archive_restore; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_archive_restore (
    archive_fingerprint character varying(64) NOT NULL,
    origin_generation_id uuid NOT NULL,
    generation_id uuid NOT NULL,
    actor_account_id uuid NOT NULL,
    restored_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT financial_archive_restore_archive_fingerprint_check CHECK (((archive_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.financial_archive_restore OWNER TO test;

--
-- Name: financial_contribution; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_contribution (
    contribution_id uuid NOT NULL,
    event_id uuid NOT NULL,
    source_type character varying(40) NOT NULL,
    source_id uuid NOT NULL,
    source_revision integer NOT NULL,
    account_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    contribution_type character varying(32) NOT NULL,
    cash_amount numeric(19,2) DEFAULT 0 NOT NULL,
    liability_amount numeric(19,2) DEFAULT 0 NOT NULL,
    revenue_amount numeric(19,2) DEFAULT 0 NOT NULL,
    refund_amount numeric(19,2) DEFAULT 0 NOT NULL,
    receivable_amount numeric(19,2) DEFAULT 0 NOT NULL,
    original_contribution_id uuid,
    business_date date NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_financial_contribution_episode_type CHECK (((care_episode_type)::text = ANY (ARRAY[('OUTPATIENT_VISIT'::character varying)::text, ('ADMISSION'::character varying)::text]))),
    CONSTRAINT ck_financial_contribution_revision CHECK ((source_revision > 0)),
    CONSTRAINT ck_financial_contribution_source_type CHECK ((btrim((source_type)::text) <> ''::text)),
    CONSTRAINT ck_financial_contribution_type CHECK (((contribution_type)::text = ANY (ARRAY[('PAYMENT'::character varying)::text, ('DEPOSIT'::character varying)::text, ('REVENUE_RECOGNITION'::character varying)::text, ('REFUND'::character varying)::text, ('RECEIVABLE'::character varying)::text, ('REVERSAL'::character varying)::text])))
);


ALTER TABLE public.financial_contribution OWNER TO test;

--
-- Name: financial_generation_acceptance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_generation_acceptance (
    generation_id uuid NOT NULL,
    manifest_fingerprint character varying(64) NOT NULL,
    history_policy character varying(40) NOT NULL,
    acceptance_reference character varying(120) NOT NULL,
    actor_account_id uuid NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT financial_generation_acceptance_acceptance_reference_check CHECK (((acceptance_reference)::text ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'::text)),
    CONSTRAINT financial_generation_acceptance_history_policy_check CHECK (((history_policy)::text = 'AVAILABLE_EVIDENCE_ONLY'::text)),
    CONSTRAINT financial_generation_acceptance_manifest_fingerprint_check CHECK (((manifest_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.financial_generation_acceptance OWNER TO test;

--
-- Name: financial_publication_audit; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_publication_audit (
    command_id uuid NOT NULL,
    generation_id uuid NOT NULL,
    previous_generation_id uuid,
    action character varying(12) NOT NULL,
    request_fingerprint character varying(64) NOT NULL,
    acceptance_reference character varying(120) NOT NULL,
    actor_account_id uuid NOT NULL,
    published_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT financial_publication_audit_acceptance_reference_check CHECK (((acceptance_reference)::text ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'::text)),
    CONSTRAINT financial_publication_audit_action_check CHECK (((action)::text = ANY (ARRAY[('PUBLISH'::character varying)::text, ('ROLLBACK'::character varying)::text]))),
    CONSTRAINT financial_publication_audit_request_fingerprint_check CHECK (((request_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.financial_publication_audit OWNER TO test;

--
-- Name: financial_report_day; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_report_day (
    generation_id uuid NOT NULL,
    business_date date NOT NULL,
    department_id uuid,
    cash_received numeric(19,2) NOT NULL,
    deposit_liability numeric(19,2) NOT NULL,
    earned_revenue numeric(19,2) NOT NULL,
    refunds numeric(19,2) NOT NULL,
    outstanding_receivable numeric(19,2) NOT NULL,
    CONSTRAINT financial_report_day_cash_received_check CHECK ((cash_received >= (0)::numeric)),
    CONSTRAINT financial_report_day_deposit_liability_check CHECK ((deposit_liability >= (0)::numeric)),
    CONSTRAINT financial_report_day_earned_revenue_check CHECK ((earned_revenue >= (0)::numeric)),
    CONSTRAINT financial_report_day_outstanding_receivable_check CHECK ((outstanding_receivable >= (0)::numeric)),
    CONSTRAINT financial_report_day_refunds_check CHECK ((refunds >= (0)::numeric))
);


ALTER TABLE public.financial_report_day OWNER TO test;

--
-- Name: financial_report_generation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_report_generation (
    generation_id uuid NOT NULL,
    status character varying(12) NOT NULL,
    snapshot_version integer NOT NULL,
    projector_version integer NOT NULL,
    covered_from date NOT NULL,
    covered_to date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    source_observed_at timestamp with time zone NOT NULL,
    reconciled_at timestamp with time zone,
    source_inputs bigint NOT NULL,
    applied_inputs bigint NOT NULL,
    unresolved_inputs bigint NOT NULL,
    manifest_fingerprint character varying(64) NOT NULL,
    aggregate_fingerprint character varying(64),
    CONSTRAINT financial_report_generation_aggregate_fingerprint_check CHECK (((aggregate_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_report_generation_check CHECK (((covered_to >= covered_from) AND (covered_to <= '2100-12-31'::date))),
    CONSTRAINT financial_report_generation_check1 CHECK (((applied_inputs >= 0) AND (applied_inputs <= source_inputs))),
    CONSTRAINT financial_report_generation_check2 CHECK (((reconciled_at IS NULL) OR (reconciled_at >= source_observed_at))),
    CONSTRAINT financial_report_generation_check3 CHECK ((((status)::text <> 'VERIFIED'::text) OR ((reconciled_at IS NOT NULL) AND (aggregate_fingerprint IS NOT NULL) AND (applied_inputs = source_inputs) AND (unresolved_inputs = 0)))),
    CONSTRAINT financial_report_generation_covered_from_check CHECK ((covered_from >= '2000-01-01'::date)),
    CONSTRAINT financial_report_generation_currency_check CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT financial_report_generation_manifest_fingerprint_check CHECK (((manifest_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_report_generation_projector_version_check CHECK ((projector_version > 0)),
    CONSTRAINT financial_report_generation_report_zone_check CHECK ((length(btrim((report_zone)::text)) > 0)),
    CONSTRAINT financial_report_generation_snapshot_version_check CHECK ((snapshot_version > 0)),
    CONSTRAINT financial_report_generation_source_inputs_check CHECK ((source_inputs >= 0)),
    CONSTRAINT financial_report_generation_status_check CHECK (((status)::text = ANY (ARRAY[('BUILDING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT financial_report_generation_unresolved_inputs_check CHECK ((unresolved_inputs >= 0))
);


ALTER TABLE public.financial_report_generation OWNER TO test;

--
-- Name: financial_report_publication; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_report_publication (
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    generation_id uuid NOT NULL,
    acceptance_reference character varying(120) NOT NULL,
    published_at timestamp with time zone NOT NULL,
    CONSTRAINT financial_report_publication_acceptance_reference_check CHECK ((length(btrim((acceptance_reference)::text)) > 0))
);


ALTER TABLE public.financial_report_publication OWNER TO test;

--
-- Name: financial_source_rebuild; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_source_rebuild (
    generation_id uuid NOT NULL,
    status character varying(12) DEFAULT 'BUILDING'::character varying NOT NULL,
    snapshot_version integer DEFAULT 1 NOT NULL,
    projector_version integer DEFAULT 1 NOT NULL,
    covered_from date NOT NULL,
    covered_to date NOT NULL,
    report_zone character varying(80) NOT NULL,
    source_observed_at timestamp with time zone DEFAULT now() NOT NULL,
    source_inputs integer NOT NULL,
    unresolved_inputs integer NOT NULL,
    processed_days integer DEFAULT 0 NOT NULL,
    manifest_fingerprint character varying(64) NOT NULL,
    manifest_sealed boolean DEFAULT false NOT NULL,
    aggregate_fingerprint character varying(64),
    reason_code character varying(80),
    reconciled_at timestamp with time zone,
    CONSTRAINT financial_source_rebuild_aggregate_fingerprint_check CHECK (((aggregate_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_source_rebuild_check CHECK (((covered_to >= covered_from) AND (covered_to <= '2100-12-31'::date) AND ((covered_to - covered_from) < 366))),
    CONSTRAINT financial_source_rebuild_check1 CHECK (((unresolved_inputs >= 0) AND (unresolved_inputs <= source_inputs))),
    CONSTRAINT financial_source_rebuild_check2 CHECK (((processed_days >= 0) AND (processed_days <= ((covered_to - covered_from) + 1)))),
    CONSTRAINT financial_source_rebuild_check3 CHECK ((((status)::text = 'FAILED'::text) = (reason_code IS NOT NULL))),
    CONSTRAINT financial_source_rebuild_check4 CHECK ((((status)::text <> 'VERIFIED'::text) OR (manifest_sealed AND (unresolved_inputs = 0) AND (processed_days = ((covered_to - covered_from) + 1)) AND (aggregate_fingerprint IS NOT NULL) AND (reconciled_at IS NOT NULL) AND (reconciled_at >= source_observed_at)))),
    CONSTRAINT financial_source_rebuild_covered_from_check CHECK ((covered_from >= '2000-01-01'::date)),
    CONSTRAINT financial_source_rebuild_manifest_fingerprint_check CHECK (((manifest_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_source_rebuild_projector_version_check CHECK ((projector_version = 1)),
    CONSTRAINT financial_source_rebuild_report_zone_check CHECK ((length(btrim((report_zone)::text)) > 0)),
    CONSTRAINT financial_source_rebuild_snapshot_version_check CHECK ((snapshot_version = 1)),
    CONSTRAINT financial_source_rebuild_source_inputs_check CHECK (((source_inputs >= 0) AND (source_inputs <= 10000))),
    CONSTRAINT financial_source_rebuild_status_check CHECK (((status)::text = ANY (ARRAY[('BUILDING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text])))
);


ALTER TABLE public.financial_source_rebuild OWNER TO test;

--
-- Name: financial_source_rebuild_day; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_source_rebuild_day (
    generation_id uuid NOT NULL,
    business_date date NOT NULL,
    department_id uuid,
    cash_received numeric(19,2) NOT NULL,
    deposit_liability numeric(19,2) NOT NULL,
    earned_revenue numeric(19,2) NOT NULL,
    refunds numeric(19,2) NOT NULL,
    outstanding_receivable numeric(19,2) NOT NULL,
    CONSTRAINT financial_source_rebuild_day_cash_received_check CHECK ((cash_received >= (0)::numeric)),
    CONSTRAINT financial_source_rebuild_day_deposit_liability_check CHECK ((deposit_liability >= (0)::numeric)),
    CONSTRAINT financial_source_rebuild_day_earned_revenue_check CHECK ((earned_revenue >= (0)::numeric)),
    CONSTRAINT financial_source_rebuild_day_outstanding_receivable_check CHECK ((outstanding_receivable >= (0)::numeric)),
    CONSTRAINT financial_source_rebuild_day_refunds_check CHECK ((refunds >= (0)::numeric))
);


ALTER TABLE public.financial_source_rebuild_day OWNER TO test;

--
-- Name: financial_source_rebuild_input; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.financial_source_rebuild_input (
    generation_id uuid NOT NULL,
    source_kind character varying(16) NOT NULL,
    source_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    source_state character varying(16) NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    envelope_fingerprint character varying(64) NOT NULL,
    fact_snapshot text NOT NULL,
    snapshot_fingerprint character varying(64) NOT NULL,
    CONSTRAINT financial_source_rebuild_input_envelope_fingerprint_check CHECK (((envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_source_rebuild_input_fact_snapshot_check CHECK ((octet_length(fact_snapshot) <= 1048576)),
    CONSTRAINT financial_source_rebuild_input_snapshot_fingerprint_check CHECK (((snapshot_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_source_rebuild_input_source_fingerprint_check CHECK (((source_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT financial_source_rebuild_input_source_kind_check CHECK (((source_kind)::text = ANY (ARRAY[('RECEIPT'::character varying)::text, ('REFUND'::character varying)::text, ('FINAL'::character varying)::text, ('BALANCE'::character varying)::text]))),
    CONSTRAINT financial_source_rebuild_input_source_state_check CHECK (((source_state)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.financial_source_rebuild_input OWNER TO test;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO test;

--
-- Name: monthly_revenue_report; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.monthly_revenue_report (
    report_id uuid NOT NULL,
    month integer NOT NULL,
    year integer NOT NULL,
    department_id uuid,
    total_revenue numeric(15,2) DEFAULT 0 NOT NULL,
    invoice_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_monthly_invoice_count_non_negative CHECK ((invoice_count >= 0)),
    CONSTRAINT ck_monthly_month CHECK (((month >= 1) AND (month <= 12))),
    CONSTRAINT ck_monthly_revenue_non_negative CHECK ((total_revenue >= (0)::numeric)),
    CONSTRAINT ck_monthly_year_positive CHECK ((year > 0))
);


ALTER TABLE public.monthly_revenue_report OWNER TO test;

--
-- Name: operational_contribution; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_contribution (
    contribution_id uuid NOT NULL,
    event_id uuid NOT NULL,
    source_type character varying(40) NOT NULL,
    source_id uuid NOT NULL,
    source_revision integer NOT NULL,
    metric_type character varying(40) NOT NULL,
    department_id uuid,
    care_episode_type character varying(32),
    care_episode_id uuid,
    metric_date date NOT NULL,
    numeric_value numeric(19,3) DEFAULT 1 NOT NULL,
    category character varying(80),
    occurred_at timestamp with time zone NOT NULL,
    fact_fingerprint character varying(64),
    CONSTRAINT ck_operational_contribution_category CHECK (((category IS NULL) OR (btrim((category)::text) <> ''::text))),
    CONSTRAINT ck_operational_contribution_episode_pair CHECK ((((care_episode_type IS NULL) AND (care_episode_id IS NULL)) OR ((care_episode_type IS NOT NULL) AND ((care_episode_type)::text = ANY (ARRAY[('OUTPATIENT_VISIT'::character varying)::text, ('ADMISSION'::character varying)::text])) AND (care_episode_id IS NOT NULL)))),
    CONSTRAINT ck_operational_contribution_metric_type CHECK ((btrim((metric_type)::text) <> ''::text)),
    CONSTRAINT ck_operational_contribution_revision CHECK ((source_revision > 0)),
    CONSTRAINT ck_operational_contribution_source_type CHECK ((btrim((source_type)::text) <> ''::text)),
    CONSTRAINT ck_operational_fact_fingerprint CHECK (((fact_fingerprint IS NULL) OR ((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)))
);


ALTER TABLE public.operational_contribution OWNER TO test;

--
-- Name: operational_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_delivery (
    event_id uuid NOT NULL,
    metric_type character varying(40) NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    CONSTRAINT operational_delivery_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.operational_delivery OWNER TO test;

--
-- Name: operational_event_journal; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_event_journal (
    event_id uuid NOT NULL,
    event_type character varying(80) NOT NULL,
    event_snapshot jsonb NOT NULL,
    contribution_snapshot jsonb NOT NULL,
    envelope_fingerprint character varying(64) NOT NULL,
    projection_fingerprint character varying(64) NOT NULL,
    projector_version integer DEFAULT 1 NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT operational_event_journal_envelope_fingerprint_check CHECK (((envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT operational_event_journal_projection_fingerprint_check CHECK (((projection_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT operational_event_journal_projector_version_check CHECK ((projector_version > 0))
);


ALTER TABLE public.operational_event_journal OWNER TO test;

--
-- Name: operational_generation_acceptance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_generation_acceptance (
    generation_id uuid NOT NULL,
    report_kind character varying(12) NOT NULL,
    manifest_fingerprint character(64) NOT NULL,
    covered_from date NOT NULL,
    covered_to date NOT NULL,
    zone_id character varying(80) NOT NULL,
    history_policy character varying(40) NOT NULL,
    acceptance_reference character varying(120) NOT NULL,
    actor_account_id uuid NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT operational_generation_acceptance_acceptance_reference_check CHECK (((acceptance_reference)::text ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'::text)),
    CONSTRAINT operational_generation_acceptance_check CHECK (((covered_to <= '2100-12-31'::date) AND (covered_to >= covered_from) AND ((covered_to - covered_from) < 366))),
    CONSTRAINT operational_generation_acceptance_covered_from_check CHECK ((covered_from >= '2000-01-01'::date)),
    CONSTRAINT operational_generation_acceptance_history_policy_check CHECK (((history_policy)::text = 'AVAILABLE_EVIDENCE_ONLY'::text)),
    CONSTRAINT operational_generation_acceptance_manifest_fingerprint_check CHECK ((manifest_fingerprint ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT operational_generation_acceptance_report_kind_check CHECK (((report_kind)::text = 'SURGERY'::text)),
    CONSTRAINT operational_generation_acceptance_zone_id_check CHECK ((btrim((zone_id)::text) <> ''::text))
);


ALTER TABLE public.operational_generation_acceptance OWNER TO test;

--
-- Name: operational_publication_audit; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_publication_audit (
    command_id uuid NOT NULL,
    generation_id uuid NOT NULL,
    previous_generation_id uuid,
    action character varying(12) NOT NULL,
    request_fingerprint character(64) NOT NULL,
    acceptance_reference character varying(120) NOT NULL,
    actor_account_id uuid NOT NULL,
    published_at timestamp with time zone NOT NULL,
    CONSTRAINT operational_publication_audit_acceptance_reference_check CHECK (((acceptance_reference)::text ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'::text)),
    CONSTRAINT operational_publication_audit_action_check CHECK (((action)::text = ANY (ARRAY[('PUBLISH'::character varying)::text, ('ROLLBACK'::character varying)::text]))),
    CONSTRAINT operational_publication_audit_request_fingerprint_check CHECK ((request_fingerprint ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.operational_publication_audit OWNER TO test;

--
-- Name: operational_replay_contribution; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_replay_contribution (
    generation_id uuid NOT NULL,
    source_type character varying(40) NOT NULL,
    source_id uuid NOT NULL,
    source_revision integer NOT NULL,
    metric_type character varying(40) NOT NULL,
    fact_fingerprint character(64) NOT NULL,
    fact_snapshot jsonb NOT NULL,
    CONSTRAINT operational_replay_contribution_fact_snapshot_check CHECK ((jsonb_typeof(fact_snapshot) = 'object'::text)),
    CONSTRAINT operational_replay_contribution_source_revision_check CHECK ((source_revision = 1))
);


ALTER TABLE public.operational_replay_contribution OWNER TO test;

--
-- Name: operational_replay_generation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_replay_generation (
    generation_id uuid NOT NULL,
    status character varying(12) NOT NULL,
    projector_version integer DEFAULT 1 NOT NULL,
    source_events bigint DEFAULT 0 NOT NULL,
    applied_events bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT operational_replay_generation_check CHECK (((applied_events >= 0) AND (applied_events <= source_events))),
    CONSTRAINT operational_replay_generation_check1 CHECK ((((status)::text = 'BUILDING'::text) = (completed_at IS NULL))),
    CONSTRAINT operational_replay_generation_projector_version_check CHECK ((projector_version = 1)),
    CONSTRAINT operational_replay_generation_source_events_check CHECK ((source_events >= 0)),
    CONSTRAINT operational_replay_generation_status_check CHECK (((status)::text = ANY (ARRAY[('BUILDING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text])))
);


ALTER TABLE public.operational_replay_generation OWNER TO test;

--
-- Name: operational_replay_input; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_replay_input (
    generation_id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_snapshot jsonb NOT NULL,
    contribution_snapshot jsonb NOT NULL,
    envelope_fingerprint character(64) NOT NULL,
    projection_fingerprint character(64) NOT NULL,
    projector_version integer NOT NULL,
    applied boolean DEFAULT false NOT NULL,
    CONSTRAINT operational_replay_input_contribution_snapshot_check CHECK ((jsonb_typeof(contribution_snapshot) = 'array'::text)),
    CONSTRAINT operational_replay_input_event_snapshot_check CHECK ((jsonb_typeof(event_snapshot) = 'object'::text)),
    CONSTRAINT operational_replay_input_projector_version_check CHECK ((projector_version > 0))
);


ALTER TABLE public.operational_replay_input OWNER TO test;

--
-- Name: operational_replay_scope; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_replay_scope (
    generation_id uuid NOT NULL,
    metric_date date NOT NULL,
    department_id uuid,
    metric_type character varying(40) NOT NULL,
    numeric_value bigint NOT NULL,
    CONSTRAINT operational_replay_scope_numeric_value_check CHECK ((numeric_value >= 0))
);


ALTER TABLE public.operational_replay_scope OWNER TO test;

--
-- Name: operational_report_publication; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_report_publication (
    report_kind character varying(12) NOT NULL,
    generation_id uuid NOT NULL,
    covered_from date NOT NULL,
    covered_to date NOT NULL,
    zone_id character varying(80) NOT NULL,
    metrics character varying(40)[] NOT NULL,
    acceptance_reference character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT operational_report_publication_acceptance_reference_check CHECK ((length(btrim((acceptance_reference)::text)) > 0)),
    CONSTRAINT operational_report_publication_check CHECK ((covered_to >= covered_from)),
    CONSTRAINT operational_report_publication_metrics_check CHECK (((cardinality(metrics) > 0) AND (array_position(metrics, NULL::character varying) IS NULL))),
    CONSTRAINT operational_report_publication_report_kind_check CHECK (((report_kind)::text = ANY (ARRAY[('DAILY'::character varying)::text, ('SURGERY'::character varying)::text]))),
    CONSTRAINT operational_report_publication_zone_id_check CHECK ((length(btrim((zone_id)::text)) > 0))
);


ALTER TABLE public.operational_report_publication OWNER TO test;

--
-- Name: operational_source_snapshot; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.operational_source_snapshot (
    source_type character varying(40) NOT NULL,
    source_id uuid NOT NULL,
    source_revision integer NOT NULL,
    event_type character varying(100) NOT NULL,
    first_event_id uuid NOT NULL,
    payload_fingerprint character varying(64),
    evidence_state character varying(24) NOT NULL,
    CONSTRAINT operational_source_snapshot_check CHECK (((((evidence_state)::text = 'VERIFIED_PAYLOAD'::text) AND (payload_fingerprint IS NOT NULL) AND ((payload_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)) OR (((evidence_state)::text = 'LEGACY_UNVERIFIED'::text) AND (payload_fingerprint IS NULL)))),
    CONSTRAINT operational_source_snapshot_source_revision_check CHECK ((source_revision > 0))
);


ALTER TABLE public.operational_source_snapshot OWNER TO test;

--
-- Name: payment_contribution; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.payment_contribution (
    invoice_id uuid NOT NULL,
    completed_event_id uuid,
    failed_event_id uuid,
    payment_date date,
    department_id uuid,
    amount numeric(15,2),
    status character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT ck_payment_amount_positive CHECK (((amount IS NULL) OR (amount > (0)::numeric))),
    CONSTRAINT ck_payment_state_data CHECK (((((status)::text = 'PENDING_REVERSAL'::text) AND (failed_event_id IS NOT NULL) AND (completed_event_id IS NULL) AND (payment_date IS NULL) AND (department_id IS NULL) AND (amount IS NULL)) OR (((status)::text = 'APPLIED'::text) AND (completed_event_id IS NOT NULL) AND (failed_event_id IS NULL) AND (payment_date IS NOT NULL) AND (amount IS NOT NULL)) OR (((status)::text = 'REVERSED'::text) AND (completed_event_id IS NOT NULL) AND (failed_event_id IS NOT NULL) AND (payment_date IS NOT NULL) AND (amount IS NOT NULL)))),
    CONSTRAINT ck_payment_status CHECK (((status)::text = ANY (ARRAY[('PENDING_REVERSAL'::character varying)::text, ('APPLIED'::character varying)::text, ('REVERSED'::character varying)::text])))
);


ALTER TABLE public.payment_contribution OWNER TO test;

--
-- Name: prescription_fill_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.prescription_fill_receipt (
    prescription_id uuid NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT prescription_fill_receipt_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.prescription_fill_receipt OWNER TO test;

--
-- Name: processed_event; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    routing_key character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.processed_event OWNER TO test;

--
-- Name: refund_replay_fact; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.refund_replay_fact (
    generation_id uuid NOT NULL,
    refund_transaction_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    state character varying(16) NOT NULL,
    fact_snapshot jsonb NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    first_envelope_fingerprint character varying(64) NOT NULL,
    CONSTRAINT refund_replay_fact_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT refund_replay_fact_fact_snapshot_check CHECK ((jsonb_typeof(fact_snapshot) = 'object'::text)),
    CONSTRAINT refund_replay_fact_first_envelope_fingerprint_check CHECK (((first_envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT refund_replay_fact_source_fingerprint_check CHECK (((source_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT refund_replay_fact_state_check CHECK (((state)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.refund_replay_fact OWNER TO test;

--
-- Name: refund_replay_generation; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.refund_replay_generation (
    generation_id uuid NOT NULL,
    status character varying(12) NOT NULL,
    format_version integer DEFAULT 1 NOT NULL,
    source_refunds bigint DEFAULT 0 NOT NULL,
    processed_refunds bigint DEFAULT 0 NOT NULL,
    pending_refunds bigint DEFAULT 0 NOT NULL,
    rejected_refunds bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT refund_replay_generation_check CHECK (((processed_refunds >= 0) AND (processed_refunds <= source_refunds))),
    CONSTRAINT refund_replay_generation_check1 CHECK (((pending_refunds + rejected_refunds) <= source_refunds)),
    CONSTRAINT refund_replay_generation_check2 CHECK ((((status)::text = 'BUILDING'::text) = (completed_at IS NULL))),
    CONSTRAINT refund_replay_generation_check3 CHECK ((((status)::text <> 'VERIFIED'::text) OR (processed_refunds = source_refunds))),
    CONSTRAINT refund_replay_generation_format_version_check CHECK ((format_version > 0)),
    CONSTRAINT refund_replay_generation_pending_refunds_check CHECK ((pending_refunds >= 0)),
    CONSTRAINT refund_replay_generation_rejected_refunds_check CHECK ((rejected_refunds >= 0)),
    CONSTRAINT refund_replay_generation_source_refunds_check CHECK ((source_refunds >= 0)),
    CONSTRAINT refund_replay_generation_status_check CHECK (((status)::text = ANY (ARRAY[('BUILDING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text])))
);


ALTER TABLE public.refund_replay_generation OWNER TO test;

--
-- Name: refund_replay_input; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.refund_replay_input (
    generation_id uuid NOT NULL,
    refund_transaction_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    state character varying(16) NOT NULL,
    fact_snapshot jsonb NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    first_envelope_fingerprint character varying(64) NOT NULL,
    processed boolean DEFAULT false NOT NULL,
    CONSTRAINT refund_replay_input_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT refund_replay_input_fact_snapshot_check CHECK ((jsonb_typeof(fact_snapshot) = 'object'::text)),
    CONSTRAINT refund_replay_input_first_envelope_fingerprint_check CHECK (((first_envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT refund_replay_input_source_fingerprint_check CHECK (((source_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT refund_replay_input_state_check CHECK (((state)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.refund_replay_input OWNER TO test;

--
-- Name: refund_replay_scope; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.refund_replay_scope (
    generation_id uuid NOT NULL,
    business_date date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    classification character varying(32) NOT NULL,
    completed_refunds numeric(19,2) NOT NULL,
    refund_count bigint NOT NULL,
    CONSTRAINT refund_replay_scope_classification_check CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text]))),
    CONSTRAINT refund_replay_scope_completed_refunds_check CHECK ((completed_refunds > (0)::numeric)),
    CONSTRAINT refund_replay_scope_currency_check CHECK (((currency)::text = 'VND'::text)),
    CONSTRAINT refund_replay_scope_refund_count_check CHECK ((refund_count > 0))
);


ALTER TABLE public.refund_replay_scope OWNER TO test;

--
-- Name: report_admission_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_admission_delivery (
    event_id uuid NOT NULL,
    admission_id uuid NOT NULL,
    event_type character varying(64) NOT NULL,
    envelope_fingerprint character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_admission_delivery_envelope_fingerprint_check CHECK (((envelope_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT report_admission_delivery_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('admission.started'::character varying)::text, ('admission.closed'::character varying)::text])))
);


ALTER TABLE public.report_admission_delivery OWNER TO test;

--
-- Name: report_admission_fact; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_admission_fact (
    admission_id uuid NOT NULL,
    fact_type character varying(16) NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid,
    bed_id uuid,
    business_at timestamp with time zone NOT NULL,
    business_at_iso character varying(35) NOT NULL,
    emergency boolean NOT NULL,
    emergency_override_id uuid,
    settlement_id uuid,
    approved_override_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_report_admission_exact_time CHECK ((business_at = (business_at_iso)::timestamp with time zone)),
    CONSTRAINT ck_report_admission_fact_shape CHECK (((((fact_type)::text = 'STARTED'::text) AND (department_id IS NOT NULL) AND (bed_id IS NOT NULL) AND (emergency = (emergency_override_id IS NOT NULL)) AND (settlement_id IS NULL) AND (approved_override_id IS NULL)) OR (((fact_type)::text = 'CLOSED'::text) AND (department_id IS NULL) AND (bed_id IS NULL) AND (NOT emergency) AND (emergency_override_id IS NULL) AND ((settlement_id IS NOT NULL) <> (approved_override_id IS NOT NULL))))),
    CONSTRAINT report_admission_fact_fact_type_check CHECK (((fact_type)::text = ANY (ARRAY[('STARTED'::character varying)::text, ('CLOSED'::character varying)::text])))
);


ALTER TABLE public.report_admission_fact OWNER TO test;

--
-- Name: report_admission_target; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_admission_target (
    admission_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.report_admission_target OWNER TO test;

--
-- Name: report_cash_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_cash_delivery (
    event_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    envelope_fingerprint character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_cash_delivery_envelope_fingerprint_check CHECK (((envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.report_cash_delivery OWNER TO test;

--
-- Name: report_cash_receipt; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_cash_receipt (
    transaction_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    invoice_id uuid,
    payment_request_id uuid NOT NULL,
    account_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    classification character varying(32) NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    payment_method character varying(16) NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    completed_at_iso character varying(40) NOT NULL,
    business_date date NOT NULL,
    report_zone character varying(80) NOT NULL,
    payload_fingerprint character varying(64) NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_cash_receipt_deposit_episode CHECK ((((classification)::text <> 'ADMISSION_DEPOSIT'::text) OR ((care_episode_type)::text = 'ADMISSION'::text))),
    CONSTRAINT report_cash_receipt_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT report_cash_receipt_care_episode_type_check CHECK (((care_episode_type)::text = ANY (ARRAY[('OUTPATIENT_VISIT'::character varying)::text, ('ADMISSION'::character varying)::text]))),
    CONSTRAINT report_cash_receipt_classification_check CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text]))),
    CONSTRAINT report_cash_receipt_currency_check CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT report_cash_receipt_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT report_cash_receipt_payload_fingerprint_check CHECK (((payload_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT report_cash_receipt_payment_method_check CHECK (((payment_method)::text = ANY (ARRAY[('CASH'::character varying)::text, ('TRANSFER'::character varying)::text]))),
    CONSTRAINT report_cash_receipt_report_zone_check CHECK ((btrim((report_zone)::text) <> ''::text))
);


ALTER TABLE public.report_cash_receipt OWNER TO test;

--
-- Name: report_cash_refund; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_cash_refund (
    refund_transaction_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    account_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    department_id uuid NOT NULL,
    care_episode_type character varying(32) NOT NULL,
    care_episode_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    completed_at_iso character varying(40) NOT NULL,
    business_date date NOT NULL,
    report_zone character varying(80) NOT NULL,
    source_fingerprint character varying(64) NOT NULL,
    state character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    reason_code character varying(80),
    original_business_date date,
    classification character varying(32),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    applied_at timestamp with time zone,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    recovery_attempts bigint DEFAULT 0 NOT NULL,
    CONSTRAINT report_cash_refund_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT report_cash_refund_care_episode_type_check CHECK (((care_episode_type)::text = ANY (ARRAY[('ADMISSION'::character varying)::text, ('OUTPATIENT_VISIT'::character varying)::text]))),
    CONSTRAINT report_cash_refund_check CHECK ((refund_transaction_id <> original_transaction_id)),
    CONSTRAINT report_cash_refund_check1 CHECK ((((state)::text <> 'APPLIED'::text) OR ((original_business_date IS NOT NULL) AND (classification IS NOT NULL) AND (applied_at IS NOT NULL)))),
    CONSTRAINT report_cash_refund_check2 CHECK ((((state)::text <> 'REJECTED'::text) OR (reason_code IS NOT NULL))),
    CONSTRAINT report_cash_refund_classification_check CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text]))),
    CONSTRAINT report_cash_refund_currency_check CHECK (((currency)::text = 'VND'::text)),
    CONSTRAINT report_cash_refund_recovery_attempts_check CHECK ((recovery_attempts >= 0)),
    CONSTRAINT report_cash_refund_source_fingerprint_check CHECK (((source_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT report_cash_refund_state_check CHECK (((state)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.report_cash_refund OWNER TO test;

--
-- Name: report_cash_refund_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_cash_refund_delivery (
    event_id uuid NOT NULL,
    refund_transaction_id uuid NOT NULL,
    envelope_fingerprint character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_cash_refund_delivery_envelope_fingerprint_check CHECK (((envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.report_cash_refund_delivery OWNER TO test;

--
-- Name: report_final_deposit_release; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_final_deposit_release (
    recognition_operation_id uuid NOT NULL,
    original_transaction_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    CONSTRAINT report_final_deposit_release_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.report_final_deposit_release OWNER TO test;

--
-- Name: report_final_recognition; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_final_recognition (
    recognition_operation_id uuid NOT NULL,
    account_id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    first_event_id uuid NOT NULL,
    state character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    reason_code character varying(80),
    payload_snapshot jsonb NOT NULL,
    payload_fingerprint character varying(64) NOT NULL,
    fact_fingerprint character varying(64) NOT NULL,
    report_zone character varying(80) NOT NULL,
    business_date date NOT NULL,
    completed_at_iso character varying(40) NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    recovery_attempts bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    applied_at timestamp with time zone,
    CONSTRAINT report_final_recognition_check CHECK ((((state)::text = 'APPLIED'::text) = (applied_at IS NOT NULL))),
    CONSTRAINT report_final_recognition_check1 CHECK ((((state)::text = 'REJECTED'::text) = (reason_code IS NOT NULL))),
    CONSTRAINT report_final_recognition_fact_fingerprint_check CHECK (((fact_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT report_final_recognition_payload_fingerprint_check CHECK (((payload_fingerprint)::text ~ '^[a-f0-9]{64}$'::text)),
    CONSTRAINT report_final_recognition_payload_snapshot_check CHECK ((jsonb_typeof(payload_snapshot) = 'object'::text)),
    CONSTRAINT report_final_recognition_recovery_attempts_check CHECK ((recovery_attempts >= 0)),
    CONSTRAINT report_final_recognition_state_check CHECK (((state)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.report_final_recognition OWNER TO test;

--
-- Name: report_final_recognition_contribution; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_final_recognition_contribution (
    recognition_operation_id uuid NOT NULL,
    business_date date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    earned_revenue numeric(19,2) NOT NULL,
    deposit_released numeric(19,2) NOT NULL,
    CONSTRAINT report_final_recognition_contribution_currency_check CHECK (((currency)::text = 'VND'::text)),
    CONSTRAINT report_final_recognition_contribution_deposit_released_check CHECK ((deposit_released >= (0)::numeric)),
    CONSTRAINT report_final_recognition_contribution_earned_revenue_check CHECK ((earned_revenue >= (0)::numeric))
);


ALTER TABLE public.report_final_recognition_contribution OWNER TO test;

--
-- Name: report_final_recognition_daily; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_final_recognition_daily (
    business_date date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    earned_revenue numeric(19,2) NOT NULL,
    deposit_released numeric(19,2) NOT NULL,
    CONSTRAINT report_final_recognition_daily_currency_check CHECK (((currency)::text = 'VND'::text)),
    CONSTRAINT report_final_recognition_daily_deposit_released_check CHECK ((deposit_released >= (0)::numeric)),
    CONSTRAINT report_final_recognition_daily_earned_revenue_check CHECK ((earned_revenue >= (0)::numeric))
);


ALTER TABLE public.report_final_recognition_daily OWNER TO test;

--
-- Name: report_final_recognition_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_final_recognition_delivery (
    event_id uuid NOT NULL,
    recognition_operation_id uuid NOT NULL,
    envelope_fingerprint character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_final_recognition_delivery_envelope_fingerprint_check CHECK (((envelope_fingerprint)::text ~ '^[a-f0-9]{64}$'::text))
);


ALTER TABLE public.report_final_recognition_delivery OWNER TO test;

--
-- Name: report_gross_cash_daily; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_gross_cash_daily (
    scope_id uuid NOT NULL,
    business_date date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    classification character varying(32) NOT NULL,
    gross_receipts numeric(19,2) NOT NULL,
    receipt_count bigint NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_gross_cash_daily_classification_check CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text]))),
    CONSTRAINT report_gross_cash_daily_currency_check CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT report_gross_cash_daily_gross_receipts_check CHECK ((gross_receipts > (0)::numeric)),
    CONSTRAINT report_gross_cash_daily_receipt_count_check CHECK ((receipt_count > 0)),
    CONSTRAINT report_gross_cash_daily_report_zone_check CHECK ((btrim((report_zone)::text) <> ''::text))
);


ALTER TABLE public.report_gross_cash_daily OWNER TO test;

--
-- Name: report_medical_discharge; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_medical_discharge (
    admission_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    summary_id uuid NOT NULL,
    approved_by uuid NOT NULL,
    approved_at_iso character varying(40) NOT NULL,
    first_event_id uuid NOT NULL,
    occurred_at_iso character varying(40) NOT NULL,
    correlation_id character varying(120) NOT NULL,
    payload_hash character(64) NOT NULL,
    status character varying(24) DEFAULT 'PENDING_START'::character varying NOT NULL,
    CONSTRAINT report_medical_discharge_payload_hash_check CHECK ((payload_hash ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT report_medical_discharge_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING_START'::character varying)::text, ('APPLIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);


ALTER TABLE public.report_medical_discharge OWNER TO test;

--
-- Name: report_medical_discharge_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_medical_discharge_delivery (
    event_id uuid NOT NULL,
    admission_id uuid NOT NULL,
    envelope_hash character(64) NOT NULL,
    CONSTRAINT report_medical_discharge_delivery_envelope_hash_check CHECK ((envelope_hash ~ '^[0-9a-f]{64}$'::text))
);


ALTER TABLE public.report_medical_discharge_delivery OWNER TO test;

--
-- Name: report_outpatient_coverage; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_outpatient_coverage (
    coverage_id uuid NOT NULL,
    covered_from date NOT NULL,
    covered_to date NOT NULL,
    department_id uuid,
    zone_id character varying(80) NOT NULL,
    currency character varying(3) NOT NULL,
    source_references jsonb NOT NULL,
    source_observed_at timestamp with time zone NOT NULL,
    reconciled_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone NOT NULL,
    accepted_by uuid NOT NULL,
    acceptance_reference character varying(255) NOT NULL,
    local_evidence_fingerprint character varying(64) NOT NULL,
    acceptance_order bigint NOT NULL,
    CONSTRAINT report_outpatient_coverage_acceptance_order_positive CHECK ((acceptance_order > 0)),
    CONSTRAINT report_outpatient_coverage_acceptance_reference_check CHECK ((btrim((acceptance_reference)::text) <> ''::text)),
    CONSTRAINT report_outpatient_coverage_check CHECK (((covered_to >= covered_from) AND ((covered_to - covered_from) < 366))),
    CONSTRAINT report_outpatient_coverage_check1 CHECK ((reconciled_at >= source_observed_at)),
    CONSTRAINT report_outpatient_coverage_check2 CHECK ((accepted_at >= reconciled_at)),
    CONSTRAINT report_outpatient_coverage_currency_check CHECK (((currency)::text = 'VND'::text)),
    CONSTRAINT report_outpatient_coverage_local_evidence_fingerprint_check CHECK (((local_evidence_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT report_outpatient_coverage_source_references_check CHECK ((jsonb_typeof(source_references) = 'object'::text)),
    CONSTRAINT report_outpatient_coverage_zone_id_check CHECK ((btrim((zone_id)::text) <> ''::text))
);


ALTER TABLE public.report_outpatient_coverage OWNER TO test;

--
-- Name: report_outpatient_coverage_acceptance; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_outpatient_coverage_acceptance (
    request_id uuid NOT NULL,
    coverage_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    request_payload jsonb NOT NULL,
    coverage_payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT report_outpatient_coverage_acceptance_coverage_payload_check CHECK ((jsonb_typeof(coverage_payload) = 'object'::text)),
    CONSTRAINT report_outpatient_coverage_acceptance_request_payload_check CHECK ((jsonb_typeof(request_payload) = 'object'::text))
);


ALTER TABLE public.report_outpatient_coverage_acceptance OWNER TO test;

--
-- Name: report_outpatient_coverage_acceptance_order_seq; Type: SEQUENCE; Schema: public; Owner: test
--

CREATE SEQUENCE public.report_outpatient_coverage_acceptance_order_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.report_outpatient_coverage_acceptance_order_seq OWNER TO test;

--
-- Name: report_outpatient_coverage_acceptance_order_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: test
--

ALTER SEQUENCE public.report_outpatient_coverage_acceptance_order_seq OWNED BY public.report_outpatient_coverage.acceptance_order;


--
-- Name: report_receivable_current; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_receivable_current (
    account_id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    settlement_version integer NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_receivable_current_settlement_version_check CHECK ((settlement_version > 0))
);


ALTER TABLE public.report_receivable_current OWNER TO test;

--
-- Name: report_receivable_stock; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_receivable_stock (
    currency character(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    outstanding_receivable numeric(19,2) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_receivable_stock_currency_check CHECK ((currency ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT report_receivable_stock_outstanding_receivable_check CHECK ((outstanding_receivable >= (0)::numeric))
);


ALTER TABLE public.report_receivable_stock OWNER TO test;

--
-- Name: report_refund_cash_daily; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_refund_cash_daily (
    scope_id uuid NOT NULL,
    business_date date NOT NULL,
    currency character varying(3) NOT NULL,
    report_zone character varying(80) NOT NULL,
    department_id uuid,
    classification character varying(32) NOT NULL,
    completed_refunds numeric(19,2) NOT NULL,
    refund_count bigint NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_refund_cash_daily_classification_check CHECK (((classification)::text = ANY (ARRAY[('SERVICE_PAYMENT'::character varying)::text, ('ADMISSION_DEPOSIT'::character varying)::text]))),
    CONSTRAINT report_refund_cash_daily_completed_refunds_check CHECK ((completed_refunds > (0)::numeric)),
    CONSTRAINT report_refund_cash_daily_refund_count_check CHECK ((refund_count > 0))
);


ALTER TABLE public.report_refund_cash_daily OWNER TO test;

--
-- Name: report_settlement_delivery; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_settlement_delivery (
    event_id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    envelope_fingerprint character(64) NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.report_settlement_delivery OWNER TO test;

--
-- Name: report_settlement_snapshot; Type: TABLE; Schema: public; Owner: test
--

CREATE TABLE public.report_settlement_snapshot (
    settlement_id uuid NOT NULL,
    account_id uuid NOT NULL,
    settlement_version integer NOT NULL,
    supersedes_settlement_id uuid,
    snapshot_version integer NOT NULL,
    payload text NOT NULL,
    fact_fingerprint character(64) NOT NULL,
    snapshot_fingerprint character(64) NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT report_settlement_snapshot_settlement_version_check CHECK ((settlement_version > 0)),
    CONSTRAINT report_settlement_snapshot_snapshot_version_check CHECK ((snapshot_version = 1))
);


ALTER TABLE public.report_settlement_snapshot OWNER TO test;

--
-- Name: report_outpatient_coverage acceptance_order; Type: DEFAULT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_outpatient_coverage ALTER COLUMN acceptance_order SET DEFAULT nextval('public.report_outpatient_coverage_acceptance_order_seq'::regclass);


--
-- Data for Name: cash_replay_generation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.cash_replay_generation (generation_id, status, snapshot_version, projector_version, source_receipts, applied_receipts, created_at, completed_at) FROM stdin;
\.


--
-- Data for Name: cash_replay_input; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.cash_replay_input (generation_id, transaction_id, first_event_id, fact_snapshot, fact_fingerprint, payload_fingerprint, first_envelope_fingerprint, snapshot_version, projector_version, applied) FROM stdin;
\.


--
-- Data for Name: cash_replay_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.cash_replay_receipt (generation_id, transaction_id, first_event_id, fact_snapshot, fact_fingerprint, payload_fingerprint, first_envelope_fingerprint) FROM stdin;
\.


--
-- Data for Name: cash_replay_scope; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.cash_replay_scope (generation_id, business_date, currency, report_zone, department_id, classification, gross_receipts, receipt_count) FROM stdin;
\.


--
-- Data for Name: daily_financial_report; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.daily_financial_report (report_id, report_date, department_id, cash_received, deposit_liability, earned_revenue, refunds, outstanding_receivable, updated_at) FROM stdin;
\.


--
-- Data for Name: daily_operational_report; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.daily_operational_report (report_id, report_date, department_id, completed_visits, admissions, discharges, inpatient_days, surgeries_completed, surgeries_cancelled, surgery_duration_minutes, updated_at, lab_tests, dispensed_prescriptions, dispensed_units, medical_los_minutes, medical_discharges, completed_stay_days) FROM stdin;
5c891928-a6af-46a6-a28e-68cd5cf51818	2026-10-22	\N	0	0	0	0	0	0	0	2026-10-22 00:52:18.00232+00	0	2	12	0	0	0
119ec4b9-db44-4e9d-8f6e-e07b4f65466a	2026-10-22	6c568d0b-debd-44ae-999d-3942e1ecfe8b	0	0	0	0	0	0	0	2026-10-22 00:52:18.00232+00	0	2	12	0	0	0
\.


--
-- Data for Name: daily_visit_report; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.daily_visit_report (report_id, report_date, department_id, visit_count, lab_count, prescription_count, revenue, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: drug_statistic; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.drug_statistic (statistic_id, drug_id, drug_name, report_date, department_id, dispensed_quantity, updated_at) FROM stdin;
\.


--
-- Data for Name: financial_archive_restore; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_archive_restore (archive_fingerprint, origin_generation_id, generation_id, actor_account_id, restored_at) FROM stdin;
\.


--
-- Data for Name: financial_contribution; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_contribution (contribution_id, event_id, source_type, source_id, source_revision, account_id, patient_id, department_id, care_episode_type, care_episode_id, contribution_type, cash_amount, liability_amount, revenue_amount, refund_amount, receivable_amount, original_contribution_id, business_date, occurred_at, created_at) FROM stdin;
\.


--
-- Data for Name: financial_generation_acceptance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_generation_acceptance (generation_id, manifest_fingerprint, history_policy, acceptance_reference, actor_account_id, accepted_at) FROM stdin;
\.


--
-- Data for Name: financial_publication_audit; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_publication_audit (command_id, generation_id, previous_generation_id, action, request_fingerprint, acceptance_reference, actor_account_id, published_at) FROM stdin;
\.


--
-- Data for Name: financial_report_day; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_report_day (generation_id, business_date, department_id, cash_received, deposit_liability, earned_revenue, refunds, outstanding_receivable) FROM stdin;
\.


--
-- Data for Name: financial_report_generation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_report_generation (generation_id, status, snapshot_version, projector_version, covered_from, covered_to, currency, report_zone, source_observed_at, reconciled_at, source_inputs, applied_inputs, unresolved_inputs, manifest_fingerprint, aggregate_fingerprint) FROM stdin;
\.


--
-- Data for Name: financial_report_publication; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_report_publication (currency, report_zone, generation_id, acceptance_reference, published_at) FROM stdin;
\.


--
-- Data for Name: financial_source_rebuild; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_source_rebuild (generation_id, status, snapshot_version, projector_version, covered_from, covered_to, report_zone, source_observed_at, source_inputs, unresolved_inputs, processed_days, manifest_fingerprint, manifest_sealed, aggregate_fingerprint, reason_code, reconciled_at) FROM stdin;
\.


--
-- Data for Name: financial_source_rebuild_day; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_source_rebuild_day (generation_id, business_date, department_id, cash_received, deposit_liability, earned_revenue, refunds, outstanding_receivable) FROM stdin;
\.


--
-- Data for Name: financial_source_rebuild_input; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.financial_source_rebuild_input (generation_id, source_kind, source_id, first_event_id, source_state, source_fingerprint, envelope_fingerprint, fact_snapshot, snapshot_fingerprint) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	-320135011	test	2026-10-22 07:39:46.162714	64	t
2	2	enforce pending payment scope	SQL	V2__enforce_pending_payment_scope.sql	-248302477	test	2026-10-22 07:39:46.280503	10	t
3	6	care finance projections	SQL	V6__care_finance_projections.sql	1642365505	test	2026-10-22 07:39:46.314386	44	t
4	7	operational journal	SQL	V7__operational_journal.sql	-4905411	test	2026-10-22 07:39:46.378313	29	t
5	8	isolated operational replay	SQL	V8__isolated_operational_replay.sql	-735248790	test	2026-10-22 07:39:46.42801	37	t
6	9	pending admission report evidence	SQL	V9__pending_admission_report_evidence.sql	-1947721797	test	2026-10-22 07:39:46.485601	30	t
7	10	operational snapshot publication	SQL	V10__operational_snapshot_publication.sql	952318951	test	2026-10-22 07:39:46.534914	12	t
8	11	operational source fingerprints	SQL	V11__operational_source_fingerprints.sql	-323477143	test	2026-10-22 07:39:46.562376	11	t
9	12	offline gross cash receipts	SQL	V12__offline_gross_cash_receipts.sql	123850184	test	2026-10-22 07:39:46.588029	38	t
10	13	isolated cash receipt replay	SQL	V13__isolated_cash_receipt_replay.sql	602547693	test	2026-10-22 07:39:46.646522	32	t
11	14	report telemetry indexes	SQL	V14__report_telemetry_indexes.sql	1471301006	test	2026-10-22 07:39:46.693972	19	t
12	15	completed cash refund evidence	SQL	V15__completed_cash_refund_evidence.sql	1658935304	test	2026-10-22 07:39:46.728469	39	t
13	16	prescription fill source receipt	SQL	V16__prescription_fill_source_receipt.sql	1449242069	test	2026-10-22 07:39:46.786419	11	t
14	17	isolated cash refund replay	SQL	V17__isolated_cash_refund_replay.sql	-1345721852	test	2026-10-22 07:39:46.815371	48	t
15	18	operational snapshot read index	SQL	V18__operational_snapshot_read_index.sql	-586979851	test	2026-10-22 07:39:46.888052	12	t
16	19	settlement receivable stock	SQL	V19__settlement_receivable_stock.sql	-145866778	test	2026-10-22 07:39:46.920839	34	t
17	20	financial snapshot read boundary	SQL	V20__financial_snapshot_read_boundary.sql	-1776564721	test	2026-10-22 07:39:46.973367	34	t
18	21	medical discharge report projection	SQL	V21__medical_discharge_report_projection.sql	-409988652	test	2026-10-22 07:39:47.025307	21	t
19	22	final settlement recognition	SQL	V22__final_settlement_recognition.sql	-1632273229	test	2026-10-22 07:39:47.062524	41	t
20	23	finite financial source rebuild	SQL	V23__finite_financial_source_rebuild.sql	-369725764	test	2026-10-22 07:39:47.12149	39	t
21	24	financial archive restore provenance	SQL	V24__financial_archive_restore_provenance.sql	-1950680665	test	2026-10-22 07:39:47.178359	11	t
22	25	controlled financial publication	SQL	V25__controlled_financial_publication.sql	-559376741	test	2026-10-22 07:39:47.201832	21	t
23	26	controlled operational publication	SQL	V26__controlled_operational_publication.sql	1376930925	test	2026-10-22 07:39:47.235084	22	t
24	27	outpatient read coverage	SQL	V27__outpatient_read_coverage.sql	-1668868080	test	2026-10-22 07:39:47.268946	8	t
25	28	outpatient coverage acceptance audit	SQL	V28__outpatient_coverage_acceptance_audit.sql	1734358167	test	2026-10-22 07:39:47.288243	9	t
26	29	durable outpatient coverage acceptance order	SQL	V29__durable_outpatient_coverage_acceptance_order.sql	1371255450	test	2026-10-22 07:39:47.307231	17	t
\.


--
-- Data for Name: monthly_revenue_report; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.monthly_revenue_report (report_id, month, year, department_id, total_revenue, invoice_count, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: operational_contribution; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_contribution (contribution_id, event_id, source_type, source_id, source_revision, metric_type, department_id, care_episode_type, care_episode_id, metric_date, numeric_value, category, occurred_at, fact_fingerprint) FROM stdin;
b26f9720-88a8-4fbc-aa17-009476868232	7d3186d9-a2c6-44e1-a89c-4565b164d105	DISPENSE	d3b54c5d-ea65-48d2-9200-cfae37dae670	1	DISPENSED_PRESCRIPTIONS	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	2026-10-22	1.000	\N	2026-10-22 00:45:34.36428+00	e92775f1a494fb040389051750369e9fb47d4e533773ae2991d4f3c9c2933e13
e775f79a-d162-4b0f-9d1a-43426c019bf1	7d3186d9-a2c6-44e1-a89c-4565b164d105	DISPENSE	d3b54c5d-ea65-48d2-9200-cfae37dae670	1	DISPENSED_UNITS	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	2026-10-22	6.000	\N	2026-10-22 00:45:34.36428+00	edc7b4ed242287ff06e21405f16bb6e4350ef503d0585c6fb7f167193311c272
811b8041-d1f7-4b58-a7a9-6b4ccedf5b3a	6400782d-fa9c-41f8-a5c3-95b1ad7081ce	DISPENSE	bfc025df-98c4-4244-bf55-3287331fc5e9	1	DISPENSED_PRESCRIPTIONS	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	2026-10-22	1.000	\N	2026-10-22 00:52:17.382605+00	f3c73efdf74e3e965ad013f902e609263df721b8be6a49594aa87ba3398db8c9
d6a35324-2e64-43c7-8f38-51c54cd17aa7	6400782d-fa9c-41f8-a5c3-95b1ad7081ce	DISPENSE	bfc025df-98c4-4244-bf55-3287331fc5e9	1	DISPENSED_UNITS	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	2026-10-22	6.000	\N	2026-10-22 00:52:17.382605+00	df3ae839a00e199a04dc2f47b8bcebc1caddac850601297ecce1fa25f88e1061
\.


--
-- Data for Name: operational_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_delivery (event_id, metric_type, fact_fingerprint) FROM stdin;
7d3186d9-a2c6-44e1-a89c-4565b164d105	DISPENSED_PRESCRIPTIONS	e92775f1a494fb040389051750369e9fb47d4e533773ae2991d4f3c9c2933e13
7d3186d9-a2c6-44e1-a89c-4565b164d105	DISPENSED_UNITS	edc7b4ed242287ff06e21405f16bb6e4350ef503d0585c6fb7f167193311c272
6400782d-fa9c-41f8-a5c3-95b1ad7081ce	DISPENSED_PRESCRIPTIONS	f3c73efdf74e3e965ad013f902e609263df721b8be6a49594aa87ba3398db8c9
6400782d-fa9c-41f8-a5c3-95b1ad7081ce	DISPENSED_UNITS	df3ae839a00e199a04dc2f47b8bcebc1caddac850601297ecce1fa25f88e1061
\.


--
-- Data for Name: operational_event_journal; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_event_journal (event_id, event_type, event_snapshot, contribution_snapshot, envelope_fingerprint, projection_fingerprint, projector_version, recorded_at) FROM stdin;
7d3186d9-a2c6-44e1-a89c-4565b164d105	prescription.filled	{"eventId": "7d3186d9-a2c6-44e1-a89c-4565b164d105", "version": 1, "producer": "pharmacy-service", "sourceId": "d3b54c5d-ea65-48d2-9200-cfae37dae670", "eventType": "prescription.filled", "occurredAt": "2026-10-22T00:45:34.364279600Z", "sourceField": "dispenseId", "correlationId": "56d13802-f3dd-4c48-a841-bcd223205143"}	[{"value": 1, "metric": "DISPENSED_PRESCRIPTIONS", "category": null, "sourceId": "d3b54c5d-ea65-48d2-9200-cfae37dae670", "metricDate": "2026-10-22", "occurredAt": "2026-10-22T00:45:34.364279600Z", "sourceType": "DISPENSE", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "sourceRevision": 1, "careEpisodeType": "OUTPATIENT_VISIT"}, {"value": 6, "metric": "DISPENSED_UNITS", "category": null, "sourceId": "d3b54c5d-ea65-48d2-9200-cfae37dae670", "metricDate": "2026-10-22", "occurredAt": "2026-10-22T00:45:34.364279600Z", "sourceType": "DISPENSE", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "71626749-ef62-42f4-9d40-35097f05dfb9", "sourceRevision": 1, "careEpisodeType": "OUTPATIENT_VISIT"}]	84164967088ea62686eb7d272a136267b681cdd43ff1e4d3ececc275a549029c	84e06f1d4814c27c294c1e46b08390b8ad47dc960a2f7aa1d62068b23fa6f302	1	2026-10-22 00:45:35.364239+00
6400782d-fa9c-41f8-a5c3-95b1ad7081ce	prescription.filled	{"eventId": "6400782d-fa9c-41f8-a5c3-95b1ad7081ce", "version": 1, "producer": "pharmacy-service", "sourceId": "bfc025df-98c4-4244-bf55-3287331fc5e9", "eventType": "prescription.filled", "occurredAt": "2026-10-22T00:52:17.382604700Z", "sourceField": "dispenseId", "correlationId": "a4e8a115-4307-42be-bdfa-2be6d65d3156"}	[{"value": 1, "metric": "DISPENSED_PRESCRIPTIONS", "category": null, "sourceId": "bfc025df-98c4-4244-bf55-3287331fc5e9", "metricDate": "2026-10-22", "occurredAt": "2026-10-22T00:52:17.382604700Z", "sourceType": "DISPENSE", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "sourceRevision": 1, "careEpisodeType": "OUTPATIENT_VISIT"}, {"value": 6, "metric": "DISPENSED_UNITS", "category": null, "sourceId": "bfc025df-98c4-4244-bf55-3287331fc5e9", "metricDate": "2026-10-22", "occurredAt": "2026-10-22T00:52:17.382604700Z", "sourceType": "DISPENSE", "departmentId": "6c568d0b-debd-44ae-999d-3942e1ecfe8b", "careEpisodeId": "eab4cb0a-de3f-495c-9304-73ff1f18b5d4", "sourceRevision": 1, "careEpisodeType": "OUTPATIENT_VISIT"}]	aff3b4608b5fdec417003e283762dfbdb7d213c427fc87306aec5825baf00fb3	12915c9164b43f7725f76b432e1e771ffc3a865672d6a9a39eddc85a1185a861	1	2026-10-22 00:52:18.00232+00
\.


--
-- Data for Name: operational_generation_acceptance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_generation_acceptance (generation_id, report_kind, manifest_fingerprint, covered_from, covered_to, zone_id, history_policy, acceptance_reference, actor_account_id, accepted_at) FROM stdin;
\.


--
-- Data for Name: operational_publication_audit; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_publication_audit (command_id, generation_id, previous_generation_id, action, request_fingerprint, acceptance_reference, actor_account_id, published_at) FROM stdin;
\.


--
-- Data for Name: operational_replay_contribution; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_replay_contribution (generation_id, source_type, source_id, source_revision, metric_type, fact_fingerprint, fact_snapshot) FROM stdin;
\.


--
-- Data for Name: operational_replay_generation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_replay_generation (generation_id, status, projector_version, source_events, applied_events, created_at, completed_at) FROM stdin;
\.


--
-- Data for Name: operational_replay_input; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_replay_input (generation_id, event_id, event_snapshot, contribution_snapshot, envelope_fingerprint, projection_fingerprint, projector_version, applied) FROM stdin;
\.


--
-- Data for Name: operational_replay_scope; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_replay_scope (generation_id, metric_date, department_id, metric_type, numeric_value) FROM stdin;
\.


--
-- Data for Name: operational_report_publication; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_report_publication (report_kind, generation_id, covered_from, covered_to, zone_id, metrics, acceptance_reference, created_at) FROM stdin;
\.


--
-- Data for Name: operational_source_snapshot; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.operational_source_snapshot (source_type, source_id, source_revision, event_type, first_event_id, payload_fingerprint, evidence_state) FROM stdin;
DISPENSE	d3b54c5d-ea65-48d2-9200-cfae37dae670	1	prescription.filled	7d3186d9-a2c6-44e1-a89c-4565b164d105	604d956b0b32aa0a243a41b4047cbfb75439def6a2b618892b30e765ba4b7f81	VERIFIED_PAYLOAD
DISPENSE	bfc025df-98c4-4244-bf55-3287331fc5e9	1	prescription.filled	6400782d-fa9c-41f8-a5c3-95b1ad7081ce	5d6b8b7d3b6e4b1b62f6dd0681fe48207b7c99b8b828376835c4ff7e90d8249b	VERIFIED_PAYLOAD
\.


--
-- Data for Name: payment_contribution; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.payment_contribution (invoice_id, completed_event_id, failed_event_id, payment_date, department_id, amount, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: prescription_fill_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.prescription_fill_receipt (prescription_id, fact_fingerprint, accepted_at) FROM stdin;
\.


--
-- Data for Name: processed_event; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.processed_event (event_id, routing_key, processed_at) FROM stdin;
\.


--
-- Data for Name: refund_replay_fact; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.refund_replay_fact (generation_id, refund_transaction_id, original_transaction_id, first_event_id, state, fact_snapshot, fact_fingerprint, source_fingerprint, first_envelope_fingerprint) FROM stdin;
\.


--
-- Data for Name: refund_replay_generation; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.refund_replay_generation (generation_id, status, format_version, source_refunds, processed_refunds, pending_refunds, rejected_refunds, created_at, completed_at) FROM stdin;
\.


--
-- Data for Name: refund_replay_input; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.refund_replay_input (generation_id, refund_transaction_id, original_transaction_id, first_event_id, state, fact_snapshot, fact_fingerprint, source_fingerprint, first_envelope_fingerprint, processed) FROM stdin;
\.


--
-- Data for Name: refund_replay_scope; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.refund_replay_scope (generation_id, business_date, currency, report_zone, department_id, classification, completed_refunds, refund_count) FROM stdin;
\.


--
-- Data for Name: report_admission_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_admission_delivery (event_id, admission_id, event_type, envelope_fingerprint, created_at) FROM stdin;
\.


--
-- Data for Name: report_admission_fact; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_admission_fact (admission_id, fact_type, patient_id, department_id, bed_id, business_at, business_at_iso, emergency, emergency_override_id, settlement_id, approved_override_id, created_at) FROM stdin;
\.


--
-- Data for Name: report_admission_target; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_admission_target (admission_id, created_at) FROM stdin;
\.


--
-- Data for Name: report_cash_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_cash_delivery (event_id, transaction_id, envelope_fingerprint, created_at) FROM stdin;
f5f81d95-ffb2-476c-a67a-d5eaff2beaf2	c4830cfc-8783-4fe7-93a6-834d90b31eba	916a2fc50bdae3a251bd3ac82fa5bc00d89fe18d9817346814e35bf9e0efdb6e	2026-10-22 00:40:18.480286+00
a7a1957a-cad4-479f-8f77-d2106b1945a0	2ba1e00e-ccb0-47ea-b27e-e81042f78ce2	657db00474c280fdf74a48601231ca1cb391219fc2c2ef5a2a8a52a2d9015dfe	2026-10-22 00:40:41.289915+00
1330fe0c-c39d-435f-91b6-f044567dc77f	cc0c7042-4358-4039-81dc-19d81ae7b14a	49047d0c337c0333d103abd08d1f0f7c14799614b37b36c2261d122da0b7495d	2026-10-22 00:40:45.380877+00
f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2	e1cffe9a-9228-45bf-9f63-511d4c6f550c	0f449b8cf88e7e67b85bd921bd8ab56a24463e0da2c981fd45f6cead229fffe1	2026-10-22 00:44:58.931692+00
9099c95d-829c-4360-910f-f52198801584	cf4b6b6a-d59d-444c-92fd-f4905bf85509	377d7fff6108e2f0cf3f682608cf3ae46496c83fcdb9dccdb5988c7c74ac01d1	2026-10-22 00:45:04.035585+00
355f326c-1b48-451e-bda2-779db121bc00	55a1ae72-572d-4daa-a23a-0176b0834aa0	05297e89a14614b9f2195582ef031002d1147a070097e70c877a10d8f7f052e7	2026-10-22 00:45:32.416676+00
c3d7ad2a-36de-46bd-ba16-bcfba18bc7be	7649919b-93b2-4aea-906c-6e69d5cc3afb	18402410e4fafd9782a130f8332283b8d0ab9999d860eebb79a4a001752a47a3	2026-10-22 00:45:38.57602+00
9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b	e55fe586-5ddb-48d7-9338-1836225c52c5	9039d4aa1ef636b352a0ca9d84aa135e5d1314897e640cb4596f4efac8ee76f4	2026-10-22 00:45:40.691567+00
0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f	b52f3117-223d-4432-ac82-4e7f06ccee62	d11105ea111704b2acdf4a8c1b7e7f6a0e96e320983954dc0fa1ab57b553a9fd	2026-10-22 00:45:44.812331+00
fa53bcf9-3b37-4ead-9583-7ed10ad93cbe	add16c27-3994-4ef8-a7a6-80c3a01f5090	aab6a8734800df3c17ca0e1eb6ca7a3999c85f102d896639a9c744058684dc3e	2026-10-22 00:45:49.007112+00
33f4d6d9-99f8-407c-b685-4f95b4a0ac95	bced71a5-0b2f-45d2-87d2-9b41bf72d8b3	a85f4678add741d4611a383fc9cd884979c51b505205341acae50136c6b1a249	2026-10-22 00:45:53.104157+00
608193a2-1f8e-493f-a59b-a7d83eb512e7	a33cb5ed-d048-4908-a7ec-2ae3f8416d77	6dc04bdf0549acc77536bd68ba07d1b9c475fa30fe136592196e0a691b725192	2026-10-22 00:52:51.694473+00
b136373c-a447-4817-a839-e28c940fd939	65284733-97d8-4108-9d85-6fa449d75310	722230eba5f27aee54d1c7158a37a9c3ae52380b8348badf94b73d13c51e8263	2026-10-22 00:56:56.941215+00
dee0992f-cbc0-4af3-aeca-3d1f806333b3	c3802b07-5f4f-47e8-afa2-0d08b2d9fe2e	7d38b191c683e30a8c5d5c4315ccdf06f19e31d8a09b625c02c8aa74b07508b9	2026-10-22 10:53:24.825909+00
b2531d42-b612-4cb9-a5f0-0781e3d5c106	2dc1ba9c-e65f-4396-be2e-f94e5d0f4a16	4ab728c9ce7d72f6b1c0dca292ff8f352e1b189c6c61aed5d11a3cf16c39b09b	2026-10-22 11:41:35.839665+00
\.


--
-- Data for Name: report_cash_receipt; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_cash_receipt (transaction_id, first_event_id, invoice_id, payment_request_id, account_id, patient_id, department_id, care_episode_type, care_episode_id, classification, amount, currency, payment_method, completed_at, completed_at_iso, business_date, report_zone, payload_fingerprint, fact_fingerprint, created_at) FROM stdin;
c4830cfc-8783-4fe7-93a6-834d90b31eba	f5f81d95-ffb2-476c-a67a-d5eaff2beaf2	48bdd0f9-a9c4-45bc-9987-40df9770e1b0	5e891e9f-3f19-447f-87dd-a4d1dbff1f54	2b0753ea-f892-4484-81c6-644b08832cc2	c36277c4-347f-480d-8473-bcf9f98c8a44	b134eebd-d327-4718-8bef-92525164178d	OUTPATIENT_VISIT	11935f4f-6428-4051-ba9e-8e8a1b7d9f11	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:40:16.917045+00	2026-10-22T00:40:16.917045Z	2026-10-22	Asia/Bangkok	1d17bf7b9105ea241d1d3c14e8ea74671e5f279e1397a06bb9f50b25a7e46c60	36631753d1775411f6e167a1633c426677c267f356309f8570cbda4bddddc52c	2026-10-22 00:40:18.480286+00
2ba1e00e-ccb0-47ea-b27e-e81042f78ce2	a7a1957a-cad4-479f-8f77-d2106b1945a0	92244ff4-319a-4ccc-8bcd-cb76aa714e08	615649a6-dcfb-4c4e-87cf-44a747595daa	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:40:39.521338+00	2026-10-22T00:40:39.521338Z	2026-10-22	Asia/Bangkok	b15bfbc64a1bd93b5c2ea1c97e76d062577e23bd4b2db9bdcb390c8971c43ebf	f11ab5786507a333638a0782ea0fb6ceeffe069cb6ba8eaaa785104958e0226d	2026-10-22 00:40:41.289915+00
cc0c7042-4358-4039-81dc-19d81ae7b14a	1330fe0c-c39d-435f-91b6-f044567dc77f	cb97113f-6b14-469f-8439-ce19ae2aa207	cf17afe5-1dff-4bb9-b98e-58aec0b9bbe7	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	SERVICE_PAYMENT	150000.00	VND	CASH	2026-10-22 00:40:44.583111+00	2026-10-22T00:40:44.583111Z	2026-10-22	Asia/Bangkok	a4e0f5bfe295abe45b4b86efbaaf0be79b6d361a7d020eba2757eb473b8c3799	2c273becdb94ddef40e1aca2ad92cb72b29ef299bdfa1d3c354c100e7a2bfe5d	2026-10-22 00:40:45.380877+00
e1cffe9a-9228-45bf-9f63-511d4c6f550c	f6bbe6f8-bd79-48ac-b370-efdeb4aa22f2	1f14c914-9c13-4e48-9003-dd73422859fa	c626fe0e-332f-4ec6-820c-462757683d5c	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:44:56.990696+00	2026-10-22T00:44:56.990696Z	2026-10-22	Asia/Bangkok	6da158404535363d76412659a734aee0ac83c92540c9122df7ebe02040772d1e	84fd7c24428c6950e33aec87333cb0e0de5942679ef968328653864e5e7c5b9c	2026-10-22 00:44:58.931692+00
cf4b6b6a-d59d-444c-92fd-f4905bf85509	9099c95d-829c-4360-910f-f52198801584	4541152d-f447-4a88-ba0d-b63ee9244d34	17047f8c-0ddf-443f-8e19-b056667afbe5	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	SERVICE_PAYMENT	150000.00	VND	CASH	2026-10-22 00:45:02.489562+00	2026-10-22T00:45:02.489562Z	2026-10-22	Asia/Bangkok	0c02465382b0673cadf408f50a6e45311e687a3b25bec45e8f33b952a63165e2	cfe33079efdec89c1254b037f3f80593584c5c207b6730f418f33d63b806851e	2026-10-22 00:45:04.035585+00
55a1ae72-572d-4daa-a23a-0176b0834aa0	355f326c-1b48-451e-bda2-779db121bc00	eb37200c-eb91-4c71-b45e-732a9ba773c8	0cff9bd3-381b-4b73-970f-b66e657e4435	e4463013-b866-45d6-aeeb-20d5e6c07197	30820325-711c-4d7b-b364-be73c78a43b0	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	71626749-ef62-42f4-9d40-35097f05dfb9	SERVICE_PAYMENT	9000.00	VND	CASH	2026-10-22 00:45:32.201665+00	2026-10-22T00:45:32.201665Z	2026-10-22	Asia/Bangkok	06a143f5680cb9fe605bf302eb290da6b9a70c816ad97f607a5f444af4896266	b2024eacda8c54c17d2e5a359d0189bdd08360144b2da9e2828592f1fbcb0652	2026-10-22 00:45:32.416676+00
7649919b-93b2-4aea-906c-6e69d5cc3afb	c3d7ad2a-36de-46bd-ba16-bcfba18bc7be	88e40197-ac94-4a80-be9b-f94934eea7ff	1b03d80a-4b4b-4ee1-abea-a19b3b7cd9f6	d7c7e3ea-2a19-4def-8ce9-aaf7b4909db5	8dd25d75-6d29-4967-ac6e-d54c6a8dd490	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	eab4cb0a-de3f-495c-9304-73ff1f18b5d4	SERVICE_PAYMENT	9000.00	VND	CASH	2026-10-22 00:45:35.739515+00	2026-10-22T00:45:35.739515Z	2026-10-22	Asia/Bangkok	9323a2b3d4a79eaa9fe489083ab025029b75db0c0dba27cac2311e6e5c9e0d99	be37fd184d94d29675c82c207ccb2171927c41b712de0e3e32c0ef8d29ffb8c3	2026-10-22 00:45:38.57602+00
e55fe586-5ddb-48d7-9338-1836225c52c5	9d0d51fd-ce4a-4ff5-86df-25cff72f9a6b	2659a221-7b81-4a18-be74-2c0a873edad7	ad6bd3f9-53dd-49ef-9750-27bd01a6b24a	8f3e4603-9e61-481a-8cb5-3b485b4c8560	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:45:38.928545+00	2026-10-22T00:45:38.928545Z	2026-10-22	Asia/Bangkok	d51fd26bba903b3dcb6d8a334867007e8e7077ff2ff54612466c7514bb2dce33	7876ae48102def6e4cb6a1ebbed92c55e47093e1d416609edaa73d0a6ef8b244	2026-10-22 00:45:40.691567+00
b52f3117-223d-4432-ac82-4e7f06ccee62	0520a8ef-7cbc-42c0-bce1-9e6a5a314a8f	e4c1681c-a2ac-40a9-9a63-e871388725fe	4cfe09d7-41d4-4d3d-8df0-6a4eff0dd8cc	8f3e4603-9e61-481a-8cb5-3b485b4c8560	8fbf50e4-67ca-42f6-96ac-c15d6aab61c8	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	037fcbd9-127b-44d6-96ee-73c571763c5e	SERVICE_PAYMENT	150000.00	VND	CASH	2026-10-22 00:45:44.434798+00	2026-10-22T00:45:44.434798Z	2026-10-22	Asia/Bangkok	9f024a9f21a4648017945e09cfca637ad7133ce086bd7e21b0da97b7fadbf63f	36adb425be88d6f50a08eab08f66595457dac68d7ed34852e53fb9bcc2c3e5ab	2026-10-22 00:45:44.812331+00
add16c27-3994-4ef8-a7a6-80c3a01f5090	fa53bcf9-3b37-4ead-9583-7ed10ad93cbe	70cfd0e8-1d1d-41a3-93c2-b7e0d9eccbb4	cc950a37-4205-444f-a620-352b617b09fe	da0ea8e1-ecc5-43cf-8f13-d2c325b6fbf7	6f75b635-a491-4191-99b3-1df16c1a3832	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	250352fd-6ae9-4108-a147-2d76e9b49eb9	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:45:47.836519+00	2026-10-22T00:45:47.836519Z	2026-10-22	Asia/Bangkok	de0561456ab54b9b71f7009b4a27768d355d45be86903f5b5b02ede2704a1eb0	0e8bcf513b6662bc7e4503b980c85c14a78da4b91585795af3736ac6af12e702	2026-10-22 00:45:49.007112+00
bced71a5-0b2f-45d2-87d2-9b41bf72d8b3	33f4d6d9-99f8-407c-b685-4f95b4a0ac95	6a11c00e-afd8-4eb9-9a6f-6c26ffa86d92	e62ca364-6afa-4a41-a7cb-e1654821950d	86874f73-ccd3-492e-ad59-d064e2520f62	1270368c-7e70-40eb-852c-b58d7b052cf7	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	a4fde265-3562-4528-8ea6-0db0e835c1d6	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:45:52.492536+00	2026-10-22T00:45:52.492536Z	2026-10-22	Asia/Bangkok	ae989aab3c9996220c8028312032a41e0921a821a4693f6498ae59947e8c3be7	b973bac159ae1af8a1a2b5930a4667a1661a8090651d349ec7132989bedd52a7	2026-10-22 00:45:53.104157+00
a33cb5ed-d048-4908-a7ec-2ae3f8416d77	608193a2-1f8e-493f-a59b-a7d83eb512e7	fbbb7a7c-7227-4118-a21c-66f30fb3ad72	1d51b9bf-3af2-4263-8b04-b868a82bffa9	a806fda3-0b26-4bc1-96fd-85f6a74ca294	3ad6444d-54b2-467a-9e25-beb664d3975f	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	b264777a-2a76-4793-b4ac-91e6a8779f0d	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:52:51.000334+00	2026-10-22T00:52:51.000334Z	2026-10-22	Asia/Bangkok	f9e10e09d2dcf1a5844ddc5ee500ac3e27181f6241dacf5ef99e801303d6269c	a90e4eb1324a8395d28825e556dc39d68ab843fb3a0426f95e37c3fb2a57abdf	2026-10-22 00:52:51.694473+00
65284733-97d8-4108-9d85-6fa449d75310	b136373c-a447-4817-a839-e28c940fd939	663b13cc-9758-4615-a7a1-d401ddbe6e21	ea2b7648-09a0-4906-9ee4-ca1224c29368	4baa16fa-2af8-43b0-9b19-1f32c7f275ac	83ee7cd5-ccb4-4005-96f3-42abb0261400	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	74fcae55-0ff2-4f0c-ae64-ea847dc3a66b	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 00:56:55.535559+00	2026-10-22T00:56:55.535559Z	2026-10-22	Asia/Bangkok	f2fb24498570e404413403c5f3889017d10b7d8271c71de40899f5d2ef0949df	1663442ba06f9d46347465331fd8f3f85ea366c09ba040b819e008914d14200c	2026-10-22 00:56:56.941215+00
c3802b07-5f4f-47e8-afa2-0d08b2d9fe2e	dee0992f-cbc0-4af3-aeca-3d1f806333b3	8e705b7f-804d-48d8-a0f4-10a8a4df1e9d	33467dbe-11a2-4c7a-8742-fa27f9fb2641	5c325e27-b74d-49d7-b8ef-2c763630967b	fc4fe9c4-a34c-4252-bd0b-3e171604d3b5	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	b9de10f8-44e8-4e44-8637-09135b5691e8	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 10:53:22.794782+00	2026-10-22T10:53:22.794782Z	2026-10-22	Asia/Bangkok	4093b96f6a4797361689d0d3477b5ad64de04ebfa5344f646836703565391b1d	5e454645054c6fc97d036b689c43b2d2119bb5eaaaddc8b270162f53d444e8d1	2026-10-22 10:53:24.825909+00
2dc1ba9c-e65f-4396-be2e-f94e5d0f4a16	b2531d42-b612-4cb9-a5f0-0781e3d5c106	a5d788e6-3ae0-4ed2-bb3d-0383e921a628	8f673d22-4cef-4eb7-8a29-134fdc8ba990	6941b792-a948-4acb-b52f-6cdcb26b5956	a2c54b05-3010-4b8d-93ee-67fb06469d77	6c568d0b-debd-44ae-999d-3942e1ecfe8b	OUTPATIENT_VISIT	f91a4cad-6e7a-4b23-9f3f-08187f1687ec	SERVICE_PAYMENT	100000.00	VND	CASH	2026-10-22 11:41:34.165631+00	2026-10-22T11:41:34.165631Z	2026-10-22	Asia/Bangkok	0a2bc64485a8e04f577b64d5ff125f3737327222d863fff369523e565073fecc	9626c6710d200ee564618203e1abeb6dfbba3c46f191f9f8306aac2e513f03ab	2026-10-22 11:41:35.839665+00
\.


--
-- Data for Name: report_cash_refund; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_cash_refund (refund_transaction_id, original_transaction_id, first_event_id, account_id, patient_id, department_id, care_episode_type, care_episode_id, amount, currency, completed_at_iso, business_date, report_zone, source_fingerprint, state, reason_code, original_business_date, classification, created_at, applied_at, next_attempt_at, recovery_attempts) FROM stdin;
\.


--
-- Data for Name: report_cash_refund_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_cash_refund_delivery (event_id, refund_transaction_id, envelope_fingerprint, created_at) FROM stdin;
\.


--
-- Data for Name: report_final_deposit_release; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_final_deposit_release (recognition_operation_id, original_transaction_id, amount) FROM stdin;
\.


--
-- Data for Name: report_final_recognition; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_final_recognition (recognition_operation_id, account_id, settlement_id, first_event_id, state, reason_code, payload_snapshot, payload_fingerprint, fact_fingerprint, report_zone, business_date, completed_at_iso, next_attempt_at, recovery_attempts, created_at, applied_at) FROM stdin;
\.


--
-- Data for Name: report_final_recognition_contribution; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_final_recognition_contribution (recognition_operation_id, business_date, currency, report_zone, department_id, earned_revenue, deposit_released) FROM stdin;
\.


--
-- Data for Name: report_final_recognition_daily; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_final_recognition_daily (business_date, currency, report_zone, department_id, earned_revenue, deposit_released) FROM stdin;
\.


--
-- Data for Name: report_final_recognition_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_final_recognition_delivery (event_id, recognition_operation_id, envelope_fingerprint, created_at) FROM stdin;
\.


--
-- Data for Name: report_gross_cash_daily; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_gross_cash_daily (scope_id, business_date, currency, report_zone, department_id, classification, gross_receipts, receipt_count, updated_at) FROM stdin;
3ce74289-8407-4ff4-b98f-a17c6d8a2930	2026-10-22	VND	Asia/Bangkok	b134eebd-d327-4718-8bef-92525164178d	SERVICE_PAYMENT	100000.00	1	2026-10-22 00:40:18.480286+00
5e1d337b-2ef6-42a4-b8e6-7313c5cc07ad	2026-10-22	VND	Asia/Bangkok	\N	SERVICE_PAYMENT	1468000.00	15	2026-10-22 11:41:35.839665+00
9f2f6714-06ad-49e1-8ddb-565a076408a4	2026-10-22	VND	Asia/Bangkok	6c568d0b-debd-44ae-999d-3942e1ecfe8b	SERVICE_PAYMENT	1368000.00	14	2026-10-22 11:41:35.839665+00
\.


--
-- Data for Name: report_medical_discharge; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_medical_discharge (admission_id, patient_id, summary_id, approved_by, approved_at_iso, first_event_id, occurred_at_iso, correlation_id, payload_hash, status) FROM stdin;
\.


--
-- Data for Name: report_medical_discharge_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_medical_discharge_delivery (event_id, admission_id, envelope_hash) FROM stdin;
\.


--
-- Data for Name: report_outpatient_coverage; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_outpatient_coverage (coverage_id, covered_from, covered_to, department_id, zone_id, currency, source_references, source_observed_at, reconciled_at, accepted_at, accepted_by, acceptance_reference, local_evidence_fingerprint, acceptance_order) FROM stdin;
\.


--
-- Data for Name: report_outpatient_coverage_acceptance; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_outpatient_coverage_acceptance (request_id, coverage_id, actor_id, request_payload, coverage_payload, created_at) FROM stdin;
\.


--
-- Data for Name: report_receivable_current; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_receivable_current (account_id, settlement_id, settlement_version, updated_at) FROM stdin;
\.


--
-- Data for Name: report_receivable_stock; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_receivable_stock (currency, report_zone, department_id, outstanding_receivable, updated_at) FROM stdin;
\.


--
-- Data for Name: report_refund_cash_daily; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_refund_cash_daily (scope_id, business_date, currency, report_zone, department_id, classification, completed_refunds, refund_count, updated_at) FROM stdin;
\.


--
-- Data for Name: report_settlement_delivery; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_settlement_delivery (event_id, settlement_id, envelope_fingerprint, accepted_at) FROM stdin;
\.


--
-- Data for Name: report_settlement_snapshot; Type: TABLE DATA; Schema: public; Owner: test
--

COPY public.report_settlement_snapshot (settlement_id, account_id, settlement_version, supersedes_settlement_id, snapshot_version, payload, fact_fingerprint, snapshot_fingerprint, accepted_at) FROM stdin;
\.


--
-- Name: report_outpatient_coverage_acceptance_order_seq; Type: SEQUENCE SET; Schema: public; Owner: test
--

SELECT pg_catalog.setval('public.report_outpatient_coverage_acceptance_order_seq', 1, false);


--
-- Name: cash_replay_generation cash_replay_generation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_generation
    ADD CONSTRAINT cash_replay_generation_pkey PRIMARY KEY (generation_id);


--
-- Name: cash_replay_input cash_replay_input_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_input
    ADD CONSTRAINT cash_replay_input_pkey PRIMARY KEY (generation_id, transaction_id);


--
-- Name: cash_replay_receipt cash_replay_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_receipt
    ADD CONSTRAINT cash_replay_receipt_pkey PRIMARY KEY (generation_id, transaction_id);


--
-- Name: cash_replay_scope cash_replay_scope_generation_id_business_date_currency_repo_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_scope
    ADD CONSTRAINT cash_replay_scope_generation_id_business_date_currency_repo_key UNIQUE NULLS NOT DISTINCT (generation_id, business_date, currency, report_zone, department_id, classification);


--
-- Name: daily_financial_report daily_financial_report_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.daily_financial_report
    ADD CONSTRAINT daily_financial_report_pkey PRIMARY KEY (report_id);


--
-- Name: daily_operational_report daily_operational_report_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.daily_operational_report
    ADD CONSTRAINT daily_operational_report_pkey PRIMARY KEY (report_id);


--
-- Name: daily_visit_report daily_visit_report_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.daily_visit_report
    ADD CONSTRAINT daily_visit_report_pkey PRIMARY KEY (report_id);


--
-- Name: drug_statistic drug_statistic_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.drug_statistic
    ADD CONSTRAINT drug_statistic_pkey PRIMARY KEY (statistic_id);


--
-- Name: financial_archive_restore financial_archive_restore_generation_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_archive_restore
    ADD CONSTRAINT financial_archive_restore_generation_id_key UNIQUE (generation_id);


--
-- Name: financial_archive_restore financial_archive_restore_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_archive_restore
    ADD CONSTRAINT financial_archive_restore_pkey PRIMARY KEY (archive_fingerprint);


--
-- Name: financial_contribution financial_contribution_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_contribution
    ADD CONSTRAINT financial_contribution_pkey PRIMARY KEY (contribution_id);


--
-- Name: financial_generation_acceptance financial_generation_acceptance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_generation_acceptance
    ADD CONSTRAINT financial_generation_acceptance_pkey PRIMARY KEY (generation_id);


--
-- Name: financial_publication_audit financial_publication_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_publication_audit
    ADD CONSTRAINT financial_publication_audit_pkey PRIMARY KEY (command_id);


--
-- Name: financial_report_generation financial_report_generation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_report_generation
    ADD CONSTRAINT financial_report_generation_pkey PRIMARY KEY (generation_id);


--
-- Name: financial_report_publication financial_report_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_report_publication
    ADD CONSTRAINT financial_report_publication_pkey PRIMARY KEY (currency, report_zone);


--
-- Name: financial_source_rebuild_day financial_source_rebuild_day_generation_id_business_date_de_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_source_rebuild_day
    ADD CONSTRAINT financial_source_rebuild_day_generation_id_business_date_de_key UNIQUE NULLS NOT DISTINCT (generation_id, business_date, department_id);


--
-- Name: financial_source_rebuild_input financial_source_rebuild_input_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_source_rebuild_input
    ADD CONSTRAINT financial_source_rebuild_input_pkey PRIMARY KEY (generation_id, source_kind, source_id);


--
-- Name: financial_source_rebuild financial_source_rebuild_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_source_rebuild
    ADD CONSTRAINT financial_source_rebuild_pkey PRIMARY KEY (generation_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: monthly_revenue_report monthly_revenue_report_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.monthly_revenue_report
    ADD CONSTRAINT monthly_revenue_report_pkey PRIMARY KEY (report_id);


--
-- Name: operational_contribution operational_contribution_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_contribution
    ADD CONSTRAINT operational_contribution_pkey PRIMARY KEY (contribution_id);


--
-- Name: operational_delivery operational_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_delivery
    ADD CONSTRAINT operational_delivery_pkey PRIMARY KEY (event_id, metric_type);


--
-- Name: operational_event_journal operational_event_journal_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_event_journal
    ADD CONSTRAINT operational_event_journal_pkey PRIMARY KEY (event_id);


--
-- Name: operational_generation_acceptance operational_generation_acceptance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_generation_acceptance
    ADD CONSTRAINT operational_generation_acceptance_pkey PRIMARY KEY (generation_id);


--
-- Name: operational_publication_audit operational_publication_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_publication_audit
    ADD CONSTRAINT operational_publication_audit_pkey PRIMARY KEY (command_id);


--
-- Name: operational_replay_contribution operational_replay_contribution_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_contribution
    ADD CONSTRAINT operational_replay_contribution_pkey PRIMARY KEY (generation_id, source_type, source_id, source_revision, metric_type);


--
-- Name: operational_replay_generation operational_replay_generation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_generation
    ADD CONSTRAINT operational_replay_generation_pkey PRIMARY KEY (generation_id);


--
-- Name: operational_replay_input operational_replay_input_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_input
    ADD CONSTRAINT operational_replay_input_pkey PRIMARY KEY (generation_id, event_id);


--
-- Name: operational_replay_scope operational_replay_scope_generation_id_metric_date_departme_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_scope
    ADD CONSTRAINT operational_replay_scope_generation_id_metric_date_departme_key UNIQUE NULLS NOT DISTINCT (generation_id, metric_date, department_id, metric_type);


--
-- Name: operational_report_publication operational_report_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_report_publication
    ADD CONSTRAINT operational_report_publication_pkey PRIMARY KEY (report_kind);


--
-- Name: operational_source_snapshot operational_source_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_source_snapshot
    ADD CONSTRAINT operational_source_snapshot_pkey PRIMARY KEY (source_type, source_id, source_revision, event_type);


--
-- Name: payment_contribution payment_contribution_completed_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_contribution
    ADD CONSTRAINT payment_contribution_completed_event_id_key UNIQUE (completed_event_id);


--
-- Name: payment_contribution payment_contribution_failed_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_contribution
    ADD CONSTRAINT payment_contribution_failed_event_id_key UNIQUE (failed_event_id);


--
-- Name: payment_contribution payment_contribution_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.payment_contribution
    ADD CONSTRAINT payment_contribution_pkey PRIMARY KEY (invoice_id);


--
-- Name: prescription_fill_receipt prescription_fill_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.prescription_fill_receipt
    ADD CONSTRAINT prescription_fill_receipt_pkey PRIMARY KEY (prescription_id);


--
-- Name: processed_event processed_event_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id);


--
-- Name: refund_replay_fact refund_replay_fact_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_fact
    ADD CONSTRAINT refund_replay_fact_pkey PRIMARY KEY (generation_id, refund_transaction_id);


--
-- Name: refund_replay_generation refund_replay_generation_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_generation
    ADD CONSTRAINT refund_replay_generation_pkey PRIMARY KEY (generation_id);


--
-- Name: refund_replay_input refund_replay_input_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_input
    ADD CONSTRAINT refund_replay_input_pkey PRIMARY KEY (generation_id, refund_transaction_id);


--
-- Name: refund_replay_scope refund_replay_scope_generation_id_business_date_currency_re_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_scope
    ADD CONSTRAINT refund_replay_scope_generation_id_business_date_currency_re_key UNIQUE NULLS NOT DISTINCT (generation_id, business_date, currency, report_zone, department_id, classification);


--
-- Name: report_admission_delivery report_admission_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_admission_delivery
    ADD CONSTRAINT report_admission_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: report_admission_fact report_admission_fact_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_admission_fact
    ADD CONSTRAINT report_admission_fact_pkey PRIMARY KEY (admission_id, fact_type);


--
-- Name: report_admission_target report_admission_target_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_admission_target
    ADD CONSTRAINT report_admission_target_pkey PRIMARY KEY (admission_id);


--
-- Name: report_cash_delivery report_cash_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_cash_delivery
    ADD CONSTRAINT report_cash_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: report_cash_receipt report_cash_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_cash_receipt
    ADD CONSTRAINT report_cash_receipt_pkey PRIMARY KEY (transaction_id);


--
-- Name: report_cash_refund_delivery report_cash_refund_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_cash_refund_delivery
    ADD CONSTRAINT report_cash_refund_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: report_cash_refund report_cash_refund_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_cash_refund
    ADD CONSTRAINT report_cash_refund_pkey PRIMARY KEY (refund_transaction_id);


--
-- Name: report_final_deposit_release report_final_deposit_release_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_deposit_release
    ADD CONSTRAINT report_final_deposit_release_pkey PRIMARY KEY (recognition_operation_id, original_transaction_id);


--
-- Name: report_final_recognition report_final_recognition_account_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition
    ADD CONSTRAINT report_final_recognition_account_id_key UNIQUE (account_id);


--
-- Name: report_final_recognition_contribution report_final_recognition_cont_recognition_operation_id_depa_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition_contribution
    ADD CONSTRAINT report_final_recognition_cont_recognition_operation_id_depa_key UNIQUE NULLS NOT DISTINCT (recognition_operation_id, department_id);


--
-- Name: report_final_recognition_daily report_final_recognition_dail_business_date_currency_report_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition_daily
    ADD CONSTRAINT report_final_recognition_dail_business_date_currency_report_key UNIQUE NULLS NOT DISTINCT (business_date, currency, report_zone, department_id);


--
-- Name: report_final_recognition_delivery report_final_recognition_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition_delivery
    ADD CONSTRAINT report_final_recognition_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: report_final_recognition report_final_recognition_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition
    ADD CONSTRAINT report_final_recognition_pkey PRIMARY KEY (recognition_operation_id);


--
-- Name: report_final_recognition report_final_recognition_settlement_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition
    ADD CONSTRAINT report_final_recognition_settlement_id_key UNIQUE (settlement_id);


--
-- Name: report_gross_cash_daily report_gross_cash_daily_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_gross_cash_daily
    ADD CONSTRAINT report_gross_cash_daily_pkey PRIMARY KEY (scope_id);


--
-- Name: report_medical_discharge_delivery report_medical_discharge_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_medical_discharge_delivery
    ADD CONSTRAINT report_medical_discharge_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: report_medical_discharge report_medical_discharge_first_event_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_medical_discharge
    ADD CONSTRAINT report_medical_discharge_first_event_id_key UNIQUE (first_event_id);


--
-- Name: report_medical_discharge report_medical_discharge_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_medical_discharge
    ADD CONSTRAINT report_medical_discharge_pkey PRIMARY KEY (admission_id);


--
-- Name: report_medical_discharge report_medical_discharge_summary_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_medical_discharge
    ADD CONSTRAINT report_medical_discharge_summary_id_key UNIQUE (summary_id);


--
-- Name: report_outpatient_coverage_acceptance report_outpatient_coverage_acceptance_coverage_id_key; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_outpatient_coverage_acceptance
    ADD CONSTRAINT report_outpatient_coverage_acceptance_coverage_id_key UNIQUE (coverage_id);


--
-- Name: report_outpatient_coverage report_outpatient_coverage_acceptance_order_unique; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_outpatient_coverage
    ADD CONSTRAINT report_outpatient_coverage_acceptance_order_unique UNIQUE (acceptance_order);


--
-- Name: report_outpatient_coverage_acceptance report_outpatient_coverage_acceptance_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_outpatient_coverage_acceptance
    ADD CONSTRAINT report_outpatient_coverage_acceptance_pkey PRIMARY KEY (request_id);


--
-- Name: report_outpatient_coverage report_outpatient_coverage_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_outpatient_coverage
    ADD CONSTRAINT report_outpatient_coverage_pkey PRIMARY KEY (coverage_id);


--
-- Name: report_receivable_current report_receivable_current_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_receivable_current
    ADD CONSTRAINT report_receivable_current_pkey PRIMARY KEY (account_id);


--
-- Name: report_refund_cash_daily report_refund_cash_daily_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_refund_cash_daily
    ADD CONSTRAINT report_refund_cash_daily_pkey PRIMARY KEY (scope_id);


--
-- Name: report_settlement_delivery report_settlement_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_settlement_delivery
    ADD CONSTRAINT report_settlement_delivery_pkey PRIMARY KEY (event_id);


--
-- Name: report_settlement_snapshot report_settlement_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_settlement_snapshot
    ADD CONSTRAINT report_settlement_snapshot_pkey PRIMARY KEY (settlement_id);


--
-- Name: report_settlement_snapshot uq_report_settlement_revision; Type: CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_settlement_snapshot
    ADD CONSTRAINT uq_report_settlement_revision UNIQUE (account_id, settlement_version);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_cash_receipt_account_transaction; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_receipt_account_transaction ON public.report_cash_receipt USING btree (account_id, transaction_id);


--
-- Name: idx_cash_receipt_invoice; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_receipt_invoice ON public.report_cash_receipt USING btree (invoice_id);


--
-- Name: idx_cash_receipt_period; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_receipt_period ON public.report_cash_receipt USING btree (business_date, currency, department_id);


--
-- Name: idx_cash_refund_applied_account_transaction; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_refund_applied_account_transaction ON public.report_cash_refund USING btree (account_id, refund_transaction_id) WHERE ((state)::text = 'APPLIED'::text);


--
-- Name: idx_cash_refund_delivery_source; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_refund_delivery_source ON public.report_cash_refund_delivery USING btree (refund_transaction_id);


--
-- Name: idx_cash_refund_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_refund_original ON public.report_cash_refund USING btree (original_transaction_id, state);


--
-- Name: idx_cash_refund_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_refund_pending ON public.report_cash_refund USING btree (next_attempt_at, created_at) WHERE ((state)::text = 'PENDING'::text);


--
-- Name: idx_cash_replay_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_cash_replay_pending ON public.cash_replay_input USING btree (generation_id, transaction_id) WHERE (NOT applied);


--
-- Name: idx_drug_statistic_date; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_drug_statistic_date ON public.drug_statistic USING btree (report_date);


--
-- Name: idx_final_recognition_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_final_recognition_pending ON public.report_final_recognition USING btree (next_attempt_at, recognition_operation_id) WHERE ((state)::text = 'PENDING'::text);


--
-- Name: idx_final_release_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_final_release_original ON public.report_final_deposit_release USING btree (original_transaction_id);


--
-- Name: idx_financial_contribution_episode; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_financial_contribution_episode ON public.financial_contribution USING btree (care_episode_type, care_episode_id);


--
-- Name: idx_financial_contribution_event_id; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_financial_contribution_event_id ON public.financial_contribution USING btree (event_id);


--
-- Name: idx_financial_contribution_period; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_financial_contribution_period ON public.financial_contribution USING btree (business_date, department_id);


--
-- Name: idx_operational_contribution_episode; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_operational_contribution_episode ON public.operational_contribution USING btree (care_episode_type, care_episode_id);


--
-- Name: idx_operational_contribution_event_id; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_operational_contribution_event_id ON public.operational_contribution USING btree (event_id);


--
-- Name: idx_operational_contribution_period; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_operational_contribution_period ON public.operational_contribution USING btree (metric_date, department_id);


--
-- Name: idx_operational_replay_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_operational_replay_pending ON public.operational_replay_input USING btree (generation_id, event_id) WHERE (NOT applied);


--
-- Name: idx_outpatient_coverage_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_outpatient_coverage_scope ON public.report_outpatient_coverage USING btree (department_id, zone_id, currency, covered_from, covered_to, accepted_at);


--
-- Name: idx_refund_replay_original; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_refund_replay_original ON public.refund_replay_input USING btree (generation_id, original_transaction_id, state);


--
-- Name: idx_refund_replay_pending; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_refund_replay_pending ON public.refund_replay_input USING btree (generation_id, refund_transaction_id) WHERE (NOT processed);


--
-- Name: idx_report_settlement_predecessor; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX idx_report_settlement_predecessor ON public.report_settlement_snapshot USING btree (supersedes_settlement_id);


--
-- Name: ix_cash_replay_telemetry; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_cash_replay_telemetry ON public.cash_replay_generation USING btree (status, created_at) INCLUDE (source_receipts, applied_receipts);


--
-- Name: ix_financial_publication_audit_generation; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_financial_publication_audit_generation ON public.financial_publication_audit USING btree (generation_id);


--
-- Name: ix_operational_legacy_unverified; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_operational_legacy_unverified ON public.operational_source_snapshot USING btree (evidence_state) WHERE ((evidence_state)::text = 'LEGACY_UNVERIFIED'::text);


--
-- Name: ix_operational_publication_audit_generation; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_operational_publication_audit_generation ON public.operational_publication_audit USING btree (generation_id);


--
-- Name: ix_operational_replay_telemetry; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_operational_replay_telemetry ON public.operational_replay_generation USING btree (status, created_at) INCLUDE (source_events, applied_events);


--
-- Name: ix_operational_snapshot_read; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_operational_snapshot_read ON public.operational_replay_scope USING btree (generation_id, department_id, metric_date, metric_type) INCLUDE (numeric_value);


--
-- Name: ix_report_admission_delivery_target; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_report_admission_delivery_target ON public.report_admission_delivery USING btree (admission_id);


--
-- Name: ix_report_admission_fact_pending_close; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_report_admission_fact_pending_close ON public.report_admission_fact USING btree (admission_id) WHERE ((fact_type)::text = 'CLOSED'::text);


--
-- Name: ix_report_pending_close_observed; Type: INDEX; Schema: public; Owner: test
--

CREATE INDEX ix_report_pending_close_observed ON public.report_admission_fact USING btree (created_at, admission_id) WHERE ((fact_type)::text = 'CLOSED'::text);


--
-- Name: uq_daily_financial_report_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_daily_financial_report_scope ON public.daily_financial_report USING btree (report_date, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_daily_operational_report_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_daily_operational_report_scope ON public.daily_operational_report USING btree (report_date, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_daily_report_date_dept; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_daily_report_date_dept ON public.daily_visit_report USING btree (report_date, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_drug_statistic_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_drug_statistic_scope ON public.drug_statistic USING btree (drug_id, report_date, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_financial_contribution_business_operation; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_financial_contribution_business_operation ON public.financial_contribution USING btree (source_type, source_id, source_revision, contribution_type, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_financial_report_day_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_financial_report_day_scope ON public.financial_report_day USING btree (generation_id, department_id, business_date) INCLUDE (cash_received, deposit_liability, earned_revenue, refunds, outstanding_receivable) NULLS NOT DISTINCT;


--
-- Name: uq_gross_cash_daily_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_gross_cash_daily_scope ON public.report_gross_cash_daily USING btree (business_date, currency, report_zone, department_id, classification) NULLS NOT DISTINCT;


--
-- Name: uq_monthly_revenue_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_monthly_revenue_scope ON public.monthly_revenue_report USING btree (year, month, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_operational_contribution_business_operation; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_operational_contribution_business_operation ON public.operational_contribution USING btree (source_type, source_id, source_revision, metric_type, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_operational_source_metric; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_operational_source_metric ON public.operational_contribution USING btree (source_type, source_id, source_revision, metric_type);


--
-- Name: uq_refund_cash_daily_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_refund_cash_daily_scope ON public.report_refund_cash_daily USING btree (business_date, currency, report_zone, department_id, classification) NULLS NOT DISTINCT;


--
-- Name: uq_report_receivable_stock_scope; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_report_receivable_stock_scope ON public.report_receivable_stock USING btree (currency, report_zone, department_id) NULLS NOT DISTINCT;


--
-- Name: uq_report_settlement_successor; Type: INDEX; Schema: public; Owner: test
--

CREATE UNIQUE INDEX uq_report_settlement_successor ON public.report_settlement_snapshot USING btree (supersedes_settlement_id) WHERE (supersedes_settlement_id IS NOT NULL);


--
-- Name: operational_replay_contribution accepted_operational_fact_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER accepted_operational_fact_immutable BEFORE INSERT OR DELETE OR UPDATE ON public.operational_replay_contribution FOR EACH ROW EXECUTE FUNCTION public.guard_accepted_operational_generation();


--
-- Name: operational_replay_generation accepted_operational_generation_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER accepted_operational_generation_immutable BEFORE INSERT OR DELETE OR UPDATE ON public.operational_replay_generation FOR EACH ROW EXECUTE FUNCTION public.guard_accepted_operational_generation();


--
-- Name: operational_replay_input accepted_operational_input_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER accepted_operational_input_immutable BEFORE INSERT OR DELETE OR UPDATE ON public.operational_replay_input FOR EACH ROW EXECUTE FUNCTION public.guard_accepted_operational_generation();


--
-- Name: operational_replay_scope accepted_operational_scope_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER accepted_operational_scope_immutable BEFORE INSERT OR DELETE OR UPDATE ON public.operational_replay_scope FOR EACH ROW EXECUTE FUNCTION public.guard_accepted_operational_generation();


--
-- Name: financial_generation_acceptance financial_acceptance_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_acceptance_immutable BEFORE DELETE OR UPDATE ON public.financial_generation_acceptance FOR EACH ROW EXECUTE FUNCTION public.reject_financial_publication_audit_mutation();


--
-- Name: financial_archive_restore financial_archive_restore_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_archive_restore_immutable BEFORE DELETE OR UPDATE ON public.financial_archive_restore FOR EACH ROW EXECUTE FUNCTION public.reject_financial_archive_restore_mutation();


--
-- Name: financial_report_day financial_day_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_day_immutable BEFORE INSERT OR DELETE OR UPDATE ON public.financial_report_day FOR EACH ROW EXECUTE FUNCTION public.reject_verified_financial_snapshot_mutation();


--
-- Name: financial_report_generation financial_generation_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_generation_immutable BEFORE DELETE OR UPDATE ON public.financial_report_generation FOR EACH ROW EXECUTE FUNCTION public.reject_verified_financial_snapshot_mutation();


--
-- Name: financial_publication_audit financial_publication_audit_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_publication_audit_immutable BEFORE DELETE OR UPDATE ON public.financial_publication_audit FOR EACH ROW EXECUTE FUNCTION public.reject_financial_publication_audit_mutation();


--
-- Name: financial_source_rebuild_day financial_source_rebuild_day_fence; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_source_rebuild_day_fence BEFORE INSERT OR DELETE OR UPDATE ON public.financial_source_rebuild_day FOR EACH ROW EXECUTE FUNCTION public.fence_financial_source_rebuild_mutation();


--
-- Name: financial_source_rebuild_input financial_source_rebuild_input_fence; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_source_rebuild_input_fence BEFORE INSERT OR DELETE OR UPDATE ON public.financial_source_rebuild_input FOR EACH ROW EXECUTE FUNCTION public.fence_financial_source_rebuild_mutation();


--
-- Name: financial_source_rebuild financial_source_rebuild_metadata_fence; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER financial_source_rebuild_metadata_fence BEFORE DELETE OR UPDATE ON public.financial_source_rebuild FOR EACH ROW EXECUTE FUNCTION public.fence_financial_source_rebuild_mutation();


--
-- Name: operational_generation_acceptance operational_acceptance_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER operational_acceptance_immutable BEFORE DELETE OR UPDATE ON public.operational_generation_acceptance FOR EACH ROW EXECUTE FUNCTION public.reject_operational_publication_evidence_mutation();


--
-- Name: operational_publication_audit operational_audit_immutable; Type: TRIGGER; Schema: public; Owner: test
--

CREATE TRIGGER operational_audit_immutable BEFORE DELETE OR UPDATE ON public.operational_publication_audit FOR EACH ROW EXECUTE FUNCTION public.reject_operational_publication_evidence_mutation();


--
-- Name: cash_replay_input cash_replay_input_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_input
    ADD CONSTRAINT cash_replay_input_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.cash_replay_generation(generation_id);


--
-- Name: cash_replay_receipt cash_replay_receipt_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_receipt
    ADD CONSTRAINT cash_replay_receipt_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.cash_replay_generation(generation_id);


--
-- Name: cash_replay_scope cash_replay_scope_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.cash_replay_scope
    ADD CONSTRAINT cash_replay_scope_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.cash_replay_generation(generation_id);


--
-- Name: financial_archive_restore financial_archive_restore_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_archive_restore
    ADD CONSTRAINT financial_archive_restore_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_source_rebuild(generation_id);


--
-- Name: financial_contribution financial_contribution_original_contribution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_contribution
    ADD CONSTRAINT financial_contribution_original_contribution_id_fkey FOREIGN KEY (original_contribution_id) REFERENCES public.financial_contribution(contribution_id);


--
-- Name: financial_generation_acceptance financial_generation_acceptance_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_generation_acceptance
    ADD CONSTRAINT financial_generation_acceptance_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_report_generation(generation_id);


--
-- Name: financial_publication_audit financial_publication_audit_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_publication_audit
    ADD CONSTRAINT financial_publication_audit_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_generation_acceptance(generation_id);


--
-- Name: financial_publication_audit financial_publication_audit_previous_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_publication_audit
    ADD CONSTRAINT financial_publication_audit_previous_generation_id_fkey FOREIGN KEY (previous_generation_id) REFERENCES public.financial_generation_acceptance(generation_id);


--
-- Name: financial_report_day financial_report_day_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_report_day
    ADD CONSTRAINT financial_report_day_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_report_generation(generation_id);


--
-- Name: financial_report_publication financial_report_publication_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_report_publication
    ADD CONSTRAINT financial_report_publication_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_report_generation(generation_id);


--
-- Name: financial_source_rebuild_day financial_source_rebuild_day_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_source_rebuild_day
    ADD CONSTRAINT financial_source_rebuild_day_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_source_rebuild(generation_id);


--
-- Name: financial_source_rebuild_input financial_source_rebuild_input_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.financial_source_rebuild_input
    ADD CONSTRAINT financial_source_rebuild_input_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.financial_source_rebuild(generation_id);


--
-- Name: operational_delivery operational_delivery_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_delivery
    ADD CONSTRAINT operational_delivery_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.operational_event_journal(event_id);


--
-- Name: operational_generation_acceptance operational_generation_acceptance_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_generation_acceptance
    ADD CONSTRAINT operational_generation_acceptance_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.operational_replay_generation(generation_id);


--
-- Name: operational_publication_audit operational_publication_audit_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_publication_audit
    ADD CONSTRAINT operational_publication_audit_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.operational_generation_acceptance(generation_id);


--
-- Name: operational_publication_audit operational_publication_audit_previous_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_publication_audit
    ADD CONSTRAINT operational_publication_audit_previous_generation_id_fkey FOREIGN KEY (previous_generation_id) REFERENCES public.operational_generation_acceptance(generation_id);


--
-- Name: operational_replay_contribution operational_replay_contribution_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_contribution
    ADD CONSTRAINT operational_replay_contribution_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.operational_replay_generation(generation_id);


--
-- Name: operational_replay_input operational_replay_input_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_input
    ADD CONSTRAINT operational_replay_input_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.operational_replay_generation(generation_id);


--
-- Name: operational_replay_scope operational_replay_scope_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_replay_scope
    ADD CONSTRAINT operational_replay_scope_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.operational_replay_generation(generation_id);


--
-- Name: operational_report_publication operational_report_publication_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.operational_report_publication
    ADD CONSTRAINT operational_report_publication_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.operational_replay_generation(generation_id);


--
-- Name: refund_replay_fact refund_replay_fact_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_fact
    ADD CONSTRAINT refund_replay_fact_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.refund_replay_generation(generation_id);


--
-- Name: refund_replay_generation refund_replay_generation_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_generation
    ADD CONSTRAINT refund_replay_generation_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.cash_replay_generation(generation_id);


--
-- Name: refund_replay_input refund_replay_input_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_input
    ADD CONSTRAINT refund_replay_input_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.refund_replay_generation(generation_id);


--
-- Name: refund_replay_scope refund_replay_scope_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.refund_replay_scope
    ADD CONSTRAINT refund_replay_scope_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.refund_replay_generation(generation_id);


--
-- Name: report_admission_fact report_admission_fact_admission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_admission_fact
    ADD CONSTRAINT report_admission_fact_admission_id_fkey FOREIGN KEY (admission_id) REFERENCES public.report_admission_target(admission_id);


--
-- Name: report_cash_delivery report_cash_delivery_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_cash_delivery
    ADD CONSTRAINT report_cash_delivery_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES public.report_cash_receipt(transaction_id);


--
-- Name: report_cash_refund_delivery report_cash_refund_delivery_refund_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_cash_refund_delivery
    ADD CONSTRAINT report_cash_refund_delivery_refund_transaction_id_fkey FOREIGN KEY (refund_transaction_id) REFERENCES public.report_cash_refund(refund_transaction_id);


--
-- Name: report_final_deposit_release report_final_deposit_release_original_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_deposit_release
    ADD CONSTRAINT report_final_deposit_release_original_transaction_id_fkey FOREIGN KEY (original_transaction_id) REFERENCES public.report_cash_receipt(transaction_id);


--
-- Name: report_final_deposit_release report_final_deposit_release_recognition_operation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_deposit_release
    ADD CONSTRAINT report_final_deposit_release_recognition_operation_id_fkey FOREIGN KEY (recognition_operation_id) REFERENCES public.report_final_recognition(recognition_operation_id);


--
-- Name: report_final_recognition_contribution report_final_recognition_contribu_recognition_operation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition_contribution
    ADD CONSTRAINT report_final_recognition_contribu_recognition_operation_id_fkey FOREIGN KEY (recognition_operation_id) REFERENCES public.report_final_recognition(recognition_operation_id);


--
-- Name: report_final_recognition_delivery report_final_recognition_delivery_recognition_operation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_final_recognition_delivery
    ADD CONSTRAINT report_final_recognition_delivery_recognition_operation_id_fkey FOREIGN KEY (recognition_operation_id) REFERENCES public.report_final_recognition(recognition_operation_id);


--
-- Name: report_medical_discharge report_medical_discharge_admission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_medical_discharge
    ADD CONSTRAINT report_medical_discharge_admission_id_fkey FOREIGN KEY (admission_id) REFERENCES public.report_admission_target(admission_id);


--
-- Name: report_medical_discharge_delivery report_medical_discharge_delivery_admission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_medical_discharge_delivery
    ADD CONSTRAINT report_medical_discharge_delivery_admission_id_fkey FOREIGN KEY (admission_id) REFERENCES public.report_admission_target(admission_id);


--
-- Name: report_outpatient_coverage_acceptance report_outpatient_coverage_acceptance_coverage_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_outpatient_coverage_acceptance
    ADD CONSTRAINT report_outpatient_coverage_acceptance_coverage_id_fkey FOREIGN KEY (coverage_id) REFERENCES public.report_outpatient_coverage(coverage_id);


--
-- Name: report_receivable_current report_receivable_current_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_receivable_current
    ADD CONSTRAINT report_receivable_current_settlement_id_fkey FOREIGN KEY (settlement_id) REFERENCES public.report_settlement_snapshot(settlement_id);


--
-- Name: report_settlement_delivery report_settlement_delivery_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: test
--

ALTER TABLE ONLY public.report_settlement_delivery
    ADD CONSTRAINT report_settlement_delivery_settlement_id_fkey FOREIGN KEY (settlement_id) REFERENCES public.report_settlement_snapshot(settlement_id);


--
-- PostgreSQL database dump complete
--

\unrestrict Gyri9NoKGnnyWjLlWYYO2REOWMioidFV1oZu4FMzGWfArIcEFol1HWhrvLQAZay

--
-- Database "postgres" dump
--

\connect postgres

--
-- PostgreSQL database dump
--

\restrict 4ORudOlq1SROQ0ixlcxX1Yhrx66OUTWbGli0nQw8TMS2HXfG20ilOe9ibFkoPd4

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- PostgreSQL database dump complete
--

\unrestrict 4ORudOlq1SROQ0ixlcxX1Yhrx66OUTWbGli0nQw8TMS2HXfG20ilOe9ibFkoPd4

--
-- Database "test" dump
--

--
-- PostgreSQL database dump
--

\restrict ODTO0p1E2hB1SBWerJWHewS5fz70EJddVbRzOnxaVt8tSQoUVmq6tsl7fF1gllY

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: test; Type: DATABASE; Schema: -; Owner: test
--

CREATE DATABASE test WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE test OWNER TO test;

\unrestrict ODTO0p1E2hB1SBWerJWHewS5fz70EJddVbRzOnxaVt8tSQoUVmq6tsl7fF1gllY
\connect test
\restrict ODTO0p1E2hB1SBWerJWHewS5fz70EJddVbRzOnxaVt8tSQoUVmq6tsl7fF1gllY

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- PostgreSQL database dump complete
--

\unrestrict ODTO0p1E2hB1SBWerJWHewS5fz70EJddVbRzOnxaVt8tSQoUVmq6tsl7fF1gllY

--
-- PostgreSQL database cluster dump complete
--
