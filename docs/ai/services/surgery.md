# Service: surgery

**Status:** approved bounded context; platform foundation and initial internal domain core exist, cross-service contracts remain open
**Owner:** Huy (`LQHuy0210`)
**Source of truth:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)
**Planned module:** `backend/surgery-service/` · **Port:** 8091 · **Database:** `mediflow_surgery` · **Base path:** `/api/v1/surgery`

## Bounded context

Owns surgery cases, indications/reference to request, pre-op checklist, consent, schedule, room/time,
team assignments, readiness snapshots, result and state history.

Does not own the admission, patient/staff identities, financial ledger, Pharmacy stock or diagnostic
results. It keeps bare UUID references and auditable snapshots only.

## Logical data model

- `SURGERY_CASE`: surgeryCaseId, surgeryRequestId, exact `careEpisodeType/careEpisodeId`, optional
  admissionId/recordId context, patientId, departmentId, procedure code, priority, status and timestamps.
- `PREOP_CHECK_ITEM`: item code, mandatory flag, status, evidence reference, confirmedBy/At.
- `CONSENT`: type, signer/witness references, signedAt, status and revocation fields.
- `SURGERY_SCHEDULE`: room reference, planned start/end and status.
- `SURGERY_TEAM`: case, staffId, role and active interval.
- `SURGERY_RESULT`: performed method/items, outcome, complications summary and times.
- `SURGERY_STATUS_HISTORY`: old/new state, actor, reason, time and correlation.

The selected episode ID is exact: outpatient uses `appointmentId` when present, otherwise `recordId`;
an admission uses its exact `admissionId`. A distinct outpatient `recordId` remains clinical context.
Physical DDL and naming mappings belong to the implementation-ready spec.

## State machine

```text
REQUESTED → PREOP_IN_PROGRESS → READY → SCHEDULED → IN_PROGRESS → COMPLETED
```

V1 `CANCELLED` is allowed only before `IN_PROGRESS`; stage is derived from persisted status.
`READY` is computed from all guards; it is not a free-form status update.

## Readiness invariant

```text
valid indication
AND mandatory pre-op checklist complete
AND active SURGERY and ANESTHESIA consents
AND team assigned
AND room/time confirmed
AND matching SURGERY financial clearance
```

Emergency override is disabled in V1. A later version may only bypass the financial guard after the
approver and Billing policies are confirmed; it can never invent consent or team assignment.

## Planned endpoints

| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/v1/surgery/cases` | ADMIN, DOCTOR | create from exact surgery request |
| GET | `/api/v1/surgery/cases/{id}` | ADMIN, MANAGER, DOCTOR, NURSE | read case/readiness |
| GET | `/api/v1/surgery/cases` | ADMIN, MANAGER, DOCTOR, NURSE | filter schedule/status/department |
| PUT | `/api/v1/surgery/cases/{id}/checklist` | ADMIN, DOCTOR, NURSE | confirm pre-op item |
| POST | `/api/v1/surgery/cases/{id}/consents` | ADMIN, DOCTOR, NURSE | record consent |
| PUT | `/api/v1/surgery/cases/{id}/schedule` | ADMIN, MANAGER, DOCTOR | assign room/time/team |
| POST | `/api/v1/surgery/cases/{id}/start` | ADMIN, DOCTOR | start after readiness guard |
| POST | `/api/v1/surgery/cases/{id}/complete` | ADMIN, DOCTOR | record result/performed items |
| POST | `/api/v1/surgery/cases/{id}/cancel` | ADMIN, DOCTOR | cancel with stage/reason |

Exact DTOs require a dedicated implementation spec before coding.

## Current implementation state

The owner-authorized platform foundation and initial pure-Java domain core exist in `backend/surgery-service/`: Maven module POM,
Spring Boot entry point, configuration, JWT authentication/default-deny authorization, correlation
handling, feature flag (disabled by default), package skeleton and test sources. Huy-delegated local
V1 defaults are recorded in the implementation-decision handoff; they guide internal Surgery work
but do **not** approve the V2 candidate's still-open cross-service contracts. There is
not yet a Surgery business endpoint, database migration, persistence adapter, Rabbit event/queue binding,
or Gateway route. The current domain slice contains exact episode identity and case lifecycle/readiness
rules. The module-local Maven test suite passes (30 tests); root-reactor, Docker/database
and Gateway integration are not yet verified. See the current Huy plan for the exact verification scope.

## Events

**Publish:** `surgery.ready`, `surgery.completed`, `surgery.cancelled`.

**Subscribe:** `surgery.requested`, `financial.clearance.granted` with `purpose=SURGERY`, and explicit
pre-op Lab/Pharmacy facts chosen by the future contract. A general Lab result does not automatically
complete a checklist item without exact case/order correlation.

## Business rules

1. One `surgeryRequestId` creates at most one case.
2. Case/admission/patient/department references must match producer facts.
3. READY and START both re-evaluate mandatory guards transactionally.
4. Team/room/time conflicts are rejected; staff eligibility comes from Organization lookup.
5. Completion records performed item codes so Billing reconciles actual charges idempotently.
6. Cancellation is append/audit preserving and never edits completed payment history.
7. Duplicate events/commands do not repeat charge, result or notification side effects.

## Mandatory handoffs

- [`CONTRACT-INPATIENT-SURGERY-01`](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)

## Business implementation and shared bootstrap gates

The initial internal domain slice is not a production workflow by itself. Before business endpoints, persistence,
or event consumers/producers are implemented, resolve the relevant H-01.2/H-01.3 decisions and use
the implementation-ready Surgery specification for the slice. The module-local test suite passes
(30 tests); root-reactor, Docker/database and Gateway integration are not yet verified. Shared root Maven registration,
database/Compose wiring and Gateway routing are tracked separately in the registered bootstrap
handoff and must be done by their assigned owners. Add same-version producer/consumer fixtures and
contract tests before enabling any integration. Status remains `DESIGN_READY` while any required
producer/consumer is missing.
