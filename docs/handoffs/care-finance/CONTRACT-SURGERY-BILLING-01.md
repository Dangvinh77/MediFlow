# CONTRACT-SURGERY-BILLING-01 — Surgery charge, clearance and financial adjustment

- **Status:** `PARTIAL_IMPLEMENTATION`: planned-charge/request issuance, ledger payments/grants and
  current-clearance lookup, standalone completed-refund writer and pre-start cancellation adjustment
  implemented behind gates; full performed financial reconciliation, distributed START fencing and
  reviewed live release remain separate implementation tasks.
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
Billing's opt-in planned-charge intake groups quantities by the explicit `priceCode` inside this
one immutable fact to preserve the existing `(SURGERY, surgeryCaseId, priceCode)` charge identity.
It also retains each distinct `itemCode` and its original quantity in a local line-to-charge receipt;
two item codes sharing a price are not silently dropped. Quantities use `NUMERIC(19,4)` without
rounding, matching Surgery's producer range. Catalog unit amounts use exact two-decimal money;
line gross uses Billing's existing `HALF_UP` two-decimal charge rule. The total must fit money storage.
No zero-total payment request or financial grant is fabricated: that case remains a policy error
until a free-care authorization path exists. Account currency is VND for this intake; other currencies
require an explicit catalog contract. Existing accounts must match patient and episode; one episode
can generate charges in multiple departments. Account department attribution is preserved, while
each charge and request fact retains the producer's explicit generating department. Receipt cash
department remains the Billing account department, not a guessed earned-revenue allocation.
One selected SURGERY request is issued atomically with the charges and immutable source/delivery
receipts. A matched replay returns it without re-pricing or reopening a closed account. This local
intake does not implement performed-item reconciliation, cancellation refunds or held-event release.
The internal capture requires the same caller transaction as case creation. It is not a new
public case-creation API and does not bypass the separately missing referral authority.

The internal caller now exists in Surgery's LOCAL creation kernel (2026-10-07): case, pinned
checklist, histories, V7 HELD canonical charge bytes and V8 global request receipt commit together.
Outbox append is mandatory; write failure rolls every creation effect back. Same request/intent
across channels/replicas replays the original case without a second charge; changed intent conflicts.
Trusted actor authorization is required even on replay, with no production positive-authority
fallback. This changes no wire version/fixture and does not activate referral/HTTP delivery,
Billing pricing/issuance/reconciliation or Inpatient reference registration.

Creation HTTP follow-up on the same date adds an explicit default-off driving adapter and kernel
wiring, not a new charge schema or pricing authority. Both business and creation API gates require
real creation authority; missing provider fails startup. The HTTP key is the same global request UUID,
authorized replay preserves the original case/receipt, and actual owned-PG tests retain one HELD
charge under HTTP/internal SYSTEM contention. No upstream referral wire, Billing consumer approval
or held event release is implied. [Scoped evidence](../../superpowers/plans/2026-10-07-surgery-creation-api-batch.md).

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
Surgery validates quantity against its existing `NUMERIC(19,4)` storage (15 integer/4 significant
fractional digits), so persisted and held-wire values cannot diverge through database rounding.
This does not change field identity/version or authorize any Billing price/default catalogue.
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

### Implemented opt-in pre-start cancellation adjustment — 2026-10-08

The same ledger + surgery-charge-consumer gates select a strict cancellation helper on the
existing `billing.q`; there is no competing listener or failure fallback into the compatibility
automatic-refund handler. Both gates remain false. V10 adds immutable cancellation/delivery
receipts, durable PENDING/APPLIED/REJECTED status and per-original/per-charge refund-due evidence.
It does not backfill unverifiable historical processed markers or alter V7/V8/V9 checksums.

Actual Surgery V1 bytes must contain exact case/request/patient/generating department/episode,
admission/record, cancellation ID/sourceRevision=1, positive case revision, one supported pre-start
stage, required recorder account/nullable staff identity and matching occurredAt/cancelledAt
(future skew <=5 seconds at receive). No staff UUID is inferred for a valid account-only actor.
`reasonCode` is the producer's
`PRE_START_CANCELLATION`. Narrative participates in the immutable source fingerprint but is not
stored in this projection, void reason or public reply. Corrections/replacements, unknown
producer/version and malformed or conflicting facts reject without payload-bearing exceptions.

An early cancellation commits PENDING before ACK. The opt-in issuer applies it inside the same
creation transaction, before the new request can become payable; successful early cancellation
also suppresses the unpaid invoice notice. SQL/storage failure rolls back both issuance and
adjustment while preserving the previously committed PENDING receipt. A permanent early-context
mismatch is quarantined without poisoning valid issuance. Creation dispatch also recovers on exact
replay for history accepted by a previous writer; a separate bounded durable worker retries 20 due
cases per poll with 60-second deferral. Known mismatches roll back the delivery. Exact new-delivery
replay never reopens a case or reapplies adjustment, even after account closure.

V11 preserves the exact producer `requestedAt` ISO Instant for newly issued sources; PostgreSQL
TIMESTAMPTZ alone rounds nanoseconds. Existing V9/V10 sources are not backfilled with invented
precision. Cancellation within their half-microsecond uncertainty window fails closed with
`BILLING_SURGERY_CANCELLATION_TIME_UNVERIFIABLE`. Audit timestamps are captured from the database
after financial locks, so a winning payment cannot produce a grant newer than its revocation.

Case -> account -> ordered charge locks serialize issuer, owner completion, payments and refunds.
The adjustment requires the original V9 planned source, exact account/charge/request targets and
OPEN account; it rejects performed/voided charges, mixed/foreign requests and mismatched allocations.
All case charges are voided, exact SURGERY requests cancelled and grants revoked atomically with
the receipt and remaining original-only allocated refund budgets. Existing payments, allocations,
request totals and completion timestamps remain historical facts. Only request eligibility changes.

**A cancellation is not proof of a bank/cash refund.** It emits no `payment.refunded` and creates
no completed transaction. The gated ADMIN/CASHIER read
`GET /api/v1/billing/surgery-cancellations/{caseId}/refunds-due` returns projection status and current
remaining amounts grouped by original transaction. Amounts are computed in one Billing-owned
snapshot, subtracting actual linked reversal allocations and never another payment's amounts.
PENDING is not a confirmed zero obligation. The existing signed cashier refund command records
money actually returned; its ordinary canonical held refund fact is consumed unchanged by
Notification/Report. Gateway is unchanged: this new read is not claimed externally routed/activated.

This supersedes the standalone writer's missing cancellation adjustment path below **only for the
opt-in issuer**. The default compatibility handler now rejects paid cancellation before effects;
it cannot create an automatic completed refund. It also rejects a missing planned case/performed
care instead of recording a misleading processed marker. Unpaid compatibility voiding is not the
reviewed strict rollout/rollback strategy. Distributed START fencing, performed-vs-selected financial reconciliation,
bank execution, final settlement and held publication remain separate tasks.
[Implementation and real PG/Rabbit evidence](../../superpowers/plans/2026-10-08-surgery-cancellation-adjustment.md).

Implementation follow-up 2026-10-08: the standalone cashier refund command is now implemented;
it appends bounded immutable refund/own-allocation reversals, revokes locally unsatisfied grants
and captures held `payment.refunded` bytes accepted by Notification/Report tests. The existing
current-clearance lookup denies that grant after commit. It does **not** consume Surgery cancellation,
void charges, cancel requests, reconcile performed items, publish revoked/superseded grants or fence
a remote START occurring after an earlier valid read. Those tasks remain open. Canonical refund
wire/consumer limits are in [CARE-PROJECTIONS](CONTRACT-CARE-PROJECTIONS-01.md#completed-refund-fact--v1-paymentrefunded-2026-10-08).

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
