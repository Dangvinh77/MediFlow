-- Preserve the producer's exact Instant for chronology checks. PostgreSQL TIMESTAMPTZ rounds
-- to microseconds; never fabricate lost nanoseconds in previously accepted V9/V10 history.
ALTER TABLE surgery_charge_source ADD COLUMN requested_at_iso VARCHAR(40);
