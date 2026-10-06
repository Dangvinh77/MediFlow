# Active cross-service handoffs

This is the only registry for implementation blockers that still require another owner. Durable
wire contracts belong in the canonical contract documents under
[`care-finance/`](care-finance/README.md), the event catalog, and the relevant service documents.

## Active blockers

| Handoff | Status | Owner who must act | Unblocks |
|---|---|---|---|
| [Clinical/Lab Gateway role alignment](HANDOFF-VINH-GATEWAY-CARE-ROLES.md) | OPEN — audited 2026-10-04 | Hoàng Anh | LAB_TECH detail, Doctor/Nurse lab queue, start/cancel and admission-referral access |
| [Clinical/Lab financial clearance](../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md) | OPEN | Lộc | Clinical EXAM authorization and Lab exact-test clearance |
| [Inpatient deposit and settlement](../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md) | OPEN — consumer implemented; producer absent | Lộc | exact ADMISSION_DEPOSIT authorization, top-up projection and administrative close after settlement |
| [Surgery G0 decisions (H-01.2/H-01.3)](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) | OPEN — Organization room/team-role producer ready; Surgery consumer fixtures and remaining owner policies pending | Vinh, Lộc, Hoàng Anh; Huy for joint mapping | episode/referral/charge/clearance, consumer room/job-role fixtures and clinical policies; Inpatient reference registration + outpatient/late-event handling; provisional READY/invalidation semantics |
| [Surgery foundation bootstrap](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) | OPEN — shared reactor/DB/Compose complete; Surgery business API precedes Gateway route | Huy, then Hoàng Anh | expose the registered Surgery service after a real endpoint and authorization matrix exist |
| [Huy Pharmacy/Report care-finance contracts](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) | OPEN — Pharmacy medical-discharge projection local PASS; transfer/live admission and finance/replay gaps remain | Vinh, Lộc, Huy; Hoàng Anh for Gateway roles | V2 producer fixtures, remaining admission/finance/replay gaps and route roles; additive Huy local work can proceed behind disabled flags |

## Lifecycle rule

1. A handoff exists only while a named owner action is blocked.
2. It states producer, consumer, required fields/behavior, reason, and acceptance criteria.
3. When both sides pass the acceptance criteria, move the lasting rule into the canonical contract,
   event catalog or service document and delete the handoff in the same PR.
4. Do not retain `COMPLETE` handoffs as mandatory reading. Git history records the rollout history.
5. A completed compatibility flow can remain documented, but it is not called a handoff and must
   not duplicate the canonical contract.

Every addition or removal updates this registry and any nested `AGENTS.md` integration gate.
