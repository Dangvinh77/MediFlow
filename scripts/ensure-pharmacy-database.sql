-- Local Docker Pharmacy store only. Never baseline, repair or delete the legacy store.
-- Flyway in pharmacy-service creates and validates the tables after provisioning.
-- Safe to run repeatedly: an existing database (including all its data) is untouched.
SELECT 'CREATE DATABASE mediflow_pharmacy_docker'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'mediflow_pharmacy_docker'
)
\gexec
