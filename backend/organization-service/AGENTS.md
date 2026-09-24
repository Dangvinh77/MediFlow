# Organization Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Hoàng Anh (`TranHoangAnh94`)
- **Writable scope:** `backend/organization-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Required handoff:** before changing staff lookup or service authentication, read
  [`HANDOFF-CLINICAL-STAFF-LOOKUP.md`](HANDOFF-CLINICAL-STAFF-LOOKUP.md).
- **Required handoff:** before changing account verification used by Gateway, read
  [`../gateway/HANDOFF-ORGANIZATION-ACCOUNT-VERIFY.md`](../gateway/HANDOFF-ORGANIZATION-ACCOUNT-VERIFY.md).
- **Integration gate:** before changing staff/department IDs, eligibility DTOs, JWT claims or
  service authentication, read the [care-finance registry](../../docs/handoffs/care-finance/README.md)
  and [IDENTITY-LOOKUP](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
- Clinical, Inpatient and Surgery consumers require stable additive lookup contracts; update their
  fixtures/tests together or leave an explicit blocked handoff.
- **Verify:** `mvn -q -pl backend/organization-service -am test`
