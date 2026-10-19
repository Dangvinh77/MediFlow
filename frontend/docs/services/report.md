# Frontend Service: Report

**Owner:** Huy (`LQHuy0210`)  
**Backend:** `backend/report-service/**`  
**Contract context:** [`docs/ai/services/report.md`](../../../docs/ai/services/report.md)

**Current execution plan (2026-10-18):** [Huy FE — six-service MVP closeout](../huy-six-service-frontend-plan.md).
It reuses existing reports and tracks real Support/coverage acceptance; full V2 stays deferred.
The [previous themed-UI plan](../huy-frontend-implementation-plan.md) retains historical evidence.

2026-10-18 follow-up: all four report views remount per role/token and check the live session
before dispatch/commit. Invalid submissions clear pending results and focus the first invalid field;
queued auto-loads cannot dispatch after unmount. These are feature guards; the shared global late-401
session race remains in the single handoff. Six-service API runtime rerun passes, including Support
two-store routing/rollback, but does not supply accepted outpatient coverage or signed FE acceptance.
Browser fixtures verify layout/themes only; latest evidence and counts are in plan sections 9/10.

2026-10-18 revalidation: live Report/OutpatientReport controller DTO/query/role/coverage contracts
still match the existing frontend. Report production code and its default-closed outpatient gate
are preserved. The Pharmacy + all-Support batch passes the final full FE suite (474/474, 53 files),
typecheck/lint/build. These local checks do not close real Support/coverage/Gateway browser acceptance.

Latest MVP checkpoint: `R-MVP-01` adds `/reports/outpatient` with a daily inclusive range and
monthly periods for a whole year, mirroring the current OutpatientReportController/DTO.
Server-derived coverage, currency, zone, source/reconciliation times and seven period metrics
remain separate from legacy revenue. Missing coverage is unavailable, not a zero report;
there is no fallback to legacy endpoints and no client aggregation of authoritative totals.

The route and its Reports entry default closed. After actual reader/coverage/Gateway acceptance,
set server environment `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true` and restart frontend.
Both Report pages render dynamically, so this environment is evaluated at runtime. This UI gate
does not enable backend profiles/flags or authorize users; roles remain ADMIN/MANAGER at the
UI and backend. The reader requires an outpatient profile and both care-finance-v2/outpatient-read
backend flags; source owners must supply valid coverage before real use.

Verification: typecheck/lint/build PASS; full suite 291/291 tests, 36 files. Local browser fixture
checked the default closed direct route with no Report requests, enabled daily/monthly reads,
12 periods, filters/history, missing coverage and responsive Light/Dark. Real signed-role,
source coverage and Support deployment acceptance remain open in the existing Huy handoff.

2026-10-16 checkpoint: all three legacy report views use URL filters, submitted result scope,
calendar/UUID/range validation, race/unmount guards and last-query retry. Shared feedback preserves
401/403, error codes and correlation IDs. The Report suite passes 70 tests across 6 files;
browser fixture smoke covers deep links, Daily submit/Back/Forward and mobile themes. This is local
UI evidence; authenticated Gateway, coverage and six-service runtime acceptance remain open.

## Writable frontend scope

- `frontend/src/features/report/**`
- `frontend/src/app/(dashboard)/reports/**`

## Current UI baseline

`/reports` preserves daily metrics, monthly revenue with zero-filled daily detail, and top-dispensed
medicine ranking through the three always-on report endpoints. UI roles are `ADMIN` and `MANAGER`.
Operational and Surgery snapshots remain absent because their Care/Finance V2 controller is
disabled by default.

## Owner queue

- `FE-REPORT-01` — `DONE`: daily validation, zero data, error and retry behavior.
- `FE-REPORT-02` — `DONE`: monthly revenue and calendar detail.
- `FE-REPORT-03` — `DONE`: bounded date/rank medicine view.
- `FE-REPORT-04` — `DONE — FE local`: component/query/validation regression tests use the existing harness.

## Handoffs and blockers

- Legacy currency labels are blocked until Billing/Report or a product decision defines currency semantics;
  the outpatient reader already supplies currency explicitly in its response.
  Producers: Billing / Lộc and Report / Huy. Acceptance: documented currency/display rule.
- Department names require an Organization contract. Producer: Organization / Hoàng Anh. Keep the
  UUID if no supported lookup exists.
- Export formats and aggregation definitions require an explicit Report contract; do not calculate
  authoritative totals from other frontend features.
- Track runtime/owner dependencies in the [single Huy handoff](../../../docs/handoffs/HANDOFF-HUY-MVP.md),
  not a second frontend handoff. Legacy success does not activate gated V2 surfaces.

## Contract and verification gate

Verify live report paths, filters, roles, numeric serialization, timezone/date semantics, and zero
behavior. Treat zero metrics as valid data.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Report tests when available. Smoke test
date/department validation, zero metrics, permission denial, and the changed report route.
