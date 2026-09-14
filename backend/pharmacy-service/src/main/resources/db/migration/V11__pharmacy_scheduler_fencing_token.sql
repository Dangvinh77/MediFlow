-- Add an opaque token so an expired/reclaimed lease cannot commit stale cursor updates.
ALTER TABLE PHARMACY_SCHEDULER_LEASE
    ADD COLUMN lease_token UUID;

UPDATE PHARMACY_SCHEDULER_LEASE
   SET lease_token = md5(job_name || COALESCE(updated_at::text, clock_timestamp()::text))::uuid
 WHERE lease_token IS NULL;

ALTER TABLE PHARMACY_SCHEDULER_LEASE
    ALTER COLUMN lease_token SET NOT NULL;
