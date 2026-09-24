# Notification Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Lộc (`locgit-89`)
- **Writable scope:** `backend/notification-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Active handoff:** before changing patient authorization or JWT parsing, read
  [`HANDOFF-NOTIFICATION-PATIENT-JWT-CLAIM.md`](HANDOFF-NOTIFICATION-PATIENT-JWT-CLAIM.md).
- **Integration gate:** before changing event bindings, templates, patient claims/contact lookup or
  fixtures, read the [care-finance registry](../../docs/handoffs/care-finance/README.md),
  [CARE-PROJECTIONS](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md), and
  [IDENTITY-LOOKUP](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
- Notification never authorizes workflow transitions. Keep payment, deposit, top-up, refund and
  settlement templates semantically distinct.
- **Verify:** `mvn -q -pl backend/notification-service -am test`
