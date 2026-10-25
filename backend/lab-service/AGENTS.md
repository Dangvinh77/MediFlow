# Lab Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/lab-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing request/result, payment/clearance, queues or fixtures, read
  [current contracts](../../docs/plans/03-contracts.md), [reliability](../../docs/plans/04-data-reliability.md)
  and the [current handoff registry](../../docs/handoffs/README.md).
- Keep the existing explicit `labTestIds` compatibility contract until clearance producer and Lab
  consumer fixtures/tests pass together. Never infer a test from patient, record or invoice.
- **Verify:** `mvn -q -pl backend/lab-service -am test`
