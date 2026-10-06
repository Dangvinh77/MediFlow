# CONTRACT-SURGERY-BILLING-01 — Surgery charge, clearance and financial adjustment

- **Status:** `PARTIAL_IMPLEMENTATION`: ledger payments/grants and current-clearance lookup implemented;
  charge issuance, performed reconciliation and cancellation/refund workflow remain separate implementation tasks.
- **Owners:** Surgery — Huy; Billing — Lộc
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Price and charge source

Surgery publishes a request with `surgeryCaseId`, `admissionId`, `patientId`, `departmentId`,
`procedureCode`, planned item/price codes and priority. Billing owns pricing and creates charges with
`sourceType=SURGERY` and `sourceId=surgeryCaseId`. Surgery never stores authoritative paid amount or
queries Billing tables.

## Clearance

Billing publishes `financial.clearance.granted` with `purpose=SURGERY`, the admission episode,
`admissionId` and `surgeryCaseId`. Surgery accepts it only when patient, admission and case all match. The
clearance satisfies the financial guard only; consent, pre-op, team and schedule guards remain
independent.

## Current financial authority — additive REST contract (2026-10-06)

The user's task-scoped override authorizes Huy to implement both Billing producer and Surgery
consumer; these engineering dependencies are **not waiting for Lộc to write code**.

- Direct internal `GET /api/v1/billing/financial-clearances/{clearanceId}/lookup`.
  Never a Gateway/human API; Gateway's default-deny matrix does not allow this path.
- Opt-in Billing switch `mediflow.billing.clearance-lookup.enabled=false` / environment
  `MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED`. It does not release held V1 events or enable Surgery.
- JWT requires `sub=surgery-service`, `type=service`, `role=SYSTEM`, signed short-lived credential,
  mandatory `iat`/`exp`, positive lifetime <=60 seconds and future issue skew <=5 seconds.
  This credential authenticates only this exact GET lookup, not payment or invoice APIs.
- Required `X-Correlation-Id`, nonblank, <=120 characters; same value in success header and envelope.
- Standard envelope data: `exists`, `clearanceId`, `eligible`, `invoiceId`, `accountId`, `patientId`,
  `purpose`, `careEpisodeType`, `careEpisodeId`, `admissionId`, `surgeryCaseId`, `grantedAt`,
  `expiresAt`, `observedAt`. No prices, paid amounts, narratives or contact details.
- Confirmed absence is 200 with `exists=false`, `eligible=false`, exact requested clearance UUID,
  null context/expiry/grant fields and fresh `observedAt`. 404/401/403/5xx are **unverifiable**, not absence.
  Storage failure is 503 `BILLING_CLEARANCE_UNAVAILABLE`, with correlation and no SQL details.
- Current eligibility requires SURGERY/PAID, nonclosed/nonsettled account, exact grant/request/account
  patient, invoice, currency and episode, exact request target, nonempty POSTED SURGERY charges for
  the same case/account/patient whose selected amounts sum to the request, and completed net
  payments minus refunds/reversals satisfying the request. Emergency override never bypasses this check.
  Revoked, future-granted and expired grants deny; expiry is exclusive. This read does not invent a
  refund writer or a payment request issuer. Those are still separate code tasks, not owner approvals.
- Billing reads its own database in one PostgreSQL statement/MVCC snapshot. `observedAt` is the
  statement time. Surgery compares all returned IDs with its stored immutable grant and exact case.
- Surgery mandates a new lookup before internal READY/finalize/START mutation locks, verifies
  observation age <=30 seconds (future skew <=5 seconds) again after resource-lock wait, and
  caps business validity at both producer/local grant expiry. Read freshness does NOT fabricate
  a 30-second expiry for a READY/SCHEDULED case: START always performs its own new lookup.
  Failure cannot reuse a prior positive observation.
  COMPLETE does not require a new financial eligibility decision for already performed care.
- This is a **bounded observation, not a distributed financial lock**. No financial business revision
  is fabricated from event version/time. The existing proof revision still identifies the stored
  immutable grant; it is not a refund-ledger revision. Refund-after-read race/fence remains a
  separately tracked readiness production task. Internal lifecycle still has no production bean/API
  until the other authority/policy/fence work is implemented.
- Billing owns `src/test/resources/contracts/clearance-authority-v1/{active,inactive,missing}.json`.
  Producer use-case tests verify these fixtures; Surgery real Feign tests read these same files,
  changing only per-request correlation. PostgreSQL/HTTP producer tests exercise actual ledger payments.

## Performed items and completion

`surgery.completed` carries the actual performed item/price codes. Billing reconciles planned and
performed charges idempotently by `(surgeryCaseId, itemCode)`. A price code not recognized by Billing
is a contract/catalog error and must not default to zero.

## Cancellation and refund

`surgery.cancelled` identifies stage (`BEFORE_PREOP`, `AFTER_PREOP`, `BEFORE_START`,
`IN_PROGRESS_ABORTED`), exact case/admission IDs and reason. Billing applies policy to void unearned
charges or create refund/credit transactions. It never mutates or deletes a completed payment.
Billing publishes `payment.refunded` when a real ledger refund completes.

## Acceptance criteria

- Duplicate surgery request/completion/cancellation events do not duplicate charges or refunds.
- Financial clearance for another case or admission is rejected.
- Surgery READY requires the financial guard plus all clinical/resource guards.
- Cancellation after payment creates an auditable adjustment; no event is described as a bank
  refund unless a completed refund transaction exists.
- Producer and consumer fixtures cover planned vs performed item differences and unknown price code.
