# Patient Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Hoàng Anh (`TranHoangAnh94`)
- **Writable scope:** `backend/patient-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Required handoff:** before implementing patient read/existence APIs, read
  [`HANDOFF-CLINICAL-PATIENT-LOOKUP.md`](HANDOFF-CLINICAL-PATIENT-LOOKUP.md).
- **Integration gate:** before changing patient IDs, JWT claims, insurance/contact DTOs or lookup
  security, read the [care-finance registry](../../docs/handoffs/care-finance/README.md) and
  [IDENTITY-LOOKUP](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
- Patient supplies identity/insurance inputs; Billing owns approved financial amounts. Consumers
  must distinguish confirmed absence from Patient service unavailability.
- **Verify:** `mvn -q -pl backend/patient-service -am test`
