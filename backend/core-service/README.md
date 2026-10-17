# Core Service

## Product scope — 2026-10-17

Part of **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Core hosts Organization and Patient for outpatient identity, staff and departments, retaining separate data authority.

Canonical [system design](../../docs/plans/01-system-design.md), [contracts](../../docs/plans/03-contracts.md) and [acceptance runbook](../../docs/plans/06-acceptance-runbook.md) define current scope. Existing APIs, feature flags and historical test counts below do not certify full workflow release acceptance.


Core is the single deployment for the Organization and Patient bounded contexts. Their source,
business rules, wire DTOs, migrations, and PostgreSQL stores live in this module while retaining
their separate `com.mediflow.organization` and `com.mediflow.patient` packages. Existing API routes
are served behind `core-service` service discovery.

Organization and Patient use separate datasource URLs, entity managers, transaction managers,
and Flyway locations. Their schemas and data remain separate. Configure
`MEDIFLOW_ORGANIZATION_DB_*`, `MEDIFLOW_PATIENT_DB_*`, and `MEDIFLOW_JWT_SECRET` for a local run.
RabbitMQ and Eureka use the shared MediFlow settings.

Request examples are in `requests/organization.http` and `requests/patient.http`.
The Markdown files in `requests/` retain the original context documentation.
