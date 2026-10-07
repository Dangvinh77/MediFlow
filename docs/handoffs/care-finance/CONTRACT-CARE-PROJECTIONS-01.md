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
| `settlement.completed` | final settlement outcome |
| `admission.closed` | discharge instructions/follow-up |

Patient contact data must come from explicit event snapshots or a permitted Patient lookup; a
consumer must not read Patient DB. Missing contact address creates a failed/skipped notification
record with reason, not a fabricated recipient.

## Report financial projections

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

| Event | KPI |
|---|---|
| `medicalrecord.completed` | completed outpatient visits/disposition |
| `lab.result.created` | completed lab tests by requesting department/date |
| `prescription.filled` | dispensed prescriptions/items |
| `admission.started` | admission count and immutable start evidence; not current-bed occupancy |
| `admission.closed` | administrative-close count/evidence; not medical discharge or bed release |
| `surgery.completed` | completed surgeries, duration, complications category |
| `surgery.cancelled` | cancellation count/reason category |
| `settlement.completed` | recognized totals, balance/refund outcome |

Every contribution is keyed by the source business ID plus event ID so replay/redelivery does not
double count. Reversal/adjustment events reference the original contribution instead of guessing its
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

These mappings are offline until producer/consumer runtime acceptance and publication gates pass.

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
