# Frontend Service: Pharmacy

**Owner:** Huy (`LQHuy0210`)  
**Backend:** `backend/pharmacy-service/**`  
**Contract context:** [`docs/ai/services/pharmacy.md`](../../../docs/ai/services/pharmacy.md)  
**Detailed task plan:** [`../pharmacy-frontend-implementation-plan.md`](../pharmacy-frontend-implementation-plan.md)

**Current execution plan (2026-10-16):** [Huy FE + approved themed UI](../huy-frontend-implementation-plan.md).
The earlier plan retains PH-FE history; the current plan records live harness/identity evidence and remaining gates.

2026-10-12 implementation checkpoint: themed catalog/local navigation and compact prescription lookup
are implemented; pending dispense/payment proof labels are independent and nullable `recordId` is supported.
The Huy plan records passing local tests/build and isolated browser smoke; real Gateway role smoke remains open.

2026-10-16 checkpoint: drug detail/create/stock and ADMIN outbox now use the approved theme and
explicit request/error states. Drug flows add UUID/DTO guards, field focus, synchronous duplicate-submit
locks and server-returned stock. Conflict/unknown stock outcomes refresh before a new command;
outbox confirmation supports Escape/cancel and distinguishes replayed=false. Drug tests: 40/40;
outbox tests: 11/11. Browser fixture smoke covers create/detail/adjust and mobile layouts. Signed
actor/Gateway/paid-source integration stays open in the [single Huy handoff](../../../docs/handoffs/HANDOFF-HUY-MVP.md).

2026-10-16 prescription continuation: P-05 is FE-local complete; P-06 has all four local subtasks
complete but remains open for real-role Gateway smoke. V0 create uses the shared themed controls,
line-level validation/focus, duplicate-submit locking and the exact server prescription ID. Cancel
and bodyless dispense use labelled inline confirmations with Escape/return-focus, server results
and a fresh read after conflict or an unknown outcome. A failed follow-up read preserves successful
mutation evidence and retries only the read. Added tests: 51; full FE suite: 262/262 across 34 files;
typecheck/lint/production build pass. Isolated browser fixtures cover create/cancel/dispense and
desktop/mobile themes; they do not prove signed identity, Gateway roles or outpatient V1 activation.
See [checkpoint 14](../huy-frontend-implementation-plan.md#14-checkpoint-kê-đơn-và-terminal-actions--2026-10-16).

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
