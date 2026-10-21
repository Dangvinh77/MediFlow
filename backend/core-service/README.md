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

## Organization delivery and staff retries — 2026-10-21

Organization mutations now append their existing event bytes to
`organization_event_outbox` in the Organization transaction. The relay publishes persistent
messages with mandatory routing and publisher confirmation. Delivery remains at least once:
after a crash between broker confirmation and the database commit, consumers may receive the
same stored event ID and payload again. Patient persistence and its outbox remain separate.

The relay runs every 5 seconds by default, with exponential retry delay capped at 300 seconds
and quarantine after 8 failed attempts. Settings are
`mediflow.organization.event-outbox.scheduling-enabled`, `delay-ms`, and `maximum-attempts`.
Monitor PENDING age and QUARANTINED rows in this context's own store. After repairing routing
or broker availability, an operator may reset a reviewed quarantined row to PENDING with
`attempts=0`, `next_attempt_at=CURRENT_TIMESTAMP`, and `last_error=NULL`; preserve its event ID,
payload and routing fields. This recovery does not create another staff record.

`POST /api/v1/org/staff` accepts an optional `Idempotency-Key`. The receipt is scoped to the
authenticated actor and exact create request fields, including null values. First creation returns 201; a matching
retry returns 200 with the original staff ID and Location, plus `Idempotency-Replayed: true`.
Reusing that actor/key for different data returns 409 `STAFF_IDEMPOTENCY_KEY_REUSED`.
Clients must keep the same key across uncertain responses, and use a new key for a new intent.
The Organization StaffForm now follows that rule. Unkeyed legacy clients retain their existing
create semantics; previously created duplicate records require separate reconciliation.

Gateway refresh reads `GET /api/v1/org/accounts/{accountId}/identity` using its own service
identity. Only the Gateway service can call this endpoint. Refresh requires an expiring,
valid refresh token and the current active account identity; stale roles and identity links
are replaced by the current snapshot. An unavailable lookup cannot issue a new access token.

Verification for these changes: `mvn -q -pl backend/core-service -am test` passed **213 Core
tests**, zero failures/errors/skips, with PostgreSQL/RabbitMQ Testcontainers. The first full
run exposed a web-slice fixture missing the new identity use-case mock; the fixture was fixed
and the full command then passed. Gateway's final full suite and separate refund-route
acceptance run passed **1,500 tests** in total, including the exact EXAM refund-due route
role/method/path checks. Organization API/StaffForm focused
frontend tests passed **11 tests**, with typecheck and lint also passing. These are module
and contract results; they do not certify deployed browser or six-application acceptance.
