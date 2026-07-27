-- One database per service — the microservice rule that matters most:
-- no service may read another service's tables (docs/ai/01-architecture.md).
--
-- Executed automatically by the postgres container on FIRST start only
-- (see docker-compose.yml). To re-run: docker compose down -v && docker compose up -d
--
-- Running this by hand against an existing server works too:
--   psql -U postgres -f scripts/init-databases.sql

-- Reference data: who works where, and who the patients are
CREATE DATABASE mediflow_organization;
CREATE DATABASE mediflow_patient;

-- Departments (khoa/phòng)
CREATE DATABASE mediflow_clinical;       -- Khoa Khám bệnh: appointments + records + diagnoses
CREATE DATABASE mediflow_lab;            -- Khoa Xét nghiệm
CREATE DATABASE mediflow_pharmacy;       -- Khoa Dược
CREATE DATABASE mediflow_billing;        -- Phòng Viện phí

-- Support
CREATE DATABASE mediflow_notification;
CREATE DATABASE mediflow_report;

-- Schemas themselves are owned by Flyway, per service, from
-- <service>/src/main/resources/db/migration/. Never create tables here.
