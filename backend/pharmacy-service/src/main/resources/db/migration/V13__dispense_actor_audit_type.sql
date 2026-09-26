-- Clarify whether a completed dispense was performed by staff, an account or automation.
ALTER TABLE DISPENSE_SLIP
    ADD COLUMN dispensed_actor_type VARCHAR(30);

-- Older rows contain only a UUID; preserve it without guessing which identity table it came from.
UPDATE DISPENSE_SLIP
SET dispensed_actor_type = CASE
        WHEN dispensed_by = '00000000-0000-0000-0000-000000000000'::UUID THEN 'SYSTEM'
        ELSE 'LEGACY_UNKNOWN'
    END
WHERE status = 'DISPENSED';

UPDATE DISPENSE_SLIP
SET dispensed_by = NULL
WHERE status = 'DISPENSED'
  AND dispensed_actor_type = 'SYSTEM';

ALTER TABLE DISPENSE_SLIP
    ADD CONSTRAINT ck_dispense_actor_audit
        CHECK (
            status <> 'DISPENSED'
            OR dispensed_actor_type = 'LEGACY_UNKNOWN'
            OR (dispensed_actor_type = 'SYSTEM' AND dispensed_by IS NULL)
            OR (dispensed_actor_type IN ('STAFF', 'ACCOUNT') AND dispensed_by IS NOT NULL)
        );
