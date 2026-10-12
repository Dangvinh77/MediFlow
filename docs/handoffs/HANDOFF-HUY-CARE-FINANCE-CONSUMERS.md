# HANDOFF — Pharmacy and Report care-finance activation

**Status:** ACTIVE — Vinh's placement lookup, placement events and capacity-history events are producer-ready; Huy still owns Pharmacy post-lock authorization and Report projection/replay/publication behind default-off gates.
**Owner:** Huy (`LQHuy0210`), Pharmacy and Report.

Producer handoff 2026-10-12: Inpatient now supplies the distinct service-only
`/admissions/{id}/placement-lookup`, `admission.placement.changed` V1 and
`bed.capacity.changed` V1 with persisted source revisions, append-only capacity history and exact
producer fixtures. The existing admission lookup is unchanged. This closes Vinh's producer gap;
it does not activate Huy's consumers or authorize dispensing/publication. PostgreSQL/Rabbit tests
are checked in and use the established Docker-unavailable skip behavior; broker-backed acceptance
still must run in a Docker-capable environment before rollout.

2026-10-12 scoped completion: Huy's finite SURGERY publication/rollback now passes actual PG
fresh/upgrade, source-writer/publication races, evidence sealing and read-pointer acceptance checks.
The internal Pharmacy preflight also consumes the existing Organization doctor-eligibility decision;
there is no fallback to generic job/team labels. Fresh full Pharmacy 625 and Report 1,252 cases pass,
zero skips, actual PostgreSQL/RabbitMQ. Other services/Gateway/producer contracts are unchanged.
This handoff remains ACTIVE for prescribing/order/current placement, distributed revoke, census/
capacity/full-KPI source contracts and approved public/held cutover. These are not replaced by
module tests, the doctor decision or finite SURGERY counters. No new handoff file is added.

Current release 2026-10-11: the user's later decision permits a reasonable available-history
fallback. It supersedes the earlier mandatory unavailable pre-V2 blocker below; missing periods
remain unavailable, never reconstructed from fixture/aggregate IDs. Huy now supplies a controlled
finite financial publisher with local writer/source fence, row/proof reconciliation, immutable
acceptance/audit, expected-pointer switch and rollback, plus explicit capability/coverage/bounded
rebuild APIs. All operator gates default OFF. Unsupported post-final/operational corrections,
operational publication/full KPI rollout and Pharmacy admission authority remain ACTIVE. Hoàng Anh
must review management-plane operator POST ingress before external exposure; Gateway is unchanged.
[Current canonical contract](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#supported-available-history-release-and-controlled-publication--2026-10-11)
and [decision/runbook](../superpowers/plans/2026-10-11-report-supported-release-60.md).

Local discovery/recovery continuation 2026-10-11: the known legacy PostgreSQL volume was cloned
and inspected read-only: Billing/Report have no public tables and Pharmacy's four relevant tables
have zero rows. Original volumes are unchanged. This is not proof of historical no-events elsewhere.
Report now has strict portable local financial evidence export/restore and immutable actor provenance,
not a producer export or accepted pre-V2 coverage. Historical sources/explicit legacy conversion and
publication remain ACTIVE. [Discovery and recovery evidence](../superpowers/plans/2026-10-11-report-financial-archive-recovery.md).

2026-10-11 requirement: the user requires history BEFORE V2. Complete local activation-era inputs
alone are insufficient for historical acceptance/cutover. R-01.3.2 local financial contribution/
pending behavior now passes all six PG/Rabbit arrival permutations and exact original-linked partial
payments/refunds. Billing/Pharmacy automatic published-outbox purge is separately default OFF to
preserve remaining history. Historical archive/source export, explicit legacy identity conversion,
retention/access/backup/restore and controlled catch-up/publication remain ACTIVE, not inferred from
tests or current totals. No accepted producer historical export has been found in the sources inspected.
[Current evidence and source requirements](../superpowers/plans/2026-10-11-financial-pending-and-history.md).

Latest continuation 2026-10-10: an initial final Billing writer and Report's exact final-only
recognition/original-deposit release kernel are now coded. Report retains missing accepted local
cash proofs as durable PENDING, deduplicates semantic operations and writes both scopes atomically.
The canonical initial wire and unsupported corrections/insurance/debt/waiver are defined in
[CARE-BILLING](care-finance/CONTRACT-CARE-BILLING-01.md#initial-final-settlement-and-explicit-earned-operation--2026-10-10)
and [CARE-PROJECTIONS](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#initial-final-only-recognition-intake--2026-10-10).
Full financial rebuild/publication and Pharmacy live medication permission remain ACTIVE; this
supersedes the earlier initial-final writer/recognition absence notes below, not their open rollout gates.

Medical/finance follow-up 2026-10-10: Report now consumes actual Inpatient medical approval paired
with original START to project medical discharges, whole LOS minutes and completed 24-hour stay
periods. This is not staffed-bed census/occupancy or redefined inpatientDays. Under the authorized
Billing override, local charge-close, original-deposit allocation/unused-cash refunds and canonical
performed Surgery proof are implemented. User-chosen earned timing is final-settlement-only; final
writer/recognition/reversal/five-metric publication and Pharmacy live medication permission remain
ACTIVE. No held release or default-on feature is inferred.
[Current execution and limits](../superpowers/plans/2026-10-10-medical-discharge-deposit-finalization.md).

Financial read follow-up 2026-10-10: Huy now provides local dual-gated ADMIN/MANAGER
`financial/daily` and `financial/monthly` aggregate APIs, with complete exact day/scope/currency/
timezone coverage and correct month-end stocks. The read namespace is EMPTY and has no production
five-metric rebuild/publication writer. Missing sources/coverage intentionally return 404, not
fabricated zeros. This removes the reader/RBAC code gap only; Billing recognition/liability release/
original reversals, durable full-financial replay and approved publication remain ACTIVE.
[Read boundary](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#financial-snapshot-read-boundary--2026-10-10).

Follow-up 2026-10-10: Billing non-final `settlement.reconciled` V1 and Report's latest-version
receivable stock now have a local producer/consumer implementation under the existing Billing
override. They remove the local balance/revision projection gap only. Final settlement, earned
recognition/liability release, admission authority and approved financial replay/publication remain
open; no consumer may use the non-final snapshot to close an admission. See
[canonical contract](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#versioned-non-final-receivable-stock--2026-10-10).
**Unblocks:** safe Inpatient lifecycle activation and complete operational/financial reporting.

## Producer

Inpatient produces approved admission/discharge lifecycle events. Billing produces clearance, allocation/refund/settlement facts. Clinical, Lab, Pharmacy and Surgery publish their canonical operational facts. Exact contracts belong to [`CONTRACT-INPATIENT-SURGERY-01`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [`CONTRACT-CARE-BILLING-01`](care-finance/CONTRACT-CARE-BILLING-01.md) and [`CONTRACT-CARE-PROJECTIONS-01`](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Consumer

- Pharmacy persists exact admission lifecycle evidence through its own opt-in queue. Start is not sufficient medication permission: medical discharge/close dominate late start, and public admission prescribing/dispensing remains closed.
- Pharmacy has separate current-admission and current-placement lookups. The placement response
  proves the exact active assignment/current-bed department at one observation and remains evidence,
  not prescriber/order authority or a distributed lease.
- Report has a separate opt-in operational/evidence queue using actual Clinical, Lab, Pharmacy,
  Surgery and Inpatient bytes. It may now add occupancy from exact placement intervals and capacity
  denominators from exact capacity-effective intervals; it must not infer either from start/close.
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

Rx creation follow-up 2026-10-09: Billing's gated issuer now accepts original Pharmacy creation
bytes and commits exact episode/bundle charge plus selected outpatient request with held invoice.
Admission is charge-only, not prepaid. Pharmacy/Report check the same Billing installment/grant
files. Creation issuance and guarded terminal cancelled/expired/failed local adjustment no longer
wait for another owner. Billing voids the exact charge/cancels the outpatient request, locally
revokes its grant and records original-only refund obligations; only actual refund commands
produce payment.refunded. Notification accepts the private PRESCRIPTION unpaid-request purpose.
Distributed revoke/supersede, admission charge-close authority and public/held activation remain open.
[Canonical semantics](care-finance/CONTRACT-CARE-BILLING-01.md#opt-in-prescription-charge-issuance--2026-10-09),
[current adjustment evidence](../superpowers/plans/2026-10-09-pharmacy-report-rx-adjustment.md).

Context follow-up 2026-10-09: Clinical now supplies an independently gated, service-only exact
prescription-context read; Pharmacy has the real Feign consumer and an internal context-checked
creation caller with post-lock/replay freshness checks. It supplies record/patient/prescriber/
department/episode relationship facts, not medication-order or current staff license permission.
Public V1 and held delivery stay closed. Current Patient existence and Organization descriptive
active-doctor/department checks are implemented with real REST, outside transactions, and rechecked
after mutation locks. This is necessary identity evidence, not doctor-license/order permission.
Authoritative order policy, Billing terminal adjustment and admission placement/races remain
required. Local Rx creation issuance is now implemented as stated above; activation is still
separate. [Earlier verification](../superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).
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

1. Pharmacy must consume the exact placement fixtures, call the fresh placement lookup outside its
   transaction, then recheck after lock waits before create and dispense. Reject missing/stale/
   unavailable placement, revision gaps, patient/department mismatch and a closed medication window.
2. Report must consume the exact placement/capacity fixtures, retain out-of-order revisions until
   contiguous, replay immutable assignment/capacity intervals, expose explicit coverage horizon and
   keep missing pre-baseline history unavailable.
3. Report guarded operational bindings are implemented; finish source coverage/export, live
   catch-up and controlled publication. Unsupported corrections remain rejected, not auto-imported.
4. Implement Billing allocation/recognition/refund/settlement projection against exact fixtures; do not count deposit cash as earned revenue.
5. Prove live catch-up, finite replay, cutover and rollback without double-counting compatibility and versioned facts.

## Acceptance criteria

- Pharmacy and Report deserialize the same producer fixture bytes.
- Duplicate, out-of-order, conflicting-source and malformed events have bounded tested behavior.
- Disabled flags produce no public delivery or publication side effects.
- Docker proves admission start/medical discharge against Pharmacy authorization.
- Docker proves operational and finance facts populate Report once under replay and live catch-up.
- Pharmacy post-lock authorization passes actual lookup plus lifecycle/placement races without
  treating an event ACK or stale projection as permission.
- Report reconstructs the same occupancy/capacity result from live intake and finite replay, while
  gaps and pre-baseline history remain explicitly unavailable.

## Local verification follow-up — 2026-10-08

Actual PostgreSQL/RabbitMQ checks now cover Pharmacy admission duplicate/new delivery, durable
medical-discharge/close-before-start, source conflict rollback, malformed DLQ and retained-byte
replay after database failure. Report checks actual producer mappings, two scopes, minimal journal,
semantic conflict, correction rejection, second-scope rollback/DLQ replay and finite rebuild, plus
admission evidence pairing after listener restart. Listener restart is not a separate JVM crash.
Defaults remain false; no V1 held row is released or report publication inserted. Fresh complete
regression results and the exact remaining acceptance are recorded in
[the execution follow-up](../superpowers/plans/2026-10-08-huy-ready-task-completion.md).
