# notification-service

## Cách đọc sau chuẩn hóa — 26/10/2026

**Nghiệp vụ hiện hành:** theo bộ quy tắc ngoại trú, không theo trạng thái checkpoint lịch sử.

- [Luồng nghiệp vụ và lịch khám/BHYT mô phỏng](../../docs/plans/02-business-flows.md).
- [Hợp đồng tích hợp hiện hành](../../docs/plans/03-contracts.md); live DTO/controller/fixtures là wire truth.
- [Bàn giao còn cần xử lý hoặc xác minh](../../docs/handoffs/README.md).
- [Kế hoạch thay đổi và owner](../../docs/plans/07-outpatient-workflow-implementation.md).

Nội dung kỹ thuật, flags, lệnh chạy và kết quả bên dưới giữ phạm vi phiên bản/ngày được ghi.
Không dùng test counts cũ để ký revision mới; lịch ca, tái khám và BHYT đích chưa tự có từ tài liệu.
Standalone launcher của Report/Notification là compatibility/rollback; mặc định chạy Support.

## Product scope — 2026-10-17

Part of **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Notification covers source-authorized in-app outpatient notifications. The default six-deployment topology co-hosts Notification and Report in Support with separate stores; the standalone launcher remains available for compatibility/rollback. See [Support acceptance](../support-service/README.md). Advanced email/SMS and deferred care projections are not mandatory MVP scope.

Canonical [system design](../../docs/plans/01-system-design.md), [contracts](../../docs/plans/03-contracts.md) and [acceptance runbook](../../docs/plans/06-acceptance-runbook.md) define current scope. Existing APIs, feature flags and historical test counts below do not certify full workflow release acceptance.


Private notification history (`NOTIFICATION`). Mostly event-driven.

Reference: [`service rules`](../../docs/ai/services/notification.md) and the canonical [projection contract](../../docs/plans/03-contracts.md).

- **Port:** 8087 · **Base path:** `/api/v1/notifications` · **DB:** `mediflow_notification` (PostgreSQL)
- **Owns tables:** `NOTIFICATION`, `PROCESSED_EVENT`, held notification outbox and Surgery reminder evidence.
- **Architecture:** clean architecture per [blueprint](../../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`, dependencies inward only.

## Status

Verification refresh, 2026-10-18: full `mvn -q -pl backend/notification-service -am test`
completed with **279 tests, zero failures/errors/skips**, including actual PostgreSQL/RabbitMQ
prescription notice, receipt and refund cases. Baseline `e44ddf487523f8e781e53196d28f17db32beed1e`;
no Notification production behavior changed in this refresh. This supersedes the Docker-unavailable
verification note below for source regression. The packaged Support/six-app acceptance remains
documented by its owning harness, separately from this module suite.

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
email/SMS. Historical complete-suite evidence before the outpatient Rx additions: **171/171**,
zero fail/error/skip, actual PG/RabbitMQ. Current verification is recorded below.
The retired 2026-10-08 refund closure plan is historical; use the [current projection contract](../../docs/plans/03-contracts.md) and rerun acceptance rather than treating the count above as fresh evidence.

### Outpatient prescription notices

Enable both `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED=true` and
`MEDIFLOW_NOTIFICATION_OUTPATIENT_PRESCRIPTION_CONSUMER_ENABLED=true` for the new intake;
both default to false. The existing single `notification.q` reader accepts the original Pharmacy
V1 outpatient `prescription.created`, `prescription.filled`, `prescription.cancelled`,
`prescription.expired`, and `prescription.dispense.failed` fixtures. Four new gated bindings reuse
that queue and its DLQ; the existing filled binding and flat V0 compatibility reader are retained.
Admission facts are rejected by this intake. No contact lookup or external delivery is introduced.

The same gates also route Billing's original outpatient PRESCRIPTION `invoice.created` to the
existing payment-request decoder/template and transaction-backed notice service. This does not
require the Surgery consumer flag or create another queue. The reused internal types retain their
historical Surgery names but this reader accepts only PRESCRIPTION; Surgery requests remain
outside this gate. A request notice does not assert payment or dispensing. Terminal prescription
notices do not assert completed refunds. Existing Billing receipt/refund intake keeps its own gates.

Delivery identity, business-operation identity, private IN_APP history and a HELD
`notification.sent` outbox row commit atomically. Operation keys are event type plus prescription ID
for lifecycle facts, and `invoice.created` plus payment-request ID for Billing requests. Same operation
with a new event ID is a no-op only when its business snapshot matches; conflicts reject without
changing the original recipient/history. The lifecycle decoder validates the original producer,
version, exact outpatient/source/record identities, timestamps and complete price/item snapshot;
history messages exclude drug names, prices and terminal reasons. Reads retain signed patient-claim
ownership checks.

The shared queue listener retries transient storage failures at most three times; malformed/conflicting
contracts reject immediately, including wrapped listener failures. Exhaustion dead-letters to the
existing `notification.dlq`. Replay retains original bytes/IDs; no automatic DLQ release is added.

Verification on 2026-10-17, baseline `16a5e06e` plus these uncommitted changes: **221 tests across
23 non-container classes**, zero failures/errors/skips (218 existing/new selected tests plus three
listener-retry tests; no duplicate reruns counted). All test sources compile, including the new
`PrescriptionNoticeRabbitIntegrationTest`. Its **11** PostgreSQL/Rabbit cases have **not run** in this
refresh because Docker is unavailable. The prepared acceptance covers all five original Pharmacy
facts through the real queue, Billing request routing, concurrent/new-ID dedupe, conflict, outbox
rollback/recovery and signed private-history reads. This is not Support-host or paid E2E acceptance.

Package layout:

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   messaging/consumer
infrastructure/persistence   infrastructure/messaging   infrastructure/client
infrastructure/security   infrastructure/config
```

## Run locally

The default six-deployment runtime now uses the [Support host](../support-service/README.md).
Notification exports `notification-service-0.0.1-SNAPSHOT-lib.jar`; the host imports only
`NotificationModuleConfiguration` with `notification-module,outpatient-mvp`. Its datasource,
EMF, transaction manager and JDBC template are explicitly named `notification*`, migrations
use `classpath:mediflow/notification/db/migration`, and credentials use
`mediflow.notification.datasource.*`. Original SQL bytes and store/history are retained.
Standalone execution below is for compatibility/rollback; stop Support before starting it
against the same store. The host excludes deferred CareProjection/Surgery consumers, scopes
JWT/advice to Notification paths, and preserves patient ownership. New host container/restart
tests are prepared but unrun locally; see the single Huy handoff section 9.

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
- **Opt-in outpatient Rx:** the five Pharmacy lifecycle facts above and Billing PRESCRIPTION
  `invoice.created`, with independent care-v1 + outpatient-prescription-consumer gates.

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

## Tests

```bash
mvn -pl backend/notification-service -am test    # complete suite, includes Testcontainers; needs Docker
mvn -pl backend/notification-service -am '-Dtest=PrescriptionNoticeContractTest,PrescriptionNoticeConfigurationTest,NotificationListenerRetryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend/notification-service -am '-Dtest=PrescriptionNoticeRabbitIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test # needs Docker
```
