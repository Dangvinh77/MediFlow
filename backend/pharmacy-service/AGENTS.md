# Pharmacy Service Agent Rules

Root `AGENTS.md` rules continue to apply.

Current scope is the [Huy MVP adjustment plan](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md).
Admission/Surgery/full V2 are deferred. Canonical `outpatient-mvp` (`mvp-outpatient` alias) and public V1 API gate do not
authorize release of historical HELD outbox rows or changes to foreign/shared modules.

- **Owner:** Huy (`LQHuy0210`)
- **Writable scope:** `backend/pharmacy-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Other services are read-only unless the user explicitly grants a task-scoped override.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **Payment concurrency policy:** before changing `payment.completed`, payment receipts,
  dispense idempotency or their tests, read the canonical receipt lifecycle and effect-idempotency
  rules in [`docs/ai/services/pharmacy.md`](../../docs/ai/services/pharmacy.md), plus the active
  care-finance integration contracts below.
- **Integration gate:** before changing prescription/payment/dispense contracts, queues or
  fixtures, read [MVP contracts](../../docs/plans/03-contracts.md),
  [reliability](../../docs/plans/04-data-reliability.md) and the
  [active Huy MVP handoff](../../docs/handoffs/HANDOFF-HUY-MVP-OUTPATIENT-ACTIVATION.md).
  Old care-finance handoffs were retired in the docs reset; they do not reopen deferred scope.
- Never infer `recordId`/`admissionId`; update Billing, Clinical/Inpatient and Report/Notification
  fixtures/tests together or keep the dependent work blocked.
- **Verify:** `mvn -q -pl backend/pharmacy-service -am test`
