# Clinical Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/clinical-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing REST clients, event DTOs/bindings, appointment payment state,
  admission referrals, or consumer fixtures, read the
  [care-finance registry](../../docs/handoffs/care-finance/README.md) and contracts
  [CARE-BILLING](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [INPATIENT-SURGERY](../../docs/handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md), and
  [IDENTITY-LOOKUP](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md), plus
  [CARE-PROJECTIONS](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md) when changing
  completed-record/report/notification events.
- A producer/consumer contract change must update both sides' fixtures/tests in the same PR or leave
  an explicit blocked handoff. Never implement a compatibility guess.
- **Verify:** `mvn -q -pl backend/clinical-service -am test`
