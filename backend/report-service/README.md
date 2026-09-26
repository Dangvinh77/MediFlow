# report-service

Aggregated analytics. A **read model** built purely from events — never queries another service's DB.

Reference: [`docs/ai/services/report.md`](../../docs/ai/services/report.md) · design doc
[`docs/eproject_general_plan/report-service.html`](../../docs/eproject_general_plan/report-service.html).

- **Port:** 8088 · **Base path:** `/api/v1/reports` · **DB:** `mediflow_report` (PostgreSQL)
- **Owns tables:** `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`,
  `PROCESSED_EVENT`, `PAYMENT_CONTRIBUTION`
- **Architecture:** clean architecture (hexagonal) per
  [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`;
  driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports.
  Dependencies inward only.

## Status

**T01–T11 và review hardening trong report-service đã hoàn tất.** Domain rules, persistence
mappings/adapters, payment compensation, aggregate updates, read queries, RabbitMQ consumer/DLQ
topology, secured HTTP endpoints và bộ cross-layer Testcontainers đều đã được kiểm chứng. Runtime
gate với Docker Desktop xanh. Gateway phát typed token; Report hiện chỉ chấp nhận JWT có
`type=access`, với regression coverage trong `JwtAuthFilterTest`.

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
5. Swagger is denied by default; set `MEDIFLOW_REPORT_SWAGGER_PERMIT=true` only for local/development.

```bash
mvn -pl backend/report-service -am spring-boot:run
```

Swagger UI: http://localhost:8088/swagger-ui.html

## Events

- **Publish:** — (read model, publishes nothing)
- **Subscribe (T08):** `medicalrecord.created`, `lab.result.created`, `payment.completed`,
  `payment.failed`, `prescription.filled` on durable `report.q`; poison deliveries are retried a
  bounded number of times and routed to `report.dlq`.

HTTP endpoints (T09) are available at `/api/v1/reports` for `ADMIN` and `MANAGER` roles. JWT is
re-verified locally, and all responses use the common `ApiResponse` envelope.

Topic exchange `mediflow.events`; see
[`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent
(dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/report-service test        # unit (domain + application, no Spring)
mvn -pl backend/report-service verify      # + integration (Testcontainers, needs Docker)
```

Nếu máy có cấu hình Testcontainers cũ, dùng `-Dapi.version=1.40` để ép Docker API tương thích:

```bash
mvn -q -pl backend/report-service -am -Dapi.version=1.40 verify
```

Quality gate đầy đủ từ repository root:

```bash
mvn -q -pl backend/report-service -am test
mvn -q -pl backend/report-service -am verify
mvn -q -pl backend/report-service -am -DskipTests javadoc:javadoc
mvn -q -pl backend/report-service -am dependency:analyze
```
