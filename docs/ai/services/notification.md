# Service: notification

**Source of truth:** `docs/eproject_general_plan/notification-service.html` plus the approved
[`care-finance redesign`](../../architecture/mediflow-care-finance-redesign.html) for new templates.
**Module:** `backend/notification-service/` · **Base path:** `/api/v1/notifications` · **DB tables:** `NOTIFICATION`, `PROCESSED_EVENT`

## Bounded context
Owns: notification history (`NOTIFICATION`) and consumer idempotency records (`PROCESSED_EVENT`). Mostly event-driven. Does NOT own core business data.

## Data

### `NOTIFICATION`

`notification_id` UUID PK · `patient_id` UUID · `title` VARCHAR(255) · `content` TEXT · `channel` ENUM('EMAIL','SMS','IN_APP') · `recipient_address` VARCHAR(150) · `status` ENUM('PENDING','SENT','FAILED') · `failure_reason` VARCHAR(255) · `retry_count` INT · `created_at` TIMESTAMPTZ · `sent_at` TIMESTAMPTZ.

### `PROCESSED_EVENT`

`event_id` UUID PK · `routing_key` VARCHAR(100) · `processed_at` TIMESTAMPTZ.

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/notifications/patient/{patientId}` | ADMIN, NURSE, PATIENT |
| GET | `/api/v1/notifications/{id}` | ADMIN, PATIENT |
| POST | `/api/v1/notifications/send` | ADMIN, SYSTEM |

`PATIENT` may only read its own notifications (ownership check).

## Events
- **Publish:** `notification.sent` `{notificationId, patientId, type, status}`.
- **Subscribe current:** `patient.created` (welcome), `appointment.created` (reminder),
  `lab.result.created` (results), `prescription.filled` (drug ready), `payment.completed` (receipt),
  `payment.failed` (payment error), flat V0 `invoice.created` (private unpaid request).
- **Subscribe target:** `invoice.created`/payment request, `payment.refunded`,
  `appointment.status.changed`, `admission.deposit.requested`, `deposit.topup.required`, `admission.started`, `surgery.ready`,
  `surgery.cancelled`, `settlement.completed`, `admission.closed`.

## Business rules
1. Email must be valid to send email.
2. SMS only if phone valid (10–11 digits).
3. Persist notification history (PENDING → SENT/FAILED).

## Flow
Consume event → create `NOTIFICATION` (PENDING) → send email/SMS (integration or mock) → update status → optionally publish `notification.sent`. Consumers idempotent through `PROCESSED_EVENT`.

## Care-finance integration gate

### CURRENT outpatient invoice compatibility (2026-10-08)

The current queue now binds flat `invoice.created` because Billing's confirmed mandatory outbox
cannot deliver an invoice predecessor without a real subscriber. The template creates a private
IN_APP payment **request**, never a paid receipt, booking or care permission. It uses only the
explicit invoice/patient IDs and amount; no contact lookup or clinical detail. Required IDs/time/
correlation and a non-negative amount are validated before effects. Billing's actual serializer
and Notification read the same `billing-service/src/test/resources/contracts/invoice.created.json`.
Versioned envelopes never downgrade: they retain the existing gated V1 handling/rejection policy.
The separate Surgery V1 queue remains off by default; deployment/cutover must retire or review
the compatibility binding rather than treating this V0 subscriber as V1 approval. Existing
quarantined outbox rows require reviewed recovery; this change does not auto-mark them published.

### Current V1 classified receipt slice (2026-10-05)

The existing single `notification.q` reader detects versioned envelopes and routes them to the
strict Billing receipt handler instead of the flat legacy DTO. There is no competing listener or
new unproven binding. `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED=false` by default; disabled/invalid
V1 intake rejects rather than downgrades. Database outages remain retriable, identity conflicts poison.

V2 migration adds source/template/correlation/privacy metadata and a payload hash to the existing
processed-event table. Claim, private IN_APP history delivery and immutable held `notification.sent`
outbox commit atomically. Service and deposit receipts have different templates; partial payments do
not promise a paid-in-full invoice. IN_APP delivery means the committed authenticated history row,
not an email/SMS provider call. Existing signed patientId ownership checks remain intact.
Producer fixture tests read Billing files directly. External delivery workers, refund/top-up/settlement
remain unfinished; this is not completion of Notification V2.

### Opt-in Surgery reminder intake (2026-10-08)

Both care-v1 and surgery-consumer flags are required (false defaults) for a separate durable
`notification.surgery-v1.q`/DLQ. The decoder reads actual Surgery V1 fixtures for READY, readiness
invalidation, cancellation and completion in both episode contexts. V3 adds exact case identity,
source fingerprints and pinned snapshot tombstones. Claim, source state, private IN_APP history and
held sent event are transactional; transient failures retry three times before retained-byte DLQ.
Same source under another event ID never re-sends. Late READY after invalidation or a terminal case
records suppressed FAILED history without a sent event. Existing SENT history is preserved. No
clinical-result template, external contact lookup or refund/settlement meaning is inferred.
Full clean Docker verification: **120 tests / 17 reports, zero failures/errors/skips** (2026-10-08).

### Held Surgery payment-request notice (2026-10-08)

**Completed-refund follow-up:** the separately two-gated reader accepts actual Billing refund
fixtures. Semantic refund source + delivery claims, private PAYMENT_REFUNDED history and held
sent fact are atomic; changed source conflicts and storage errors roll back. No free-text reason,
external address, settlement or clinical permission is inferred. Full PG/Rabbit **171/171**, 21
reports, no failure/error/skip; refund notice is no longer missing. Admission/top-up/settlement,
external workers and release stay open. [Canonical limits](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#completed-refund-fact--v1-paymentrefunded-2026-10-08).

An independent care-v1 + surgery-payment-request-consumer gate accepts Billing's exact nested
`invoice.created` V1 Surgery request fixtures. V4 adds semantic source receipts: request identity
under another event ID cannot send again, and changed amount/context conflicts. Private IN_APP
template explicitly states that it is neither a paid receipt nor confirmation of a surgery booking.
There is no email/SMS/contact inference. Claim/source/history/held sent event roll back together.
Final full clean Notification verification including both Surgery consumers: **145/145**, 19 reports,
zero failure/error/skip, PostgreSQL/RabbitMQ, 2026-10-08 10:34:36–10:36:42 Bangkok. This supersedes
the earlier 120-test slice above; the counts are not additive. Remaining admission/refund/top-up/
settlement templates and reviewed event release are still open.

- Read [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
  before changing event bindings/templates and
  [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md) before
  changing patient JWT/contact lookup behavior.
- Notification is not a workflow authority. Delivery failure never rolls back a committed care or
  financial transaction.
- Templates use explicit event fields or permitted Patient lookup; they never query another DB.
- Diagnosis/results are not placed in insecure SMS/email content. Missing recipient data creates a
  failed/skipped history record with reason.
- Deposit, payment and refund messages must use different templates; a deposit receipt must not say
  that final treatment cost is settled.
