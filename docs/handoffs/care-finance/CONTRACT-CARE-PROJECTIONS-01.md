# CONTRACT-CARE-PROJECTIONS-01 — Notification and Report projections

- **Status:** `DESIGN_READY`; current outpatient projections are partially implemented
- **Owners:** Notification/Billing — Lộc; Report/Pharmacy/Surgery — Huy; Clinical/Lab/Inpatient — Vinh
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Projection rule

Notification and Report are consumers, not workflow authorities. Their failure must not roll back
the producer's committed care/payment transaction. Both deduplicate by `eventId`; Report projection
must be rebuildable from replay, and Notification must keep delivery history/retry state.

## Notification subscriptions

| Event | Template intent |
|---|---|
| `invoice.created` / payment request fact | amount and payment instructions |
| `payment.completed` | payment receipt; financial fact only |
| `payment.refunded` | completed refund/credit notice |
| `admission.deposit.requested` | deposit request |
| `deposit.topup.required` | additional deposit request |
| `admission.started` | admission/bed information |
| `surgery.ready` | surgery schedule/readiness notice |
| `surgery.cancelled` | cancellation/reschedule notice |
| `surgery.readiness.invalidated` | suppress only the exact prior snapshot's provisional reminder |
| `surgery.completed` | terminal reminder suppression only; no new clinical-result notification template |
| `settlement.completed` | final settlement outcome |
| `admission.closed` | discharge instructions/follow-up |

Patient contact data must come from explicit event snapshots or a permitted Patient lookup; a
consumer must not read Patient DB. Missing contact address creates a failed/skipped notification
record with reason, not a fabricated recipient.

### Surgery provisional reminder projection

READY is not confirmed room reservation or a finalized booking. Notification renders only a private
IN_APP provisional notice, never a promise that surgery will happen or clinical text copied from the
producer. Its immutable source is `readinessSnapshotId`; schedule ID/revision and exact case/request/
patient/department/episode/admission/record identity must match. Invalidation tombstones that exact
snapshot; a delayed READY for it records a suppressed history row without `notification.sent`.
Cancellation/completion are absorbing terminal case evidence and suppress every pending snapshot,
including when they arrive before READY. A new valid snapshot can produce a new provisional notice
only on a nonterminal case. Conflicting same-source evidence rejects, not another notification.

Cancellation has its own safe generic template; completion only suppresses reminders. Existing SENT
history is never deleted or rewritten. Future external reminder workers must consult the durable
suppression state; this slice sends no email/SMS and releases no held sent events. Invalidations may
record suppression state without an additional patient message; no arbitrary reason/narrative is
copied. Live publication/activation remains separately gated by same-byte producer/consumer tests.

## Report financial projections

### Completed-refund fact — V1 payment.refunded, 2026-10-08

Actual Billing refund transactions now capture a HELD nested envelope with `eventType=payment.refunded`,
integer `version=1`, producer `billing-service`, routing key `payment.refunded` on `mediflow.events`.
Payload fields are `refundTransactionId`, `originalTransactionId`, `accountId`, `patientId`,
`departmentId`, `careEpisodeType`, `careEpisodeId`, exact positive NUMERIC(19,2) `amount`,
`currency=VND`, `reason=CASHIER_RECORDED_REFUND`, `completedAt`; envelope `occurredAt=completedAt`.
Refund/original IDs must differ. Free-text reason is private Billing audit, never this public fact.
No source revision, replacement marker, settlement, clinical outcome or earned allocation is inferred.
Producer-service serializer fixtures are `ledger-v1/refund-service.json` and `refund-deposit.json`;
Notification and Report read the same original files. [Raw hashes and verification](../../superpowers/plans/2026-10-08-billing-refund-closure.md).

Notification's care-v1 AND refund-consumer gates, both false, register
`notification.refunds-v1.q`/DLQ and only this routing key. Semantic identity is
`(payment.refunded,refundTransactionId)` with payload fingerprint, separate from delivery identity.
Claim/source receipt, private `PAYMENT_REFUNDED` IN_APP history and HELD sent outbox commit together.
Duplicate or new delivery for the same source cannot notify twice; changed source facts conflict.
Template does not promise settlement or a booked/cancelled surgery. No insecure text/contact lookup.

Report's care-finance-v2 AND cash-refund-consumer gates, both false, register
`report.cash-refunds-v2.q`/DLQ and only this routing key. V15 separates typed refund/delivery evidence
and cash-out daily totals from V12 gross inflows. Report must match its own original completed receipt
by exact transaction/account/patient/department/episode/currency/zone, and inherit SERVICE_PAYMENT or
ADMISSION_DEPOSIT classification **from that receipt**, not episode type. Completed time cannot precede
the original. Applied refunds for one original cannot exceed the original receipt. Both scope totals
and evidence commit together under a shared original-payment advisory lock used by receipt writing.

Unsupported correction/replacement markers are rejected by both consumers, not reclassified as new
completed refunds. Original absence yields durable PENDING with no cash-out effects. Later receipt processing recovers
20 rows; a separate due-original worker processes 20 originals per poll and defers failed work by
60 seconds. Invalid early evidence becomes REJECTED without poisoning the valid original. Known
invalid deliveries reject transactionally; source/delivery collisions never replace prior evidence.
Both listeners retry transient failures three times; DLQs retain original bytes for deliberate replay.
Durable recovery is tested separately from process/JVM-crash acceptance.

Cash-out uses actual refund completion date and the original receipt's account department/classification;
hospital scope is null. Original business date is retained for future recognition/liability reversal.
Gross receipts are not decremented, and no earned revenue/deposit liability/settlement reversal is
fabricated without allocation/recognition facts. Existing V13 cash replay rebuilds **gross inflows only**;
full refund/financial replay, accepted publication and historical coverage remain open. This local
cash-refund slice does not close the final-period financial reversal acceptance below or release V1.

### Report paired finite accepted cash rebuild — V17, 2026-10-08

Internal `ReplayCashRefundsUseCase` now freezes a new V13 receipt manifest and all accepted V15
refund states in one PostgreSQL REPEATABLE READ transaction. Missing first-delivery evidence
aborts both; a lower-isolation outer caller is rejected. Existing gross-only runs remain unchanged.
Version 1 is a local copied-input format, not a Billing revision; exact monetary values, typed IDs,
ISO nanoseconds, original day/classification and pending/rejected reasons are retained minimally.
New frozen JSONB hashes protect local snapshot integrity; copied source/envelope hashes remain
provenance, not reconstructed raw-event proof.

After gross replay verifies, bounded 1..500 refund batches recheck APPLIED refunds against the
frozen exact original and total accepted refunds for it, restore isolated facts and apply refund-day
cash-out to hospital/account-department scopes. Live and replay share the same scope planner.
Generation locks and one transaction protect facts, both scopes and progress. PENDING/REJECTED
are retained inventory, never silently recovered into cash-out by replay. Later live recovery
does not change this snapshot; a new generation can capture the new state.

Full bidirectional copied fact/proof/scope and count/version reconciliation yields VERIFIED or
FAILED; receipts must also verify. VERIFIED means finite accepted cash-state equality only,
not historical arrival/rejection replay, pending live catch-up, export coverage, recognition,
deposit-liability release, settlement or financial publication. No live rows/inbox are reset,
commands republished, flags enabled or public APIs added. This supersedes only the absence of an
accepted refund cash-state rebuild above. [Implementation/tests/limitations](../../superpowers/plans/2026-10-08-huy-master-sync-refund-replay.md).

### Held Surgery payment-request fact — V1 invoice.created

CURRENT V0 compatibility (2026-10-08): Notification's current queue also binds flat
`invoice.created` and records a private IN_APP unpaid-request notice from the explicit invoice ID,
patient ID and amount. This supplies a real subscriber for Billing's mandatory confirmed invoice
outbox, without skipping an unroutable predecessor. Both producer serialization and consumer tests
use Billing's `contracts/invoice.created.json` bytes. Required metadata/non-negative amount are
validated; no contact, booking, paid-receipt or clinical authorization is inferred. V1 envelopes
remain subject to separate gated readers and are never downgraded into this compatibility template.
The mixed current queue's V1 rejection behavior and the separate opt-in Surgery queue must be
reviewed at cutover; this binding does not enable held publication or certify V1 deployment.

Billing's new planned-charge issuer captures a HELD nested V1 `invoice.created`, not the flat V0
invoice saga payload. Producer is `billing-service`; payload: `invoiceId`, `paymentRequestId`,
`accountId`, `patientId`, `departmentId`, `careEpisodeType`, `careEpisodeId`, `purpose=SURGERY`,
`surgeryCaseId`, nullable `admissionId`, exact positive two-decimal `totalAmount`, `currency=VND`,
`createdAt` and nullable `expiresAt`; envelope `occurredAt=createdAt`. Invoice/request IDs are distinct
Billing-owned identifiers; case is the explicit Surgery source. ADMISSION requires its exact admission
episode; outpatient has no admission. This is a request, never a receipt/clearance/revenue fact.
Notification may render a private IN_APP payment request, with no external address or procedure text.
No legacy consumer is allowed to reinterpret the V1 envelope as a flat V0 invoice. Publication remains
held until version-aware consumer fixtures, runtime and rollout gates pass together.

Local acceptance 2026-10-08: Billing actual issuer tests serialize the checked-in
`ledger-v1/invoice-surgery-{admission,outpatient}.json`; Notification decoder and PG/Rabbit tests read
those same raw files. Its distinct private SURGERY_PAYMENT_REQUEST template, semantic request dedupe,
changed-source conflict, rollback and retained-byte recovery pass. Full Notification suite is
145/145 with zero skips. This closes that local engineering acceptance, not V6 held release,
external delivery, other purpose schemas or whole-system rollout. [Hashes and evidence](../../superpowers/plans/2026-10-08-cross-service-closure.md).

Report must separate:

- cash received: completed payment transaction;
- deposit liability: unearned inpatient deposit balance;
- earned revenue: settled/recognized charge amount according to Billing event;
- refunds: completed refund transactions;
- outstanding receivable/debt: settlement outcome when explicitly published.

`payment.completed` alone must not count an admission deposit as earned revenue. During migration,
current outpatient invoice revenue can continue under the existing contribution model, while every
event includes a transaction/account/episode classification that lets Report distinguish deposit.

### Report offline gross receipt evidence — 2026-10-07

Report now reads Billing's actual `ledger-v1/payment-service.json` and `payment-deposit.json`.
The accepted **local cash-only** input is `payment.completed` V1 with `transactionId`, optional
`invoiceId`, required `paymentRequestId/accountId/patientId/departmentId/careEpisodeType/careEpisodeId`,
explicit classification, positive exact NUMERIC(19,2) `totalAmount`, currency, CASH/TRANSFER and
business `completedAt`. The transaction is an immutable PENDING → COMPLETED singleton in Billing;
Report keys it by `transactionId`, not invoice/request or delivery ID. No business revision is
invented from envelope version. Correction/replacement markers and unsupported classifications,
including SETTLEMENT_PAYMENT without a same-byte producer fixture, remain rejected.

V12 isolates minimal receipt evidence, delivery/source fingerprints and gross receipt aggregates
from all legacy/V6 financial tables. Exact delivery or same transaction/new event has one effect;
changed business/payload/date/zone evidence conflicts. Two distinct transactions on one invoice
are independent receipts. Currency, report timezone and SERVICE_PAYMENT/ADMISSION_DEPOSIT remain
separate aggregate dimensions. Department here means the **receipt's Billing account department**,
not a charge allocation or earned-revenue department; hospital is null scope. Both scope updates
and source/delivery evidence commit together, including under PostgreSQL concurrency/rollback.
Financial JSON amounts stay BigDecimal from parsing; existing operational V11 normalization is
unchanged. Persisted completion ISO text retains nanoseconds without retaining raw payloads.

Gross receipts are completed cash inflows **before any refunds**, not net cash or a liability
balance. This local input/kernel does not resolve the full financial dashboard equations: no
allocated-earned amount, unallocated deposit amount, release, refund, settlement, currency
conversion or department split is inferred. Those metrics remain unavailable, not zero. No live
binding, HTTP financial API, accepted publication, history backfill or financial replay/cutover is
enabled. Producer/consumer runtime and financial expected-totals acceptance remain OPEN.

### Report internal finite gross receipt rebuild — 2026-10-07

**Runtime intake follow-up 2026-10-08:** the existing cash-only mapping/kernel/replay now has a
separate opt-in broker adapter. `report.cash-receipts-v2.q` binds only `payment.completed`, has
its own DLQ and requires both `care-finance-v2` and `report.cash-receipt-consumer.enabled` (false
defaults). It uses unchanged Billing fixture bytes/source semantics above, ACKs after the V12
transaction and rejects unsupported/colliding source facts; transient storage failures have three
attempts and retained-byte recovery. Compatibility and V1 listeners can coexist without legacy
deposit revenue. This supersedes historical cash-listener absence below, not five-metric finance,
historical/source approval, publication or held delivery. [Exact manifest and evidence](../../superpowers/plans/2026-10-08-report-cash-intake.md).

V13 adds an **internal/offline cash-only rebuild** from the V12 accepted minimal receipt store.
One `INSERT ... SELECT` freezes the statement-visible committed receipt set, copying exact typed
inputs, transaction identity, first delivery ID and source/fact/envelope fingerprints. A missing
first-delivery proof aborts freeze; it is not silently excluded. A delivery hash is retained
provenance, not a reconstructed/verified raw Billing envelope. There is no purge/backfill/export
approval claim, and no event predating this local source can be recovered without owner export.

Snapshot version 1 and projector version 1 are Report-local formats, not Billing source revisions.
The strict codec retains exact monetary decimals and completed-at nanoseconds and preserves V12
hash bytes. Replay and receipt writing share the pure gross-cash scope planner. Generations own
their frozen manifest, semantic receipt keys and currency/classification/timezone/scoped totals;
no live inbox/projection reset, Billing REST enrichment or republished command is involved.

Bounded 1..500 input batches hold a DB generation lock and commit receipt dedupe, both scopes,
applied markers and progress together. Failure rolls the whole batch back so it is resumable;
malformed/unknown-version/corrupt snapshots reject before any effect. An exhausted manifest with
progress drift goes to reconciliation, not a perpetual BUILDING loop. Final bidirectional checks
compare complete fact/provenance sets, all scope dimensions, amounts, receipt counts and progress
against the frozen manifest, never advancing live totals. Equality yields VERIFIED, mismatch FAILED.
Terminal retry is no-effect, and separate generations do not share dedupe.

VERIFIED proves only equality with that finite accepted receipt manifest. It does not certify
historical completeness, earned revenue/deposit liability/refund/settlement, clinical clearance,
owner approval, live catch-up or a financial read publication. No listener, financial API, accepted
read switch or held producer delivery is enabled. The ten local implementation subtasks and real
verification are recorded in the [Huy cash replay batch](../../superpowers/plans/2026-10-07-report-cash-replay-batch.md).

## Report operational projections

### CURRENT V0 prescription source dedupe — 2026-10-08

The existing single-slip/single-fill Pharmacy contract provides exact `prescriptionId` in its flat
`prescription.filled` fact. Report V16 now distinguishes that immutable business source from
`eventId`: accepted fills after deployment apply once across new deliveries/concurrent replicas.
Changed exact occurredAt, department, reporting zone or effective grouped drug/name/quantity
conflicts, rather than creating an implicit correction. Original-ID dedupe is unchanged.
Source receipt, delivery claim and both legacy aggregate scopes share one local transaction.
No wire/binding/version changes or owner-side producer IDs are introduced; no historical source
proof is reconstructed from old aggregate totals. This hardens compatibility behavior, not full
V2 operational coverage/replay/publication. [Scope and verification](../../superpowers/plans/2026-10-08-huy-distributed-flow-priority.md).

| Event | KPI |
|---|---|
| `medicalrecord.completed` | completed outpatient visits/disposition |
| `lab.result.created` | completed lab tests by requesting department/date |
| `prescription.filled` | dispensed prescriptions/items |
| `admission.started` | admission count and immutable start evidence; not current-bed occupancy |
| `admission.closed` | administrative-close evidence only currently; not medical discharge or bed release |
| `surgery.completed` | completed surgeries, duration, complications category |
| `surgery.cancelled` | cancellation count/reason category |
| `settlement.completed` | recognized totals, balance/refund outcome |

Delivery provenance uses event ID; contribution uniqueness uses the exact source business
operation/revision/metric, independently of delivery ID. Reversal/adjustment events reference the original contribution instead of guessing its
date or department.

### Operational source identity and revision

- Clinical V1 completion is an immutable singleton `(medicalrecord.completed, recordId)` operation:
  the producer permits only OPEN → COMPLETED and no subsequent update/correction. Its implicit
  operation revision is 1, not the envelope version. Count one completed outpatient encounter even
  if disposition is ADMISSION; this does not count an inpatient admission. Current producer bytes
  do not contain an episode pair: Report retains null episode dimensions, never substitutes recordId
  or appointmentId. Disposition and admissionRequired must agree. Corrections remain unsupported.
- Pharmacy V1 fill is an immutable singleton `(prescription.filled, dispenseId)` operation, revision 1.
  Report counts one fill plus the sum of explicit positive integral item quantities, using filledAt
  and requesting department/episode. It never counts requested or cancelled prescriptions as fills.
- Surgery V1 result/cancellation use explicit resultId/cancellationId and sourceRevision=1. Count
  actual completions/cancellations at completedAt/cancelledAt. Duration is whole elapsed minutes per
  result, floored before aggregation from actual startedAt → completedAt (not planned schedule or
  recordedAt). A missing interval/category is not inferred. Only controlled category/stage is retained;
  clinical narrative and patient IDs never enter the operational replay journal.

These mappings now have a separate default-off operational intake (2026-10-08). The seven approved
operational/admission keys feed only their existing transactional kernels; financial events are
not bound. Admission start counting was added on 2026-10-09 as specified below; administrative
close remains minimal pending/pairing evidence, not medical discharge/LOS/occupancy.
Actual producer-byte PG/Rabbit tests include duplicate/conflict, correction rejection, atomic
two-scope rollback, retained-byte DLQ replay and finite rebuild. Disabled gates register no queue
or listener. Legacy projections and accepted publications stay unchanged; coverage/export,
correction/import, live catch-up and read-cutover acceptance are separate gates. See
[current evidence](../../superpowers/plans/2026-10-08-huy-ready-task-completion.md).

- For `lab.result.created`, `labId` is the source business ID and payload `resultVersion` is the
  business result revision. Envelope `version` is only the wire-schema version. The current Lab V2
  state machine starts at result revision `0`, emits its first and only supported completed snapshot
  as revision `1`, and makes `COMPLETED` terminal. Legacy/imported rows remain revision `0` and are
  not a V2 replay source. Import publication and result correction/replacement are not approved;
  revisions above `1` must be rejected until that contract and producer fixture exist.
- An exact redelivery or same `labId + resultVersion` snapshot is idempotent. The same business key
  with different payload bytes is a contract conflict, not another result. A lower revision is stale
  only after a future multi-revision contract defines the accepted correction chain; current V1 does
  not silently apply, recast or reorder unsupported revisions.
- `admission.started` and `admission.closed` are separate immutable singleton operations. Their
  business identities are `(admission.started, admissionId)` and `(admission.closed, admissionId)`;
  each has implicit operation revision `1` because Core V1 permits each transition once and exposes
  no correction command. This revision is a contract constant, not envelope `version` and not a new
  payload field. Exact semantic duplicates are idempotent; conflicting snapshots for the same
  operation are contract errors.
- As of 2026-10-09, canonical starts additionally apply exactly one `ADMISSIONS` contribution at
  `admittedAt` in the configured report zone, with the exact start department and ADMISSION episode.
  Evidence validation, delivery/source claims, minimal replay journal and department/hospital scopes
  commit in one transaction. Same-source/new-delivery retries count once; changed bed/emergency/time
  snapshots conflict even if the scalar KPI is unchanged. An early close must match patient and
  chronology before counting the late start. Close itself adds no metric. Existing operational finite
  replay rebuilds the same two start-count scopes without changing publication or live reads.
  Old evidence-only rows require a newly received actual, revalidated source start, not inferred
  backfill. Unsupported correction/supersedes/revision markers reject before effects.
  [Verification](../../superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).
- `admittedAt`, `approvedAt` and `closedAt` are producer business times. Delivery may be reordered:
  a close received before its exact start remains pending and may pair only with the same admission
  and patient. The later start never reopens a closed admission. Medical discharge and administrative
  close remain distinct facts.

The current events do not define bed transfer, bed release, staffed/available capacity or medical
LOS. Report must not turn the start bed into current occupancy, administrative `closedAt` into medical
discharge time, or a missing dimension into zero. Those additions require approved producer events,
business identity/revision, time/rounding rules and same-byte fixtures.

## Acceptance criteria

- Duplicate events create one notification intent and one report contribution.
- Deposit payment increases cash and liability, not earned revenue.
- Refund reverses the original financial contribution in the correct period/department.
- Report can rebuild the same totals from an empty projection using event replay.
- Notification templates do not expose diagnosis/result details on insecure channels.
- Unknown event version or missing source ID follows retry/DLQ policy and does not silently alter a
  projection.
