# Frontend Service: Lab

**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Backend:** `backend/lab-service/**`  
**Contract context:** [`docs/ai/services/lab.md`](../../../docs/ai/services/lab.md)

## Writable frontend scope

- `frontend/src/features/lab/**`
- `frontend/src/app/(dashboard)/lab/**`

## Current UI baseline

`/lab` renders a paged queue from `GET /v1/lab` with department, status and exact episode filters.
It displays all six live lifecycle states separately from payment state. UI roles mirror the
controller and corrected Gateway matrix: `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE`, and `LAB_TECH`.

`/lab/{testId}` renders the exact live detail DTO, including care episode, clearance, result
revision, textual results and conclusion. It is available to `ADMIN`, `DOCTOR`, `NURSE`, and
`LAB_TECH`, matching the implemented Gateway role code; real deployment smoke remains open.

`/lab/new` lets `ADMIN` and `DOCTOR` create the current version-0 compatibility request using the
required record, patient, department, type and requested date fields. It intentionally omits V2
episode/price identity while activation is held and never infers financial clearance in the browser.

The detail workspace now exposes start to `ADMIN`/`LAB_TECH` for `PENDING` or `READY`, result entry
to those roles for `IN_PROGRESS`, and cancel to `ADMIN`/`DOCTOR`/`LAB_TECH` for non-terminal tests.
The backend remains authoritative for every 403 and lifecycle conflict.

## Owner queue

- `FE-LAB-01` — `DONE`: test detail mirrors the exact live response DTO and keeps payment/lifecycle separate.
- `FE-LAB-02` — `DONE`: request creation mirrors the live compatibility DTO, roles, validation,
  success navigation, and 400/403/404/409/business-rule handling.
- `FE-LAB-03` — `DONE`: start, multi-indicator result entry and audited cancellation use the exact
  live request records and roles.
- `FE-LAB-04` — `IMPLEMENT`: cover filters, pagination, payment/clinical status separation, and
  retry behavior with focused tests after the shared harness exists.

## Handoffs and blockers

- Record validation and result attachment depend on Clinical. Producer: Clinical / Vinh; keep this
  as a contract call or event, never a cross-feature import.
- Paid eligibility depends on Billing's documented event/API semantics. Producer: Billing / Lộc.
  A boolean or status must not be inferred from invoice text.
- Patient and department display enrichment require Patient/Organization contracts; do not perform
  client-side cross-service joins as a workaround.

## Contract and verification gate

Confirm live Lab controller paths, DTOs, roles, status enums, and error codes before implementation.
Do not interpret `COMPLETED` as a normal result and do not merge payment state into clinical status.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Lab tests when available. Smoke test the
queue with filters, empty/error/retry states, and a denied role.
