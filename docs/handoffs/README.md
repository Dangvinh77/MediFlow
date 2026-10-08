# Active cross-service handoffs

This is the only registry for implementation blockers that still require another owner. Durable wire contracts belong in the canonical contract documents under [`care-finance/`](care-finance/README.md), the event catalog, and the relevant service documents.

## Active blockers

| Handoff | Status | Owner who must act | Unblocks |
|---|---|---|---|
| [Clinical/Lab Gateway policy and deployment smoke](HANDOFF-VINH-GATEWAY-CARE-ROLES.md) | CI RED — 13 Gateway policy/route failures; reconcile matcher and Surgery default flag before smoke | Hoàng Anh | restore green integration CI and close routing acceptance |
| [Clinical/Lab financial clearance](../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md) | ACTIVE — request issuance and live publication pending | Lộc | Clinical EXAM and Lab exact-test activation |
| [Inpatient deposit and settlement](../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md) | ACTIVE — request issuance, top-up and settlement pending | Lộc | admission activation and administrative close |
| [Surgery intake and held outbound activation](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) | ACTIVE — held V1 capture, LOCAL creation/lifecycle kernels and Inpatient code-level acceptance exist; production authority/intake, live dispatch, broker proof and remaining consumers pending | Huy plus affected consumers | Clinical/Inpatient referral publication and Surgery runtime workflow |
| [Pharmacy/Report care-finance activation](HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) | ACTIVE — Report operational mappings and LOCAL gross receipt/replay kernels exist; Pharmacy admission, full finance/live publication and placement policy pending | Huy plus Vinh/Lộc contract inputs | safe admission medication and complete reporting activation |
| [Notification care projections](HANDOFF-NOTIFICATION-CARE-PROJECTIONS.md) | ACTIVE — version-1 admission, top-up, settlement and Surgery bindings absent | Lộc | privacy-safe Care-Finance notification intents and broker acceptance |

## Huy local evidence — 2026-10-07

Surgery now has an internal atomic creation caller, lifecycle kernel and default-off creation HTTP
adapter, but no production referral authority or activated create workflow. Report has V12 gross
receipt evidence and V13 finite cash replay,
but no accepted full finance/read-model publication. The active handoffs above retain those exact
remaining gates. Charge event naming is fixed in
[SURGERY-BILLING](care-finance/CONTRACT-SURGERY-BILLING-01.md); it is no longer an outstanding
Huy decision. Local tests do not close consumer acceptance or the Gateway CI gate.

## Lifecycle rule

1. A handoff exists only while a named owner action is blocked.
2. It states producer, consumer, required fields/behavior, reason, and acceptance criteria.
3. When both sides pass the acceptance criteria, move the lasting rule into the canonical contract, event catalog or service document and delete the handoff in the same PR.
4. Do not retain `COMPLETE` handoffs as mandatory reading. Git history records the rollout history.
5. A completed compatibility flow can remain documented, but it is not called a handoff and must not duplicate the canonical contract.

Every addition or removal updates this registry and any nested `AGENTS.md` integration gate.
