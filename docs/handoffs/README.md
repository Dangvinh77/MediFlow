# Active cross-service handoffs

This is the only registry for implementation blockers that still require another owner. Durable wire contracts belong in the canonical contract documents under [`care-finance/`](care-finance/README.md), the event catalog, and the relevant service documents.

## Active blockers

| Handoff | Status | Owner who must act | Unblocks |
|---|---|---|---|
| [Clinical/Lab Gateway policy and deployment smoke](HANDOFF-VINH-GATEWAY-CARE-ROLES.md) | CI RED — 13 Gateway policy/route failures; reconcile matcher and Surgery default flag before smoke | Hoàng Anh | restore green integration CI and close routing acceptance |
| [Clinical/Lab financial clearance](../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md) | ACTIVE — opt-in LAB_TEST request issuance exists (default off); EXAM issuance blocked on Clinical's own producer fixture; live publication pending | Lộc | Clinical EXAM and Lab exact-test activation |
| [Inpatient deposit and settlement](../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md) | ACTIVE — opt-in deposit issuance and settlement, local allocation/non-final stock and separate initial final-recognition writer exist; top-up policy, corrections/insurance attribution and live close acceptance remain | Lộc; current user-scoped Huy implementation override | admission activation and administrative close |
| [Surgery intake and held outbound activation](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) | ACTIVE — held V1 capture, local kernels, Inpatient acceptance and researched safety-policy design exist; real authority/intake, full workflow and reviewed release pending | Huy plus affected consumers | Clinical/Inpatient referral publication and Surgery runtime workflow |
| [Pharmacy/Report care-finance activation](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) | ACTIVE — Inpatient placement/capacity producer is ready; Pharmacy post-lock medication authority and Report occupancy/capacity replay/publication remain | Huy, with remaining Lộc financial inputs | safe admission medication and complete reporting activation |
| [Notification care projections](HANDOFF-NOTIFICATION-CARE-PROJECTIONS.md) | ACTIVE — guarded Surgery/PRESCRIPTION request/refund notices locally verified; admission/top-up/settlement and publication pending | Lộc; current user-scoped Huy implementation override | privacy-safe Care-Finance notification intents and broker acceptance |

## Huy local evidence — 2026-10-11

Current 2026-10-11 supported release: later user decision replaces mandatory unavailable pre-V2
coverage with explicit available-evidence snapshot policy. Finite financial publication now has
local source catch-up fencing, verified all-five reconciliation, immutable acceptance/audit and
controlled expected-pointer switch/rollback. Capability/coverage and operator routes default OFF;
unknown history is unavailable, not zero. Gateway operator POST policy, operational full-KPI
publication, corrections and Pharmacy medication authority remain active separately.
[Canonical current release](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#supported-available-history-release-and-controlled-publication--2026-10-11).

The dated evidence below is historical. The current supported-release decision supersedes its
financial-writer absence and mandatory unavailable-pre-V2-history statements; wider target gaps
and unactivated rollout gates remain.

2026-10-11 discovery/recovery: known local legacy volume has no relevant historical rows when
inspected on a read-only isolated clone. Report now exports/restores sealed local financial evidence
with exact IDs/bytes, ADMIN-only default-off routes and immutable actor provenance. This is local
backup/recovery, not an accepted producer pre-V2 export or publication. Active history gates remain.
[Discovery and verified recovery scope](../superpowers/plans/2026-10-11-report-financial-archive-recovery.md).

2026-10-11: Report financial contribution/pending R-01.3.2 now has same-byte PG/Rabbit acceptance,
shared account-first fencing and stable scope ordering. The user requires history BEFORE V2;
activation-only coverage is not sufficient. Real source archive/export/backup and explicit legacy
conversion remain blockers for historical publication. Billing/Pharmacy automatic published-outbox
purge is independently default OFF, preserving remaining bytes without claiming restoration of
already purged events. [Current code/checks/history requirements](../superpowers/plans/2026-10-11-financial-pending-and-history.md).

Financial daily/monthly LOCAL read/API/RBAC code is now implemented behind two false gates,
with complete five-metric snapshot coverage required and absent data returning 404. V20 starts
EMPTY; this is not a financial producer/rebuild/publication or Gateway fix. Recognition/liability
release, original financial reversals, history/live catch-up and approved publication remain open.
[Canonical read semantics](care-finance/CONTRACT-CARE-PROJECTIONS-01.md#financial-snapshot-read-boundary--2026-10-10).

2026-10-10: opt-in Billing non-final reconciliation writer and Report's versioned receivable stock
are locally implemented; this supersedes the read-only-only balance prerequisite, not the final
settlement/admission/recognition/full-publication blockers above. Canonical
[reconciliation source](care-finance/CONTRACT-CARE-BILLING-01.md#non-final-versioned-reconciliation--2026-10-10)
is separate from discharge/final settlement.

## Huy local evidence — 2026-10-07

Surgery now has an internal atomic creation caller, lifecycle kernel and default-off creation HTTP
adapter, plus a separately gated checklist/consent recording boundary with mandatory source authority,
but no production referral/pre-op policy provider or activated workflow. Report has V12 gross
receipt evidence and V13 finite cash replay,
but no accepted full finance/read-model publication. The active handoffs above retain those exact
remaining gates. Charge event naming is fixed in
[SURGERY-BILLING](care-finance/CONTRACT-SURGERY-BILLING-01.md); it is no longer an outstanding
Huy decision. Local tests do not close consumer acceptance or the Gateway CI gate.

## Lifecycle rule

Latest additive evidence (2026-10-08): standalone Billing refund, private Notification
refund notice and Report cash-refund intake/recovery are locally tested; full finance/
refund replay, recognition/settlement and cutover stay open.
[Refund batch](../superpowers/plans/2026-10-08-billing-refund-closure.md).
Surgery now has a [WHO/Vietnam source design](../architecture/surgery-safety-policy-research-2026-10-08.md),
not a clinical approval or authority provider. Existing handoffs retain unmet acceptance.

1. A handoff exists only while a named owner action is blocked.
2. It states producer, consumer, required fields/behavior, reason, and acceptance criteria.
3. When both sides pass the acceptance criteria, move the lasting rule into the canonical contract, event catalog or service document and delete the handoff in the same PR.
4. Do not retain `COMPLETE` handoffs as mandatory reading. Git history records the rollout history.
5. A completed compatibility flow can remain documented, but it is not called a handoff and must not duplicate the canonical contract.

Every addition or removal updates this registry and any nested `AGENTS.md` integration gate.
