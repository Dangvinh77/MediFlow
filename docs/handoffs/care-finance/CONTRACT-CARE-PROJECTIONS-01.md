# CONTRACT-CARE-PROJECTIONS-01 — Notification and Report projections

Rx continuation 2026-10-09: Report reads the exact Billing producer-service installment fixtures
`payment-prescription-first.json` and `payment-prescription-last.json` from Billing's ledger-v1
directory. They retain the existing receipt schema: two transaction sources on one selected Rx
request are two SERVICE_PAYMENT inflows, not two invoices or earned revenue. No new financial
equation, liability/recognition/reversal or historical hash representation is introduced. Optional
canonical Rx/Lab evidence must be singular, with distinct canonical Lab UUIDs; a deposit cannot
carry service targets. Invalid evidence rejects before claims and reaches the cash DLQ.
PRESCRIPTION invoice notice now has guarded single-reader private intake as specified below.
Actual Billing original-linked Rx refunds use the unchanged completed-refund schema: separate
cash-out, never an earned-revenue reversal or settlement. No held event is released.
[Current adjustment evidence](../../superpowers/plans/2026-10-09-pharmacy-report-rx-adjustment.md).

- **Status:** placement/capacity producer sources `PRODUCER_READY`; Report consumption, replay,
  coverage and publication remain active owner work
- **Owners:** Notification/Billing — Lộc; Report/Pharmacy/Surgery — Huy; Clinical/Lab/Inpatient — Vinh
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Projection rule

### Finite operational SURGERY publication — 2026-10-12, LOCAL / PostgreSQL verified

Report now supplies its own additive operator workflow on the existing retained operational journal:
ADMIN starts a finite rebuild, advances 1..500 events per atomic batch and obtains the frozen
manifest fingerprint via `/api/v1/reports/operations/rebuilds/{id}`. Publish/rollback commands are
under `/api/v1/reports/operations/publications`. Both care-finance-v2 and the separate
`mediflow.report.operational-publication.enabled` flag are required; defaults remain false.

This first slice publishes **SURGERY only**: existing sourced completed/cancelled/whole-duration
counters, not category breakdown, DAILY discharges, census inpatient-days or staffed-bed capacity.
Available local evidence is a finite snapshot, not a claim about all upstream events or missing
history. An empty/no-Surgery manifest or coverage before the earliest retained Surgery business
day cannot certify zeroes. Coverage is 1..366 completed days in the configured reporting zone.

Publication exclusively fences local journal writers before a fresh READ COMMITTED source check.
All frozen metadata/snapshots/hashes/projector versions must match current committed journal;
new delivery evidence requires a new freeze even when semantic totals are unchanged. Frozen
projection hashes, distinct facts and department/hospital scope equality are checked again rather
than trusting the historic VERIFIED label. No remote call or other service DB is introduced.

V26 records immutable acceptance/actor and command intent/audit with an expected-current pointer.
Accepted generation/input/contribution/scope rows are sealed against normal insert/update/delete.
Same command/actor/intent retries return original receipts without changing later pointers;
changed intent conflicts. Rollback selects only a previously accepted/published generation,
retains its coverage/zone/as-of and does not claim fresh catch-up. Manual historical V10 pointers
are preserved by migration, not silently adopted into operator-approved history.

Signed human access tokens require expiry and canonical account UUID; ADMIN alone may manage
these endpoints. Duplicate/unknown/trailing JSON is rejected. Routes disabled by gates return
redacted 404 rather than an unexpected 500. New POST ingress still needs Gateway-owner review;
Gateway and all producer schemas/fixtures/bindings/held gates are unchanged.

The 20 actual PostgreSQL migration/publication/race cases and 19 existing replay PG cases now pass.
Fresh clean full Report: 1,252 tests / 117 suites, zero failure/error/skip, actual PostgreSQL/RabbitMQ.
Earlier focused/NOT_RUN evidence is superseded, not summed with the full run. This verifies the local
database/kernel acceptance, not packaged full V1 E2E, full R-04.5.2 or approved production deployment.

### Initial final-only recognition intake — 2026-10-10

The dual-gated Report final-recognition consumer binds only `settlement.recognition.completed`
V1 behind `mediflow.features.care-finance-v2` and
`mediflow.report.final-recognition-consumer.enabled` (both default OFF). It does not bind
`deposit.allocated`, `settlement.completed` or non-final reconciliation as earned sources.
Exact producer/envelope/type/version/source key, the 25 payload fields and bounded nested fields,
initial revision/predecessor, final boolean/outcome, immutable performed hashes, exact numeric
amounts, original allocation/refund pairs and original-deposit releases must pass validation.
Amounts/date/zone cannot be rewritten by republishing another delivery ID.

V22 stores minimal financial snapshot/hash and durable delivery claims. A missing local accepted
cash receipt or actual refund remains PENDING, without earned/release effects. Up to 1000 local
receipts and 1000 APPLIED refunds must prove the exact account/patient/admission/currency/zone,
original classification, amounts and completed cash totals. Report never enriches from REST or
another service's database. Excess or mismatched evidence rejects. Bounded recovery (20 operations,
60-second deferral, finite transaction timeouts) validates the retained snapshot; early invalid
evidence becomes REJECTED with a reason, not a poisoned cash receipt.

Account then sorted original-transaction fences are shared with cash/refund intake. Final source,
delivery, APPLIED state, original release, per-operation contributions and daily department/hospital
totals commit atomically. Cash refunds subsequently cannot borrow an already released deposit budget.
The golden 120 receipt / 20 actual unused refund / 100 final operation yields earned 60/40 at the
two generating departments and release 100 at the original receipt department. Hospital earned
and release are each 100, not 120; neither is another cash receipt. Exact source/delivery replay
has no extra contribution. Invalid facts use dedicated durable DLQ; storage failures get three
bounded retries and retained original bytes can be replayed after repair.

This is an isolated live contribution kernel, **not** V20 financial publication or complete
financial rebuild. V20 remains EMPTY. Initial-final revisions only; correction/predecessor chains,
post-final earned reversals, historical approval/cutover and SETTLEMENT_PAYMENT cash classification
are not claimed. The exact wire is defined once in
[CARE-BILLING](CONTRACT-CARE-BILLING-01.md#initial-final-settlement-and-explicit-earned-operation--2026-10-10).

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

### Prescription unpaid-request notice — additive V1 purpose, 2026-10-09

Notification's existing two-gated payment-request queue/reader (historical Surgery naming retained
for durable topology/config compatibility) also accepts Billing's exact `ledger-v1/invoice-prescription.json`.
It does not introduce a competing listener on `notification.q` or another copy of invoice.created.
PRESCRIPTION requires OUTPATIENT_VISIT and a singular canonical prescriptionId; no admission,
Surgery, Lab or EXAM target may accompany it. SURGERY retains its exact case/admission validation.
The purpose enters the typed command and selects a distinct PRESCRIPTION_PAYMENT_REQUEST private
IN_APP template: unpaid request only, neither paid receipt nor confirmation that medicine was
dispensed. No name, diagnosis, contact lookup or external channel is added. Source identity remains
invoice.created + paymentRequestId, so changed purpose/target under the same request conflicts.
Claim/source/history/held sent outbox share one transaction. Unknown/malformed purposes reject
before claims and retain original bytes in the same bounded-retry DLQ. Both gates default OFF;
acceptance does not release any Billing/Pharmacy/Notification held row.

## Report financial projections

### Supported available-history release and controlled publication — 2026-10-11

The user's later decision authorizes a reasonable fallback when pre-V2 sources cannot be found.
It supersedes the earlier mandatory unavailable-history blocker, not the target correction/KPI
architecture. Current release is explicitly `AVAILABLE_EVIDENCE_ONLY`: initial immutable producer
operations plus contiguous explicit non-final settlement revisions. Missing pre-V2 periods are
unavailable, never synthetic IDs, fake cash or inferred zeroes. Read models remain local event-only.
The publisher rejects coverage starting before the earliest retained source business day; an empty
manifest cannot certify historical no-events. These are finite accepted-source snapshots, not an
attestation that every upstream transaction within the period has arrived.

ADMIN-only capability metadata lists the twelve supported operational/financial sources, exact
producer/source field/wire version and revision policy. Unsupported post-final earned reversal,
operational correction/legacy import, census inpatient-days and staffed-bed occupancy are explicit.
Correction/replacement/supersedes extensions reject before effects. New cash refunds on an APPLIED
final account require a future explicit original-allocation earned-correction contract; duplicates
of an already accepted refund remain harmless. Pre-final partial refunds inherit the exact original
department/classification; cash-out uses refund business time, unused deposit reduces liability, and
initial final recognizes only explicit original allocation/refund/release evidence at final time.

V25 supplies a production finite financial publication writer. ADMIN must submit command UUID,
verified generation, exact manifest proof, non-PII acceptance reference, explicit history policy
and expected current pointer. All financial writers/recoveries hold a shared database publication
fence before account/original locks; publication uses its exclusive counterpart. READ COMMITTED
checks the current local manifest AFTER waiting. Source changes require a new complete freeze/
bounded replay/reconcile, never mutation of the verified manifest. This is local catch-up only:
neither undelivered broker messages nor upstream completeness are inferred.

All five metric rows and provenance reconcile before and after V23-to-V20 copy. The immutable
V20 generation, generation acceptance, read pointer and immutable actor/request audit commit
atomically; failed/unresolved/empty/unverified/corrupt/stale candidates do not publish. Exact
command retry returns its original result, never reverses a later switch. Changed actor/request
under the same command conflicts. Rollback only selects a previously accepted/published target
with unchanged as-of evidence and `snapshotOnly=true`. No manual SQL promotion is supported.

New management APIs are dual-gated OFF (`care-finance-v2` AND `financial-publication.enabled`,
env `MEDIFLOW_REPORT_FINANCIAL_PUBLICATION_ENABLED`). ADMIN alone can start/advance/read rebuild
progress, inspect capabilities, publish and roll back. ADMIN/MANAGER can inspect financial coverage:
`AVAILABLE_SNAPSHOT`, `UNAVAILABLE_HISTORY` or `UNAVAILABLE_PUBLICATION`, with no monetary zeroes
or patient payload. Existing readers retain separate read gates and 404 outside exact coverage.
Opt-in management JSON rejects duplicate/unknown fields, trailing input and numeric history-policy
enum values. Actor UUID comes only from the signed authentication; command receipts preserve
correlation and return `Cache-Control: no-store`.
Keep minimal Report evidence without automatic purge; encrypted operator backups and restricted
archive access/verified restore precede any future deletion. Default Billing/Pharmacy outbox purge
remains OFF. Gateway is not changed; operator POST ingress needs its own reviewed policy.

This completes the revised supported-release acceptance, not full target corrections, operational
publication, staffed-bed metrics, deployment or HELD producer release. Dated absence statements
below describe earlier tranches; this section is the current finite financial workflow.
[Decision, six-task mapping, operator runbook and verification](../../superpowers/plans/2026-10-11-report-supported-release-60.md).

### Portable local financial evidence archive — 2026-10-11

Report can export a sealed V23 manifest and restore it into a new isolated V23 generation using
archive schema version 1. This is Report's own minimal journal backup, **not a Billing export**, a
producer signature, an event republisher, historical coverage approval or V20 publication.

Archive metadata binds origin generation, exact covered period/zone, frozen observation, unresolved
count and manifest fingerprint. Ordered entries retain kind/source ID, first event ID, source state,
source/envelope fingerprints and exact typed snapshot bytes/hash. Source IDs/times are never minted
or inferred. SHA-256 detects damaged or mismatched bytes; an authorized operator could recompute a
digest, so it is not proof of authenticity/completeness. Empty archives do not prove no upstream events.

Limits: 10,000 entries, 1 MiB per snapshot, 16 MiB total snapshots, 32 MiB wire body, 366 completed
days, supported archive/projector/snapshot version 1. Strict JSON rejects duplicate/unknown/missing
keys, trailing tokens, unsupported versions, wrong counts, duplicate/unsorted source keys, damaged
snapshot/manifest/metadata hashes and invalid periods. Original nanosecond business times survive
inside snapshot bytes; database observation time remains the original PostgreSQL precision.

Export reads one REPEATABLE READ snapshot. Restore uses a database archive fence and immutable V24
provenance (origin/new generation, archive digest, authenticated actor, restore time). One archive
restores once across concurrent processes/retries, including after restart; another actor's retry
does not rewrite the original audit actor. Source rows, seal and provenance commit together. Applied
imports also decode and pass the same pure projector before commit. Frozen PENDING/REJECTED remains
unresolved and fails rebuild rather than borrowing newer live proof. An audit/storage failure rolls
back the entire new generation. No live source, outbox, payment or read pointer is changed.

Operator routes, both gated OFF by default (`care-finance-v2` and
`mediflow.report.financial-archive.enabled`, env `MEDIFLOW_REPORT_FINANCIAL_ARCHIVE_ENABLED`):

- ADMIN only: `GET /api/v1/reports/financial/source-archives/{generationId}` downloads sensitive
  local evidence as an octet stream, no-store/nosniff, fixed UUID-derived filename.
- ADMIN only: `POST /api/v1/reports/financial/source-archives` accepts the bounded octet stream;
  it takes the actor from the verified access-token UUID, not request JSON. No URL/path/file lookup.

MANAGER and all other roles cannot export these patient-reference snapshots; aggregates remain on
their existing reader routes. Human access type, expiration and canonical UUID subject are required.
No Gateway code/permission is changed and no gate is enabled by this implementation. Store backups
in operator-controlled encrypted storage with restricted access; the endpoint does not add encryption
at rest or automatically upload a backup. Initial source/correction/history/publication gates remain.

Local source discovery inspected an isolated read-only clone of `mediflow_mediflow-pgdata`: Billing
and Report databases have no public tables; legacy Pharmacy prescription/dispense/drug/event tables
each have zero rows. Observed Rabbit volume message-store segments have zero length. No dump/export
was found in the scoped repo, project history or Documents/Downloads locations inspected. This
describes known local evidence only, not global completeness or a historical no-events approval.
[Implementation, discovery and verification](../../superpowers/plans/2026-10-11-report-financial-archive-recovery.md).

### Financial account fencing and historical evidence — 2026-10-11

Receipt/refund intake, refund recovery, initial-final intake/recovery and non-final stock intake
share one account fence before original/source locks. Recovery resolves immutable original identity
first and re-reads under account/original fences; it never takes account after an original lock.
Initial-final contributions write hospital first, then departments sorted by UUID. Exact receipt
department must match the account department even for service funding without deposit releases.
All six receipt/refund/final arrival orders and actual partial Rx receipts/original refunds have
PG/Rabbit acceptance. This is local contribution/pending acceptance, not a new wire or correction
policy. Five-metric finite rebuilding remains isolated; V20 publication is not activated.

The user requires pre-V2 history as well as retained activation-era sources. Historical no-events,
complete exports or V2 transaction/allocation/episode identities cannot be inferred from legacy
invoice aggregates, current pointers or test fixtures. Real archive/backup/source inventory and
explicit producer-supported legacy conversion remain required. Billing/Pharmacy automatic published
outbox cleanup is independently default OFF (`mediflow.<service>.outbox.purge-published-enabled`),
preserving remaining bytes while archive/backup coverage is unverified. Dispatch/operator replay
remain available. An explicit purge opt-in requires reviewed source archival/restore/retention;
it is not itself completeness proof and cannot restore earlier deleted rows.
[Verification and historical acceptance requirements](../../superpowers/plans/2026-10-11-financial-pending-and-history.md).

### Finite initial-financial source rebuild — 2026-10-11

Report V23 adds an internal finite five-metric rebuild from its own accepted receipt/refund,
initial-final recognition and every retained non-final receivable revision. It never calls Billing
REST/DB, resets live claims/totals, republishes commands or creates a V20 read publication.
Cash/refund/earned are daily flows; liability/receivable are closing stocks, including exact opening
stocks from earlier originals. Initial earned uses final completedAt and explicit generating charge
departments; deposit cash/refund/release stays at the original receipt department. Final completion
extinguishes its account's last non-final receivable without another cash/earned effect.

One REPEATABLE READ transaction freezes the committed local VND/report-zone sources and provenance.
The finite manifest includes pending/rejected refunds/finals rather than excluding them. Missing
first-delivery proof, changed receipt/settlement/final snapshot hash or incomplete bounded source set
aborts freeze. Known unresolved frozen sources fail the generation even after later live recovery;
a new freeze is required. Every revision of non-final stock is retained, with contiguous predecessor
history required; the latest current-account pointer alone cannot reconstruct prior daily stocks.
Receipt/final/non-final sources on one account must share the exact patient/episode/receipt-department
context even without a final operation. Frozen BUILDING manifests cannot append inputs or reopen.

Limits are 10,000 sources, 16 MiB total manifest, 1 MiB/snapshot, 366 days, 100 departments plus
hospital and 1..31-day atomic batches. Requested covered days must have ended by sourceObservedAt;
business completion after that observation is invalid, including outside the requested period.
Generation row locks serialize replicas. All scope rows/checkpoint commit or roll back together.
Manifest validation failure may record FAILED; database write failures still roll back the batch.
Full source/provenance and bidirectional scope/five-amount equality yield local VERIFIED; copied
inputs/header and terminal generations are sealed. Terminal retry has no effects.

VERIFIED proves equality with this finite local accepted set, **not** upstream history/export
completeness, correction acceptance, no-events coverage, live catch-up or approved publication.
Opaque retained original hashes are provenance, not reconstructed raw upstream envelopes.
V20 stays EMPTY; no public command, scheduler, read switch, feature activation or held release is
introduced. Post-final corrections and full source/coverage/publication gates remain open.
[Implementation and verification](../../superpowers/plans/2026-10-11-financial-source-rebuild.md).

### Final-only earned recognition — user decision, 2026-10-10

Earned revenue starts only after Billing final settlement completes, not on performed care, cash,
deposit allocation, medical discharge or non-final reconciliation alone. Recognition date is final
completedAt in the configured Report zone. Exact recognition operation/revision, original charge/
allocation references, currency and generating-department amounts are mandatory. Report never
derives earned deltas from cash or cumulative settlement gross. Correction references the original
operation/scope. Deposit remains liability until explicit final allocation/release or original-linked
actual deposit refund. Refund-due/additional-payment estimates are not final. This user decision does
not itself implement or accept the final source, financial replay or publication.
[Billing canonical boundary](CONTRACT-CARE-BILLING-01.md#recognition-timing--user-decision-2026-10-10).

### Medical discharge / completed-stay statistics — LOCAL, 2026-10-10

The eighth key on Report's existing two-gated private operations queue is the exact five-field
Inpatient `discharge.medically.approved` V1. V21 stores typed minimal proof/exact timestamp text,
never clinical summary. START/CLOSE/medical proof share admission and delivery fences. Source,
receipt, replay journal and hospital/department metrics commit together. Missing START stays
PENDING_START; invalid early proof is quarantined without poisoning valid START; storage errors
roll back. A later CLOSE earlier than an applied approval cannot rewrite medical history.

`medicalDischarges` counts one immutable approval; `medicalLosMinutes` sums whole elapsed minutes
per completed stay; `completedStayDays` sums whole 24-hour periods per stay, floor before sum.
Durations use exact admittedAt -> approvedAt, never administrative closedAt. Contributions use
approvedAt's configured-zone date and exact stored START department plus hospital. Average LOS
denominator is the same medical-discharge cohort; no average/public reader is activated here.
These are **not census inpatientDays, staffed-bed utilization or billable days**. Existing inpatient_days
is not filled; those metrics still need actual bed intervals/capacity and accepted definitions.
Correction/historical coverage/live publication/held release remain separate tasks.

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

Rx continuation 2026-10-09 additionally verifies actual producer-service files
`refund-prescription-first.json` (250), `refund-prescription-remainder.json` (500) and
`refund-prescription-last.json` (1250). The first two link only to the original 750 receipt;
the last links only to the original 1250 receipt. The unchanged schema needs no prescription ID
in the refund payload: the authoritative original receipt supplies its context/classification.
Report keeps gross 2000 separate from refunded 2000, including early recovery/dedupe and isolated
finite paired replay. Notification emits private actual-refund notices, not a settlement or
dispense confirmation. No recognition/reversal/coverage is inferred from this cash-only proof.
[Current verification](../../superpowers/plans/2026-10-09-pharmacy-report-rx-adjustment.md).

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

### Versioned non-final receivable stock — 2026-10-10

Local source is Billing **`settlement.reconciled`** V1, source `settlementId`, with full payload
defined only in [CARE-BILLING](CONTRACT-CARE-BILLING-01.md#non-final-versioned-reconciliation--2026-10-10).
It is NOT `settlement.completed`; `finalSettlement` must be false. Clinical authorization,
finalization, producer release and public financial acceptance are unchanged.

Strict mapper validates exact producer/type/source/field set, positive integral business revision,
UUIDs/predecessor shape, NUMERIC(19,2), liability/balance equations, net allocated ≤ gross and net
completed funds, outcome sign and exact business/envelope time. Unknown/missing fields,
floating/string amounts, final/debt/waiver semantics and contradictory evidence reject before writes.

V19 stores immutable minimal typed inputs (snapshot format 1), normalized source proof and a
separate typed-input fingerprint verified on restoration before any delta. No raw medical
narrative, source account API or cross-service REST/database read is added. Source uniqueness is
both `settlementId` and `(accountId, settlementVersion)`; delivery ID/hash is independent.
Same fact/new delivery has no second effect; changed source/version or changed evidence under
an existing event ID conflicts and rolls back.

Receivable is **stock** `max(balance,0)`, not daily/monthly flow. Latest accepted account revision
replaces prior stock, applying only `newStock − persistedPriorStock` to account-department and
all-hospital scopes. Currency and Report timezone are isolated. Refund-due contributes zero, not
negative debt. Snapshot completed payments/refunds/reversals never change cash again and never
represent earned revenue/liability release. Account department is NOT 60/40 charge allocation.

Full snapshots may arrive before predecessors: greatest revision wins, stale accepted versions
retain proof but cannot downgrade current stock. Account/admission/patient/department/currency/zone
are immutable. Known adjacent predecessor/successor identity and exact nanosecond chronology are
checked in either order; non-adjacent business chronology cannot contradict the current revision.
Known future-predecessor cycles and siblings claiming the same missing predecessor reject
immediately; a unique predecessor/successor database fence protects concurrent namespace claims.
Missing history is not declared covered. Database advisory fences serialize replicas; source,
delivery, latest pointer and hospital-then-department stock commit/rollback together. Failure in
the second scope leaves no source claim or first-scope effect and supports unchanged-byte retry.

Dedicated durable queue `report.settlement-snapshots-v2.q`/DLQ binds only `settlement.reconciled`.
Both `mediflow.features.care-finance-v2` and `mediflow.report.settlement-consumer.enabled` default
false. ACK follows committed application transaction; permanent conflicts reject without requeue,
transient storage failures have three bounded attempts then retain original bytes in the DLQ.
Legacy queues and financial endpoints are unchanged; no accepted/public financial read switch exists.

Same actual Billing producer-service fixtures are verified at mapper, PostgreSQL replica/order/
deduplication/rollback and real Rabbit intake/DLQ replay boundaries. Applying the accepted fixture
set to empty local stock in different orders produces the same latest balance. This completes
the local **R-03.5 stock/revision projector**, not finalization/recognition, durable full-financial
replay, historical coverage/retention approval, financial publication or held release.

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

### Financial snapshot read boundary — 2026-10-10

The aggregate-only GETs `/api/v1/reports/financial/daily?from&to&departmentId` and
`/api/v1/reports/financial/monthly?year&departmentId` now have a complete local read implementation.
They require BOTH `care-finance-v2` and `report.financial-read.enabled`, default false. Direct roles
are ADMIN/MANAGER only. The existing unmodified Gateway reports-GET rule has the same financial
roles; its DOCTOR exception remains restricted to the two exact operations routes. No Gateway
production/test file is changed by this read slice.

V20 introduces a separate EMPTY financial generation/day/publication namespace, not a producer
fact, export, replay writer or activation. Cash-only V13/V17 and non-final V19 must never be promoted
into it. There is deliberately no production publication writer or manual SQL activation path.
Future complete five-metric rebuilding must retain and reconcile its accepted input manifest,
all scopes and source horizon before an explicitly approved workflow can populate/switch it.
Synthetic read-boundary tests are NOT Billing expected-result fixtures or source acceptance.

The reader requires a VERIFIED generation, snapshot/projector version 1, applied=source inputs,
no unresolved inputs, reconciliation/proof metadata, explicit acceptance and matching configured
currency/timezone. Covered days must have ended in that timezone by sourceObservedAt, which cannot
follow reconciledAt or publishedAt; a publishedAt in the reader's future is unavailable rather than
fabricated freshness. Response exposes these three instants, generationId,
snapshotOnly=true, currency/reportZone and aggregate periods; no patient IDs or acceptance/audit
reference is returned. One currency is configured (VND by default); no conversion or summation of
currencies occurs.

Every requested day/scope must have one EXPLICIT complete row for all five metrics. Missing day,
unknown department, partial coverage, pending/failed/unsupported snapshot or an empty publication
is 404 REPORT_NOT_FOUND, never fabricated zero stock. Explicit accepted all-zero rows are allowed.
Daily periods are inclusive and bounded to 1..366 days, years 2000..2100. Monthly requests require
the complete calendar year and return 12 ordered periods, including leap-day evidence.
Invalid/malformed period, year or department returns 400 REPORT_VALIDATION_ERROR.

cashReceived is gross completed cash inflow; refunds is actual completed cash-out, separately
reported. Neither is earnedRevenue. Monthly cashReceived/earnedRevenue/refunds sum daily flows.
depositLiability and outstandingReceivable are closing stocks taken from the LAST accepted day of
each month, never sums of daily balances. Every amount is exact non-negative NUMERIC(19,2);
unknown amounts are not null-to-zero, and overflowing monthly totals are unavailable, not rounded.

The application read transaction is read-only REPEATABLE READ with an 8-second transaction timeout; each of the
two indexed JDBC statements has a 3-second query timeout and the rows are bounded to days+1 for
corruption detection. Adapter-level resource/cancellation failures are unavailable without exposed
database data. These timeouts are not a hard end-to-end SLA including connection acquisition or
transaction startup; errors before adapter execution retain the general backend error handling.
Verified generation metadata/amount rows cannot be inserted/updated/deleted through ordinary
mutations: database triggers fence the generation while building and seal it at verification.
Pointer switch/rollback selects one immutable generation; this storage/read property is not proof
of an approved production switch workflow or live catch-up.

The new financial path additionally requires a signed access token with expiry and canonical UUID
subject. Refresh/service/missing-type/missing-expiry/malformed-subject tokens are denied. Existing
legacy DTOs, queues, source hashes, feature defaults and historical amounts are unchanged.

This completes the LOCAL R-03.6 read code and R-02.3.2 financial-role/API boundary, not R-03.3/.4
recognition/reversal, full financial replay/publication or approved historical coverage. Until those
sources/workflows exist, the financial endpoint is intentionally unavailable in a real environment.

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
| `admission.placement.changed` | immutable bed-use interval start/end and current-placement revision |
| `admission.closed` | administrative-close evidence only currently; not medical discharge or bed release |
| `bed.capacity.changed` | effective capacity membership/dimensions; not occupancy |
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

### Inpatient placement and capacity producer sources — 2026-10-12

Inpatient now publishes `admission.placement.changed` V1 keyed by
`(admissionId, placementRevision)`. `ASSIGNED`, atomic `TRANSFERRED` and `RELEASED` carry exact
assignment/bed/department/ward interval facts. Report derives occupancy only from the half-open
assignment intervals supplied by these facts; `admission.started` remains an admission-count and
initial-snapshot source, not current occupancy. Administrative `closedAt` is not a bed-release or
medical-discharge time.

Inpatient now publishes `bed.capacity.changed` V1 keyed by `(bedId, capacityRevision)`. Payload is
the exact effective snapshot: `departmentId`, `wardCode`, `bedType`, `active`,
`operationalStatus`, `capacityEligible`, `effectiveAt` and actor. `capacityEligible` is true when
the bed is active and not `OUT_OF_SERVICE`; assignment-controlled AVAILABLE ↔ OCCUPIED changes do
not alter capacity revision or publish this event. Report derives the denominator from effective
capacity-eligible intervals and must not reinterpret AVAILABLE as the only capacity state.

Migration V3 creates one baseline per pre-existing bed at its then-current `updated_at`. That is
the earliest supported capacity coverage point; it does not fabricate earlier history. Missing
placement revisions, missing baseline coverage or out-of-order gaps are unavailable/pending, never
zero. The greatest contiguous accepted source revision is current; identical duplicate source bytes
are idempotent and changed bytes at the same key are conflicts.

Completed medical LOS remains `admittedAt → approvedAt` from `admission.started` and
`discharge.medically.approved`. Inpatient supplies placement/capacity source facts only. Report owns
timezone bucketing, census cut-off, interval splitting, rounding, aggregation, coverage metadata,
replay and publication. Canonical producer fixtures are under
`backend/inpatient-service/src/test/resources/contracts/admission-placement-v1/` and
`bed-capacity-v1/`; Report activation stays OFF until it consumes those exact bytes.

## Acceptance criteria

- Duplicate events create one notification intent and one report contribution.
- Deposit payment increases cash and liability, not earned revenue.
- Refund reverses the original financial contribution in the correct period/department.
- Report can rebuild the same totals from an empty projection using event replay.
- Notification templates do not expose diagnosis/result details on insecure channels.
- Unknown event version or missing source ID follows retry/DLQ policy and does not silently alter a
  projection.
- Placement/capacity duplicates, conflicts and revision gaps have bounded behavior, and missing
  pre-baseline history is unavailable rather than zero.
