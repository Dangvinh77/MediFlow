# Service: notification

**Source of truth:** `docs/eproject_general_plan/notification-service.html`
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
- **Subscribe:** `patient.created` (welcome), `appointment.created` (reminder), `lab.result.created` (results), `prescription.filled` (drug ready), `payment.completed` (confirm), `payment.failed` (payment error).

## Business rules
1. Email must be valid to send email.
2. SMS only if phone valid (10–11 digits).
3. Persist notification history (PENDING → SENT/FAILED).

## Flow
Consume event → create `NOTIFICATION` (PENDING) → send email/SMS (integration or mock) → update status → optionally publish `notification.sent`. Consumers idempotent through `PROCESSED_EVENT`.
