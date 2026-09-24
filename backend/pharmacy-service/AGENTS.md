# Pharmacy Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Huy (`LQHuy0210`)
- **Writable scope:** `backend/pharmacy-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Required concurrency handoff:** before changing `payment.completed`, payment receipts,
  dispense idempotency or their tests, read
  [`HANDOFF-PAYMENT-IDEMPOTENCY-RACE.md`](HANDOFF-PAYMENT-IDEMPOTENCY-RACE.md).
- **Integration gate:** before changing prescription/payment/dispense contracts, inpatient
  medication context, queues or fixtures, read the
  [care-finance registry](../../docs/handoffs/care-finance/README.md),
  [CARE-BILLING](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [INPATIENT-SURGERY](../../docs/handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md), and
  [SURGERY-BILLING](../../docs/handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md), plus
  [CARE-PROJECTIONS](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
- Never infer `recordId`/`admissionId`; update Billing, Clinical/Inpatient and Report/Notification
  fixtures/tests together or keep the dependent work blocked.
- **Verify:** `mvn -q -pl backend/pharmacy-service -am test`
