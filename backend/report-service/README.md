# report-service

## MVP adjustment — 2026-10-14

Scope: outpatient counts/dispensed items and source-evidenced cash/refund; future Report module
inside Support with Notification remaining Lộc-owned. Support host is not created by this batch.

2026-10-15: canonical mode is `outpatient-mvp`; earlier `mvp-outpatient` is an alias.
For co-hosting, import `ReportModuleConfiguration` from classifier `lib` and activate
`report-module,outpatient-mvp`. Report data credentials must be explicit via
`mediflow.report.datasource.*`; named Report DS/Flyway/EMF/tx/JDBC have no foreign/default fallback.
Namespaced migration copies live at `classpath:mediflow/report/db/migration`, bytes unchanged.
Standalone keeps its original datasource and aliases for compatibility. All Report transactions
select reportTransactionManager; native adapters select reportJdbcTemplate. Security/advice/Rabbit
beans and scheduled work are now namespaced, module security matches Report routes only.
Full composition contract and owner actions: [Support handoff](../../docs/handoffs/HANDOFF-HUY-SUPPORT-COMPOSITION.md).
Local wiring does not prove real DB/broker/Support startup acceptance.

Cutover preparation and rollback checklist: [owned runbook](../../docs/plans/huy-mvp-cutover-runbook.md).
The operator inventory in `src/main/resources/operations/mvp-cutover-inventory.sql` is READ ONLY,
not a publication/recovery/acceptance writer. Current Report migration history contains 24 SQL files,
latest V27; version gaps are preserved. Artifact profile now compares every SQL copy and runs three
forked-JVM probes from the actual plain JAR, not Report target/classes; DB/broker gates remain open.
Canonical scope: [MVP design](../../docs/plans/01-system-design.md),
[Huy plan](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md),
[manifest/evidence](../../docs/plans/huy-backend-mvp-baseline.md).

The module `mvp-outpatient` profile excludes legacy flat intake, mixed Admission/Surgery intake,
settlement/final-recognition and full V2 read/publication/archive APIs, even with global overrides.
Mixed standalone cash/refund listeners are also excluded. The default-OFF selected V1 listener
uses `mediflow.report.outpatient-consumer.enabled` AND global care-finance-v2. It binds only
medicalrecord.completed/lab.result.created/prescription.filled/payment.completed/payment.refunded
to `report.outpatient-v1.q`, with a durable `report.outpatient-v1.dlq`. Invalid/deferred/conflicting
facts go to DLQ, not ACK-drop; transient storage errors get bounded retry. Existing operation
receipts/cash/refund kernels remain authoritative, including original-linked early-refund recovery.

New ADMIN/MANAGER reads require `mediflow.report.outpatient-read.enabled` AND global care-finance-v2:
`GET /api/v1/reports/outpatient/daily?fromDate&toDate&departmentId` and
`GET /api/v1/reports/outpatient/monthly?year&departmentId`. At most 366 complete calendar days,
years 2000–2100, configured Report zone, VND. Periods carry visits/Lab/Rx/units, cashReceived,
refunds/netCash; never earned revenue/liability. Coverage metadata includes coverageId,
sourceObservedAt/reconciledAt. Legacy endpoints retain their wire and zero-fill semantics.

V27 creates an EMPTY accepted-coverage table. No write API or automatic acceptance is provided.
An audited source export/reconciliation for all five inputs and exact department/date/zone is
required before rollout. No coverage, pending/invalid refund, fingerprint drift, wrong scope or
future acceptance returns 404 REPORT_NOT_FOUND, not invented zero. One REPEATABLE READ snapshot
reads only Report stores; late facts invalidate the accepted local witness. Gateway routing and
real Clinical outpatient/source-coverage acceptance are still owner gates; do not enable after pull.

Packaging preserves executable `report-service-0.0.1-SNAPSHOT.jar` for standalone rollback and
adds plain `report-service-0.0.1-SNAPSHOT-lib.jar`. Future Support dependency must explicitly use
classifier `lib`, never the executable BOOT-INF JAR; exclude ReportServiceApplication from host
scans. Module datasource/EMF/Flyway/transaction manager/security wiring is supplied locally;
real PostgreSQL/upgrade and co-host startup acceptance remains H6-10.4/H6-11.
Artifact verification: `mvn -q -pl backend/report-service -am -Preport-packaging-acceptance
-DskipUnitTests=true verify`. This checks artifact structure/migration bytes, not host startup.
Existing stores, migrations, queues, raw evidence and compatibility behavior remain intact.

The implementation notes/test counts below are historical; they do not reopen full V2 or prove
this profile has passed packaged Support/Gateway/PostgreSQL/RabbitMQ acceptance.

Aggregated analytics. A **read model** built purely from events — never queries another service's DB.

Reference: [`docs/ai/services/report.md`](../../docs/ai/services/report.md) · design doc
[`docs/eproject_general_plan/report-service.html`](../../docs/plans/02-business-flows.md).

- **Port:** 8088 · **Base path:** `/api/v1/reports` · **DB:** `mediflow_report` (PostgreSQL)
- **Owns tables:** `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`,
  `PROCESSED_EVENT`, `PAYMENT_CONTRIBUTION`
- **Architecture:** clean architecture (hexagonal) per
  [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`;
  driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports.
  Dependencies inward only.

## Status

2026-10-12 operational continuation: ADMIN-only `/operations/rebuilds` and
`/operations/publications` (including rollback) require both care-finance-v2 and
`MEDIFLOW_REPORT_OPERATIONAL_PUBLICATION_ENABLED`, default false. Finite SURGERY snapshots only:
three existing completion/cancellation/duration metrics, retained evidence, completed-day bounds,
exact manifest/current-source checks, local shared-writer/exclusive publication fence,
expected-pointer switch, immutable acceptance/audit and sealed generation rows. Retry does not
revert a later pointer; rollback keeps original as-of. DAILY/census/capacity/category publication,
upstream completeness, Gateway ingress and held-event release remain unimplemented/unapproved.
Final full Report: 1,252 tests / 117 suites PASS, zero failures/errors/skips, actual PostgreSQL/RabbitMQ.
20 new PG publication/migration + 19 existing replay cases pass separately; focused reruns are not summed.
No existing developer database is modified. Broader R-04.5.2 remains OPEN.

Current 2026-10-11 supported release: V25 implements finite five-metric publication, exact local
catch-up/final fence, provenance/row reconciliation, immutable acceptance/audit, expected-pointer
switch and rollback. New capability/coverage/bounded rebuild/publish management APIs are ADMIN-only
(coverage additionally MANAGER), separately gated by `MEDIFLOW_CARE_FINANCE_V2` AND
`MEDIFLOW_REPORT_FINANCIAL_PUBLICATION_ENABLED`, both false. Missing historical periods stay
unavailable; no transaction is fabricated. Unsupported correction/replacement facts reject before
effects; new refunds after an APPLIED final account require a future original-allocation earned
reversal contract. No source-retention purge, Gateway changes, gate enablement or HELD release.
Dated publisher/history absence notes below are earlier tranches, not the current finite financial
workflow. [Canonical release and limits](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#supported-available-history-release-and-controlled-publication--2026-10-11).

2026-10-09 admission follow-up: the existing opt-in operational receiver now counts actual
`admission.started` once at admittedAt/start department, with exact admission evidence, delivery/
source claims, minimal replay journal and hospital/department scopes in one transaction. Close-before-
start, conflicting snapshots, rollback, replica delivery and finite replay are tested. Administrative
close remains evidence only, never medical discharge/LOS/occupancy. No new binding, publication or
activation. [Verification and remaining V2 work](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md).

Cash-only intake follow-up (2026-10-08): `MEDIFLOW_REPORT_CASH_RECEIPT_CONSUMER_ENABLED`
AND `MEDIFLOW_CARE_FINANCE_V2` independently gate `report.cash-receipts-v2.q`/DLQ, both false by
default. Only actual Billing V1 `payment.completed` service/deposit receipts are accepted into
existing V12 gross receipt evidence and both scopes. ACK follows commit, permanent errors reject
with redacted reasons, and storage failures retry three times before original-byte DLQ recovery.
This gross-receipt slice alone does not implement recognition, deposit release, refunds, settlement,
financial API/publication or held-row release; the separate cash-refund follow-up is described below.
Legacy listeners remain unchanged and reject V1 envelopes rather than
counting a deposit as revenue. [Verification and source manifest](../../docs/plans/03-contracts.md).

Completed-refund follow-up: care-finance-v2 AND `MEDIFLOW_REPORT_CASH_REFUND_CONSUMER_ENABLED`
(false) gate `report.cash-refunds-v2.q`/DLQ and a bounded durable pending-recovery worker. V15 accepts
actual Billing refund bytes, matches the original local receipt and records actual-refund-day cash-out
for hospital/account-department scopes. Early refunds remain PENDING; context/budget violations reject
or quarantine without guessed classification. Gross inflows stay unchanged. This is not earned/liability
reversal, full financial/refund replay or accepted publication. [Implementation and verification](../../docs/plans/04-data-reliability.md).

V2 operational intake follow-up (2026-10-08): `MEDIFLOW_REPORT_OPERATIONAL_CONSUMER_ENABLED`
AND `MEDIFLOW_CARE_FINANCE_V2` independently gate a private seven-key operational/evidence queue.
Both default false. It commits validated revision-one source metrics to the existing isolated
minimal journal/two scopes; admission start counting is added by the 2026-10-09 follow-up above,
while administrative close remains pending/pairing evidence only.
Permanent contract errors use DLQ, storage failures bounded retry; retained bytes can be replayed
without repeated metrics. Five compatibility bindings/read APIs remain unchanged. No financial
intake, LOS/occupancy inference, accepted coverage or read-publication activation is introduced.
See [fresh module and broker evidence](../../docs/plans/huy-backend-mvp-baseline.md).

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

## Local financial evidence backup and recovery (default OFF)

Sealed V23 manifests can be downloaded/restored through ADMIN-only `financial/source-archives`
routes when BOTH `MEDIFLOW_CARE_FINANCE_V2` and `MEDIFLOW_REPORT_FINANCIAL_ARCHIVE_ENABLED`
are explicitly enabled. Gateway is unchanged; this implementation does not enable either gate.
Backups contain sensitive minimal patient references. Restrict operator access, use encrypted
backup storage and never publish them in logs/Git. Exact source/delivery IDs, business-time nanos,
hashes and unresolved state survive. V24 records immutable restore actor/origin/new generation.
Restore starts a separate BUILDING generation, never mutates live sources or creates a report
publication. Advance/reconcile still uses the existing bounded rebuild use case.
This supplies local recovery only: digests are not signatures, and verified replay is not accepted
pre-V2 history. [Canonical archive contract](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#portable-local-financial-evidence-archive--2026-10-11).

## Tests

V17 adds an internal paired cash/refund rebuild, separate from public financial reports. Receipt
and refund manifests share one REPEATABLE READ snapshot; APPLIED refunds recheck their frozen
original/cumulative amount and rebuild both cash-out scopes, while PENDING/REJECTED remain explicit
inventory. Bounded generation-row-fenced batches commit facts/scopes/progress atomically and
bidirectional reconciliation verifies only the finite accepted state. Old gross-only runs and live
tables are unchanged. It does not retry copied pending refunds, invent recognition/liability,
publish reads or establish historical coverage. [Canonical semantics](../../docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#report-paired-finite-accepted-cash-rebuild--v17-2026-10-08)
and [current evidence](../../docs/plans/huy-backend-mvp-baseline.md).

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
