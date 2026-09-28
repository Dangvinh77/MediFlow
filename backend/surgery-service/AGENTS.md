# Surgery Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Huy (`LQHuy0210`)
- **Writable scope:** `backend/surgery-service/**`
- **Task types:** implement Surgery-owned code here; update the registered handoffs when an external contract or shared owner action is required.
- Other services, `backend/common`, root build files, database bootstrap, Compose, Gateway, and CI are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, prices, event names, producer payloads, or query another service's database.
- Before REST/event work, read `docs/ai/16-care-finance-integration-contracts.md` and all active Surgery handoffs in `docs/handoffs/README.md`.
- Keep `mediflow.features.surgery.enabled=false` until its API, producers, consumers, Gateway route, and rollout criteria are verified.
- **Verify the local module:** `mvn -f backend/surgery-service/pom.xml test`; root reactor verification requires registration by the shared-build owner.
