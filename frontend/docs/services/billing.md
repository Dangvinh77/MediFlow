# Frontend Service: Billing

**Owner:** Lộc (`locgit-89`)  
**Backend:** `backend/billing-service/**`  
**Contract context:** [`docs/ai/services/billing.md`](../../../docs/ai/services/billing.md)

## Writable frontend scope

- `frontend/src/features/billing/**`
- `frontend/src/app/(dashboard)/billing/**`

## Current UI baseline

`/billing` performs patient invoice lookup (legacy V0 invoice flow). `/billing/new` creates an
invoice from server-owned unpaid fees, and `/billing/{invoiceId}` renders fee detail and supports
payment. `/billing/payment-requests` + `/billing/payment-requests/{id}` complete the live V1
ledger "selected charge payment" flow (`LedgerPaymentController`) that Clinical/Lab's EXAM/LAB_TEST
charges depend on for clearance — there is no live list/search endpoint for a pending payment
request, so the cashier enters the exact id given by the originating screen. `/billing/revenue`
is the administrator/manager revenue-by-department report. `/billing/admin/outbox` is the
administrator outbox-replay tool. UI roles are `ADMIN` and `CASHIER` for invoices/payments,
`ADMIN`/`MANAGER` for revenue, `ADMIN` only for outbox replay; the browser never calculates
authoritative totals.

## Owner queue

- `FE-BILLING-01` — `DONE`: invoice detail uses the exact live DTO.
- `FE-BILLING-02` — `DONE`: invoice creation and fee detail are implemented.
- `FE-BILLING-03` — `DONE` for payment; refund remains absent because no live controller command exists.
- `FE-BILLING-04` — `IMPLEMENT`: test pagination, money display, terminal states, errors, and retries
  after the shared harness exists.
- `FE-BILLING-05` — `DONE` (2026-10-15): `/billing/revenue`, confirmed against the live Gateway rule
  (`ADMIN`/`MANAGER`), `RevenueByDeptDTO`, and empty/error behavior.
- `FE-BILLING-06` — `DONE` (2026-10-15): `/billing/admin/outbox`, confirmed against the live Gateway
  rule (`ADMIN`), the conditional maintenance settings, and the 404 not-found response.
- `FE-BILLING-07` — split in two (2026-10-15):
  - Prescription-adjustment refund-due view: `BLOCKED` — `PrescriptionAdjustmentController` is live
    and its two ledger consumer settings are already on in `docker-compose.yml`, but the Gateway has
    no allowlist rule for `/api/v1/billing/prescription-adjustments/**` (default-deny), so any call
    is rejected before reaching Billing. See
    [HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES](../../../docs/handoffs/HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md).
  - Surgery-cancellation refund-due view: stays out of scope — Surgery is deferred from the MVP
    (`docs/plans/01-system-design.md`), not merely Gateway-blocked.
- `FE-BILLING-08` — `DONE` (2026-10-15): `/billing/payment-requests` + `/billing/payment-requests/{id}`,
  the live V1 ledger payment flow (`CompleteLedgerPaymentRequest` / `LedgerPaymentDTO`). No new
  frontend read endpoint exists to list pending requests; the cashier enters the id directly.
  Payment method excludes `INSURANCE` (`LedgerPaymentService` rejects it as
  `BILLING_INSURANCE_IS_NOT_CASH`). Currency is a free-text ISO code input, not a hard-coded "VND"
  label, per the existing currency-semantics blocker below.

## Handoffs and blockers

- Currency labels are blocked until Billing or a product decision defines currency semantics.
  Acceptance: explicit currency/display rule; never assume VND from locale. The new ledger-payment
  form (`FE-BILLING-08`) works around this by asking the cashier for the 3-letter currency code
  directly instead of assuming VND.
- Patient display enrichment requires Patient's live contract. Producer: Patient / Hoàng Anh.
- Lab/Pharmacy references and saga state must come from Billing DTOs/events; do not query or import
  those frontend features to reconstruct an invoice.
- Prescription-adjustment refund-due view is blocked on the Gateway allowlist gap above; Surgery
  cancellation refund-due stays deferred with Surgery itself.

## Contract and verification gate

Verify live controller paths, roles, request validation, monetary serialization, idempotency, and
saga enums. Java `BigDecimal` responses are display-only numbers in the current frontend; do not
perform authoritative financial arithmetic in JavaScript.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Billing tests when available. Smoke test
invalid patient UUID, empty/error/retry, pagination, permission denial, and the changed payment flow.
