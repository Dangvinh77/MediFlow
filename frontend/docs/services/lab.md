# Frontend Service: Lab

**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Backend:** `backend/lab-service/**`  
**Contract context:** [`docs/ai/services/lab.md`](../../../docs/ai/services/lab.md)

## Writable frontend scope

- `frontend/src/features/lab/**`
- `frontend/src/app/(dashboard)/lab/**`

## Current implementation — 2026-10-18

The default-off server UI gate `MEDIFLOW_OUTPATIENT_UI_ENABLED` now controls the outpatient
command UI. The following older baseline descriptions are historical where they describe
commands as not implemented or compatibility-only. See the latest [workboard](../frontend-workboard.md)
and [implementation record](../vinh-outpatient-implementation-2026-10-18.md).

The app-level Lab-order workspace submits the original Clinical sourceOrderId and all returned
context fields. Enabled UI mode directs creation through the record; existing start/results/cancel
remain, with synchronous locks, unmount guards, 401 handling and manual read refresh. V1 start
requires READY/clearance from the response, never an inferred payment. Runtime smoke and new
behavioral regression tests remain open; this session validates build only by user instruction.

## Historical UI baseline

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

## MVP contract preparation — 2026-10-15

The existing create facade accepts the exact V1 source-order/episode/price fields. Feature-local
HTTP tests verify that supplied identity reaches Gateway unchanged and that the returned lifecycle,
payment and clearance remain separate. Start defaults to `emergencyOverride: null`; its request
type now mirrors the full backend audit record, without enabling an override approval UI.
422 financial blocks and 503 source-authority errors preserve their code/correlation ID and do not
trigger automatic mutation retry or fallback. Transport/session are mocked; integrated Gateway,
Billing and Clinical acceptance and V1 create controls remain open under `FE-VINH-MVP-03/04`.

## Owner queue

- `FE-LAB-01` — `DONE`: test detail mirrors the exact live response DTO and keeps payment/lifecycle separate.
- `FE-LAB-02` — `DONE`: request creation mirrors the live compatibility DTO, roles, validation,
  success navigation, and 400/403/404/409/business-rule handling.
- `FE-LAB-03` — `DONE`: start, multi-indicator result entry and audited cancellation use the exact
  live request records and roles.
- `FE-LAB-04` — `IN PROGRESS`: source identity/normal start HTTP tests plus 15 detail component
  regressions for loading/retry/correlation, stale read/mutation, roles/terminal states and payment
  separation. Detail workspace resets by testId so an old start response cannot blank the current
  detail. Queue filter/pagination and create-form lifecycle are now component-tested; authenticated
  Gateway/browser smoke remains open. API/session are mocked, not proof of runtime authorization or
  actual payment.

2026-10-17 continuation adds queue pagination/filter coverage and the Lab create-form lifecycle.
Pagination keeps the submitted status/episode filters; retry ignores unsent drafts. Validation
rejects an incomplete episode pair, stale and post-unmount responses cannot replace the queue or
redirect, and role visibility is checked. Lab create locks duplicate submits, ignores late results
after unmount, and preserves backend field errors/correlation IDs. Full frontend suite 363/363
(45 files), typecheck/lint/build pass. Tests mock API/session; authenticated Gateway smoke remains
open. See the dated [workboard](../frontend-workboard.md).

Follow-up type audit found the Lab form test used `LabTestDTO` in deferred promises without a
type-only import. A fresh `pnpm typecheck` reproduced two TS2304 errors; restoring the import made
the focused form suite pass 4/4 and fresh `pnpm typecheck`/`pnpm build` pass. The frontend
`tsconfig.json` includes test `.ts`/`.tsx` files; the earlier typecheck evidence predated that import
removal and is superseded by the fresh result.

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
