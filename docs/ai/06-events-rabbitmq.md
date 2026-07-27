# 06 — Events & RabbitMQ

## When to use an event

Publish an event whenever a service **changes its own state and other contexts may care**. Do **not** use events to fetch data you need to finish the current request (that's a REST call — see `01`).

## Topology

- **One topic exchange** for domain events: `mediflow.events` (durable).
- **Routing key = event name in dot.case**, e.g. `patient.created`, `billing.payment.completed`, `pharmacy.stock.low`.
- Each consuming service declares its **own durable queue** bound to the routing keys it cares about, e.g. `notification.q` bound to `patient.created`, `appointment.created`, `lab.result.created`, ...
- Dead-letter: each queue has a DLX (`mediflow.events.dlx`) + `*.dlq` for poison messages.

## Event payloads

- One `record` per event in `infrastructure/messaging/payload/`, named `<Thing><PastTenseVerb>Event`.
- Every event carries envelope fields plus the domain fields from the design doc:
  ```java
  public record PatientCreatedEvent(
      UUID eventId,          // unique -> consumers dedupe on this
      Instant occurredAt,
      String correlationId,
      UUID patientId,
      String hoTen,
      String email,
      String sdt
  ) {}
  ```
- Serialize as JSON (`Jackson2JsonMessageConverter`, configured in `common`).

## Publishing

- Publish **after** the DB transaction commits (avoid publishing an event for a rollback). Use `@TransactionalEventListener(phase = AFTER_COMMIT)` or an outbox pattern for critical flows (billing/pharmacy saga).
- The application layer depends on an **out-port** (`application/port/out/XxxEventPublisherPort`); the adapter that talks to RabbitMQ lives in `infrastructure/messaging/`. Application code never touches `RabbitTemplate` — that is the whole point of the port.

## Consuming

- `@RabbitListener` handlers live in `infrastructure/messaging/consumer/`. They are driving adapters: parse the message, call an in-port, ack. No business logic.
- **Idempotent always:** dedupe on `eventId` (store processed ids, or make the effect naturally idempotent). A redelivered message must not double-apply.
- Handlers are thin: parse → call a service method → ack. Business logic stays in the service layer.
- On unrecoverable error, let it dead-letter; do not silently swallow.

## Canonical event catalog (from the design docs)

| Event (routing key) | Publisher | Subscribers |
|---------------------|-----------|-------------|
| `department.created` | organization | — |
| `staff.created` | organization | — |
| `staff.department.changed` | organization | report |
| `patient.created` | patient | notification |
| `patient.updated` | patient | — |
| `appointment.created` | clinical | notification |
| `appointment.status.changed` | clinical | billing |
| `medicalrecord.created` | clinical | lab, billing, report |
| `diagnosis.added` | clinical | — |
| `lab.request.created` | lab | — |
| `lab.result.created` | lab | clinical, billing, notification, report |
| `prescription.created` | pharmacy | billing (saga) |
| `prescription.filled` | pharmacy | clinical, notification, report |
| `stock.low` | pharmacy | (ops/notification) |
| `invoice.created` | billing | — |
| `payment.completed` | billing | pharmacy, lab, patient(log), notification, report |
| `payment.failed` | billing | notification (+ saga compensation) |
| `notification.sent` | notification | — |

**Operational events carry `maKhoa`.** `appointment.created`, `medicalrecord.created`,
`lab.result.created`, `prescription.created` and the billing events all include the originating
department, so `report-service` can aggregate by department without ever calling another service.

> **Never publish an event to update your own database.** Setting an appointment to `DA_DEN` when its
> record is created happens in a local transaction inside `clinical-service`, not over the bus. If you
> reach for an event to change something you already own, the service boundary is cut in the wrong place.

> Keep this table in sync with each `services/*.md` publish/subscribe section. If they disagree, the per-service design doc (`EProject/*.html`) is authoritative.

## Saga (billing orchestrates prescribe → dispense → pay)

- Forward: `prescription.created` → billing creates invoice → on pay, `payment.completed` → pharmacy dispenses (`CHO_XUAT`→`DA_XUAT`).
- Compensate: dispense failure (out of stock / expired) → publish failure → `payment.failed` → notify + reverse. See `services/billing.md` and `services/pharmacy.md`.
