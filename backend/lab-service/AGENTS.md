# Lab Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/lab-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing Lab request/result events, payment/clearance state, queues or
  fixtures, read the [care-finance registry](../../docs/handoffs/care-finance/README.md),
  [CARE-BILLING](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [IDENTITY-LOOKUP](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), and
  [CARE-PROJECTIONS](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
- Keep the existing explicit `labTestIds` compatibility contract until clearance producer and Lab
  consumer fixtures/tests pass together. Never infer a test from patient, record or invoice.
- **Verify:** `mvn -q -pl backend/lab-service -am test`
