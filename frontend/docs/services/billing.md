# Frontend Service: Billing

**Frontend owner:** Lộc (`locgit-89`); Vinh is delegated this closeout by the user's 2026-10-20 assignment. Backend ownership follows the root AGENTS.md.

**Backend:** `backend/billing-service/**`  
**Contract context:** [`docs/ai/services/billing.md`](../../../docs/ai/services/billing.md)

## Current implementation — 2026-10-20

`/billing` supports paginated patient invoice lookup and prescription-adjustment refund lookup.
`/billing/new` creates an invoice from server-owned fees; invoice detail displays fees and accepts
payment. Confirmed payment refreshes authoritative fee rows; a refresh failure cannot reopen payment.
An uncertain V0 payment stays locked because that endpoint has no idempotency contract. Checking
status only reads; an unpaid read after an uncertain response does not authorize another payment.

`/billing/payment-requests` discovers the exact server request from purpose, source, patient and
care episode through `GET /v1/billing/payment-requests/by-source`. EXAM uses the appointment source
identity defined by the contract; LAB_TEST/PRESCRIPTION require the explicit episode reference.
Amount, remaining amount, currency, expiry and account/request status come from Billing. A direct
request-ID route links back to discovery before payment. No free-text currency or guessed balance.
Ledger payment accepts CASH/TRANSFER and at most the server remaining amount. It locks synchronously,
retains the same payload/idempotency key for explicit retry after uncertainty, and only COMPLETED
means success. Lookup remains locked while payment is pending or uncertain, preventing a fresh
read from unmounting the payment intent. Session changes clear private drafts/results.

Prescription refund lookup uses `GET /v1/billing/prescription-adjustments/{id}/refunds-due`.
ADMIN/CASHIER can record an already completed refund with
`POST /v1/billing/transactions/{id}/refunds`, explicit amount/reason/method and reconciliation
confirmation. The obligation is not presented as the remaining refundable balance. The backend
validates eligibility and cumulative refunds. Unknown outcomes block blind resend. This command
records a cashier refund; it does not transfer money through a bank.
The previous Gateway blocker is superseded by implemented rules (PR #431); the existing handoff's
broader acceptance criteria remain owned by its maintainers.

Revenue remains ADMIN/MANAGER; outbox replay remains ADMIN only. Replay feedback distinguishes
`replayed: false` and successful requeue from downstream processing. Invoice/refund/payment UI is
ADMIN/CASHIER, with backend authorization authoritative.

## Owner queue

- `FE-BILLING-01/02` — DONE: exact invoice DTO, create/detail/fee flows.
- `FE-BILLING-03` — DONE for implemented MVP payment and prescription refund recording.
- `FE-BILLING-04` — DONE: API/component regression coverage for pagination, money, terminal states,
  errors, session changes, duplicate submits and uncertain outcomes; signed browser payment/refund.
- `FE-BILLING-05/06` — DONE: existing revenue and outbox routes; replay feedback hardened.
- `FE-BILLING-07` — DONE for prescription adjustment/refund; Surgery remains deferred from MVP.
- `FE-BILLING-08` — DONE: source discovery and server-backed selected-charge ledger payment.

## Acceptance and remaining dependencies

See [2026-10-20 evidence](../evidence/vinh-loc/2026-10-20/README.md) for signed browser flows,
verification and limits. Legacy V0 invoice amounts still have no currency field; display them
without inventing VND. V1 currency is the server response currency. Patient/staff display selectors
and cross-context enrichment require their own authorized contracts. No client-side joins.
Production flag rollout and external payment/provider acceptance are separate release work.
