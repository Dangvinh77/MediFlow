# billing-service

Compatibility fees/invoices and episode-isolated Care-Finance ledger. Billing owns all money.

Reference: [`service rules`](../../docs/ai/services/billing.md) and [Surgery/Billing contract](../../docs/handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md).

- **Port:** 8086 · **Base path:** `/api/v1/billing` · **DB:** `mediflow_billing` (PostgreSQL)
- **Owns tables:** compatibility `FEE`/`INVOICE`; account/charge/request/transaction/allocation/clearance/settlement ledger, source receipts and reliable outbox.
- **Architecture:** clean architecture (hexagonal) per [blueprint](../../docs/ai/04-microservice-blueprint.md) — `application → domain`; driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports. Dependencies inward only.

## Status

Opt-in pre-start cancellation adjustment now uses the same `billing.q` dispatcher/issuer gates.
V10 durable cancellation receipts and pending recovery atomically void exact unperformed charges,
cancel exact requests, revoke grants and record original-only refund obligations. No cancellation
creates a completed refund or emits a refund receipt. The gated ADMIN/CASHIER read
`GET /api/v1/billing/surgery-cancellations/{id}/refunds-due` returns remaining own-allocation budgets;
the existing cashier refund endpoint records money actually returned. Raw narrative is not retained.
Gateway/default false/held fences remain unchanged. See the canonical Surgery/Billing contract and
[verification](../../docs/superpowers/plans/2026-10-08-surgery-cancellation-adjustment.md).

Compatibility saga, ledger installment payment and current Surgery clearance lookup are implemented.
The opt-in `surgery.case.created` consumer atomically opens/reuses the exact episode account, prices
planned items using `billing.care-finance.prices`, creates selected SURGERY charges/request and captures
a held V1 `invoice.created`. Catalog defaults empty; unknown prices reject, never become zero.
V9 preserves four-decimal quantities and line-to-charge provenance (master's V7 reconciliation
and V8 refund migrations keep their immutable versions). Replays preserve the original
request and price snapshot, including after account close. One episode can generate charges in
multiple departments without rewriting account attribution. No arbitrary public charge endpoint.

Both `MEDIFLOW_BILLING_LEDGER_ENABLED` and `MEDIFLOW_BILLING_SURGERY_CHARGE_CONSUMER_ENABLED` are
false by default; both select the strict issuer inside the existing `billing.q` dispatcher,
with its existing bounded retry/DLQ. There is exactly one case-created writer: disabled selects
master's charge-only handler, enabled selects the transactional planned-request issuer, with no
fallback on decode/storage failure. No second Surgery creation queue/listener is declared.
Existing V6 prevents live
delivery of all V1 outbox rows. Performed reconciliation, automated cancellation adjustments/refunds, deposit
issuance/top-up, settlement and reviewed cutover remain open; this is not all of Billing V2.

Completed-refund follow-up: ledger AND `MEDIFLOW_BILLING_REFUNDS_ENABLED` (false) gate
`POST /transactions/{id}/refunds`, ADMIN/CASHIER with signed access identity. V8 appends private
reason audit; immutable completed CASH/TRANSFER refunds are bounded by the original payment,
reverse only its own allocations and revoke unsatisfied grants atomically with a held V1 fact.
This records a completed refund, not bank execution or automatic surgery cancellation. Closed/settled
accounts and already-allocated deposits require a later policy; no grant supersession/revoke event.
Full module **314/314**, zero failure/error/skip, PG/RabbitMQ; [evidence and exact limits](../../docs/superpowers/plans/2026-10-08-billing-refund-closure.md).

Package layout:

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   (driving HTTP: controllers + GlobalExceptionHandler)
messaging/consumer    (driving events: @RabbitListener)
infrastructure/persistence   infrastructure/messaging   infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_billing;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/billing-service -am spring-boot:run
```

Swagger UI: http://localhost:8086/swagger-ui.html

## Events

- **Publish:** `invoice.created`, `payment.completed`, `payment.failed`
- **Subscribe:** `prescription.created`, `lab.result.created`, `medicalrecord.created`, `appointment.status.changed`
- **Opt-in planned Surgery charges:** `surgery.case.created` (not upstream `surgery.requested`).
- **Held V1 ledger facts:** classified `payment.completed`, purpose-specific `financial.clearance.granted`, Surgery `invoice.created` request and completed `payment.refunded`.

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/billing-service test        # unit (domain + application, no Spring)
mvn -pl backend/billing-service verify      # + integration (Testcontainers, needs Docker)
```
