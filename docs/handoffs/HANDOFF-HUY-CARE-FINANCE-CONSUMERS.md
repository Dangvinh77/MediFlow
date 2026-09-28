# HANDOFF — Huy-owned Pharmacy and Report care/finance contracts

- **Status:** `OPEN` — V2 target specs now exist; missing producer facts/fixtures and compatibility evidence still block enablement.
- **Consumer owner:** Huy (`LQHuy0210`) — Pharmacy and Report.
- **Producers to act:** Inpatient/Clinical — Vinh; Billing/Notification — Lộc; Surgery — Huy for its future result facts.
- **Source:** [Huy plan D08–D11](../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md#31-decision-backlog) and [independent preparation](../superpowers/plans/2026-09-26-huy-independent-slices.md).

## Producer actions and Huy acceptance gates

**Reassessed 2026-09-27 at `30e0296`:** [Pharmacy V2](../eproject_general_plan/backend-spec/care-finance-v2/05-pharmacy.md)
and [Report V2](../eproject_general_plan/backend-spec/care-finance-v2/08-report.md) permit additive local
implementation behind `mediflow.features.care-finance-v2=false`; this is not permission to activate
unverified consumers. Code still uses legacy Pharmacy payment proof and invoice-keyed Report
contributions. Billing has explicit legacy prescription/lab targets but no classified transaction,
clearance or settlement producer; Inpatient has no business lifecycle producer yet. New target
specs therefore reduce the decision backlog without establishing implementation or shared test success.

| Decision | Producer action needed | Huy consumer behavior after contract approval | Acceptance evidence |
|---|---|---|---|
| **D08 — admission medication** | V2 selects one slip per prescription, active admission with exact patient/department, and admission charging rather than outpatient prepayment. Vinh must supply lifecycle fixtures and resolve medical-discharge eligibility, transfers/freshness and close-before-start; Lộc supplies cancel/expiry/failure adjustment semantics. | Keep version-0 outpatient unchanged; version-1 outpatient uses exact clearance, admission uses exact eligible lifecycle projection. Never reinterpret `paymentConfirmed`, allow late start to reopen closed admission, or infer IDs. Multiple-dose/returns are outside this V1 target. | Same-byte lifecycle/clearance/prescription fixtures for wrong/closed admission, duplicates, out-of-order/restart, failed/expired/cancelled and late compensation; stock/authorization concurrency and Rabbit tests. |
| **D09 — classified finance** | Lộc provides versioned completed receipt, deposit allocation/release, earned charge/recognition, completed refund and receivable/settlement facts. Specify transaction/allocation/reversal IDs, currency, signed delta versus replacement snapshot, effective period, account/episode and department allocation. | Report projects cash, liability, earned revenue, refund and receivable from explicit source facts. It does not treat an invoice total or deposit as earned revenue, or collapse legal partial transactions by invoice ID. Unknown classification/reference remains pending or rejected according to the agreed contract. | Billing producer and Report consumer decode the same fixture bytes; deposit→recognition→refund and two-partial-payment expected totals reconcile under replay, duplicates and reversed delivery. |
| **D10 — inpatient/surgery reporting** | Vinh provides admission start/transfer/release/discharge/administrative-close facts and staffed/available-bed capacity if occupancy is wanted; Huy's future Surgery producer supplies completed/cancelled result operation/revision and actual times/items. Agree LOS and complication category semantics. | Report exposes only metrics backed by exact authoritative facts; active-admission count is not bed occupancy, `scheduledAt` is not actual start, and free-text is not a complication code. | Producer fixtures covering transfer across two departments, overnight stay, bed-capacity change, partial abort/correction and late delivery; Report totals and unavailable-vs-zero contract. |
| **D11 — compatibility/replay** | All producers/consumers agree envelope versioning, field nullability, source business keys, cutover markers, correction revisions and legacy history retention/replay source. Identify which old events coexist with new classified facts. | Pharmacy retains committed legacy outbox bytes; Report retains five current bindings/queries until the cutover is proven. A new generation replays from a durable finite source without deleting live inbox or double-counting old/new finance facts. | Same-byte producer/consumer old/new fixtures, explicit source watermark, deterministic rebuild and live catch-up tests, retention limit and rollback runbook. |

### Concrete remaining gaps in the new targets

- Billing payment/settlement payloads do not yet provide the allocated-earned amount, deposit
  allocation/release, multi-department allocation or settlement revision/supersedes data required by
  Report's equations. Decide gross versus net cash and receivable balance versus daily movement;
  partial completed receipts need a fact even when they do not grant operational clearance.
- Report's proposed uniqueness includes eventId and does not by itself prevent a repeated business
  operation with a new eventId. Replay also needs a durable source and a processing ledger isolated
  from the live inbox; do not truncate live projections to satisfy the rebuild requirement.
- Admission started/closed do not provide bed transfer/release/capacity facts. Administrative close
  duration cannot silently become medical LOS or bed occupancy; missing close dimensions may only
  come from the exact stored admission source, never REST enrichment or patient inference.
- Pharmacy V14 must reconcile CURRENT record_id NOT NULL with the nullable V2 request and preserve
  the legacy prescribedDate contract. Pending early-clearance/close-before-start storage and V2
  cancelled/expired payloads are not supplied by copying the target DDL/event table unchanged.
- Surgery ready/completed/cancelled payloads must match Notification planned-time requirements,
  Inpatient clinical summary and Report dimensions/category requirements. Target operations Report
  permits DOCTOR; CURRENT Gateway reports route only permits ADMIN/MANAGER.

## Huy baseline and boundary

- Pharmacy currently publishes `prescription.created`, `.filled`, `.dispense.failed`, `.cancelled`, `.expired` through its outbox. Legacy cancelled/expired fixture copies and Pharmacy decoder tests pass locally; this does **not** prove an admission payload.
- Report currently binds `medicalrecord.created`, `lab.result.created`, `prescription.filled`, `payment.completed`, `payment.failed`. Its present payment projection is a legacy invoice contribution, not a classified ledger.
- Huy may implement legacy-safe changes and the specified additive local V2 models/adapters/tests with activation off. Missing fixtures block live binding/writer/API enablement, not every local scaffold; unresolved amount/identity/policy gaps still block the affected behavior. Do not invent IDs, transaction semantics or producer fixtures. Huy does not edit Clinical, Inpatient, Billing, Notification or Gateway here.
- Surgery-specific D01–D07/D12 actions remain in [Surgery implementation decisions](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). D08 and D09 appear there only as cross-context summary; this file supplies Pharmacy/Report consumer acceptance criteria.

## Vinh producer evidence — 2026-09-28

Vinh's next producer slice supplies deterministic version-1 fixtures for Clinical
`admission.requested` and the currently approved Inpatient lifecycle events under each service's
`src/test/resources/contracts/` directory. Producer tests serialize the real envelope/wire mapper;
the Inpatient consumer test reads the same `admission.requested` shape. These fixtures are evidence
for D08/D10/D11, but they do not enable any feature flag or close this handoff until Huy's consumers
read the same bytes and the remaining acceptance paths pass.

Still open: Pharmacy admission eligibility/freshness and late compensation, Report bed
transfer/release/capacity facts and LOS semantics, and a durable replay/cutover policy. No new
transfer, release, capacity or Surgery event may be inferred from the existing admission events.

## Close criteria

For each row, record approval owner/date/link in the Huy plan, update the canonical care-finance contract and event catalog with the approved version, pass producer/consumer same-byte and failure-path tests, then remove the resolved row or this handoff from the active registry. A proposal, local decoder test or green build alone does not close a row.
