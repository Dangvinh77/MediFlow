# 06 — Events & RabbitMQ

## When to use an event

Publish an event whenever a service **changes its own state and other contexts may care**. Do **not** use events to fetch data you need to finish the current request (that's a REST call — see `01`).

## Topology

- **One topic exchange** for domain events: `mediflow.events` (durable).
- **Routing key = event name in dot.case**, e.g. `patient.created`, `billing.payment.completed`, `pharmacy.stock.low`.
- Each consuming service declares its **own durable queue** bound to the routing keys it cares about, e.g. `notification.q` bound to `patient.created`, `appointment.created`, `lab.result.created`, ...
- Dead-letter: each queue has a DLX (`mediflow.events.dlx`) + `*.dlq` for poison messages.

## Event payloads

- One `record` per event, named `<Thing><PastTenseVerb>Event`. Published records used by application ports live in `application/event/`; adapter-only payload placement follows [the blueprint](04-microservice-blueprint.md). Application must never import an infrastructure payload.
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
- New care-finance contracts also carry `eventType`, integer `version`, and `producer` in the wire
  envelope. Existing unversioned event records migrate additively; do not create a second routing
  key solely to encode the version. Full rules: [`16-care-finance-integration-contracts.md`](16-care-finance-integration-contracts.md).
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

| Event (routing key) | Publisher | Subscribers | Status / contract note |
|---------------------|-----------|-------------|------------------------|
| `department.created` | organization | — | current |
| `staff.created` | organization | — | current |
| `staff.department.changed` | organization | report | current when staffing projection exists |
| `patient.created` | patient | notification | current |
| `patient.updated` | patient | — | current |
| `appointment.created` | clinical | notification | current |
| `appointment.status.changed` | clinical | billing, notification | compatibility path for exam fee; arrival/cancel notice projection |
| `medicalrecord.created` | clinical | billing, report | current; Lab must not infer an order from diagnosis text |
| `medicalrecord.completed` | clinical | report, notification | planned |
| `diagnosis.added` | clinical | — | current |
| `admission.requested` | clinical | inpatient, billing | planned; explicit admission referral |
| `lab.request.created` | lab | billing | target charge trigger |
| `lab.result.created` | lab | clinical, notification, report | current; Billing result-time fee is deprecated |
| `prescription.created` | pharmacy | billing | current; adds `careContext`/`admissionId` for inpatient use |
| `prescription.filled` | pharmacy | clinical, notification, report | current |
| `prescription.dispense.failed` | pharmacy | billing | current compensation fact |
| `stock.low` | pharmacy | notification/ops | current |
| `admission.deposit.requested` | inpatient | billing, notification | planned |
| `admission.started` | inpatient | billing, notification, report | planned |
| `discharge.medically.approved` | inpatient | billing | planned |
| `admission.closed` | inpatient | notification, report | planned |
| `surgery.requested` | clinical/inpatient | surgery, billing | planned |
| `surgery.ready` | surgery | inpatient, notification | planned |
| `surgery.completed` | surgery | inpatient, billing, report | planned |
| `surgery.cancelled` | surgery | inpatient, billing, notification, report | planned |
| `invoice.created` | billing | notification | current compatibility event/payment request notice |
| `payment.completed` | billing | pharmacy, lab, patient(log), notification, report | financial fact; do not unlock unrelated targets |
| `financial.clearance.granted` | billing | clinical, lab, pharmacy, inpatient, surgery | planned purpose/target-specific authorization |
| `deposit.topup.required` | billing | inpatient, notification | planned |
| `payment.refunded` | billing | notification, report | planned immutable refund fact |
| `settlement.completed` | billing | inpatient, notification, report | planned final reconciliation |
| `payment.failed` | billing | notification (+ current pharmacy saga compensation) | current compatibility event |
| `notification.sent` | notification | — | current |

**Operational events carry `departmentId`.** `appointment.created`, `medicalrecord.created`,
`lab.result.created`, `prescription.created` and the billing events all include the originating
department, so `report-service` can aggregate by department without ever calling another service.

> **Never publish an event to update your own database.** Setting an appointment to `DA_DEN` when its
> record is created happens in a local transaction inside `clinical-service`, not over the bus. If you
> reach for an event to change something you already own, the service boundary is cut in the wrong place.

> Keep this table in sync with each `services/*.md` publish/subscribe section. For care-finance
> flows, the approved architecture HTML and canonical contracts in `docs/handoffs/care-finance/`
> are authoritative. `planned` means the contract is designed, not live.

## Compatibility saga (billing orchestrates prescribe → pay → dispense)

- Forward: `prescription.created` → billing creates invoice → on pay, `payment.completed` → pharmacy dispenses (`CHO_XUAT`→`DA_XUAT`).
- Compensate: dispense failure (out of stock / expired) → publish failure → `payment.failed` → notify + reverse. See `services/billing.md` and `services/pharmacy.md`.
