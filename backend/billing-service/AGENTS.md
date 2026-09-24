# Billing Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Lộc (`locgit-89`)
- **Writable scope:** `backend/billing-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Required handoff:** before changing `PaymentCompletedEvent` or lab fee aggregation, read
  [`HANDOFF-LAB-PAYMENT-COMPLETED.md`](HANDOFF-LAB-PAYMENT-COMPLETED.md).
- **Integration gate:** before changing account/charge/invoice/payment/deposit/refund/settlement
  events or fixtures, read the [care-finance registry](../../docs/handoffs/care-finance/README.md),
  [CARE-BILLING](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [SURGERY-BILLING](../../docs/handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md), and
  [CARE-PROJECTIONS](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
- Billing is the sole money owner. Contract changes update outbox fixtures and every consumer
  fixture/test together or remain explicitly blocked; `payment.completed` is not a universal gate.
- **Verify:** `mvn -q -pl backend/billing-service -am test`
