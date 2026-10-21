# Core Service

Quy định chức danh/trưởng khoa, xử lý đồng thời và vận hành Patient outbox:
[docs.md](docs.md).

Audit fixes verified locally on 2026-10-21: `mvn -q -pl backend/core-service -am test`
passed **205 Core tests** across 38 suites, zero failures/errors/skips, with Docker
PostgreSQL/RabbitMQ. Includes Patient transactional outbox and Organization head-title/
department-state concurrency regressions. Final Gateway/Notification/packaged acceptance
remains with Vinh; see [owner handoff](../../docs/handoffs/HANDOFF-HA-BACKEND-AUDIT-2026-10-20.md).

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

## Patient identity correction — 2026-10-20

`PUT /api/v1/patients/{id}` accepts optional `soCmnd` for ADMIN/NURSE. Missing/null retains
the current number for existing clients. A supplied number must contain 1–20 characters with
nonblank content and is trimmed before persistence. A number belonging to another patient
returns `409 PATIENT_CMND_DUPLICATE`; retaining the current number is allowed. The patient UUID
and creation time remain unchanged. The existing PostgreSQL unique constraint guards concurrent
assignments; no schema migration or new endpoint/role is introduced. Existing `patient.updated`
event bytes retain their contract and do not include the identity number.

Verification 2026-10-21: `mvn -q -pl backend/core-service -am test` passed with Docker Engine
28.5.1, **194 Core tests**, zero failures/errors/skips. All three Patient integration tests ran
against PostgreSQL/RabbitMQ Testcontainers, including correction/legacy request compatibility
and concurrent assignment of the same identity number to two patients (exactly one succeeds).
This is module integration evidence; signed Gateway/browser acceptance is separate.
