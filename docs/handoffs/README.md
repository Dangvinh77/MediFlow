# Active cross-service handoffs

This is the only registry for implementation blockers that still require another owner. Durable
wire contracts belong in the canonical contract documents under
[`care-finance/`](care-finance/README.md), the event catalog, and the relevant service documents.

## Active blockers

| Handoff | Status | Owner who must act | Unblocks |
|---|---|---|---|
| [Clinical/Lab Gateway role alignment](HANDOFF-VINH-GATEWAY-CARE-ROLES.md) | PARTIAL — exact roles implemented; Gateway 135 tests pass, deployment smoke pending | Huy (user-authorized dependency scope); Hoàng Anh retains long-term ownership | verify actual Gateway-to-Clinical/Lab deployment; no remaining role-code blocker |
| [Clinical/Lab financial clearance](../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md) | PARTIAL — Billing held grant producer and same-byte EXAM/LAB_TEST consumer tests added; request issuance/live rollout remain open | Huy (user-authorized dependency scope); Lộc/Vinh retain long-term ownership | Clinical EXAM authorization and Lab exact-test clearance |
| [Inpatient deposit and settlement](../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md) | PARTIAL — held ADMISSION_DEPOSIT grant/receipt producer and exact consumer fixture added; top-up/settlement absent | Huy (user-authorized dependency scope); Lộc/Vinh retain long-term ownership | deposit request issuance, top-up projection and administrative close after settlement |
| [Surgery G0 decisions (H-01.2/H-01.3)](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) | PARTIAL — local Surgery V1 wire/HELD capture + producer fixtures added; clinical/referral/finance/runtime gaps remain | Huy has user-authorized dependency implementation scope; original owners retain long-term ownership | episode/referral/charge/clearance and clinical policies; Inpatient reference registration + outpatient/late-event handling; full READY/finalize/START authority wiring and downstream invalidation-reminder acceptance |
| [Huy Pharmacy/Report care-finance contracts](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) | PARTIAL — medical discharge plus Billing clearance/receipt fixtures added; transfer/live admission, financial projector and reversal/settlement remain open | Huy has user-authorized dependency scope; original owners retain long-term ownership | remaining admission/finance/replay gaps; additive local work proceeds behind disabled flags |

## Lifecycle rule

**Huy update 2026-10-07:** the Surgery handoff's missing outbound shapes are implemented locally:
five typed V1 facts, ten actual producer fixtures and atomic HELD capture. Report supplies offline
Clinical/Pharmacy/Surgery mappings and source-payload conflict fencing. Existing handoffs stay active
for referral/reference/outpatient/late handling, finance writers, reminder intake, clinical policy
and actual live acceptance; no whole workflow is closed by local wire tests. See
[current evidence and remaining scope](../superpowers/plans/2026-10-07-huy-outbound-contracts.md).

The H-01.2 charge event name/version/routing decision is now fixed by Huy in
[SURGERY-BILLING](care-finance/CONTRACT-SURGERY-BILLING-01.md): `surgery.case.created` for planned
charges and `surgery.completed` for actual reconciliation, both envelope V1 on `mediflow.events`.
Billing can consume the Surgery-owned fixtures after pull; naming is no longer an outstanding
Huy decision. Billing consumer/catalogue/effects acceptance and live delivery remain open.

1. A handoff exists only while a named owner action is blocked.
2. It states producer, consumer, required fields/behavior, reason, and acceptance criteria.
3. When both sides pass the acceptance criteria, move the lasting rule into the canonical contract,
   event catalog or service document and delete the handoff in the same PR.
4. Do not retain `COMPLETE` handoffs as mandatory reading. Git history records the rollout history.
5. A completed compatibility flow can remain documented, but it is not called a handoff and must
   not duplicate the canonical contract.

Every addition or removal updates this registry and any nested `AGENTS.md` integration gate.
