# Notification Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`), taking over from Lộc per user assignment on 2026-10-18
- **Writable scope:** `backend/notification-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing event bindings, patient mapping or templates/fixtures, read
  [current contracts](../../docs/plans/03-contracts.md), [business flows](../../docs/plans/02-business-flows.md)
  and the [current handoff registry](../../docs/handoffs/README.md).
  Deferred projection checklists do not reopen Admission/Surgery.
- Notification never authorizes workflow transitions. Keep payment, deposit, top-up, refund and
  settlement templates semantically distinct.
- **Verify:** `mvn -q -pl backend/notification-service -am test`
