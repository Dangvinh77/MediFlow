# Active cross-service handoffs

This is the only registry for implementation blockers that still require another owner. Durable
wire contracts belong in the canonical contract documents under
[`care-finance/`](care-finance/README.md), the event catalog, and the relevant service documents.

## Active blockers

| Handoff | Status | Owner who must act | Unblocks |
|---|---|---|---|
| [Patient read/existence API](../../backend/patient-service/HANDOFF-CLINICAL-PATIENT-LOOKUP.md) | OPEN | Hoàng Anh | Clinical patient validation and frontend patient landing |
| [Notification patient JWT claim](../../backend/notification-service/HANDOFF-NOTIFICATION-PATIENT-JWT-CLAIM.md) | OPEN | Lộc | correct patient authorization with `sub=accountId` |
| [Surgery implementation decisions](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) | OPEN | Vinh, Lộc, Hoàng Anh, Huy | lock episode/clearance, referral, checklist, room and staff contracts before Surgery spec |
| [Surgery foundation bootstrap](HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) | OPEN | Shared-build integrator (confirm with Vinh), Hoàng Anh | register Surgery module/DB/Compose and route through Gateway after module/spec gates |
| [Huy Pharmacy/Report care-finance contracts](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) | OPEN | Vinh, Lộc, Huy | D08–D11 producer fixtures, admission authorization, classified finance and replay-safe Report projections |
| [Inpatient Gateway route](HANDOFF-INPATIENT-GATEWAY-ROUTE.md) | OPEN | Hoàng Anh | expose the future inpatient API through Gateway |

## Lifecycle rule

1. A handoff exists only while a named owner action is blocked.
2. It states producer, consumer, required fields/behavior, reason, and acceptance criteria.
3. When both sides pass the acceptance criteria, move the lasting rule into the canonical contract,
   event catalog or service document and delete the handoff in the same PR.
4. Do not retain `COMPLETE` handoffs as mandatory reading. Git history records the rollout history.
5. A completed compatibility flow can remain documented, but it is not called a handoff and must
   not duplicate the canonical contract.

Every addition or removal updates this registry and any nested `AGENTS.md` integration gate.
