# lab-service

Lab tests and results (`LAB_TEST`, `LAB_RESULT`).

Reference: [`docs/ai/services/lab.md`](../../docs/ai/services/lab.md) · design doc [`docs/eproject_general_plan/lab-service.html`](../../docs/eproject_general_plan/lab-service.html).

- **Port:** 8084 · **Base path:** `/api/v1/lab` · **DB:** `mediflow_lab` (PostgreSQL)
- **Owns tables:** `LAB_TEST`, `LAB_RESULT`
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`, dependencies inward only.

## Status

The domain and application-contract foundation is in place: `LabTest` owns its `LabResult` values,
validated request/response records and a MapStruct DTO mapper define the boundary, and published
events carry the standard envelope. HTTP, persistence, application services and RabbitMQ adapters
remain follow-up work.

Cross-service field choices and unresolved producer gaps are recorded in the
[clinical/lab contract handoff](../../docs/eproject_general_plan/backend-spec/clinical-lab-contract-handoff.md).

The remaining adapter packages are reserved for the follow-up service implementation:

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
infrastructure/web    infrastructure/persistence   infrastructure/messaging   infrastructure/client
infrastructure/security   infrastructure/config
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
mvn -pl backend/lab-service test        # unit (domain + application, no Spring)
mvn -pl backend/lab-service verify      # current unit/contract checks; adapters/integration are follow-up work
```
