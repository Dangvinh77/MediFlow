-- Operator attests only this finite accepted source set, not fabricated pre-V2 coverage.
CREATE TABLE financial_generation_acceptance (
    generation_id UUID PRIMARY KEY REFERENCES financial_report_generation(generation_id),
    manifest_fingerprint VARCHAR(64) NOT NULL CHECK(manifest_fingerprint ~ '^[a-f0-9]{64}$'),
    history_policy VARCHAR(40) NOT NULL CHECK(history_policy='AVAILABLE_EVIDENCE_ONLY'),
    acceptance_reference VARCHAR(120) NOT NULL CHECK(acceptance_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'),
    actor_account_id UUID NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE financial_publication_audit (
    command_id UUID PRIMARY KEY,
    generation_id UUID NOT NULL REFERENCES financial_generation_acceptance(generation_id),
    previous_generation_id UUID REFERENCES financial_generation_acceptance(generation_id),
    action VARCHAR(12) NOT NULL CHECK(action IN ('PUBLISH','ROLLBACK')),
    request_fingerprint VARCHAR(64) NOT NULL CHECK(request_fingerprint ~ '^[a-f0-9]{64}$'),
    acceptance_reference VARCHAR(120) NOT NULL CHECK(acceptance_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}$'),
    actor_account_id UUID NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_financial_publication_audit_generation ON financial_publication_audit(generation_id);
CREATE FUNCTION reject_financial_publication_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Financial publication evidence is immutable'; END;
$$;
CREATE TRIGGER financial_acceptance_immutable BEFORE UPDATE OR DELETE ON financial_generation_acceptance
    FOR EACH ROW EXECUTE FUNCTION reject_financial_publication_audit_mutation();
CREATE TRIGGER financial_publication_audit_immutable BEFORE UPDATE OR DELETE ON financial_publication_audit
    FOR EACH ROW EXECUTE FUNCTION reject_financial_publication_audit_mutation();
