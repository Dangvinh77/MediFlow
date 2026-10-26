# Clinical Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/clinical-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing appointment/payment, client/event DTOs or fixtures, read
  [current contracts](../../docs/plans/03-contracts.md), [business flows](../../docs/plans/02-business-flows.md),
  [reliability](../../docs/plans/04-data-reliability.md) and the [current handoff registry](../../docs/handoffs/README.md).
  Inpatient/Surgery remain deferred; historical contract pointers do not authorize activation.
- A producer/consumer contract change must update both sides' fixtures/tests in the same PR or leave
  an explicit blocked handoff. Never implement a compatibility guess.
- **Verify:** `mvn -q -pl backend/clinical-service -am test`
