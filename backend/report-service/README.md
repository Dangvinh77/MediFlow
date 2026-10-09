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

2026-10-09 admission follow-up: the existing opt-in operational receiver now counts actual
`admission.started` once at admittedAt/start department, with exact admission evidence, delivery/
source claims, minimal replay journal and hospital/department scopes in one transaction. Close-before-
start, conflicting snapshots, rollback, replica delivery and finite replay are tested. Administrative
close remains evidence only, never medical discharge/LOS/occupancy. No new binding, publication or
activation. [Verification and remaining V2 work](../../docs/superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).

Cash-only intake follow-up (2026-10-08): `MEDIFLOW_REPORT_CASH_RECEIPT_CONSUMER_ENABLED`
AND `MEDIFLOW_CARE_FINANCE_V2` independently gate `report.cash-receipts-v2.q`/DLQ, both false by
default. Only actual Billing V1 `payment.completed` service/deposit receipts are accepted into
existing V12 gross receipt evidence and both scopes. ACK follows commit, permanent errors reject
with redacted reasons, and storage failures retry three times before original-byte DLQ recovery.
This gross-receipt slice alone does not implement recognition, deposit release, refunds, settlement,
financial API/publication or held-row release; the separate cash-refund follow-up is described below.
Legacy listeners remain unchanged and reject V1 envelopes rather than
counting a deposit as revenue. [Verification and source manifest](../../docs/superpowers/plans/2026-10-08-report-cash-intake.md).

Completed-refund follow-up: care-finance-v2 AND `MEDIFLOW_REPORT_CASH_REFUND_CONSUMER_ENABLED`
(false) gate `report.cash-refunds-v2.q`/DLQ and a bounded durable pending-recovery worker. V15 accepts
actual Billing refund bytes, matches the original local receipt and records actual-refund-day cash-out
for hospital/account-department scopes. Early refunds remain PENDING; context/budget violations reject
or quarantine without guessed classification. Gross inflows stay unchanged. This is not earned/liability
reversal, full financial/refund replay or accepted publication. [Implementation and verification](../../docs/superpowers/plans/2026-10-08-billing-refund-closure.md).

V2 operational intake follow-up (2026-10-08): `MEDIFLOW_REPORT_OPERATIONAL_CONSUMER_ENABLED`
AND `MEDIFLOW_CARE_FINANCE_V2` independently gate a private seven-key operational/evidence queue.
Both default false. It commits validated revision-one source metrics to the existing isolated
minimal journal/two scopes; admission start counting is added by the 2026-10-09 follow-up above,
while administrative close remains pending/pairing evidence only.
Permanent contract errors use DLQ, storage failures bounded retry; retained bytes can be replayed
without repeated metrics. Five compatibility bindings/read APIs remain unchanged. No financial
intake, LOS/occupancy inference, accepted coverage or read-publication activation is introduced.
See [fresh module and broker evidence](../../docs/superpowers/plans/2026-10-08-huy-ready-task-completion.md).

**T01–T11 legacy và review hardening trong report-service đã hoàn tất, không phải toàn bộ V2.** Domain rules, persistence
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
currently supported; the actual admission Lab fixture now carries first-completion revision 1.
Imported completion/correction revisions are rejected until their semantics are agreed. Consumer tests do
not signify producer-owner approval or live activation.

V9 adds offline minimal admission start/administrative-close evidence and a delivery fingerprint
ledger. Close-before-start survives reload as PENDING_START; only the exact admission/patient start
supplies department and completes the pair, never reopens it. Immutable time/department/proof conflicts
roll back the claim; exact business timestamps retain nanoseconds. Actual Inpatient bytes are tested.
The 2026-10-09 follow-up supersedes the original evidence-only start mapping: immutable singleton
start revision 1 now supplies the admission count. Medical LOS, bed occupancy and unsupported
correction semantics remain open; publication/public activation is not implied.
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

V17 adds an internal paired cash/refund rebuild, separate from public financial reports. Receipt
and refund manifests share one REPEATABLE READ snapshot; APPLIED refunds recheck their frozen
original/cumulative amount and rebuild both cash-out scopes, while PENDING/REJECTED remain explicit
inventory. Bounded generation-row-fenced batches commit facts/scopes/progress atomically and
bidirectional reconciliation verifies only the finite accepted state. Old gross-only runs and live
tables are unchanged. It does not retry copied pending refunds, invent recognition/liability,
publish reads or establish historical coverage. [Canonical semantics](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#report-paired-finite-accepted-cash-rebuild--v17-2026-10-08)
and [current evidence](../../docs/superpowers/plans/2026-10-08-huy-master-sync-refund-replay.md).

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
