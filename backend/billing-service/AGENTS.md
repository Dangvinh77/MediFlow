# Billing Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`), taking over from Lộc per user assignment on 2026-10-18
- **Writable scope:** `backend/billing-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing charge/payment/refund/clearance events or fixtures, read
  [current contracts](../../docs/plans/03-contracts.md), [business flows](../../docs/plans/02-business-flows.md),
  [reliability](../../docs/plans/04-data-reliability.md) and the [current handoff registry](../../docs/handoffs/README.md).
- Billing is the sole money owner. Contract changes update outbox fixtures and every consumer
  fixture/test together or remain explicitly blocked; `payment.completed` is not a universal gate.
- Historical producer evidence is in the [Billing clearance checkpoint](../../docs/handoffs/history/2026-10-16-billing-clearance-checkpoints.md);
  active acceptance gaps are registered centrally. Deposit/settlement is [deferred](../../docs/handoffs/deferred/README.md), not a prerequisite for outpatient work.
- **Verify:** `mvn -q -pl backend/billing-service -am test`
