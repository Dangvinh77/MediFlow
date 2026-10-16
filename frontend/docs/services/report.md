# Frontend Service: Report

**Owner:** Huy (`LQHuy0210`)  
**Backend:** `backend/report-service/**`  
**Contract context:** [`docs/ai/services/report.md`](../../../docs/ai/services/report.md)

**Current execution plan (2026-10-16):** [Huy FE + approved themed UI](../huy-frontend-implementation-plan.md).
The current plan separates existing legacy reports, request-state hardening and gated V2 surfaces.

2026-10-16 checkpoint: all three legacy report views use URL filters, submitted result scope,
calendar/UUID/range validation, race/unmount guards and last-query retry. Shared feedback preserves
401/403, error codes and correlation IDs. The Report suite passes 70 tests across 6 files;
browser fixture smoke covers deep links, Daily submit/Back/Forward and mobile themes. This is local
UI evidence; authenticated Gateway, coverage and six-service runtime acceptance remain open.

## Writable frontend scope

- `frontend/src/features/report/**`
- `frontend/src/app/(dashboard)/reports/**`

## Current UI baseline

`/reports` provides daily metrics, monthly revenue with zero-filled daily detail, and top-dispensed
medicine ranking through the three always-on report endpoints. UI roles are `ADMIN` and `MANAGER`.
Operational and Surgery snapshots remain absent because their Care/Finance V2 controller is
disabled by default.

## Owner queue

- `FE-REPORT-01` — `DONE`: daily validation, zero data, error and retry behavior.
- `FE-REPORT-02` — `DONE`: monthly revenue and calendar detail.
- `FE-REPORT-03` — `DONE`: bounded date/rank medicine view.
- `FE-REPORT-04` — `DONE — FE local`: component/query/validation regression tests use the existing harness.

## Handoffs and blockers

- Currency labels are blocked until Billing/Report or a product decision defines currency semantics.
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
