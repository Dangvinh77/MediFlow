# Inpatient Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`)
- **Writable scope:** `backend/inpatient-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract or Gateway route.
- Other services may be inspected read-only; do not edit their production source or infer their identifiers.
- Implement only the approved Inpatient Core V1 scope in `docs/eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md`. Keep cross-service producers and consumers disabled until the named owners pass canonical same-byte fixtures; do not add unapproved event fields, routing keys, or inferred identifiers.
- Before REST/event integration, read `docs/ai/16-care-finance-integration-contracts.md`, the active `docs/handoffs/` registry, and the Inpatient-related canonical contracts listed in `docs/ai/services/inpatient.md`.
- Keep Inpatient business contracts `DESIGN_READY` until producers and consumers pass fixtures together; preserve separately tracked statuses such as the identity contract's `PARTIAL`.
- **Verify:** `mvn -q -pl backend/inpatient-service -am test`
