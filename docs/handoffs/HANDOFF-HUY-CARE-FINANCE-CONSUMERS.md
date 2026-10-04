# HANDOFF — Huy-owned Pharmacy and Report care/finance contracts

- **Status:** `OPEN` — V2 target specs now exist; missing producer facts/fixtures and compatibility evidence still block enablement.
- **Consumer owner:** Huy (`LQHuy0210`) — Pharmacy and Report.
- **Producers to act:** Inpatient/Clinical — Vinh; Billing/Notification — Lộc; Surgery — Huy for its future result facts.
- **Source:** [Huy plan D08–D11](../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md#31-decision-backlog) and [independent preparation](../superpowers/plans/2026-09-26-huy-independent-slices.md).

## Producer actions and Huy acceptance gates

**Reassessed 2026-09-29 at `d252492`:** [Pharmacy V2](../eproject_general_plan/backend-spec/care-finance-v2/05-pharmacy.md)
and [Report V2](../eproject_general_plan/backend-spec/care-finance-v2/08-report.md) permit additive local
implementation behind `mediflow.features.care-finance-v2=false`; this is not permission to activate
unverified consumers. Pharmacy now has a guarded V1 care-context model, and Report has a guarded
version-1 envelope decoder for Clinical/Lab/Inpatient facts. The live paths still use legacy Pharmacy
payment proof and invoice-keyed Report contributions. Huy's offline tests now read exact Vinh
fixture bytes; local projection/kernel evidence is detailed below. Billing still has no classified transaction, clearance or settlement
producer. Inpatient now publishes the approved start/discharge/close lifecycle facts, but not bed
transfer/release/capacity facts. These additions reduce the local implementation gap without
establishing cross-owner acceptance or permission to enable the flags.

**Huy consumer update — 2026-10-04:** Pharmacy V19 now persists the exact Inpatient
`discharge.medically.approved` singleton separately from administrative close. The offline decoder
reads the producer's checked-in fixture bytes; domain and PostgreSQL tests cover discharge before
start, late start, conflict rollback and medication-eligibility denial before close. This closes
only the local medical-discharge projection slice. Transfer/release freshness, live Rabbit binding,
admission create/dispense authorization and shared activation acceptance remain OPEN. Patient
service-only existence is already implemented; it is not a remaining producer-endpoint blocker.
Organization staff/department lookup is likewise implemented, while Surgery room/job-title and
Gateway Surgery/Report route-role work remain in their registered handoffs.

| Decision | Producer action needed | Huy consumer behavior after contract approval | Acceptance evidence |
|---|---|---|---|
| **D08 — admission medication** | V2 selects one slip per prescription, exact patient/department and admission charging rather than outpatient prepayment. Vinh has supplied start/discharge/close fixtures: eligibility ends at medical discharge, and close-before-start never reopens. Vinh still owes transfer/release freshness and cross-department policy; Lộc supplies cancel/expiry/failure adjustment semantics. | Keep version-0 outpatient unchanged; version-1 outpatient uses exact clearance, admission uses exact eligible lifecycle projection. Never reinterpret `paymentConfirmed`, allow late start to reopen closed/medically discharged admission, or infer IDs. Multiple-dose/returns are outside this V1 target. | Same-byte start/discharge/close plus future transfer/release fixtures for wrong/closed/discharged admission, duplicates, out-of-order/restart, failed/expired/cancelled and late compensation; stock/authorization concurrency and Rabbit tests. |
| **D09 — classified finance** | Lộc provides versioned completed receipt, deposit allocation/release, earned charge/recognition, completed refund and receivable/settlement facts. Specify transaction/allocation/reversal IDs, currency, signed delta versus replacement snapshot, effective period, account/episode and department allocation. | Report projects cash, liability, earned revenue, refund and receivable from explicit source facts. It does not treat an invoice total or deposit as earned revenue, or collapse legal partial transactions by invoice ID. Unknown classification/reference remains pending or rejected according to the agreed contract. | Billing producer and Report consumer decode the same fixture bytes; deposit→recognition→refund and two-partial-payment expected totals reconcile under replay, duplicates and reversed delivery. |
| **D10 — inpatient/surgery reporting** | Vinh provides admission start/transfer/release/discharge/administrative-close facts and staffed/available-bed capacity if occupancy is wanted; Huy's future Surgery producer supplies completed/cancelled result operation/revision and actual times/items. Agree LOS and complication category semantics. | Report exposes only metrics backed by exact authoritative facts; active-admission count is not bed occupancy, `scheduledAt` is not actual start, and free-text is not a complication code. | Producer fixtures covering transfer across two departments, overnight stay, bed-capacity change, partial abort/correction and late delivery; Report totals and unavailable-vs-zero contract. |
| **D11 — compatibility/replay** | All producers/consumers agree envelope versioning, field nullability, source business keys, cutover markers, correction revisions and legacy history retention/replay source. Identify which old events coexist with new classified facts. | Pharmacy retains committed legacy outbox bytes; Report retains five current bindings/queries until the cutover is proven. A new generation replays from a durable finite source without deleting live inbox or double-counting old/new finance facts. | Same-byte producer/consumer old/new fixtures, explicit source watermark, deterministic rebuild and live catch-up tests, retention limit and rollback runbook. |

### Concrete remaining gaps in the new targets

- Billing payment/settlement payloads do not yet provide the allocated-earned amount, deposit
  allocation/release, multi-department allocation or settlement revision/supersedes data required by
  Report's equations. Decide gross versus net cash and receivable balance versus daily movement;
  partial completed receipts need a fact even when they do not grant operational clearance.
- Report V6 now treats `eventId` as delivery provenance and has a local semantic-key shape
  (`sourceType + sourceId + sourceRevision + contribution/metric type + department scope`) so a new
  eventId cannot by itself reapply the same operation. Lab now supplies `labId + resultVersion` and
  Inpatient start/close use immutable singleton operation keys with implicit revision 1. Clinical,
  Pharmacy, Surgery, finance and every future correction/supersedes chain remain open. V2 has an
  offline operational writer/kernel, no active mapper/listener. Replay also needs a durable finite source and a processing
  ledger isolated from the live inbox; do not truncate live projections to satisfy rebuild.
- Admission started/closed do not provide bed transfer/release/capacity facts. Administrative close
  duration cannot silently become medical LOS or bed occupancy; missing close dimensions may only
  come from the exact stored admission source, never REST enrichment or patient inference.
- Pharmacy V14 already reconciles nullable V1 recordId with required V0 recordId and preserves
  prescribedDate. V15 implements close-before-start storage; five V1 proposal DTO/fixtures now
  include cancelled/expired payloads. Medical discharge now ends normal medication eligibility;
  early clearance, transfer freshness/cross-department policy, authorization fence and V1 writer/
  consumer approval remain open.
- Surgery ready/completed/cancelled payloads must match Notification planned-time requirements,
  Inpatient clinical summary and Report dimensions/category requirements. Target operations Report
  permits DOCTOR; CURRENT Gateway reports route only permits ADMIN/MANAGER.

## Huy baseline and boundary

- Pharmacy currently publishes `prescription.created`, `.filled`, `.dispense.failed`, `.cancelled`, `.expired` through its outbox. Legacy cancelled/expired fixture copies and Pharmacy decoder tests pass locally; this does **not** prove an admission payload.
- Report currently binds `medicalrecord.created`, `lab.result.created`, `prescription.filled`, `payment.completed`, `payment.failed`. Its present payment projection is a legacy invoice contribution, not a classified ledger.
- Huy may implement legacy-safe changes and the specified additive local V2 models/adapters/tests with activation off. Missing fixtures block live binding/writer/API enablement, not every local scaffold; unresolved amount/identity/policy gaps still block the affected behavior. Do not invent IDs, transaction semantics or producer fixtures. Huy does not edit Clinical, Inpatient, Billing, Notification or Gateway here.
- Surgery-specific D01–D07/D12 actions remain in [Surgery implementation decisions](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). D08 and D09 appear there only as cross-context summary; this file supplies Pharmacy/Report consumer acceptance criteria.

## Vinh producer evidence — 2026-09-28

Vinh's producer slice supplies deterministic version-1 fixtures for Clinical
`admission.requested` and the currently approved Inpatient lifecycle events under each service's
`src/test/resources/contracts/` directory. Producer tests serialize the real envelope/wire mapper;
the Inpatient consumer test reads the same `admission.requested` shape. These fixtures are evidence
for D08/D10/D11, but they do not enable any feature flag or close this handoff until Huy's consumers
read the same bytes and the remaining acceptance paths pass.

Still open: Pharmacy transfer freshness/cross-department eligibility and late compensation, Report bed
transfer/release/capacity facts and LOS semantics, and a durable replay/cutover policy. No new
transfer, release, capacity or Surgery event may be inferred from the existing admission events.

Huy's current Report envelope decoder recognizes `medicalrecord.completed`, `admission.started`,
`admission.closed` and `lab.result.created`. Decoder tests now read exact producer bytes for Clinical
`medicalrecord.completed.v1`, Lab outpatient `lab.result.created.v1`, Lab admission
`lab.result.created.admission.v1`, and Inpatient `admission.started.v1`/`admission.closed.v1`; the Lab
admission case preserves its explicit `careEpisodeId` separately from `recordId`, and both Inpatient
events preserve the exact `admissionId`. This is local decoder evidence only: no V2 Report
listener/projector is active, and projection-level duplicate/out-of-order assertions plus owner
fixture acceptance remain required before changing the feature flag or declaring D10/D11 accepted.

## Lab producer evidence — 2026-09-28

Lab now supplies deterministic version-1 producer fixtures for `lab.request.created` and
`lab.result.created` under `backend/lab-service/src/test/resources/contracts/`. The fixture tests
serialize the real event envelope, and application tests prove each event uses one clock instant
for both the envelope and its payload timestamp. This gives Huy stable bytes for Report consumer
tests and gives Billing a stable charge-trigger fixture.

This evidence does not enable Report, Notification or Billing consumers and does not close
D09–D11. Those owners still need same-byte decoder tests plus the classified finance,
replay/cutover and failure-path acceptance evidence described above. The Lab care-finance feature
flag remains disabled until those gates pass.

## Clinical completion and admission-lab evidence — 2026-09-28

Clinical now publishes a deterministic `medicalrecord.completed` version-1 fixture at
`backend/clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json`, serialized
from the real Clinical envelope and payload records. Huy can use these exact bytes to replace the
Report decoder's handwritten sample before enabling that projection.

Lab also publishes `lab.result.created.admission.v1.json` with an explicit
`careEpisodeType=ADMISSION` and exact `careEpisodeId`. Inpatient reads its byte-identical copy in
the real consumer test and maps the Lab, admission, patient and result-version identifiers without
patient-based inference. Report still needs its own same-fixture decoder/projection acceptance test;
this evidence does not enable any feature flag or close D10/D11.

## Huy local implementation evidence — 2026-10-01

- Pharmacy: `AdmissionLifecycleDecoder` consumes exact Inpatient started/closed fixture bytes.
  Domain/application/V15 JDBC tests prove close-before-start survives restart, no late reopen,
  exact patient/department checks, semantic duplicates, conflict rollback, concurrent start/close
  and source nanosecond preservation. No queue binding or V1 dispense activation was added.
- Pharmacy proposal fixtures: `backend/pharmacy-service/src/test/resources/contracts/care-finance-v1/`
  contains all five lifecycle shapes, including exact `dispenseId` and cancelled/expired reasons.
  They round-trip through the real offline codec. Lộc/Vinh/Notification must confirm the same bytes,
  context/source/adjustment rules and V0/V1 cutover; these are not live producer acceptance.
- Report: V6/V7 and typed operational kernel run on PostgreSQL. Same event or source/new event has
  one effect; changed department/time/payload conflicts; both scopes and journal roll back together;
  concurrent first-scope upserts preserve totals. Correction revision is not blindly counted again.
  No actual producer-to-metric mapper/listener/API was activated.
- Huy chooses minimal accepted-input operational journal from activation as a local replay source.
  It stores metadata and aggregate inputs, not clinical/results payloads; no automatic purge or
  public read access. Pre-activation history still needs producer export. Isolated generations,
  financial pending/reversals, catch-up/watermark and read cutover remain Huy implementation tasks,
  not proof that D11 is accepted.
- Full regression evidence and remaining local/contract task split are recorded in the Huy plan.

## Huy local continuation — 2026-10-02

- Pharmacy has consumer-local clearance storage (V16), immutable event/clearance dedupe and durable
  PENDING grants arriving before the prescription. Huy chooses AUTHORIZE ONLY: no auto-dispense,
  legacy receipt or invented financial compensation. The caller-transaction-only check matches exact
  V1 outpatient prescription/patient/episode and uses time after authorization lock waits. The V0
  executor rejects V1; authorization denial never enters its stock-failure compensation writer.
  The V1 stock/lifecycle writer and Rabbit binding remain off.
- **Lộc:** supply actual PRESCRIPTION clearance producer bytes and confirm immutable grant time
  on republish (the current contract command uses envelope occurredAt), nullable/exclusive expiry,
  revocation/replacement and adjustment semantics. Consumer inline tests are specification examples,
  not a replacement Billing fixture. Wrong purpose/unrelated targets/episode/patient must fail with
  no stock/receipt/refund effect. Amount remains a Billing snapshot, not equal-by-assumption to Rx total.
- Report now has an offline Lab mapper using actual labId/resultVersion/requesting department,
  episode and completedAt. Source revision 1 maps to LAB_TESTS once in department/hospital scopes;
  missing fields fail without fallback. Completion date follows configured Report timezone, not
  republish or performedDate. No queue binding or API is enabled.
- **Vinh update:** the Lab aggregate and target spec establish `resultVersion` as the business source
  revision. Both real first-completion fixtures now carry revision 1; envelope version 1 remains the
  schema version. Imported result publication and correction/replacement revisions remain blocked
  rather than being inferred. Clinical/Pharmacy facts without accepted business revision still
  require an explicit source-operation contract before their mapper is activated.
- New PostgreSQL tests are present for clearance pending/reload/rollback/race and actual Lab bytes
  through both scopes. They have not run in this turn: Docker startup failed at dockerInference.
  Current unit tests do not close real DB, producer-owner, replay or end-to-end acceptance gates.

## Huy held lifecycle / finite replay continuation — 2026-10-02

- Pharmacy V17 now has historical name snapshots, explicit exact terminal business times,
  a pure five-event factory and a caller-transaction-required capture hook from locked prescription/
  slip evidence. The internal V1 writer stores immutable bytes in the existing outbox, **held** by
  DB constraint and dispatcher/lease guards. One creation and one terminal outcome are permitted;
  replay never enables held bytes. Only the internal V1 outpatient stock executor now invokes the
  hook; no public/legacy workflow or listener activates it. Legacy lifecycle
  writers do not downgrade V1 to V0. This adds local implementation, not consumer acceptance.
- Consumers still review the five proposal fixtures under Pharmacy `contracts/care-finance-v1/`.
  Huy's hold is not a live publication promise or a finalized financial adjustment/refund contract.
  Activation needs coordinated same-byte acceptance and a reviewed migration/cutover; do not enable
  rows manually after pulling, or treat a rejected/held event as successfully consumed live V2.
- Report V8 provides an internal finite operational rebuild from a frozen committed journal
  manifest. Isolated generations, bounded resume, shared pure planner/snapshot codec and final
  fact/scope reconciliation exist. VERIFIED means equality with that finite manifest only; it does
  not prove pre-activation coverage, financial/pending projections, catch-up or live read readiness.
  No read pointer, V2 API or listener changed. Those remaining local tasks are still Huy's work.
- New PostgreSQL writer/replay/nanosecond-reload tests are present but VERIFY OPEN: Docker was
  unavailable in this run. Unit/static/architecture regression is recorded in the plan. Producer
  approvals, real DB/broker verification and E2E gates are still OPEN, not closed by the held writer.

## Huy internal outpatient / admission pending continuation — 2026-10-02

- Pharmacy now has an internal V1 outpatient transaction joining exact clearance, whole-stock/
  reservation locks, fresh grant/TTL/drug expiry checks, Rx/slip exact business proof and held filled
  bytes. Staff/account command is required; it never auto-dispenses or invokes V0 receipt/refund/
  filled/failure compensation. Repeat command requires matching persisted AND held terminal proof.
  Public API/payment/listener stays unchanged. Creation/admission/cancel/expiry/failure orchestration,
  real PG rollback/race evidence and same-byte Billing/consumer approvals still gate live activation.
- Report V9 stores minimal exact start/administrative-close evidence with atomic event claim and
  admission row fence. Close-before-start is durable pending, then paired only by exact admission/
  patient; department comes solely from that start. Exact source nanos are retained; changed
  department/time/proof/patient or chronology conflicts cannot become additional operations.
  This evidence does NOT emit admission counts, medical LOS or bed occupancy. No listener/API is on.
- **Vinh update:** `admission.started` and `admission.closed` are immutable singleton operations keyed
  by routing key plus exact admission ID, with implicit operation revision 1 independent of envelope
  version. Close-before-start pending/pairing is therefore approved. Medical discharge remains
  distinct from administrative close; LOS metric/time/rounding and transfer/release/capacity facts
  remain blocked. The local pending store requires no cross-service REST enrichment.
- New stock/pending/upgrade tests are written, but PostgreSQL runtime verification remains OPEN
  while Docker engine is unreachable. Latest unit/static/architecture counts are in the Huy plan.

## Vinh producer/contract reassessment — 2026-10-02

| Class | Gap | Authoritative basis and outcome |
|---|---|---|
| **A — IMPLEMENTABLE** | Lab first-completion revision | Lab V2 DDL/DTO and start-complete algorithm define aggregate `resultVersion`, `0 → 1`, terminal completion and row locking. Both outpatient/admission producer fixtures now use revision 1; envelope version remains schema-only. |
| **A — IMPLEMENTABLE** | Pharmacy eligibility at medical discharge | Care-Billing freezes normal charges at `discharge.medically.approved`; Inpatient V1 already emits its real mapper fixture. Pharmacy eligibility ends at that exact fact, before administrative close. |
| **A — IMPLEMENTABLE** | Start/close identity, revision and out-of-order delivery | Inpatient V1 allows each transition once and emits immutable outbox facts. Business identity is routing key + admission ID, implicit operation revision is 1, and close-before-start may stay pending without a late reopen. |
| **B — BLOCKED** | Imported Lab completions and correction/replacement | Vinh, as Lab producer owner, must obtain the business decision and define whether imports publish, their initial revision, correction command, supersedes link and immutable snapshot storage. Acceptance requires real-mapper fixtures for an import plus revision-2 correction, and duplicate/stale/conflict tests. Runtime remains terminal-only. |
| **B — BLOCKED** | Bed transfer/release and placement freshness | Vinh owns the producer decision/fixture; Huy owns Pharmacy/Report acceptance. They must decide whether transfers may cross departments and approve routing keys, source identity/revision, old/new assignment/bed/department and business times. Acceptance requires real producer fixtures for a two-department transfer and release, plus duplicate/reordered/conflict tests read by both consumers. |
| **B — BLOCKED** | Capacity/occupancy | Vinh owns the capacity producer contract; Huy owns Report acceptance. They must define staffed versus physical capacity, available/out-of-service effects and snapshot-versus-delta semantics. Acceptance requires an approved capacity fixture and transfer/release/capacity sequence whose Report result distinguishes unavailable from zero. |
| **B — BLOCKED** | LOS | Vinh owns the Inpatient business-time definition; Huy owns Report mapping/acceptance. They must choose medical (`admittedAt → approvedAt`) versus administrative (`admittedAt → closedAt`) duration, inclusivity, timezone/rounding and transfer/death handling. Acceptance requires one overnight fixture with distinct medical-discharge and close times and exact expected duration. |
| **C — ALREADY COMPLETE** | Inpatient source lifecycle and separation | Producer code and real wire-mapper fixtures already cover start, medical discharge and administrative close; close requires discharge, settlement/override and released bed. No production rewrite is required. |
| **C — ALREADY COMPLETE** | No invented placement facts | Transfer/release mutate only Inpatient-owned persistence and publish no unapproved routing key. Existing runtime stays unchanged until the category-B contracts are approved. |

This closes Vinh's current Lab revision-1, medication medical-discharge, lifecycle singleton-revision
and close-before-start decisions. It does not approve Lab amendment history, medical LOS, bed
occupancy, transfer/release/capacity events or feature activation.

## Close criteria

### Current local completion slice — 2026-10-02

- Huy now has V18 internal outpatient creation receipts and all five held lifecycle mutation paths
  (create/filled/cancel/expire/definitive stock failure). Actor/intent replay, rollback and terminal
  concurrency are tested on actual PostgreSQL. Public V1 creation/payment/listeners remain closed;
  admission still needs authoritative eligibility. Lộc/Vinh/consumers must approve exact proposed
  bytes/adjustments, grant time/revocation and patient/episode authority before public wiring/delivery.
- Report adds V10 empty accepted finite-snapshot publication and two default-off aggregate operations
  read routes. Period/zone/metric/generation coverage gates distinguish unavailable from covered zero.
  VERIFIED replay does not create publication, cover pre-activation history or catch up live facts.
  Do not insert publication or enable flags by hand. Counts/duration snapshot is not full target
  medical LOS/occupancy/surgery categories/finance; no production publication writer is present.
- Docker is available again; real local PostgreSQL/Rabbit regression evidence is now in the plan
  and the linked completion assessment. Prior “Docker unavailable” entries are dated history, not
  the current verification state. Passing module tests still does not close owner acceptance/E2E.
- Vinh: imported Lab result/correction, medication transfer freshness/cross-department policy and
  discharge/LOS/transfer/release/capacity contracts. Lộc: exact clearance + classified transactions/allocations/
  recognition/refunds/settlement/expected finance totals. Hoàng Anh: route-specific Gateway DOCTOR
  roles. Huy after those inputs: admission/source/finance wiring, controlled publication/live catch-up
  and cross-service E2E. These remain real tasks, not automatically DONE when this handoff is pulled.

For each row, record approval owner/date/link in the Huy plan, update the canonical care-finance contract and event catalog with the approved version, pass producer/consumer same-byte and failure-path tests, then remove the resolved row or this handoff from the active registry. A proposal, local decoder test or green build alone does not close a row.
