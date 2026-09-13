# Report Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Huy (`LQHuy0210`)
- **Writable scope:** `backend/report-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Verify:** `mvn -q -pl backend/report-service -am test`
