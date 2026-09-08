# Clinical and Lab Foundation Implementation Plan

> Execute with subagent-driven-development and test-driven-development. User approved this scope on 2026-09-08.

**Goal:** Bring Vinh's clinical/lab modules to the domain and application-contract milestone represented by billing/notification, with explicit compatibility checks.

**Architecture:** Pure Java rich domain models, framework-free repository and use-case ports, validated request records, response records and MapStruct mappers. Published event records live in `application/event`, following existing billing/pharmacy, so publisher ports never depend on infrastructure.

**Tech stack:** Existing Java 21, Maven, JUnit/AssertJ, Jakarta Validation, Jackson and MapStruct.

## Tasks

- [x] Re-index source with codebase-memory-mcp; read changelog and verify clean baseline.
- [x] Create isolated branch/worktree; baseline `mvn -q -pl backend/clinical-service,backend/lab-service -am test` succeeds.
- [x] Audit clinical/lab HTML, all collaborating service specifications and existing event records. Record unresolved producer gaps instead of inventing identifiers.
- [x] Clinical: create domain tests under `backend/clinical-service/src/test/java/com/mediflow/clinical/domain/model/` for date/time boundaries, state transitions, required references, diagnosis invariants and collection ownership. Run red, implement `Appointment`, `AppointmentStatus`, `MedicalRecord`, `Diagnosis` and typed exceptions under corresponding main packages, run green.
- [x] Clinical: create validated request/response DTOs, repository/lookup/event/incoming ports, MapStruct mapper and four published events. Test nested validation, HH:mm JSON, all field mappings, recordId correlation for billing and event envelope shape.
- [x] Lab: implement `LabTest`, `LabResult`, status and exceptions using failing domain tests; add DTOs/ports/mapper/two published events and validation/mapping/JSON tests. Keep result values textual and completion atomic.
- [x] Review spec compliance, then code quality; resolve findings and rerun affected tests.
- [x] Run Maven verify for both modules and collaborating modules; check diff and inward dependencies. Re-index final worktree and update module README status and contract handoff notes.

## Contract decisions

- English fields follow authoritative per-service HTML and implementation specs/current peer code; older generic Vietnamese naming examples are stale for these contracts.
- Preserve canonical event names and envelope fields `eventId`, `occurredAt`, `correlationId`.
- `appointment.status.changed` additionally carries nullable `recordId`: known when a record is created; unavailable for a standalone arrival. Billing must not invent this relationship.
- `lab.result.created`: canonical `labId` comes from `testId`, `departmentId` from requesting department. Include `labType` for billing/notification and `performedDate` for report.
- Upstream lookup misses are false/empty; transport failures throw a distinct `UPSTREAM_UNAVAILABLE` exception for future HTTP 503 mapping.
- Payment-to-lab and filled-prescription-to-record correlation gaps remain explicit integration prerequisites until their owning producers supply identifiers. No consumers or network calls ship in this foundation slice.

## Acceptance boundary

This slice provides real domain behavior and compile-checked application contracts. It does not claim working HTTP, persistence, transaction coordination, RabbitMQ delivery/idempotency or complete service readiness. Those require subsequent adapters/application services and integration tests.

## Verification outcome

- Clinical: 46 tests; lab: 39 tests; zero failures/errors/skips for these two modules. Maven verify passed.
- Repeatable script checks three actual producer-to-billing JSON contracts with no cross-service Maven dependency.
- Broader verify passed for billing/notification/pharmacy/organization/report; seven existing pharmacy container tests skipped without Docker. Patient packaging is blocked by its unchanged missing main class.
- Initial independent clinical spec review found documentation mismatches; blueprint, service specs and HTML were reconciled. Follow-up independent reviews were interrupted by workspace credit limits; final code/spec review was completed locally, including the lab null-element validation fix and regression test.
- Full codebase-memory re-index includes the new domain, ports, DTOs, events and tests.
