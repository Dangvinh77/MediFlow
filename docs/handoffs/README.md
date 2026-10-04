# Active cross-service handoffs

This is the only registry for implementation blockers that still require another owner. Durable
wire contracts belong in the canonical contract documents under
[`care-finance/`](care-finance/README.md), the event catalog, and the relevant service documents.

## Active blockers

| Handoff | Status | Owner who must act | Unblocks |
|---|---|---|---|
| [Clinical/Lab Gateway role alignment](HANDOFF-VINH-GATEWAY-CARE-ROLES.md) | OPEN — audited 2026-10-03 | Hoàng Anh | LAB_TECH detail, Doctor/Nurse lab queue, start/cancel and admission-referral access |
| [Clinical/Lab financial clearance](../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md) | OPEN | Lộc | Clinical EXAM authorization and Lab exact-test clearance |
| [Surgery G0 decisions (H-01.2/H-01.3)](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) | OPEN — Huy-local V1 core/application choices implemented; cross-owner wire fixtures pending | Vinh, Lộc, Hoàng Anh; Huy for joint mapping | episode/referral/charge/clearance, clinical/Organization policies; Inpatient reference registration + outpatient/late-event handling; provisional READY/invalidation semantics. Detailed code tasks in current Huy plan §6 |
| [Surgery foundation bootstrap](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) | OPEN — shared reactor/DB/Compose complete; Gateway route pending | Hoàng Anh | route the registered Surgery service through Gateway after endpoint/spec gates |
| [Huy Pharmacy/Report care-finance contracts](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) | OPEN | Vinh, Lộc, Huy; Hoàng Anh for Gateway roles | V2 producer fixtures, remaining admission/finance/replay gaps and route roles; additive Huy local work can proceed behind disabled flags |
| [Inpatient Gateway route](HANDOFF-INPATIENT-GATEWAY-ROUTE.md) | OPEN — route/RBAC tests pass; live discovery/downstream smoke pending | Hoàng Anh | expose the implemented Inpatient API through Gateway |

## Lifecycle rule

1. A handoff exists only while a named owner action is blocked.
2. It states producer, consumer, required fields/behavior, reason, and acceptance criteria.
3. When both sides pass the acceptance criteria, move the lasting rule into the canonical contract,
   event catalog or service document and delete the handoff in the same PR.
4. Do not retain `COMPLETE` handoffs as mandatory reading. Git history records the rollout history.
5. A completed compatibility flow can remain documented, but it is not called a handoff and must
   not duplicate the canonical contract.

Every addition or removal updates this registry and any nested `AGENTS.md` integration gate.
