# Gateway Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Vinh (`Dangvinh77` / `Harori`), entire-backend assignment on 2026-10-27
- **Writable scope:** `backend/gateway/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Cross-module backend work follows Vinh's current entire-backend assignment in root AGENTS;
  shared repository paths and other developers' frontend scopes still require explicit delegation.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Integration gate:** before changing identity/service tokens or routes, read
  [current contracts](../../docs/plans/03-contracts.md) and the [current handoff registry](../../docs/handoffs/README.md).
  Inpatient/Surgery route activation is outside the outpatient MVP.
- Gateway must not derive or rewrite domain identifiers. Planned routes become live only with
  downstream health, authorization and correlation tests.
- **Verify:** `mvn -q -pl backend/gateway -am test`
