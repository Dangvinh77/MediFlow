# Frontend Service: Pharmacy

**Owner:** Huy (`LQHuy0210`)  
**Backend:** `backend/pharmacy-service/**`  
**Contract context:** [`docs/ai/services/pharmacy.md`](../../../docs/ai/services/pharmacy.md)  
**Detailed task plan:** [`../pharmacy-frontend-implementation-plan.md`](../pharmacy-frontend-implementation-plan.md)

## Writable frontend scope

- `frontend/src/features/pharmacy/**`
- `frontend/src/app/(dashboard)/pharmacy/**`
- `frontend/docs/pharmacy-frontend-implementation-plan.md`

## Current UI baseline

Pharmacy currently has the deepest frontend implementation: drug catalog/detail/create/stock
adjustment; prescription create/lookup/detail; cancel and dispense actions; and an ADMIN outbox
replay screen. Route gates distinguish catalog/detail access, pharmacist inventory actions, doctor
prescription creation, terminal actions, and ADMIN replay.

## Owner queue

- Continue the exact next unfinished task in the detailed Pharmacy plan; update its evidence in the
  same PR.
- `FE-PHARMACY-CONTRACT` — recheck live controller/DTO enums before every mutation slice.
- `FE-PHARMACY-TEST` — add focused validation, permission, terminal-state, and API error tests after
  the shared test harness exists.
- `FE-PHARMACY-RELEASE` — capture successful typecheck/lint/build and role smoke-test evidence.

## Handoffs and blockers

- Staff-scoped end-to-end behavior needs a signed, stable `staffId`. Producer: Gateway and
  Organization / Hoàng Anh. Acceptance: documented identity claim/mapping enforced by the backend.
- Prescription creation consumes Clinical identifiers. Producer: Clinical / Vinh. Never infer a
  record or patient association from the current user.
- Billing and Notification saga behavior stays in those services; Pharmacy UI displays only fields
  explicitly present in Pharmacy contracts.

## Contract and verification gate

Do not duplicate the detailed task checklist here. Follow the linked plan, preserve terminal-state
guards, and keep replay restricted to a known event ID and ADMIN UX gate.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, focused Pharmacy tests when available, and smoke
test role-specific create/adjust/cancel/dispense/replay actions affected by the change.
