# pharmacy-service

Drugs, prescriptions, dispensing and stock. **Saga participant** in prescribe → pay → dispense.

Reference: [`docs/ai/services/pharmacy.md`](../../docs/ai/services/pharmacy.md) · design doc `docs/eproject_general_plan/backend-spec/05-pharmacy.md`.

- **Port:** 8085 · **Base path:** `/api/v1/pharmacy` · **DB:** `mediflow_pharmacy` (PostgreSQL)
- **Owns tables:** `DRUG`, `PRESCRIPTION`, `PRESCRIPTION_LINE`, `DISPENSE_SLIP`, `PROCESSED_EVENT`, `STOCK_RESERVATION`, `PAYMENT_RECEIPT`, `PHARMACY_EVENT_OUTBOX`, `STOCK_ADJUSTMENT`, `PHARMACY_SCHEDULER_LEASE`
- **Architecture:** clean architecture (hexagonal) per [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`; driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports. Dependencies inward only.

## Status

**In progress.** Domain, application ports/services, persistence adapters, stock reservations,
expiry scheduler, HTTP/JWT security and RabbitMQ consumer/topology are implemented. The
transactional outbox is enabled for durable event delivery; cross-service E2E gates remain before release.
Outbox delivery now supports bounded exponential retry, per-row replay, retention cleanup and
per-aggregate causal ordering. Administrators can replay quarantined rows, and Micrometer exposes
pending/quarantine count and age gauges. Stock adjustments persist an immutable audit row in the same
transaction as the drug mutation and `stock.adjusted` outbox event.

Package layout (already created, each folder holds a `.gitkeep` until you fill it):

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   (driving HTTP: controllers + GlobalExceptionHandler)
messaging/consumer    (driving events: @RabbitListener)
infrastructure/persistence   infrastructure/messaging   infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_pharmacy;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/pharmacy-service -am spring-boot:run
```

Swagger UI: http://localhost:8085/swagger-ui.html

## Events

- **Publish:** `prescription.created`, `prescription.cancelled`, `prescription.expired`, `prescription.filled`, `prescription.dispense.failed`, `stock.low`, `stock.adjusted`
- **Subscribe:** `payment.completed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

Payment events are claimed atomically by `eventId`; duplicate deliveries are ignored. Manual
dispensing never accepts a client-supplied `paid` flag and requires a durable `PAYMENT_RECEIPT`.
Listener
retries are bounded (default 3 attempts with exponential backoff) and poison messages are routed
to `pharmacy.dlq`. Configure with `MEDIFLOW_PHARMACY_RABBIT_RETRY_*` environment variables.

## Care-finance V2 boundary

The prescription request accepts an explicit `careContractVersion: 1` context shape for validation,
but V1 creation remains fail-closed with `PHARMACY_CARE_FINANCE_V2_UNAVAILABLE` until exact Billing
clearance and Inpatient admission projections are implemented and verified. The legacy
`payment.completed` consumer accepts version-0 prescriptions only; it rejects V1 before claiming a
receipt or touching stock. The feature flag remains `false` by default.

V15 adds a local admission context/event ledger. The offline Inpatient V1 decoder reads actual
producer fixtures; CLOSED is absorbing even when received before STARTED, conflicting source facts
roll back their claims, and exact nanosecond source instants survive PostgreSQL reload. No new Rabbit
binding or V1 dispense permission is enabled. Medical discharge, transfers and freshness still need
the Inpatient contract; this context check alone is not an authorization decision.

`PrescriptionCareEventCodec` supplies separate V1 DTOs/round-trip serialization for all five
prescription lifecycle events. Proposed fixtures are in
`src/test/resources/contracts/care-finance-v1/`; they are not live producer/consumer acceptance.
The V0 publisher and committed outbox bytes are untouched. V1 create/dispense remains closed.

V16 adds consumer-local prescription clearance grants, target fences and event claims. Exact
PRESCRIPTION purpose, V1 outpatient target, patient and episode are required. A grant arriving before
its prescription remains `PENDING`; a matching late prescription is rechecked before verification.
The grant only authorizes, never auto-dispenses or creates a `PAYMENT_RECEIPT`. Grants are immutable;
duplicate events/new-event-ID same grant have no repeated effect, changed snapshots are conflicts.
The transaction-only authorization primitive checks the clock after its lock waits, including exact
source nanosecond expiry. An internal held-event outpatient transaction now invokes it; no public
API, legacy workflow or Rabbit listener invokes this transaction.

The legacy dispense executor now rejects V1 even if a V0 receipt exists. Authorization denial is
not recorded as stock failure/compensation. V0 reservation TTL is rechecked after **all** stock locks
are acquired. Billing fixture approval, grant-time/revocation policy and cross-owner acceptance
remain activation gates. PostgreSQL runtime evidence is recorded in the current Huy plan.

V17 adds historical drug-name snapshots and exact terminal business times (`lifecycle_at_iso`),
independent of the JPA audit timestamp. New V0 lines capture the server catalogue name without
changing their wire shape; old names/times are not backfilled. A pure V1 lifecycle factory and
transaction-required capture hook read locked prescription/slip evidence; no public or legacy
workflow invokes this hook. The internal V1 outpatient executor invokes it in the stock transaction.
Its writer stores immutable V1 bytes in the existing outbox with
`delivery_enabled=false`: one creation and one terminal outcome per prescription. A DB constraint,
dispatcher claim filter and lease completion guard prevent accidental delivery; admin replay does
not enable the row. Held critical predecessors still block causal successors. Live activation
requires a reviewed migration/cutover, not a flag toggle. Legacy cancel/failure/compensation reject
V1; the V0 expiry job skips V1 without release or events. Internal V1 commands below are separate.

`DispenseCarePrescriptionUseCase` is internal only. It requires a staff/account command and exact
V1 outpatient context, locks Rx/slip and every stock/reservation in stable drug order, then checks
clearance. All reservation TTLs, drug expiry and grant expiry are rechecked after authorization
lock/write waits, before any stock mutation. Stock, reservation, explicit Rx/slip business time,
held filled bytes and existing `stock.low` outbox commit together. Missing creation/invalid snapshots
fail the whole transaction; no V0 filled event, payment receipt or refund is produced. Retry returns
only matching persisted slip/Rx AND held filled proof, never creates another event or decrements stock.
Admission eligibility/live activation remain open; the internal outpatient creation/terminal paths
now exist and real PostgreSQL rollback/concurrency/nanosecond evidence is in the plan.

V18 adds an internal create-command receipt/fence. `CreateCarePrescriptionUseCase` checks DOCTOR
self/ADMIN delegation before replay, rejects unsupported admission eligibility, locks drugs in UUID
order, checks expiry/available stock with a fresh clock and snapshots server price/name. Rx, complete
reservations, pending slip, held creation and receipt commit together. Same command/intent retries
return the original Rx; changed actor/intent conflicts; failed writes roll back the fence too. This
port is not connected to the public API. Patient/episode authority approval still gates activation.

`CancelCarePrescriptionUseCase` and `ExpireCarePrescriptionUseCase` terminate whole reserved orders
with matching Rx/slip/held proof; cancellation is restricted to the prescribing doctor or ADMIN,
expiry uses time after lock waits. Retry does not release twice or change the original exact time.
`RecordCareStockFailureUseCase` runs in REQUIRES_NEW after dispense rollback and rechecks locked
current business stock/authorization. Missing grant/structure or infrastructure error never becomes
FAILED; healthy stock or a competing terminal winner is not overwritten. Definitive shortage/expiry
releases reservations and holds one failure fact, without stock decrement, PaymentReceipt or refund.
These internal paths do not enable a listener, scheduler, public route or V1 outbox delivery.

Reservation TTL defaults to 24 hours and is configurable with
`MEDIFLOW_PHARMACY_RESERVATION_TTL` (ISO-8601 duration, for example `PT24H`).

## Tests

```bash
mvn -pl backend/pharmacy-service test        # unit (domain + application, no Spring)
mvn -pl backend/pharmacy-service verify      # + integration (Testcontainers, needs Docker)
```

For the installed Docker Desktop engine, the verified command is
`mvn -q -f backend/pharmacy-service/pom.xml -Dapi.version=1.44 test`.
