-- Empty read boundary, NOT a source export, replay implementation or accepted publication.
-- Only a reviewed complete five-metric rebuild/publication workflow may populate this namespace.
-- Do not manually promote cash-only V13/V17 or non-final V19 into these rows.
CREATE TABLE financial_report_generation (
    generation_id UUID PRIMARY KEY,
    status VARCHAR(12) NOT NULL CHECK(status IN ('BUILDING','VERIFIED','FAILED')),
    snapshot_version INTEGER NOT NULL CHECK(snapshot_version>0),
    projector_version INTEGER NOT NULL CHECK(projector_version>0),
    covered_from DATE NOT NULL CHECK(covered_from >= DATE '2000-01-01'),
    covered_to DATE NOT NULL CHECK(covered_to >= covered_from AND covered_to <= DATE '2100-12-31'),
    currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
    report_zone VARCHAR(80) NOT NULL CHECK(length(btrim(report_zone))>0),
    source_observed_at TIMESTAMPTZ NOT NULL,
    reconciled_at TIMESTAMPTZ,
    source_inputs BIGINT NOT NULL CHECK(source_inputs>=0),
    applied_inputs BIGINT NOT NULL CHECK(applied_inputs>=0 AND applied_inputs<=source_inputs),
    unresolved_inputs BIGINT NOT NULL CHECK(unresolved_inputs>=0),
    manifest_fingerprint VARCHAR(64) NOT NULL CHECK(manifest_fingerprint ~ '^[a-f0-9]{64}$'),
    aggregate_fingerprint VARCHAR(64) CHECK(aggregate_fingerprint ~ '^[a-f0-9]{64}$'),
    CHECK(reconciled_at IS NULL OR reconciled_at >= source_observed_at),
    CHECK(status <> 'VERIFIED' OR (reconciled_at IS NOT NULL AND aggregate_fingerprint IS NOT NULL
        AND applied_inputs=source_inputs AND unresolved_inputs=0))
);
CREATE TABLE financial_report_day (
    generation_id UUID NOT NULL REFERENCES financial_report_generation(generation_id),
    business_date DATE NOT NULL,
    department_id UUID,
    cash_received NUMERIC(19,2) NOT NULL CHECK(cash_received>=0),
    deposit_liability NUMERIC(19,2) NOT NULL CHECK(deposit_liability>=0),
    earned_revenue NUMERIC(19,2) NOT NULL CHECK(earned_revenue>=0),
    refunds NUMERIC(19,2) NOT NULL CHECK(refunds>=0),
    outstanding_receivable NUMERIC(19,2) NOT NULL CHECK(outstanding_receivable>=0)
);
CREATE UNIQUE INDEX uq_financial_report_day_scope ON financial_report_day(generation_id,department_id,business_date)
    INCLUDE(cash_received,deposit_liability,earned_revenue,refunds,outstanding_receivable) NULLS NOT DISTINCT;
CREATE TABLE financial_report_publication (
    currency VARCHAR(3) NOT NULL,
    report_zone VARCHAR(80) NOT NULL,
    generation_id UUID NOT NULL REFERENCES financial_report_generation(generation_id),
    acceptance_reference VARCHAR(120) NOT NULL CHECK(length(btrim(acceptance_reference))>0),
    published_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(currency,report_zone)
);
-- VERIFIED amounts/metadata are immutable across read transactions. Pointer rollback is separate.
CREATE FUNCTION reject_verified_financial_snapshot_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
CREATE TRIGGER financial_generation_immutable BEFORE UPDATE OR DELETE ON financial_report_generation
    FOR EACH ROW EXECUTE FUNCTION reject_verified_financial_snapshot_mutation();
CREATE TRIGGER financial_day_immutable BEFORE INSERT OR UPDATE OR DELETE ON financial_report_day
    FOR EACH ROW EXECUTE FUNCTION reject_verified_financial_snapshot_mutation();
