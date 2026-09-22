# Frontend Service: Lab

**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Backend:** `backend/lab-service/**`  
**Contract context:** [`docs/ai/services/lab.md`](../../../docs/ai/services/lab.md)

## Writable frontend scope

- `frontend/src/features/lab/**`
- `frontend/src/app/(dashboard)/lab/**`

## Current UI baseline

`/lab` renders a paged queue from `GET /v1/lab` with department and status filters. It displays
clinical lifecycle status separately from payment state. UI roles are `ADMIN`, `MANAGER`, and
`LAB_TECH`.

## Owner queue

- `FE-LAB-01` — `VERIFY-CONTRACT`: add test detail with the exact live response DTO.
- `FE-LAB-02` — `VERIFY-CONTRACT`: add one request-creation slice, including record/patient
  references and backend validation errors.
- `FE-LAB-03` — `VERIFY-CONTRACT`: add one result-entry or lifecycle-transition slice supported by
  the live controller; keep abnormal-result semantics explicit.
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
