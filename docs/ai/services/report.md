# Service: report

**Source of truth:** `docs/eproject_general_plan/report-service.html`
**Module:** `backend/report-service/` · **Base path:** `/api/v1/reports` · **DB tables:** `BAO_CAO_KHAM`, `BAO_CAO_DOANH_THU`, ...

## Bounded context
Owns: aggregate/summary tables (a **read model**). Does NOT own detailed business data — it builds aggregates purely from events (never queries other services' DBs).

## Data — `BAO_CAO_KHAM`
`ma_bao_cao` UUID PK · `ngay` DATE · `ma_khoa` UUID (ref → organization `KHOA`, nullable = hospital-wide) · `so_luong_kham` INT · `so_luong_xn` INT · `doanh_thu` DECIMAL(15,2) · `created_at`. (Plus additional detail tables as needed.)

Aggregates are keyed by **`(ngay, ma_khoa)`**, so the same event stream yields both per-department and
hospital-wide figures. `ma_khoa` comes from the `maKhoa` field on the incoming events — this service
never calls another service to resolve it.

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/reports/daily?date` | ADMIN, MANAGER |
| GET | `/api/v1/reports/monthly?month&year` | ADMIN, MANAGER |
| GET | `/api/v1/reports/top-medicines?period` | ADMIN, MANAGER |

## Events (subscribe-only, updates aggregates)
- `medicalrecord.created` → +1 exam count for the day.
- `lab.result.created` → +1 lab count.
- `payment.completed` → add to revenue.
- `prescription.filled` → update top-medicines stats.

## Business rules
1. Reports computed & stored per day (may run as an end-of-day batch).
2. Aggregate data is reference-only; it never affects core business flow.

## Flow
Consume event → find/create today's report row → update counters/revenue → save. Idempotent (dedupe on eventId so a redelivery doesn't double-count).
