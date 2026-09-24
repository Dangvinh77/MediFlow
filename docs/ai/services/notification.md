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
  `payment.failed` (payment error).
- **Subscribe target:** `invoice.created`/payment request, `payment.refunded`,
  `admission.deposit.requested`, `deposit.topup.required`, `admission.started`, `surgery.ready`,
  `surgery.cancelled`, `settlement.completed`, `admission.closed`.

## Business rules
1. Email must be valid to send email.
2. SMS only if phone valid (10–11 digits).
3. Persist notification history (PENDING → SENT/FAILED).

## Flow
Consume event → create `NOTIFICATION` (PENDING) → send email/SMS (integration or mock) → update status → optionally publish `notification.sent`. Consumers idempotent through `PROCESSED_EVENT`.

## Care-finance integration gate

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
