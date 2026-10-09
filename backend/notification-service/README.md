# notification-service

Private notification history (`NOTIFICATION`). Mostly event-driven.

Reference: [`service rules`](../../docs/ai/services/notification.md) and the canonical [projection contract](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

- **Port:** 8087 · **Base path:** `/api/v1/notifications` · **DB:** `mediflow_notification` (PostgreSQL)
- **Owns tables:** `NOTIFICATION`, `PROCESSED_EVENT`, held notification outbox and Surgery reminder evidence.
- **Architecture:** clean architecture per [blueprint](../../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`, dependencies inward only.

## Status

Compatibility consumers, authenticated history APIs and gated V1 Billing receipts are implemented.
Surgery's ready/invalidation/cancel/completed intake is separately opt-in and stores exact-case
suppression evidence. READY is provisional only; completion suppresses reminders without exposing
clinical results. Cancellation uses a generic private IN_APP message. No email/SMS address is inferred.
Both `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED` and `MEDIFLOW_NOTIFICATION_SURGERY_CONSUMER_ENABLED`
must be true to create the dedicated durable queue/DLQ; defaults are false. Notification sent events
remain HELD. Billing's V1 Surgery payment-request facts have a separate opt-in private notice;
`MEDIFLOW_NOTIFICATION_SURGERY_PAYMENT_REQUEST_CONSUMER_ENABLED=false` and care-v1 must both be
enabled. Requests are not receipts or booked surgery. Admission/top-up/settlement projections and
reviewed publication remain separate tasks.

Completed-refund follow-up: care-v1 AND `MEDIFLOW_NOTIFICATION_REFUND_CONSUMER_ENABLED` (false)
gate a separate refund queue/DLQ. Actual Billing fixtures produce one private `PAYMENT_REFUNDED`
history and held sent event per immutable refund source, with atomic rollback/dedupe/conflict and
retained-byte recovery. It does not imply final settlement or surgery authorization and does not send
email/SMS. Latest complete suite: **171/171**, zero fail/error/skip, actual PG/RabbitMQ.
[Canonical contract and evidence](../../docs/superpowers/plans/2026-10-08-billing-refund-closure.md).

Package layout:

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   messaging/consumer
infrastructure/persistence   infrastructure/messaging   infrastructure/client
infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_notification;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/notification-service -am spring-boot:run
```

Swagger UI: http://localhost:8087/swagger-ui.html

## Events

- **Publish:** `notification.sent`
- **Subscribe:** `patient.created`, `appointment.created`, `lab.result.created`, `prescription.filled`, `payment.completed`, `payment.failed`
- **Opt-in Surgery:** `surgery.ready`, `surgery.readiness.invalidated`, `surgery.cancelled`, `surgery.completed`.
- **Opt-in completed refunds:** `payment.refunded`, with independent care-v1 + refund-consumer gates.

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/notification-service test        # unit (domain + application, no Spring)
mvn -pl backend/notification-service verify      # + integration (Testcontainers, needs Docker)
```
