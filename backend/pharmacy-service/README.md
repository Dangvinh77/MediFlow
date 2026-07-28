# pharmacy-service

Drugs, prescriptions, dispensing and stock. **Saga participant** in prescribe → dispense → pay.

Reference: [`docs/ai/services/pharmacy.md`](../docs/ai/services/pharmacy.md) · design doc `docs/eproject_general_plan/pharmacy-service.html`.

- **Port:** 8085 · **Base path:** `/api/v1/pharmacy` · **DB:** `mediflow_pharmacy` (PostgreSQL)
- **Owns tables:** `THUOC`, `BAN_KE_CP`, `CHI_TIET_BAN_KE`, `PHIEU_XUAT`
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

1. Create the database once: `CREATE DATABASE mediflow_pharmacy;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/pharmacy-service -am spring-boot:run
```

Swagger UI: http://localhost:8085/swagger-ui.html

## Events

- **Publish:** `prescription.created`, `prescription.filled`, `stock.low`
- **Subscribe:** `payment.completed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/pharmacy-service test        # unit (domain + application, no Spring)
mvn -pl backend/pharmacy-service verify      # + integration (Testcontainers, needs Docker)
```
