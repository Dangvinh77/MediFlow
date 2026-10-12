-- No template seed/backfill: existing pre-op checklists do not prove intraoperative pauses.
CREATE TABLE surgery_safety_pause (
    pause_id UUID PRIMARY KEY,
    command_id UUID NOT NULL,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    phase VARCHAR(32) NOT NULL CHECK (phase IN ('BEFORE_ANAESTHESIA','BEFORE_INCISION','BEFORE_OR_EXIT')),
    schedule_id UUID NOT NULL REFERENCES surgery_schedule(schedule_id),
    schedule_revision BIGINT NOT NULL CHECK (schedule_revision >= 0),
    expected_case_revision BIGINT NOT NULL CHECK (expected_case_revision >= 0),
    intent_fingerprint CHAR(64) NOT NULL CHECK (intent_fingerprint ~ '^[a-f0-9]{64}$'),
    observed_at_iso TEXT NOT NULL,
    recorded_at_iso TEXT NOT NULL,
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    CONSTRAINT uq_safety_pause_command UNIQUE(surgery_case_id,command_id),
    CONSTRAINT uq_safety_pause_phase UNIQUE(surgery_case_id,phase)
);

CREATE FUNCTION reject_surgery_safety_pause_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- An empty ledger has no evidence to destroy. TRUNCATE holds ACCESS EXCLUSIVE,
    -- so an insert cannot race this check; any populated ledger still rejects it.
    IF TG_OP = 'TRUNCATE' AND NOT EXISTS (SELECT 1 FROM surgery_safety_pause) THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Surgery safety pause evidence is immutable';
END;
$$;
CREATE TRIGGER surgery_safety_pause_immutable
    BEFORE UPDATE OR DELETE ON surgery_safety_pause
    FOR EACH ROW EXECUTE FUNCTION reject_surgery_safety_pause_mutation();
CREATE TRIGGER surgery_safety_pause_no_truncate
    BEFORE TRUNCATE ON surgery_safety_pause
    FOR EACH STATEMENT EXECUTE FUNCTION reject_surgery_safety_pause_mutation();
