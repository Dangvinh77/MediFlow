-- Durable scheduler ownership and cursor.  No business data is repaired by this migration.
CREATE TABLE PHARMACY_SCHEDULER_LEASE (
    job_name     VARCHAR(100) PRIMARY KEY,
    cursor_id    UUID,
    lease_owner  VARCHAR(100),
    lease_until  TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pharmacy_scheduler_lease_until
    ON PHARMACY_SCHEDULER_LEASE (lease_until);
