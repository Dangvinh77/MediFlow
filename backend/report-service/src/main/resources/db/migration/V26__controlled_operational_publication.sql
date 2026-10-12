-- Existing V10 publications are not silently adopted as operator-approved snapshots.
CREATE TABLE operational_generation_acceptance (
    generation_id UUID PRIMARY KEY REFERENCES operational_replay_generation(generation_id),
    report_kind VARCHAR(12) NOT NULL CHECK(report_kind='SURGERY'),
    manifest_fingerprint CHAR(64) NOT NULL CHECK(manifest_fingerprint ~ '^[a-f0-9]{64}$'),
    covered_from DATE NOT NULL CHECK(covered_from >= DATE '2000-01-01'),
    covered_to DATE NOT NULL CHECK(covered_to <= DATE '2100-12-31' AND covered_to >= covered_from AND covered_to-covered_from < 366),
    zone_id VARCHAR(80) NOT NULL CHECK(btrim(zone_id) <> ''),
    history_policy VARCHAR(40) NOT NULL CHECK(history_policy='AVAILABLE_EVIDENCE_ONLY'),
    acceptance_reference VARCHAR(120) NOT NULL CHECK(acceptance_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'),
    actor_account_id UUID NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE operational_publication_audit (
    command_id UUID PRIMARY KEY,
    generation_id UUID NOT NULL REFERENCES operational_generation_acceptance(generation_id),
    previous_generation_id UUID REFERENCES operational_generation_acceptance(generation_id),
    action VARCHAR(12) NOT NULL CHECK(action IN ('PUBLISH','ROLLBACK')),
    request_fingerprint CHAR(64) NOT NULL CHECK(request_fingerprint ~ '^[a-f0-9]{64}$'),
    acceptance_reference VARCHAR(120) NOT NULL CHECK(acceptance_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'),
    actor_account_id UUID NOT NULL,
    published_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_operational_publication_audit_generation ON operational_publication_audit(generation_id);
CREATE FUNCTION reject_operational_publication_evidence_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Operational publication evidence is immutable'; END;
$$;
CREATE TRIGGER operational_acceptance_immutable BEFORE UPDATE OR DELETE ON operational_generation_acceptance
    FOR EACH ROW EXECUTE FUNCTION reject_operational_publication_evidence_mutation();
CREATE TRIGGER operational_audit_immutable BEFORE UPDATE OR DELETE ON operational_publication_audit
    FOR EACH ROW EXECUTE FUNCTION reject_operational_publication_evidence_mutation();
CREATE FUNCTION guard_accepted_operational_generation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
CREATE TRIGGER accepted_operational_generation_immutable BEFORE INSERT OR UPDATE OR DELETE ON operational_replay_generation
    FOR EACH ROW EXECUTE FUNCTION guard_accepted_operational_generation();
CREATE TRIGGER accepted_operational_input_immutable BEFORE INSERT OR UPDATE OR DELETE ON operational_replay_input
    FOR EACH ROW EXECUTE FUNCTION guard_accepted_operational_generation();
CREATE TRIGGER accepted_operational_fact_immutable BEFORE INSERT OR UPDATE OR DELETE ON operational_replay_contribution
    FOR EACH ROW EXECUTE FUNCTION guard_accepted_operational_generation();
CREATE TRIGGER accepted_operational_scope_immutable BEFORE INSERT OR UPDATE OR DELETE ON operational_replay_scope
    FOR EACH ROW EXECUTE FUNCTION guard_accepted_operational_generation();
