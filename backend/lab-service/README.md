# lab-service

## Product scope — 2026-10-17

Part of **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Lab owns outpatient test requests, processing and results; laboratory lifecycle and payment authority remain separate.

Canonical [system design](../../docs/plans/01-system-design.md), [contracts](../../docs/plans/03-contracts.md) and [acceptance runbook](../../docs/plans/06-acceptance-runbook.md) define current scope. Existing APIs, feature flags and historical test counts below do not certify full workflow release acceptance.


Lab tests and results (`LAB_TEST`, `LAB_RESULT`).

Reference: [`docs/ai/services/lab.md`](../../docs/ai/services/lab.md) · [business flows](../../docs/plans/02-business-flows.md) · [Vinh handoff/evidence](../../docs/handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md).

- **Port:** 8084 · **Base path:** `/api/v1/lab` · **DB:** `mediflow_lab` (PostgreSQL)
- **Owns tables:** `LAB_TEST`, `LAB_RESULT`
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`, dependencies inward only.

## Status

The domain, application, persistence, HTTP controller, JWT security and error handling layers are
in place:
`LabTest` owns its `LabResult` values, the application service handles creation, results,
lifecycle and explicit payment updates, and published events carry the standard envelope. Flyway
and JPA adapters persist aggregate results, use locked mutation reads and provide an insert-only
processed-event ledger.
The controller exposes the six specified endpoints with validation, role declarations, standard
response envelopes and web-slice coverage.
Bearer tokens are verified locally using the gateway's shared HS256 secret; invalid or missing
tokens and forbidden roles return the common API error envelope.
Domain and validation failures are mapped to the specified HTTP statuses with stable error codes.

The RabbitMQ publisher sends JSON events to the durable shared exchange only after transaction
commit. Inbound consumers use explicit producer IDs; Billing supplies `labTestIds` and Clinical
supplies record identifiers. Cross-service fields and remaining target workflow work are recorded
in the [event catalog](../../docs/ai/06-events-rabbitmq.md), the
[care-finance contracts](../../docs/handoffs/care-finance/README.md), and the
[active handoff registry](../../docs/handoffs/README.md).

Integration adapters are organized under:

```
infrastructure/client   messaging/consumer
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_lab;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/lab-service -am spring-boot:run
```

Swagger UI: http://localhost:8084/swagger-ui.html

## Events

- **Publish:** `lab.request.created`, `lab.result.created`
- **Subscribe:** `medicalrecord.created` (idempotently acknowledged; no test is auto-created because
  the canonical Clinical payload does not contain an explicit lab order), `payment.completed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

`lab.result.created` uses `labId` as its canonical test identifier (sourced from the API's
`testId`) and includes `labType` and `performedDate` for billing, notification and reporting.
The current `payment.completed` producer does not provide a lab/test id, so the future consumer
must not infer one from `invoiceId` or `recordId`; `ReactToClinicalUseCase` and
`ProcessedEventPort` are declared for that later adapter work.

## Tests

```bash
mvn -pl backend/lab-service test        # unit/contract tests + PostgreSQL slices when Docker is available
mvn -pl backend/lab-service verify
```
