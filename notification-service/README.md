# notification-service

Email/SMS/in-app notification history (`THONG_BAO`). Mostly event-driven.

Reference: [`docs/ai/services/notification.md`](../docs/ai/services/notification.md) · design doc `EProject/notification-service.html`.

- **Port:** 8087 · **Base path:** `/api/v1/notifications` · **DB:** `mediflow_notification` (PostgreSQL)
- **Owns tables:** `THONG_BAO`
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

1. Create the database once: `CREATE DATABASE mediflow_notification;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl notification-service -am spring-boot:run
```

Swagger UI: http://localhost:8087/swagger-ui.html

## Events

- **Publish:** `notification.sent`
- **Subscribe:** `patient.created`, `appointment.created`, `lab.result.created`, `prescription.filled`, `payment.completed`, `payment.failed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

## Tests

```bash
mvn -pl notification-service test        # unit (domain + application, no Spring)
mvn -pl notification-service verify      # + integration (Testcontainers, needs Docker)
```
