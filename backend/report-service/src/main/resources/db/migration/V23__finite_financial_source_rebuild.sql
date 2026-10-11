-- Local five-metric rebuild only. No V20 write, publication, historical approval or read switch.
CREATE TABLE financial_source_rebuild (
    generation_id UUID PRIMARY KEY,
    status VARCHAR(12) NOT NULL DEFAULT 'BUILDING' CHECK(status IN ('BUILDING','VERIFIED','FAILED')),
    snapshot_version INTEGER NOT NULL DEFAULT 1 CHECK(snapshot_version=1),
    projector_version INTEGER NOT NULL DEFAULT 1 CHECK(projector_version=1),
    covered_from DATE NOT NULL CHECK(covered_from >= DATE '2000-01-01'),
    covered_to DATE NOT NULL CHECK(covered_to >= covered_from AND covered_to <= DATE '2100-12-31' AND covered_to-covered_from<366),
    report_zone VARCHAR(80) NOT NULL CHECK(length(btrim(report_zone))>0),
    source_observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_inputs INTEGER NOT NULL CHECK(source_inputs BETWEEN 0 AND 10000),
    unresolved_inputs INTEGER NOT NULL CHECK(unresolved_inputs BETWEEN 0 AND source_inputs),
    processed_days INTEGER NOT NULL DEFAULT 0 CHECK(processed_days>=0 AND processed_days<=covered_to-covered_from+1),
    manifest_fingerprint VARCHAR(64) NOT NULL CHECK(manifest_fingerprint ~ '^[a-f0-9]{64}$'),
    manifest_sealed BOOLEAN NOT NULL DEFAULT false,
    aggregate_fingerprint VARCHAR(64) CHECK(aggregate_fingerprint ~ '^[a-f0-9]{64}$'),
    reason_code VARCHAR(80),
    reconciled_at TIMESTAMPTZ,
    CHECK((status='FAILED')=(reason_code IS NOT NULL)),
    CHECK(status<>'VERIFIED' OR (manifest_sealed AND unresolved_inputs=0 AND processed_days=covered_to-covered_from+1
        AND aggregate_fingerprint IS NOT NULL AND reconciled_at IS NOT NULL AND reconciled_at>=source_observed_at))
);
CREATE TABLE financial_source_rebuild_input (
    generation_id UUID NOT NULL REFERENCES financial_source_rebuild(generation_id),
    source_kind VARCHAR(16) NOT NULL CHECK(source_kind IN ('RECEIPT','REFUND','FINAL','BALANCE')),
    source_id UUID NOT NULL,
    first_event_id UUID NOT NULL,
    source_state VARCHAR(16) NOT NULL CHECK(source_state IN ('PENDING','APPLIED','REJECTED')),
    source_fingerprint VARCHAR(64) NOT NULL CHECK(source_fingerprint ~ '^[a-f0-9]{64}$'),
    envelope_fingerprint VARCHAR(64) NOT NULL CHECK(envelope_fingerprint ~ '^[a-f0-9]{64}$'),
    fact_snapshot TEXT NOT NULL CHECK(octet_length(fact_snapshot)<=1048576),
    snapshot_fingerprint VARCHAR(64) NOT NULL CHECK(snapshot_fingerprint ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY(generation_id,source_kind,source_id)
);
CREATE TABLE financial_source_rebuild_day (
    generation_id UUID NOT NULL REFERENCES financial_source_rebuild(generation_id),
    business_date DATE NOT NULL,
    department_id UUID,
    cash_received NUMERIC(19,2) NOT NULL CHECK(cash_received>=0),
    deposit_liability NUMERIC(19,2) NOT NULL CHECK(deposit_liability>=0),
    earned_revenue NUMERIC(19,2) NOT NULL CHECK(earned_revenue>=0),
    refunds NUMERIC(19,2) NOT NULL CHECK(refunds>=0),
    outstanding_receivable NUMERIC(19,2) NOT NULL CHECK(outstanding_receivable>=0),
    UNIQUE NULLS NOT DISTINCT(generation_id,business_date,department_id)
);
CREATE FUNCTION fence_financial_source_rebuild_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
CREATE TRIGGER financial_source_rebuild_metadata_fence BEFORE UPDATE OR DELETE ON financial_source_rebuild
    FOR EACH ROW EXECUTE FUNCTION fence_financial_source_rebuild_mutation();
CREATE TRIGGER financial_source_rebuild_input_fence BEFORE INSERT OR UPDATE OR DELETE ON financial_source_rebuild_input
    FOR EACH ROW EXECUTE FUNCTION fence_financial_source_rebuild_mutation();
CREATE TRIGGER financial_source_rebuild_day_fence BEFORE INSERT OR UPDATE OR DELETE ON financial_source_rebuild_day
    FOR EACH ROW EXECUTE FUNCTION fence_financial_source_rebuild_mutation();
