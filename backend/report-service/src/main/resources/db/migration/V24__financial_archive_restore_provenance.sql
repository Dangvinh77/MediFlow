-- Portable LOCAL evidence recovery only, not producer completeness/history approval or publication.
CREATE TABLE financial_archive_restore (
    archive_fingerprint VARCHAR(64) PRIMARY KEY CHECK(archive_fingerprint ~ '^[a-f0-9]{64}$'),
    origin_generation_id UUID NOT NULL,
    generation_id UUID NOT NULL UNIQUE REFERENCES financial_source_rebuild(generation_id),
    actor_account_id UUID NOT NULL,
    restored_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE FUNCTION reject_financial_archive_restore_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Financial archive restore provenance is immutable';
END;
$$;
CREATE TRIGGER financial_archive_restore_immutable BEFORE UPDATE OR DELETE ON financial_archive_restore
    FOR EACH ROW EXECUTE FUNCTION reject_financial_archive_restore_mutation();
