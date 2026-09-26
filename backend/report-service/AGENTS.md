# Report Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Huy (`LQHuy0210`)
- **Writable scope:** `backend/report-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **JWT contract:** human endpoints require `type=access`; preserve the cases in
  `src/test/java/com/mediflow/report/infrastructure/security/JwtAuthFilterTest.java`.
- **Integration gate:** before changing event bindings, revenue/payment semantics or projection
  fixtures, read the [care-finance registry](../../docs/handoffs/care-finance/README.md) and
  [CARE-PROJECTIONS](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md), plus the
  [active Huy care-finance handoff](../../docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md).
- Deposits are cash/liability, not earned revenue. New producer events require matching Report
  fixtures/replay/idempotency tests before the contract is marked implemented.
- **Verify:** `mvn -q -pl backend/report-service -am test`
