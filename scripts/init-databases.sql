-- One database per service — the microservice rule that matters most:
-- no service may read another service's tables (docs/ai/01-architecture.md).
--
-- Executed automatically by the postgres container on FIRST start only
-- (see docker-compose.yml). On an existing volume this script does not rerun; provision any
-- missing database with a reviewed idempotent check/create operation. Never delete a volume just
-- to replay this bootstrap.
--
-- Do not run this whole file against an existing server: CREATE DATABASE is not idempotent.
-- Use the reviewed check/create operation documented in the Surgery foundation handoff instead.

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

-- Planned care context (foundation only; schema will be owned by Flyway)
CREATE DATABASE mediflow_inpatient;
CREATE DATABASE mediflow_surgery;

-- Schemas themselves are owned by Flyway, per service, from
-- <service>/src/main/resources/db/migration/. Never create tables here.
