# Surgery Service Agent Rules

Root `AGENTS.md` rules continue to apply.

Current MVP scope defers Surgery. Preserve source/data/migrations and default-OFF gates.
Historical policy/full-V2 specs are not new MVP tasks. Only compatibility/regression work explicitly
needed by the [Huy adjustment plan](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md)
is active; use the [handoff registry](../../docs/handoffs/README.md) for later reactivation.

- **Owner:** Huy (`LQHuy0210`)
- **Writable scope:** `backend/surgery-service/**`
- **Task types:** implement Surgery-owned code here; update the registered handoffs when an external contract or shared owner action is required.
- Other services, `backend/common`, root build files, database bootstrap, Compose, Gateway, and CI are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, prices, event names, producer payloads, or query another service's database.
- Before REST/event work, read `docs/ai/16-care-finance-integration-contracts.md` and all active Surgery handoffs in `docs/handoffs/README.md`.
- Keep `mediflow.features.surgery.enabled=false` until its API, producers, consumers, Gateway route, and rollout criteria are verified.
- **Verify regression only:** `mvn -q -pl backend/surgery-service -am test`. Surgery already belongs to the reactor; running tests does not activate deferred runtime features.
