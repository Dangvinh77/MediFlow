# HANDOFF — Pharmacy and Report care-finance activation

**Status:** ACTIVE — Pharmacy admission intake/current-state lookup and Report operational intake are implemented behind separate default-off gates; admission command authorization, full finance/live publication and placement policy remain open.
**Owner:** Huy (`LQHuy0210`), Pharmacy and Report.
**Unblocks:** safe Inpatient lifecycle activation and complete operational/financial reporting.

## Producer

Inpatient produces approved admission/discharge lifecycle events. Billing produces clearance, allocation/refund/settlement facts. Clinical, Lab, Pharmacy and Surgery publish their canonical operational facts. Exact contracts belong to [`CONTRACT-INPATIENT-SURGERY-01`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [`CONTRACT-CARE-BILLING-01`](care-finance/CONTRACT-CARE-BILLING-01.md) and [`CONTRACT-CARE-PROJECTIONS-01`](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Consumer

- Pharmacy persists exact admission lifecycle evidence through its own opt-in queue. Start is not sufficient medication permission: medical discharge/close dominate late start, and public admission prescribing/dispensing remains closed.
- Pharmacy has a separately gated service-only current-admission lookup, exact IDs/correlation and bounded freshness, with unavailable distinct from absence. It proves admission state, not placement, prescriber/order authority or a distributed lease.
- Report has a separate opt-in operational/evidence queue using actual Clinical, Lab, Pharmacy, Surgery and Inpatient bytes. It uses transactional kernels and minimal replay inputs, not the legacy totals or accepted publication. Canonical admission starts now count once in department/hospital scopes; administrative close remains evidence only, not medical discharge/LOS/occupancy.
- Report also has opt-in cash-only `payment.completed` intake into its existing gross receipt kernel
  and finite replay, with a separate queue/DLQ and default-off gate. Actual Billing service/deposit
  bytes commit once; legacy projections do not count these deposits as revenue. Allocation, release,
  recognition, refund, settlement and accepted financial publication remain missing source gates.
  [Current code/verification](../superpowers/plans/2026-10-08-report-cash-intake.md).

## Huy local implementation — 2026-10-07

Report V12 maps actual Billing service/deposit receipt bytes into transaction-keyed gross cash
receipt evidence with atomic dedupe, rollback and currency isolation. V13 rebuilds those accepted
minimal facts into isolated finite generations with frozen input, bounded resume and bidirectional
reconciliation. VERIFIED does not publish a read model, prove historical completeness, release held
events or establish owner acceptance. Deposit cash is not earned revenue.

These local kernels supersede the historical absence of gross-receipt mapping/replay only, not
recognition, deposit release, refunds, settlement or corrections. Operational Clinical/Pharmacy/
Surgery mappings and source conflict fencing now have guarded operational intake; no live producer
publication or accepted read cutover is implied.

Evidence and remaining scope:
[cash receipt batch](../superpowers/plans/2026-10-07-report-cash-receipts.md),
[cash replay batch](../superpowers/plans/2026-10-07-report-cash-replay-batch.md),
[canonical cash-only boundary](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#report-offline-gross-receipt-evidence--2026-10-07).

## Owner actions

Context follow-up 2026-10-09: Clinical now supplies an independently gated, service-only exact
prescription-context read; Pharmacy has the real Feign consumer and an internal context-checked
creation caller with post-lock/replay freshness checks. It supplies record/patient/prescriber/
department/episode relationship facts, not medication-order or current staff license permission.
Public V1 and held delivery stay closed. Current Patient existence and Organization descriptive
active-doctor/department checks are implemented with real REST, outside transactions, and rechecked
after mutation locks. This is necessary identity evidence, not doctor-license/order permission.
Authoritative order policy, Billing Rx issuance/terminal adjustment and admission placement/races
remain required. [Current verification](../superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).
[Canonical contract](care-finance/CONTRACT-CARE-BILLING-01.md#additive-clinical-prescription-context-lookup--2026-10-09).

Finite rebuild follow-up 2026-10-08: Report now has paired receipt/refund manifests and isolated
accepted cash-state replay (V17), including frozen pending/rejected inventory and applied cash-out.
It does not retry copied pending rows, replay all historical decisions or publish financial reads.
Recognition/liability/settlement, historical coverage and live cutover remain open.
[Canonical limits and verification](../superpowers/plans/2026-10-08-huy-master-sync-refund-replay.md).

Refund follow-up 2026-10-08: actual Billing completed-refund writer and Report's
default-off cash-out/V15 pending recovery now have same-byte PG/Rabbit acceptance.
The missing refund cash input is implemented, not earned/deposit-liability reversal,
settlement, full refund replay, historical coverage or accepted publication.
[Current evidence](../superpowers/plans/2026-10-08-billing-refund-closure.md).

1. Pharmacy lifecycle intake is implemented; finish admission create/dispense authorization and discharge/close-versus-stock mutation races before public activation. Do not treat ACK or an initial start projection as permission.
2. Keep current placement absent until Vinh and Huy approve transfer/release/capacity identity, revision and business-time fixtures. Never reuse the initial start bed as current occupancy.
3. Report guarded operational bindings are implemented; finish approved source coverage/export, live catch-up and controlled publication with source revalidation for legacy rows. Unsupported corrections remain rejected, not auto-imported.
4. Implement Billing allocation/recognition/refund/settlement projection against exact fixtures; do not count deposit cash as earned revenue.
5. Prove live catch-up, finite replay, cutover and rollback without double-counting compatibility and versioned facts.

## Acceptance criteria

- Pharmacy and Report deserialize the same producer fixture bytes.
- Duplicate, out-of-order, conflicting-source and malformed events have bounded tested behavior.
- Disabled flags produce no public delivery or publication side effects.
- Docker proves admission start/medical discharge against Pharmacy authorization.
- Docker proves operational and finance facts populate Report once under replay and live catch-up.
- Placement, capacity and LOS remain absent/unavailable until their separate contracts are approved.

## Local verification follow-up — 2026-10-08

Actual PostgreSQL/RabbitMQ checks now cover Pharmacy admission duplicate/new delivery, durable
medical-discharge/close-before-start, source conflict rollback, malformed DLQ and retained-byte
replay after database failure. Report checks actual producer mappings, two scopes, minimal journal,
semantic conflict, correction rejection, second-scope rollback/DLQ replay and finite rebuild, plus
admission evidence pairing after listener restart. Listener restart is not a separate JVM crash.
Defaults remain false; no V1 held row is released or report publication inserted. Fresh complete
regression results and the exact remaining acceptance are recorded in
[the execution follow-up](../superpowers/plans/2026-10-08-huy-ready-task-completion.md).
