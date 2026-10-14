# Frontend Service: Surgery

- **Owner:** Huy (`LQHuy0210`)
- **Backend:** `backend/surgery-service/**`
- **Contract context:** [`docs/ai/services/surgery.md`](../../../docs/ai/services/surgery.md)

**Current execution plan (2026-10-12):** [Huy FE + approved themed UI](../huy-frontend-implementation-plan.md).
Local DTO/presenter/tests can start using the existing Vitest harness; public routes/commands still
require the activation and authority gates below. The older queue's absent-harness statement is historical.

## Planned frontend scope

- `frontend/src/features/surgery/**`
- `frontend/src/app/(dashboard)/surgery/**`

Shared navigation, authentication/session files, common components, API wrappers, packages, and
configuration remain shared scope and require an explicitly assigned shared task.

## Current UI baseline

No Surgery feature folder, route, or navigation entry exists. The backend exposes conditional case
query and lifecycle controllers, while both the Surgery business capability and Gateway route remain
disabled by default. The frontend must not advertise Surgery as available until the activation,
authority, DTO, role, error, and producer/consumer contracts pass their release gates.

## Owner queue

- `FE-SURGERY-01` — `BLOCKED`: verify the live case list/detail DTOs, roles, filters, error codes,
  business flag, and Gateway route before creating `/surgery` and `/surgery/[caseId]`.
- `FE-SURGERY-02` — `BLOCKED`: implement request, pre-op, checklist, consent, readiness, schedule,
  execution, completion, and cancellation controls only for commands whose contracts and authority
  providers are production-ready.
- `FE-SURGERY-03` — `BLOCKED`: add permission, validation, lifecycle, conflict, idempotency, retry,
  and integration tests after the unavailable shared frontend test harness and exact Surgery fixtures
  exist.

## Handoffs and blockers

- The Gateway Surgery route remains disabled by default and requires an explicit release decision.
- Patient, staff, department, Clinical, Inpatient, Billing, Pharmacy, and Report data remain owned by
  their producing contexts. Surgery UI uses only fields exposed by Surgery contracts.
- Authority evidence, consent relationships, financial clearance, event publication, and
  producer/consumer fixtures must be approved before their controls are exposed.

## Contract and verification gate

Confirm the live Surgery controllers, conditional properties, request/response DTOs, role
annotations, lifecycle transitions, idempotency headers, and error codes before each frontend slice.
Use the Gateway through `src/lib/api.ts`; never call port `8091` directly.

When implementation becomes available, run `pnpm typecheck`, `pnpm lint`, `pnpm build`, focused
Surgery tests, and an authenticated Gateway smoke test for every affected role.
