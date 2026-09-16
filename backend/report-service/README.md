# report-service

Aggregated analytics. A **read model** built purely from events — never queries another service's DB.

Reference: [`docs/ai/services/report.md`](../../docs/ai/services/report.md) · design doc
[`docs/eproject_general_plan/report-service.html`](../../docs/eproject_general_plan/report-service.html).

- **Port:** 8088 · **Base path:** `/api/v1/reports` · **DB:** `mediflow_report` (PostgreSQL)
- **Owns tables:** `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`
- **Architecture:** clean architecture (hexagonal) per
  [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`;
  driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports.
  Dependencies inward only.

## Status

**T01–T07 implemented.** Domain rules, persistence mappings/adapters, payment compensation,
aggregate updates and read queries are in place. T04/T06 still require a successful PostgreSQL
concurrency run; HTTP/security and RabbitMQ consumer work remain in T08–T09.

Package layout (already created, each folder holds a `.gitkeep` until you fill it):

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   (driving HTTP: controllers + GlobalExceptionHandler)
messaging/consumer    (driving events: @RabbitListener)
infrastructure/persistence   infrastructure/messaging   infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_report;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).
4. Provide `MEDIFLOW_JWT_SECRET` and optionally `MEDIFLOW_REPORT_ZONE_ID` in the environment.

```bash
mvn -pl backend/report-service -am spring-boot:run
```

Swagger UI: http://localhost:8088/swagger-ui.html

## Events

- **Publish:** — (read model, publishes nothing)
- **Subscribe (T08):** `medicalrecord.created`, `lab.result.created`, `payment.completed`,
  `payment.failed`, `prescription.filled`

Topic exchange `mediflow.events`; see
[`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent
(dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/report-service test        # unit (domain + application, no Spring)
mvn -pl backend/report-service verify      # + integration (Testcontainers, needs Docker)
```
