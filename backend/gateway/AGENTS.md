# Gateway Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Hoàng Anh (`TranHoangAnh94`)
- **Writable scope:** `backend/gateway/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Required handoff:** before replacing demo login or changing Organization authentication, read
  [`HANDOFF-ORGANIZATION-ACCOUNT-VERIFY.md`](HANDOFF-ORGANIZATION-ACCOUNT-VERIFY.md).
- **Integration gate:** before changing identity claims, service JWTs or adding Inpatient/Surgery
  routes, read the [care-finance registry](../../docs/handoffs/care-finance/README.md) and
  [IDENTITY-LOOKUP](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
- Gateway must not derive or rewrite domain identifiers. Planned routes become live only with
  downstream health, authorization and correlation tests.
- **Verify:** `mvn -q -pl backend/gateway -am test`
