# Inpatient Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/inpatient-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract or Gateway route.
- Other services may be inspected read-only; do not edit their production source or infer their identifiers.
- **MVP status: DEFERRED.** Preserve source/data/migrations and default-OFF integration gates.
  Historical Inpatient Core V1 pointers do not authorize new implementation or activation.
  Reactivation needs an explicit task, source lock and producer/consumer fixture acceptance; never infer wire fields or identifiers.
- Before REST/event integration, read `docs/ai/16-care-finance-integration-contracts.md`, the active `docs/handoffs/` registry, and the Inpatient-related canonical contracts listed in `docs/ai/services/inpatient.md`.
- Keep Inpatient business contracts `DESIGN_READY` until producers and consumers pass fixtures together; preserve separately tracked statuses such as the identity contract's `PARTIAL`.
- **Verify:** `mvn -q -pl backend/inpatient-service -am -DskipTests install` (compile only). Its tests were removed on 2026-11-11 while the context is deferred; `src/test/resources/contracts/` stays because Billing, Notification, Pharmacy and Report tests read those producer fixtures. Write new tests before re-enabling the context.
