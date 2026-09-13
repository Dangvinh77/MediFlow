# pharmacy-service

Drugs, prescriptions, dispensing and stock. **Saga participant** in prescribe → pay → dispense.

Reference: [`docs/ai/services/pharmacy.md`](../../docs/ai/services/pharmacy.md) · design doc `docs/eproject_general_plan/backend-spec/05-pharmacy.md`.

- **Port:** 8085 · **Base path:** `/api/v1/pharmacy` · **DB:** `mediflow_pharmacy` (PostgreSQL)
- **Owns tables:** `DRUG`, `PRESCRIPTION`, `PRESCRIPTION_LINE`, `DISPENSE_SLIP`, `PROCESSED_EVENT`, `STOCK_RESERVATION`
- **Architecture:** clean architecture (hexagonal) per [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`; driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports. Dependencies inward only.

## Status

**In progress.** Domain, application ports/services, persistence adapters, stock reservations,
expiry scheduler, HTTP/JWT security and RabbitMQ consumer/topology are implemented. Transactional
transactional outbox is enabled for durable event delivery; real PostgreSQL/RabbitMQ integration gates remain before release.

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

- **Publish:** `prescription.created`, `prescription.filled`, `prescription.dispense.failed`, `stock.low`
- **Subscribe:** `payment.completed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

Payment events are claimed atomically by `eventId`; duplicate deliveries are ignored. Listener
retries are bounded (default 3 attempts with exponential backoff) and poison messages are routed
to `pharmacy.dlq`. Configure with `MEDIFLOW_PHARMACY_RABBIT_RETRY_*` environment variables.

Reservation TTL defaults to 24 hours and is configurable with
`MEDIFLOW_PHARMACY_RESERVATION_TTL` (ISO-8601 duration, for example `PT24H`).

## Tests

```bash
mvn -pl backend/pharmacy-service test        # unit (domain + application, no Spring)
mvn -pl backend/pharmacy-service verify      # + integration (Testcontainers, needs Docker)
```
