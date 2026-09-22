# Frontend Service: Report

**Owner:** Huy (`LQHuy0210`)  
**Backend:** `backend/report-service/**`  
**Contract context:** [`docs/ai/services/report.md`](../../../docs/ai/services/report.md)

## Writable frontend scope

- `frontend/src/features/report/**`
- `frontend/src/app/(dashboard)/reports/**`

## Current UI baseline

`/reports` provides a daily report query with required date and optional department UUID through
`GET /v1/reports/daily`. It renders visit, lab, prescription, and revenue metrics. UI roles are
`ADMIN` and `MANAGER`.

## Owner queue

- `FE-REPORT-01` — `VERIFY-CONTRACT`: harden daily report validation, empty/zero, error, and retry
  behavior against the live DTO.
- `FE-REPORT-02` — `VERIFY-CONTRACT`: add one monthly drill-down supported by the live controller.
- `FE-REPORT-03` — `VERIFY-CONTRACT`: add one medicine-ranking view supported by the live controller.
- `FE-REPORT-04` — `IMPLEMENT`: add formatter and component tests after the shared harness exists.

## Handoffs and blockers

- Currency labels are blocked until Billing/Report or a product decision defines currency semantics.
  Producers: Billing / Lộc and Report / Huy. Acceptance: documented currency/display rule.
- Department names require an Organization contract. Producer: Organization / Hoàng Anh. Keep the
  UUID if no supported lookup exists.
- Export formats and aggregation definitions require an explicit Report contract; do not calculate
  authoritative totals from other frontend features.

## Contract and verification gate

Verify live report paths, filters, roles, numeric serialization, timezone/date semantics, and zero
behavior. Treat zero metrics as valid data.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Report tests when available. Smoke test
date/department validation, zero metrics, permission denial, and the changed report route.
