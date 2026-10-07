# Huy — Report operational telemetry, batch 10

Date: 2026-10-07. Parent: **X-01.6**, not ten additional completed parent contracts.
Scope: `backend/report-service/**` and this documentation. Gateway, other services, shared
dependencies and CI are unchanged. Existing uncommitted cash receipt/replay work is preserved.

**Completion: 10/10 local deliverables; X-01.6 as a whole remains OPEN.**

## Ten acceptance deliverables

| ID | Implementation / acceptance | Status |
|---|---|---|
| RT-01 | Immutable aggregate-only snapshot, inward in/out ports and bounded read-only application use case; reject inconsistent negative/progress/age state. | LOCAL PASS |
| RT-02 | Close-without-start pending count and oldest persisted observation; matching exact admission ID removes pending, clinical business date is not intake age. | LOCAL PG PASS |
| RT-03 | Operational replay BUILDING/VERIFIED/FAILED inventory, active manifest remaining count and oldest BUILDING age; terminal progress excluded. | LOCAL PG PASS |
| RT-04 | Separate cash replay inventory/progress/age, no currency aggregation, cash amount labels or claims of financial completion. | LOCAL PG PASS |
| RT-05 | Count LEGACY_UNVERIFIED source hash evidence separately; one MVCC SQL statement and additive V14 diagnostic indexes, no invented payload hash. | LOCAL PG PASS |
| RT-06 | Fixed-cardinality Micrometer metrics, no DB query on scrape, no UUID/patient/department/correlation/payload labels. | LOCAL UNIT PASS |
| RT-07 | Opt-in fixed-delay single-process collection; concurrent triggers serialized, bounded query, failed/stale samples unavailable/NaN, recovery next collection. | LOCAL UNIT/PG PASS |
| RT-08 | Validated interval/age/staleness thresholds and seven explicit alert-candidate signals; future DB clock cannot keep a sample fresh. | LOCAL UNIT PASS |
| RT-09 | Default-off configuration/exposure unchanged; opt-in metrics restricted to ADMIN/MANAGER access tokens, V2 API/bindings/publication untouched. Actual Actuator/scheduler runtime verification. | LOCAL RUNTIME PASS |
| RT-10 | V13→V14 preserves existing rows, fresh schema/regression/privacy evidence and operator runbook; no claim of dashboard or remote alert deployment. | LOCAL REGRESSION PASS |

## Runtime boundary

- `ReadReportTelemetryUseCase` is internal, not a business report or public producer contract.
- The adapter performs **one SELECT statement** on Report-owned tables. No external service/DB,
  Rabbit Management HTTP call, raw event read, row mutation or authority decision occurs.
- Pending admission means persisted CLOSED with no STARTED for the same admission. Its age starts
  from `created_at` in Report, not discharge/close clinical time. This is **not medical LOS**.
- Replay counts are lifetime retained generation inventory. Remaining inputs are
  `SUM(source - applied)` over BUILDING only. Age is oldest generation creation time, **not stall
  detection**: a large replay making progress can still exceed the age threshold.
- VERIFIED means finite-manifest reconciliation only, never historical/export completeness,
  producer acceptance or permission to publish. An empty inventory does not prove healthy sources.
- Legacy-unverified count means source hashes cannot be reconstructed from old redacted evidence.
  Do not change rows to VERIFIED or delete evidence to silence a signal.
- One fixed-delay collection per process, no overlapping local trigger, 5-second application/query
  SQL execution budget (not a claim to override pool acquisition/driver connection timeouts).
  Aggregate queries are not falsely capped to a sample of rows; timeout means unavailable.
  Multi-instance collection repeats per instance; this is not distributed scheduler ownership.

## Configuration and thresholds

Defaults in `application.yml`; no POM change or exporter dependency is added.

| Property under `mediflow.report.telemetry` | Default | Valid range / meaning |
|---|---|---|
| `enabled` | false | Explicit opt-in; false creates no sampler/meters/scheduling configuration. |
| `sample-interval-ms` | 30000 | 5000..3600000; delay after previous completion, also initial delay. |
| `pending-age-seconds` | 900 | 60..604800; pending-age signal at or above threshold. |
| `replay-age-seconds` | 3600 | 300..2592000; BUILDING-age signal at or above threshold. |
| `stale-after-seconds` | 180 | At least ceiling(2 × interval / 1000), at most 86400; equality is stale. |

Environment names: `MEDIFLOW_REPORT_TELEMETRY_ENABLED`,
`MEDIFLOW_REPORT_TELEMETRY_SAMPLE_INTERVAL_MS`, `MEDIFLOW_REPORT_TELEMETRY_PENDING_AGE_SECONDS`,
`MEDIFLOW_REPORT_TELEMETRY_REPLAY_AGE_SECONDS`, `MEDIFLOW_REPORT_TELEMETRY_STALE_AFTER_SECONDS`.
Invalid enabled configuration fails startup. Disabled telemetry does not activate care-finance V2.
Age values clamp future observations to zero; ensure host/DB clocks are synchronized. Freshness
uses local receipt time, independently of a potentially skewed DB statement timestamp.

## Metric inventory — 23 meters (22 gauges, one counter)

| Name | Fixed tags | Interpretation |
|---|---|---|
| `report.telemetry.available` | none | 1 only when last successful sample exists and is fresh, else 0. |
| `report.telemetry.sample.age.seconds` | none | Local elapsed time since last successful collection; NaN before first success. |
| `report.telemetry.sample.failures` | none | Cumulative failed collection attempts; zero is not source-health proof. |
| `report.admission.pending` | none | Unpaired close facts in the local evidence store. |
| `report.admission.pending.age.seconds` | none | Oldest observed pending age, 0 when no pending. |
| `report.source.legacy.unverified` | none | Count of source evidence needing controlled revalidation. |
| `report.replay.generations` | `kind=operational/cash`, `status=building/verified/failed` | Retained lifetime generation count; no generation-ID label. |
| `report.replay.remaining` | `kind=operational/cash` | Frozen inputs still unapplied in BUILDING generations. |
| `report.replay.building.age.seconds` | `kind=operational/cash` | Oldest active generation age, 0 if none. |
| `report.telemetry.alert` | Seven `signal` values below | Local diagnostic candidate, not a deployed pager/dashboard. |

Closed signal set: `sample_unavailable`, `pending_admission_age`, `operational_replay_age`,
`cash_replay_age`, `operational_replay_failed`, `cash_replay_failed`, `legacy_unverified_sources`.
The two `failed` and legacy signals mean **retained inventory > 0**, not a new incident rate. They
remain true when a later new generation succeeds; acknowledge/triage historical failures in the
monitoring system, never delete generations. No retention/acknowledgement workflow is invented.

Before first success, after a collection failure, or once stale: available=0,
sample_unavailable=1, data/other signals=NaN. Never render NaN/missing as healthy zero.
Failure log is fixed metadata with no exception message/stack/payload; next scheduled collection
may recover. A scrape only reads immutable cached state and cannot start a database query.

## Operator runbook and access

1. Migrate forward to V14 before opt-in; V14 only adds four indexes. Existing journal, receipt,
   hash, generation progress and publication data remain unchanged.
2. Opt in telemetry independently of `care-finance-v2`. Verify sample availability after initial
   delay; monitoring should allow an initial two-interval warm-up, not page on startup alone.
3. Default web exposure remains **health,info**, so metrics HTTP is still unavailable by default.
   If needed, separately expose `health,info,metrics` only on a private management plane. Report
   rejects metrics list/detail unless telemetry is enabled and the access JWT is ADMIN/MANAGER.
   PATIENT/DOCTOR/other roles, refresh/service tokens and anonymous callers remain denied.
   This batch adds no Gateway route; never forward the management plane as a patient API.
4. On unavailable samples check Report DB/connectivity/query contention/schema and collection
   configuration. On pending age check upstream STARTED delivery acceptance; do not infer an ID or
   query another service's DB. On old BUILDING generation inspect the internal replay progress and
   retry its bounded advance only via the approved operational workflow; never mark it VERIFIED.
5. On FAILED inventory investigate reconciliation evidence and create a new isolated generation
   only after the cause is addressed. On LEGACY_UNVERIFIED coordinate controlled source revalidation.
6. To disable collection, redeploy with telemetry=false. This removes collection/meters and denies
   metrics even if exposure was separately configured; it does not purge data or change readers.

## Verification evidence

TDD: new collector test first failed compilation because the production types were absent;
then implementation passed unit tests. Full Report regression on 2026-10-07 at 10:54 +07:00
completed with Maven **exit 0: 416 tests, 0 failures/errors/skips**, 55 new cases over the prior
361-test cash-replay baseline. Fresh Surefire XML, oldest 10:51:43, prevents reuse of earlier reports.
PostgreSQL 16.14 (`postgres:16-alpine`), RabbitMQ 3.13 (`rabbitmq:3.13-management-alpine`) and
Docker Desktop engine 29.6.2 were real, not mocks. Architecture rules pass; scope/diff check passes.

New-case breakdown: application/snapshot invariants 8, configuration bounds/default-off 11,
collector/privacy/concurrency/staleness 9, SQL/overflow/lock-timeout/visibility 8, V13→V14 preservation 1,
metrics JWT/role policy 13, actual scheduler/Actuator runtime 3, disabled metrics web regression 1,
V14 index-only schema assertion 1. Existing cash/operational/legacy tests are preserved.
Runtime context is explicitly closed after the class so sampling does not outlive its test containers.
After adding this test-only cleanup, the focused runtime rerun at 10:56 +07:00 also completed
**exit 0, 3/3 PASS**, with scheduler/context/pool shutdown; these three reruns are not added to 416.

```powershell
mvn -q -pl backend/report-service -am '-Dapi.version=1.44' test
```

Test mapping: `ReportTelemetryApplicationServiceTest`, `ReportTelemetryCollectorTest`,
`ReportTelemetryConfigurationTest`, `ReportTelemetryPostgresTest`,
`ReportTelemetryMigrationPostgresTest`, `ReportTelemetrySecurityTest`,
`ReportTelemetryRuntimeTest`, plus `ReportControllerTest` disabled-policy regression,
`ReportMigrationSchemaTest` V14 index-only assertion and `ArchitectureTest`.

Synthetic database rows in diagnostic SQL tests are not producer workflow acceptance. Runtime
test uses the actual Spring Boot application context, scheduler, Actuator, PostgreSQL/RabbitMQ containers;
it does not claim full producer history or end-to-end financial/surgical lifecycle acceptance.

## Remaining parent work

X-01.6 remains OPEN for Pharmacy/Surgery outbox pending/oldest/quarantine, broker consumer lag/DLQ,
cross-service traces, actual dashboard/exporter/alert receiver deployment and operational acceptance.
R-01.7.3 controlled coverage/publication/catch-up, full finance/refund/settlement and source-owner
acceptance are unchanged. No commit/push or Gateway/shared production change is part of this batch.
