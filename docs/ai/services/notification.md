# Service: notification

**Source of truth:** `docs/eproject_general_plan/notification-service.html`
**Module:** `backend/notification-service/` · **Base path:** `/api/v1/notifications` · **DB table:** `THONG_BAO`

## Bounded context
Owns: notification history (`THONG_BAO`). Mostly event-driven. Does NOT own core business data.

## Data — `THONG_BAO`
`ma_thong_bao` UUID PK · `ma_benh_nhan` UUID · `tieu_de` VARCHAR(255) · `noi_dung` TEXT · `loai` ENUM('EMAIL','SMS','IN_APP') · `trang_thai` ENUM('PENDING','SENT','FAILED') · `ngay_tao` TIMESTAMP · `ngay_gui` TIMESTAMP.

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
Consume event → create `THONG_BAO` (PENDING) → send email/SMS (integration or mock) → update status → optionally publish `notification.sent`. Consumers idempotent.
