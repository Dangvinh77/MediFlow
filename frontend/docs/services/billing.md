# Frontend Service: Billing

**Owner:** Lộc (`locgit-89`)  
**Backend:** `backend/billing-service/**`  
**Contract context:** [`docs/ai/services/billing.md`](../../../docs/ai/services/billing.md)

## Writable frontend scope

- `frontend/src/features/billing/**`
- `frontend/src/app/(dashboard)/billing/**`

## Current UI baseline

`/billing` performs patient invoice lookup. `/billing/new` creates an invoice from server-owned
unpaid fees, and `/billing/{invoiceId}` renders fee detail and supports payment. UI roles are
`ADMIN` and `CASHIER`; the browser never calculates authoritative totals.

## Owner queue

- `FE-BILLING-01` — `DONE`: invoice detail uses the exact live DTO.
- `FE-BILLING-02` — `DONE`: invoice creation and fee detail are implemented.
- `FE-BILLING-03` — `DONE` for payment; refund remains absent because no live controller command exists.
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
