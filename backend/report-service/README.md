# report-service

Aggregated analytics. A **read model** built purely from events — never queries another service's DB.

Reference: [`docs/ai/services/report.md`](../../docs/ai/services/report.md) · design doc
[`docs/eproject_general_plan/report-service.html`](../../docs/eproject_general_plan/report-service.html).

- **Port:** 8088 · **Base path:** `/api/v1/reports` · **DB:** `mediflow_report` (PostgreSQL)
- **Owns tables:** `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`,
  `PROCESSED_EVENT`, `PAYMENT_CONTRIBUTION`
- **Architecture:** clean architecture (hexagonal) per
  [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`;
  driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports.
  Dependencies inward only.

## Status

**T01–T11 và review hardening trong report-service đã hoàn tất.** Domain rules, persistence
mappings/adapters, payment compensation, aggregate updates, read queries, RabbitMQ consumer/DLQ
topology, secured HTTP endpoints và bộ cross-layer Testcontainers đều đã được kiểm chứng. Runtime
gate với Docker Desktop xanh. Gateway phát typed token; Report hiện chỉ chấp nhận JWT có
`type=access`, với regression coverage trong `JwtAuthFilterTest`.

Package layout (already created, each folder holds a `.gitkeep` until you fill it):

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   (driving HTTP: controllers + GlobalExceptionHandler)
messaging/consumer    (driving events: @RabbitListener)
infrastructure/persistence   infrastructure/messaging   infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_report;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).
4. Provide `MEDIFLOW_JWT_SECRET` and optionally `MEDIFLOW_REPORT_ZONE_ID` in the environment.
5. Swagger is denied by default; set `MEDIFLOW_REPORT_SWAGGER_PERMIT=true` only for local/development.

```bash
mvn -pl backend/report-service -am spring-boot:run
```

Swagger UI: http://localhost:8088/swagger-ui.html

## Events

- **Publish:** — (read model, publishes nothing)
- **Subscribe (T08):** `medicalrecord.created`, `lab.result.created`, `payment.completed`,
  `payment.failed`, `prescription.filled` on durable `report.q`; poison deliveries are retried a
  bounded number of times and routed to `report.dlq`.

HTTP endpoints (T09) are available at `/api/v1/reports` for `ADMIN` and `MANAGER` roles. JWT is
re-verified locally, and all responses use the common `ApiResponse` envelope.

Topic exchange `mediflow.events`; see
[`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent
(dedupe on `eventId`).

## Care-finance V2 local shadow writer

V6/V7 add separate contributions, aggregate scopes and an operational journal. The typed local
kernel commits minimal accepted replay inputs, delivery claim, business contribution and both
department/hospital updates in one transaction. Same event/different payload and same operation/
changed department/time are conflicts; new event IDs cannot repeat the business effect. Batch
metrics acquire locks in stable order. Corrections are rejected until replacement/reversal semantics
are implemented; no source revision is guessed from envelope version.

Journal snapshots contain metadata/accepted aggregate inputs, never full diagnosis/lab-result
payloads. The full incoming normalized envelope is fingerprinted transiently for conflict checking.
The journal only covers accepted post-activation inputs; pre-activation history needs an export.
V8 adds an internal finite operational replay: start freezes the committed journal input set;
bounded batches build isolated contributions/scopes and can resume after rollback/restart.
Same-source deliveries dedupe within each generation; snapshot fingerprints and final fact/scope
reconciliation protect the rebuild. VERIFIED is only relative to that manifest, not authorization
to serve live reports. No live catch-up/read switch or V2 listener exists. Real PostgreSQL replay
evidence is recorded in the current Huy plan; finite snapshot read routes below remain gated off.
Existing five bindings, three APIs and financial compatibility projections remain unchanged.
`mediflow.features.care-finance-v2` stays disabled. Classified Billing facts are still required.

The offline `LabOperationalContributionMapper` uses actual Lab V1 bytes: `labId`, the explicit
`resultVersion`, requesting `departmentId`, exact care episode and `completedAt`. Completion date is
derived using the configured Report zone, not delivery time or `performedDate`. Missing source fields
are rejected without record/episode or envelope-version fallbacks. Only source revision 1 is
currently supported; the actual admission Lab fixture has revision 3 and is explicitly rejected
until initial imported-result versus correction/replacement semantics are agreed. Consumer tests do
not signify producer-owner approval or live activation.

V9 adds offline minimal admission start/administrative-close evidence and a delivery fingerprint
ledger. Close-before-start survives reload as PENDING_START; only the exact admission/patient start
supplies department and completes the pair, never reopens it. Immutable time/department/proof conflicts
roll back the claim; exact business timestamps retain nanoseconds. Actual Inpatient bytes are tested.
No admission contribution, medical LOS, bed occupancy or V2 API/listener is enabled: the current
producer lacks an accepted business revision and those metric/correction semantics remain open.
PostgreSQL pending/reload/rollback/race/upgrade tests run with Docker; see the plan's latest evidence.

V10 adds an **empty-by-default** accepted-coverage publication table for immutable operational replay
snapshots. Two aggregate-only GET routes (`/operations/daily`, `/operations/surgery`) exist only with
`care-finance-v2=true`; ADMIN/MANAGER/DOCTOR are permitted directly. Legacy finance roles and routes
do not change. Invalid periods return 400 `REPORT_VALIDATION_ERROR`; unavailable coverage returns
404 `REPORT_NOT_FOUND`, never invented zeros. Zero-fill is allowed only inside explicit accepted
date/timezone/metric coverage for a VERIFIED generation. DTOs include generationId, reconciledAt,
`snapshotOnly=true` and stable daily English camelCase counters, no clinical payload.

Replay VERIFIED does not insert publication or establish historical completeness. There is no
production publication writer/admin API. Do not insert publication manually or turn flags on after
pulling: source acceptance, approved coverage, controlled publish/rollback, live catch-up, Gateway
DOCTOR routing, inpatient-day/surgery-category/occupancy facts and classified finance remain gates.
These routes are a local finite-snapshot slice, not the complete live operations/financial dashboard.
Matching gated requests are in `report.http`.

## Tests

```bash
mvn -pl backend/report-service test        # unit (domain + application, no Spring)
mvn -pl backend/report-service verify      # + integration (Testcontainers, needs Docker)
```

Nếu máy có cấu hình Testcontainers cũ, dùng `-Dapi.version=1.40` để ép Docker API tương thích:

```bash
mvn -q -pl backend/report-service -am -Dapi.version=1.40 verify
```

Quality gate đầy đủ từ repository root:

```bash
mvn -q -pl backend/report-service -am test
mvn -q -pl backend/report-service -am verify
mvn -q -pl backend/report-service -am -DskipTests javadoc:javadoc
mvn -q -pl backend/report-service -am dependency:analyze
```
