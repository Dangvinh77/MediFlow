# clinical-service

## Cách đọc sau chuẩn hóa — 26/10/2026

**Nghiệp vụ hiện hành:** theo bộ quy tắc ngoại trú, không theo trạng thái checkpoint lịch sử.

- [Luồng nghiệp vụ và lịch khám/BHYT mô phỏng](../../docs/plans/02-business-flows.md).
- [Hợp đồng tích hợp hiện hành](../../docs/plans/03-contracts.md); live DTO/controller/fixtures là wire truth.
- [Bàn giao còn cần xử lý hoặc xác minh](../../docs/handoffs/README.md).
- [Kế hoạch thay đổi và owner](../../docs/plans/07-outpatient-workflow-implementation.md).

Nội dung kỹ thuật, flags, lệnh chạy và kết quả bên dưới giữ phạm vi phiên bản/ngày được ghi.
Không dùng test counts cũ để ký revision mới; lịch ca, tái khám và BHYT đích chưa tự có từ tài liệu.

## Product scope — 2026-10-17

Part of **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Clinical owns appointments, reception/check-in, examinations and outpatient medical records.

Canonical [system design](../../docs/plans/01-system-design.md), [contracts](../../docs/plans/03-contracts.md) and [acceptance runbook](../../docs/plans/06-acceptance-runbook.md) define current scope. Existing APIs, feature flags and historical test counts below do not certify full workflow release acceptance.


**Khoa Khám bệnh** — the outpatient examination workflow end to end: booking an appointment, the examination, the medical record and its diagnoses.

Reference: [service rules](../../docs/ai/services/clinical.md) · [business flows](../../docs/plans/02-business-flows.md) · [Vinh handoff/evidence](../../docs/handoffs/history/2026-10-18-clinical-lab-checkpoints.md).

- **Port:** 8082 · **Base paths:** `/api/v1/appointments`, `/api/v1/records` · **DB:** `mediflow_clinical`
- **Owns tables:** `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS`, `ATTACHED_RESULT`
- **Architecture:** clean architecture per [blueprint](../../docs/ai/04-microservice-blueprint.md).

## Why appointments and records are one service

They are **one department's single workflow**: book, examine, record. Separating them would buy
nothing but distributed-systems overhead:

- `MEDICAL_RECORD.appointment_id` points straight at `APPOINTMENT`. In one service that is a real foreign key; split apart it becomes a bare UUID across a network boundary.
- Legacy record creation and `ARRIVED` belong in one local transaction. `ARRIVED` means arrival, not proof that examination occurred.
  The outpatient V1 path separately checks in, obtains exact EXAM authority and starts examination before recording clinical progress.

Kept together, that is a local transaction. The two URL prefixes remain distinct, so the public API
is exactly what `05-api-conventions.md` specifies.

## Status

**Application, persistence, HTTP API, JWT security and error handling implemented.** Rich domain
models enforce
appointment dates/hours and transitions, required references, diagnosis names/ICD codes and the
nonempty diagnosis aggregate. Application services coordinate remote validation,
record/appointment transactions and published events. Flyway and JPA adapters persist both
aggregates with locked mutation reads and database uniqueness for pending daily appointments and
appointment-linked records. `AppointmentController` and `MedicalRecordController` expose all
eleven specified endpoints with validation, role declarations, standard response envelopes and
web-slice coverage.
Bearer tokens are verified locally using the gateway's shared HS256 secret; invalid or missing
tokens and forbidden roles return the common API error envelope.
Domain, validation and upstream failures are mapped to the specified 400/404/409/422/503 statuses
with stable error codes.

Published records live in `application/event`, following billing/pharmacy, to keep publisher ports
independent of infrastructure. Cross-service wire rules live in the
[event catalog](../../docs/ai/06-events-rabbitmq.md), the
[care-finance contracts](../../docs/plans/03-contracts.md), and the
[active handoff registry](../../docs/handoffs/README.md).

The RabbitMQ publisher sends all four domain events as JSON to the durable shared exchange only
after transaction commit. External Lab and Pharmacy references are stored separately with an
idempotent composite key instead of altering clinical text. The `lab.result.created` consumer uses
a durable queue, dead-letter queue and processed-event ledger. Feign clients and the
`prescription.filled` consumer use explicit producer IDs. The Patient lookup consumer implements
the locked service-only `/{id}/exists` envelope contract; live lookup remains blocked until Patient
ships that producer endpoint. The `.http` requests match both controllers.

## Run locally

1. `CREATE DATABASE mediflow_clinical;` (or `docker compose up -d`)
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ on localhost:5672.

```bash
mvn -pl backend/clinical-service -am spring-boot:run
```

Swagger UI: http://localhost:8082/swagger-ui.html

## Events

- **Publish:** `appointment.created`, `appointment.status.changed`, `medicalrecord.created`, `diagnosis.added`
- **Subscribe:** `lab.result.created` (attach result to the record), `prescription.filled` (attach prescription info)

## Cross-service reads (resilient)

Clinical resolves patient existence through `patient-service` and doctor department membership
through `organization-service` using short-timeout Feign clients. Confirmed 404 responses become
domain lookup misses; transport failures, circuit-open responses and invalid envelopes become
`UPSTREAM_UNAVAILABLE` (503). Each internal read uses a short-lived `type=service`, `role=SYSTEM`
JWT and preserves the request correlation ID; a human bearer token is never reused.

- `patient-service` — does this patient exist?
- `organization-service` — does this doctor exist, and which department are they in?

Adapters use Feign with a 2s connect / 3s read timeout and a circuit breaker. A confirmed miss
returns false/empty; an outage throws `UpstreamUnavailableException` (`UPSTREAM_UNAVAILABLE`, HTTP
503 mapping). Returning false from every fallback would incorrectly report a missing patient or
doctor.

## Tests

```bash
mvn -pl backend/clinical-service -am test    # unit/contract tests + PostgreSQL slices when Docker is available
mvn -pl backend/clinical-service -am verify
```

## Care & Finance V2 rollout

The additive Clinical V2 schema and use cases support check-in, purpose-scoped EXAM clearance,
examination start, audited emergency overrides, record completion and admission referrals. The
HTTP endpoints, clearance consumer, Billing authority client and transactional outbox dispatcher
are registered only when `MEDIFLOW_CARE_FINANCE_V2_ENABLED=true`; the default remains disabled.
Department-specific stable exam price codes can be configured under
`mediflow.clinical.exam-price.by-department`. The V1 `financial.clearance.granted` Billing contract
fixture is checked in at `src/test/resources/contracts/financial.clearance.granted.v1.json`, paired
with Billing's EXAM ledger example at
`../billing-service/src/test/resources/contracts/ledger-v1/clearance-exam.json`.

Before a normal examination starts, Clinical calls Billing's
`POST /api/v1/billing/financial-clearances/{clearanceId}/execution-starts` with the appointment as
the stable operation and source ID. Billing serializes admission against refunds and rechecks the
current exact EXAM ledger grant. Retries use the same appointment ID; Billing revalidates current
eligibility before replaying its durable start receipt. Revoked, mismatched or unavailable Billing
authority fails closed. A valid audited emergency override is the explicit exception and is
recorded locally without relying on a stale clearance snapshot.

For a V1 appointment, `PUT /api/v1/appointments/{id}/status` with `status: "CANCELLED"` and a
nonblank `reason` records the authenticated staff actor, reason and cancellation time with the
appointment, then emits `appointment.exam.cancelled` V1 in the same transaction. The producer
fixture is `src/test/resources/contracts/appointment-exam-cancelled-v1/appointment.exam.cancelled.v1.json`;
Billing's mirrored consumer fixture is
`../billing-service/src/test/resources/contracts/appointment-exam-cancelled-v1/appointment.exam.cancelled.v1.json`.
Billing consumes the matching source identity as cancellation evidence for refund review. The fact
permits an approved refund against the remaining net obligation; it does not claim cash was returned.
