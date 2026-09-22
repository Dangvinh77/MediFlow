# Frontend Service: Billing

**Owner:** Lộc (`locgit-89`)  
**Backend:** `backend/billing-service/**`  
**Contract context:** [`docs/ai/services/billing.md`](../../../docs/ai/services/billing.md)

## Writable frontend scope

- `frontend/src/features/billing/**`
- `frontend/src/app/(dashboard)/billing/**`

## Current UI baseline

`/billing` performs an explicit patient UUID lookup and renders paged invoices through
`GET /v1/billing/patient/{patientId}`. It displays invoice totals, paid/payment method state, saga
status, and timestamps. UI roles are `ADMIN` and `CASHIER`.

## Owner queue

- `FE-BILLING-01` — `VERIFY-CONTRACT`: add invoice detail using the exact live DTO.
- `FE-BILLING-02` — `VERIFY-CONTRACT`: add one invoice creation or fee-detail slice supported by
  the live controller.
- `FE-BILLING-03` — `VERIFY-CONTRACT`: add one payment/refund lifecycle action with exact idempotency,
  saga status, and failure behavior.
- `FE-BILLING-04` — `IMPLEMENT`: test pagination, money display, terminal states, errors, and retries
  after the shared harness exists.

## Handoffs and blockers

- Currency labels are blocked until Billing or a product decision defines currency semantics.
  Acceptance: explicit currency/display rule; never assume VND from locale.
- Patient display enrichment requires Patient's live contract. Producer: Patient / Hoàng Anh.
- Lab/Pharmacy references and saga state must come from Billing DTOs/events; do not query or import
  those frontend features to reconstruct an invoice.

## Contract and verification gate

Verify live controller paths, roles, request validation, monetary serialization, idempotency, and
saga enums. Java `BigDecimal` responses are display-only numbers in the current frontend; do not
perform authoritative financial arithmetic in JavaScript.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Billing tests when available. Smoke test
invalid patient UUID, empty/error/retry, pagination, permission denial, and the changed payment flow.
