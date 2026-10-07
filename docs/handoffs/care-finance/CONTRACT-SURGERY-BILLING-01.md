# CONTRACT-SURGERY-BILLING-01 — Surgery charge, clearance and financial adjustment

- **Status:** `PARTIAL_IMPLEMENTATION`: ledger payments/grants and current-clearance lookup implemented;
  charge issuance, performed reconciliation and cancellation/refund workflow remain separate implementation tasks.
- **Owners:** Surgery — Huy; Billing — Lộc
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Price and charge source

### Huy-owned event identity — fixed for Billing fixtures (2026-10-07)

| Business fact | `eventType` and Rabbit routing key | Envelope `version` | Producer |
|---|---|---:|---|
| Case exists; request planned charges | `surgery.case.created` | 1 | `surgery-service` |
| Surgery performed; reconcile actual charges | `surgery.completed` | 1 | `surgery-service` |
| Pre-start cancellation; apply financial adjustment policy | `surgery.cancelled` | 1 | `surgery-service` |

Exchange: durable topic `mediflow.events`. No `.v1` routing suffix. JSON is the nested common
envelope, not the legacy flat payload. AMQP `messageId=eventId`, `correlationId=correlationId`,
`contentType=application/json`, headers `eventType` and integer `eventVersion=1`.
These names/version are no longer a Huy decision blocker: Billing may build fixtures against
the checked-in producer files below. This does not claim Billing consumer acceptance or live delivery.

`surgery.requested` remains Clinical/Inpatient → Surgery referral only. “Post-case charge” means
after creating a Surgery case, not after performing surgery. Actual post-operation reconciliation
uses `surgery.completed`; do not create a second `surgery.charge.*` event for the same fact.

After creating its own case, Surgery captures `surgery.case.created` with `surgeryCaseId`, `admissionId`, `patientId`, `departmentId`,
`procedureCode`, planned item/price codes and priority. Billing owns pricing and creates charges with
`sourceType=SURGERY` and `sourceId=surgeryCaseId`. Surgery never stores authoritative paid amount or
queries Billing tables.

### Post-case identity bridge V1 — 2026-10-07

`surgery.requested` is the upstream Clinical/Inpatient referral and cannot contain a Surgery-owned
case UUID before creation. It is NOT the Billing charge command. Surgery's separate
`surgery.case.created` envelope includes exact `surgeryRequestId`, patient/department/care episode,
nullable admission/record, `caseRevision=0`, `sourceRevision=1`, `sourceType=SURGERY`,
`sourceId=surgeryCaseId`, procedure/priority/requester/requestedAt and explicit nonempty
`plannedItems[{itemCode,priceCode,quantity}]`. No price, paid amount or clinical indication is sent.
One case has one immutable creation fact; repeated bytes are idempotent, changed planned items
conflict rather than silently revise the charge request. Billing owns price lookup and charge IDs.
The internal capture requires the same caller transaction as case creation. It is not a new
public case-creation API and does not bypass the separately missing referral authority.

The internal caller now exists in Surgery's LOCAL creation kernel (2026-10-07): case, pinned
checklist, histories, V7 HELD canonical charge bytes and V8 global request receipt commit together.
Outbox append is mandatory; write failure rolls every creation effect back. Same request/intent
across channels/replicas replays the original case without a second charge; changed intent conflicts.
Trusted actor authorization is required even on replay, with no production positive-authority
fallback. This changes no wire version/fixture and does not activate referral/HTTP delivery,
Billing pricing/issuance/reconciliation or Inpatient reference registration.

Producer fixtures live in Surgery's `src/test/resources/contracts/surgery-outcomes-v1/`.
Delivery stays HELD until Billing creates/reconciles charges and Inpatient registers exact case
references with durable early-event handling. A held row is NOT consumer acceptance.

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
  separately tracked readiness production task. Lifecycle still has no production authority-backed
  bean until other authority/policy/fence work is implemented. Its HTTP boundary is implemented but
  independently gated OFF; remote preflight now runs outside all transactions. No DB locks are held
  while Billing is queried, and committed local proof/Clock checks follow mutation-lock waits.
- Billing owns `src/test/resources/contracts/clearance-authority-v1/{active,inactive,missing}.json`.
  Producer use-case tests verify these fixtures; Surgery real Feign tests read these same files,
  changing only per-request correlation. PostgreSQL/HTTP producer tests exercise actual ledger payments.

## Performed items and completion

`surgery.completed` carries the actual performed item/price codes. Billing reconciles planned and
performed charges idempotently by `(surgeryCaseId, itemCode)`. A price code not recognized by Billing
is a contract/catalog error and must not default to zero.

The exact immutable source operation is `resultId` with `sourceRevision=1`, independent of
envelope `version` and mutable `caseRevision`. `surgeryCaseId` ties the result to the original
planned charge source. Payload carries exact patient/department/episode, nullable admission/record,
actual `startedAt`/`completedAt`, and `recordedAt`; envelope `occurredAt=recordedAt`.
`performedItems[{performedItemId,itemCode,priceCode,quantity}]` contains no price or amount.
Repeated delivery or identical business source under a new event ID must not apply reconciliation
twice; changed content for the same result/revision is a conflict, not an implicit correction.

### Files Billing should read after pull

All paths are relative to `backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/`:

| Scenario | Producer fixture (both `admission` and `outpatient`) |
|---|---|
| Planned charges | `surgery.case.created.<context>.v1.json` |
| Actual completion matching plan | `surgery.completed.<context>.v1.json` |
| Actual quantity 2 rather than planned 1; additional quantity 0.5 line | `surgery.completed.<context>.planned-difference.v1.json` |
| Syntactically valid code absent from Billing's test catalogue | `surgery.completed.<context>.unknown-price.v1.json` |
| Pre-start cancellation | `surgery.cancelled.<context>.v1.json` |

Use each completion variant in an isolated scenario with its matching case-created fixture.
Variants intentionally reuse the same case/result/event IDs to model alternative results,
NOT a valid correction stream. Feeding two different variants together must conflict.
`ITEM`, `PRICE`, `EXTRA_ITEM`, `EXTRA_PRICE` are synthetic contract test codes: Billing supplies
its own test catalogue/prices. `UNRECOGNIZED_PRICE` must be absent in the unknown-code test;
no production catalogue approval or expected monetary total is invented by Surgery.

Producer serializer tests validate these exact JSON structures. A real RabbitMQ test verifies
creation/completion routing, envelope version/headers and byte-identical retries. PostgreSQL tests
verify atomic HELD capture, duplicate/conflict and rollback. The transport test sends test fixture
bytes directly; it does not release V7 rows or connect a live referral/public creation endpoint.

## Cancellation and refund

`surgery.cancelled` identifies stage (`BEFORE_PREOP`, `AFTER_PREOP`,
`BEFORE_START` only in V1), exact case/admission IDs and reason. Post-start abort is not an implemented
V1 operation. Billing applies policy to void unearned
charges or create refund/credit transactions. It never mutates or deletes a completed payment.
Billing publishes `payment.refunded` when a real ledger refund completes.

## Acceptance criteria

- Duplicate surgery request/completion/cancellation events do not duplicate charges or refunds.
- Financial clearance for another case or admission is rejected.
- Surgery READY requires the financial guard plus all clinical/resource guards.
- Cancellation after payment creates an auditable adjustment; no event is described as a bank
  refund unless a completed refund transaction exists.
- Producer and consumer fixtures cover planned vs performed item differences and unknown price code.
