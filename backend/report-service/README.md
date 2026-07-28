# report-service

Aggregated analytics. A **read model** built purely from events — never queries another service's DB.

Reference: [`docs/ai/services/report.md`](../docs/ai/services/report.md) · design doc `docs/eproject_general_plan/report-service.html`.

- **Port:** 8088 · **Base path:** `/api/v1/reports` · **DB:** `mediflow_report` (PostgreSQL)
- **Owns tables:** `BAO_CAO_KHAM`, `BAO_CAO_DOANH_THU`
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`, dependencies inward only.

## Status

**Skeleton only.** Module, dependencies, config and the mandated package layout are in place — there is **no business code yet**. Build it out against the *Definition of Done* in the blueprint, and the bounded context / rules in the service doc above.

Package layout (already created, each folder holds a `.gitkeep` until you fill it):

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
infrastructure/web    infrastructure/persistence   infrastructure/messaging   infrastructure/client
infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_report;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/report-service -am spring-boot:run
```

Swagger UI: http://localhost:8088/swagger-ui.html

## Events

- **Publish:** — (read model, publishes nothing)
- **Subscribe:** `medicalrecord.created`, `lab.result.created`, `payment.completed`, `prescription.filled`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/report-service test        # unit (domain + application, no Spring)
mvn -pl backend/report-service verify      # + integration (Testcontainers, needs Docker)
```
