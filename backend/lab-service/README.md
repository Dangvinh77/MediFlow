# lab-service

Lab tests and results (`LAB_TEST`, `LAB_RESULT`).

Reference: [`docs/ai/services/lab.md`](../../docs/ai/services/lab.md) · design doc [`docs/eproject_general_plan/lab-service.html`](../../docs/eproject_general_plan/lab-service.html).

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
commit. Inbound consumers remain follow-up work, and payment consumers still require an explicit
test identifier.

Cross-service field choices and unresolved producer gaps are recorded in the
[clinical/lab contract handoff](../../docs/eproject_general_plan/backend-spec/clinical-lab-contract-handoff.md).

The remaining adapter packages are reserved for follow-up integration work:

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
- **Subscribe:** `medicalrecord.created`, `payment.completed`

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
