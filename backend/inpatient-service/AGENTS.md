# Inpatient Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/inpatient-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract or Gateway route.
- Other services may be inspected read-only; do not edit their production source or infer their identifiers.
- Keep this module at preliminary foundation scope until an implementation-ready Inpatient spec exists. Do not add business aggregates, DDL, endpoints, DTOs, publishers, consumers, or event bindings ahead of that spec.
- Before REST/event integration, read `docs/ai/16-care-finance-integration-contracts.md`, the active `docs/handoffs/` registry, and the Inpatient-related canonical contracts listed in `docs/ai/services/inpatient.md`.
- Keep Inpatient business contracts `DESIGN_READY` until producers and consumers pass fixtures together; preserve separately tracked statuses such as the identity contract's `PARTIAL`.
- **Verify:** `mvn -q -pl backend/inpatient-service -am test`
