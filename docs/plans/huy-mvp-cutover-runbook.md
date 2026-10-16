# Huy MVP — preparation, inventory and rollback checklist

2026-10-15. Applies to Pharmacy/Report only. This is a reproducible checklist, **not evidence
that cutover ran**. Canonical scope and global acceptance remain [system design](01-system-design.md),
[contracts](03-contracts.md), [data reliability](04-data-reliability.md) and [acceptance](06-acceptance-runbook.md).
Workboard: [Huy plan](2026-10-14-huy-backend-mvp-adjustment-plan.md).

## 1. Check the artifact and mode before touching a deployment

| Item | Required value / behavior |
|---|---|
| Pharmacy discovery | pharmacy-service, Patient/Organization callers point to core-service in outpatient-mvp |
| Logical caller/events | pharmacy-service unchanged; deployment reduction does not rename event producers |
| Canonical profile | outpatient-mvp; earlier mvp-outpatient is an owned compatibility alias |
| Standalone Report | default executable report-service JAR; original spring.datasource properties |
| Report co-host export | plain report-service artifact classifier lib; import ReportModuleConfiguration only |
| Co-host profiles | report-module,outpatient-mvp; neither means standalone launcher may be broad-scanned |
| Co-host data | explicit mediflow.report.datasource.url/username/password; no host/foreign fallback |
| Store boundaries | reportDataSource/reportFlyway/reportEntityManagerFactory/reportTransactionManager/reportJdbcTemplate |
| Migration resources | standalone db/migration; co-host mediflow/report/db/migration, exact same SQL bytes |
| Migration inventory | Pharmacy: 20 SQL files, latest V20. Report: 24 SQL files, latest V27; V3–V5 are absent by existing history |
| Resource ownership | reportTaskScheduler, Report listener factories/queue, Report-only HTTP chain/advice |
| Deferred | Surgery/Inpatient apps/routes/producers/jobs remain outside MVP; no databases/code deleted |

These artifact counts describe this source baseline, not permission to fabricate missing versions,
renumber migrations, repair Flyway history, or assume an older installed database is up to date.
Fail a mismatched checksum/version validation; inspect the cause with the owning developer.

Existing gates stay OFF until their source/authority/DB/broker acceptance:

- Pharmacy public V1 requires global mediflow.features.care-finance-v2, identity, clinical-context,
  clearance-consumer, clearance-authority and outpatient-api enabled together; mode never falls back to V0 on denial.
- Report selected intake and read are independently gated by outpatient-consumer.enabled and
  outpatient-read.enabled plus global V2. Full V2/admission/settlement publication stays excluded.
- Clinical's care-finance-v2.enabled / MEDIFLOW_CARE_FINANCE_V2_ENABLED differs from Huy's boolean
  care-finance-v2 / MEDIFLOW_CARE_FINANCE_V2. Shared owner maps each app explicitly; never assume
  one environment variable or profile implicitly enables the entire six-app chain.
- Pharmacy V1 delivery defaults HELD. V20 supports NEW outpatient cohort enrollment only with all
  six gates plus outpatient-events.enabled and an explicit acceptance-reference after owner acceptance.
  Neither this guide, inventory nor profile authorizes historical HELD release; replay cannot enroll it.
  Turning off new enrollment does not stop terminal delivery for already accepted in-flight cohorts.
  An emergency publish pause uses the dispatcher deployment gate, preserving rows and cohort identity.
  No activation/release SQL is supplied here; published V1 evidence is not automatically purged.

V20 upgrades the schema without changing historical outbox payloads/states/leases/checksums.
After cohort writes exist, do not downgrade to a pre-V20 application that assumes every V1 row is HELD
or purges V1 terminal proof. A rollback artifact must understand V20, keep enrollment OFF and preserve
accepted in-flight deliveries; test it on an approved clone. Do not drop the cohort table/column or restore
an old snapshot over newly accepted writes. The existing Report library rollback remains separate.

## 2. Read-only inventory — owner-approved clone first

Use a PostgreSQL client and service definitions outside Git with **read-only operator credentials**
and verified target DBs. The example service names below must be configured by the operator;
they are not connection strings or installed tooling. Do not put passwords in command history.

From repository root, after verifying each service resolves to its OWN approved clone:

```text
psql "service=mediflow_pharmacy_clone" -X -v ON_ERROR_STOP=1 -c "SELECT current_database();"
psql "service=mediflow_pharmacy_clone" -X -v ON_ERROR_STOP=1 -f backend/pharmacy-service/src/main/resources/operations/mvp-cutover-inventory.sql
psql "service=mediflow_report_clone" -X -v ON_ERROR_STOP=1 -c "SELECT current_database();"
psql "service=mediflow_report_clone" -X -v ON_ERROR_STOP=1 -f backend/report-service/src/main/resources/operations/mvp-cutover-inventory.sql
```

Each resource begins a REPEATABLE READ / READ ONLY transaction, has statement/lock timeouts and
ends ROLLBACK. No INSERT/UPDATE/DELETE/DDL/activation, no event payload or patient/account identifiers
are selected. The scripts are NOT under db/migration and must not be auto-executed by app startup.
Record the database_name/observed_at/read_only/isolation metadata and redacted result sets.

| Pharmacy inventory | Interpretation / action requiring owner approval |
|---|---|
| Outbox contract/context/delivery_enabled/state/count/oldest | Keep HELD and quarantined unchanged. UNKNOWN aggregate context is not inferred as outpatient. LEASE_MARKED is not proof that a lease is live or expired. |
| Payment receipts by mode/status | V0 RECEIVED remains compatibility recovery work, not a V1 dispense instruction. Do not convert receipt mode or invent authorization. |
| Reservations by mode/state | Preserve counts/quantities/expiry. Do not expire/release historical admission rows to make the demo clean. |
| Job cursor/owner/fence presence and lease time | Record before stopping/restarting jobs. Do not reset cursor/token or steal a lease. |

| Report inventory | Interpretation / action requiring owner approval |
|---|---|
| Legacy payment contribution status | Preserve pending reversals and dedupe evidence; not earned revenue. |
| Refund state/context/classification/attempt time | PENDING or REJECTED is not completed refund; the inventory never recovers or rejects a row. |
| Operational sources and fingerprint presence | Missing historical fingerprint is not verified source coverage. Counts here are diagnostics, not published KPIs. |
| Event journal count/times | Silence or latest delivery time does not establish an external cutover cursor. |
| Accepted coverage row metadata | A record count is not a fresh valid fingerprint/owner reconciliation proof. Do not insert acceptance rows from fixtures. |
| Rebuild states, including deferred finance | BUILDING/FAILED stay durable; VERIFIED local replay is not automatic publication/history acceptance. |

This is an initial owned-store inventory, not an exhaustive audit of every deferred table or broker.
Pharmacy and Report observations are independent snapshots, **not a cross-service transaction**.
Broker queue/DLQ depth, in-flight publishes, source cursors and live scheduler ownership must be
observed separately using approved read-only broker/operator access. No purge/reset/bulk release.
Zero rows do not certify no in-flight work, historical completeness, or readiness to switch.

## 3. Transition sequence — do not execute without the missing owners

1. Lock exact artifact versions, active flags, source fixtures, caller identity and gateway role/path
   manifest. Resolve clearance revocation/current-authority and real source/coverage acceptance.
2. Verify backups on disposable clones; inventory original stores and broker. Rehearse transaction,
   duplicate/restart/poison scenarios. Do not replace real acceptance with a quiet empty DB.
3. Stop/drain the standalone Report listener/jobs/rebuild writers through the operator/shared
   deployment mechanism. Capture unresolved state; no operator script here changes any row.
4. Confirm old process cannot write. Start the reviewed Support host with separate Report and
   Notification stores/credentials/EMF/Flyway/tx/queues/workers and explicit default-deny host policy.
5. Hoàng Anh switches routes; shared owner supplies runtime/CI. Exercise exact read/ownership/token
   denials and accepted source delivery, replay, pool starvation and source-effect reconciliation.
6. Publish cutover evidence only after all relevant tests execute successfully. Application startup
   or a JAR smoke pass alone cannot close H6-10.4/H6-11/H6-12.

The host/shared changes above are **not implemented by Huy in this scope**. See the
[composition handoff](../handoffs/HANDOFF-HUY-MVP.md). Do not start a second
standalone Report alongside Support on the same DB to simulate a safe canary.

## 4. Rollback without deleting accepted writes

- Stop/drain the new host first. Keep current DB, accepted payments/messages, coverage proofs,
  original bytes, pending refunds and HELD markers. Do not restore a pre-cutover snapshot over them.
- Roll back only to an artifact/profile rehearsed on that current schema and data. Use the same
  original standalone Report migration location/checksums; if incompatible, stop and fix forward.
- Restore routes/callers using owner-controlled deployment configuration, then resume only one
  writer/job/listener set. Do not rename logical event producers or clear consumer claims.
- Reconcile before/after inventories plus business effects; notification already sent is not
  undone by a Report rollback/rebuild. Scheduler fence tokens and in-flight broker work matter.

No rollback/backup restore was performed in this coding batch.

## 5. Repeatable verification (not interchangeable acceptance levels)

```text
mvn -q -pl backend/pharmacy-service,backend/report-service -am test '-Dtest=!**/*IntegrationTest,!**/*PostgresTest,!**/*IT' -Dsurefire.failIfNoSpecifiedTests=false
mvn -q -pl backend/report-service -am -Preport-packaging-acceptance -DskipUnitTests=true verify
mvn -q -pl backend/pharmacy-service,backend/report-service -am test '-Dtest=MvpCutoverInventoryPostgresTest,ReportModulePostgresTest,OutpatientReadPostgresTest' -Dsurefire.failIfNoSpecifiedTests=false
```

The first is selected local regression; self-skipped container tests are not passes. The second
packages real artifacts, checks **every current SQL resource** and forks three JVM probes with
Report target/classes/test-classes removed: scoped reader with missing coverage unavailable,
wrong mode refused, foreign default datasource rejected. The shim asserts Report loaded from
the plain JAR. Mock source ports are limited to the reader probe; no real store/broker is claimed.
The third requires Docker for disposable PostgreSQL tests; report executed/failed/skipped counts.

Packaging probes do not create Support, prove producer acceptance, or validate migration upgrade
against real historical data. Record those gates separately rather than relabel skipped as done.

## 6. H6-12.2 evidence map — refreshed 2026-10-16

This map separates Huy-owned evidence from integrated acceptance. A named test is not evidence for
another service's producer, the live six-service topology, or a test that was not executed. In the
2026-10-16 continuation, only the explicitly listed non-container suites below were rerun; Docker
was unavailable, so container-backed rows remain **NOT RUN in this refresh**.

| Runbook case | Huy-owned evidence | Refresh result / remaining acceptance |
|---|---|---|
| T02 — identity lookup denied/unavailable, no guessed IDs | `PrescriptionIdentityHttpTest`; `PrescriptionIdentityAdapterTest`; `PharmacyCoreDestinationTest` | Packaged Core lookup and owner acceptance not rerun in this refresh. |
| T07 — server price, insufficient multi-drug stock | `OutpatientApiPostgresTest` create/rollback cases | Isolated-PostgreSQL tests exist; **not rerun** in this refresh. |
| T08 — dispense/payment duplicate and concurrency | `OutpatientApiPostgresTest` dispense-race cases; `OutpatientPrescriptionLifecycleApplicationServiceTest` | Lifecycle application suite rerun: 17 tests passed. PostgreSQL race cases not rerun. |
| T09 — Pharmacy restart/recovery | `PharmacyOutpatientRuntimeAcceptanceIT` | Packaged multi-container test not run; Billing V1 publication is separately held by the active handoff. |
| T10 — terminal state and late payment | `OutpatientApiPostgresTest` cancel/expiry/late-grant cases; `PrescriptionClearanceRabbitIntegrationTest` | Isolated PostgreSQL/RabbitMQ acceptance not rerun. |
| T11 — payment/allocation/refund races and wrong episode | Pharmacy wrong-episode/current-authority cases in `OutpatientApiPostgresTest`; Billing ledger tests are Lộc-owned | Huy-side PostgreSQL cases not rerun; Billing allocation/refund acceptance is not Huy evidence. |
| T12 — receipt/grant does not auto-dispense | `PrescriptionClearanceRabbitIntegrationTest`; `PrescriptionCareEventCodecTest`; `OutpatientEventActivationTest` | Codec/activation suites rerun: 23 + 10 tests passed. RabbitMQ grant test not rerun. |
| T13 — Report replay/conflicting bytes | `OutpatientReportRabbitIntegrationTest`; `OperationalContributionPostgresTest`; `OperationalReportFactReceiverTest` | Receiver unit suite rerun: 10 passed. Container-backed replay/conflict cases not rerun. |
| T14 — cash/refund/coverage unavailable vs zero | `OutpatientReportRabbitIntegrationTest`; `FinancialSnapshotReadServiceTest`; `OperationalSnapshotReadServiceTest` | Snapshot-reader suites rerun: 21 + 9 passed. Cash/refund PostgreSQL/RabbitMQ cases not rerun. |
| T15 — replay/publication/owner-only reads | Report replay/publication services and web tests | Publication-service suites rerun: 30 operational + 9 financial passed. Packaged Support/Notification isolation remains unaccepted. |
| T16 — poison, retry, rollback, confirms | Pharmacy/Report Rabbit integration suites and outbox tests | **Not rerun** in this refresh; Docker broker/DB evidence is required. |
| T17 — deferred services remain off | Pharmacy/Report MVP deferred-configuration and profile-alias tests | Pharmacy configuration suites rerun: 2 + 2 passed. Report configuration suites and full six-app flag matrix remain open. |
| T18 — six-service startup and rollback | [Handoff chung Huy, sections 3–4](../handoffs/HANDOFF-HUY-MVP.md#3-support-composition--hợp-đồng-report-đã-cung-cấp) | No Support host or runtime change was made; remains shared-scope work. |
| T19 — Gateway policy and FE smoke | Gateway owner acceptance and frontend workboard | Outside Huy backend scope; no Gateway/FE test was run or claimed. |

The refresh executed 15 non-container test classes across Pharmacy and Report (215 tests,
0 failures/errors), including both services' static inventory-safety tests. It is regression
evidence for the named local logic only. H6-12.2 remains open until the integrated evidence/source
IDs and owner/runtime acceptance required by T02/T07–T19 are recorded; none of the container or
external-owner cases above is closed by these unit results.

## 7. Batch 9 — Docker/source refresh 2026-10-16

Lượt tiếp tục sau section 6 đã có Docker. Các dòng NOT RUN ở section 6 ghi đúng trạng thái
lượt 215 tests trước đó; không còn mô tả lượt mới. [Batch 9 evidence](huy-mvp-batch-9-acceptance.md)
ghi commands, counts, original source SHA-256 và ranh giới nghiệm thu.

- Pharmacy full regression và rerun API bổ sung: 822 unique tests, không failure/error/skip.
  T07/T08/T10/T11 phía Huy có PostgreSQL evidence; upstream test doubles không đóng Billing chain.
- Report full regression và rerun Rabbit bổ sung: 1.330 unique tests, không failure/error/skip.
  T13/T14/T16 có PG/Rabbit retry, rollback, early-refund recovery, duplicate/conflict và fault
  sau commit trước ACK; legacy queue backlog không bị drain. Không gọi fault này là JVM kill.
- Source acceptance: packaged Core/Clinical qua public HTTP phát completion ngoại trú thật,
  Report giữ một contribution sau listener restart/replay; conflicting bytes đi DLQ, coverage=0.
  Một integration test passed, không skipped. Emergency override không là paid-flow evidence.
- Report packaging acceptance: 5 passed, không failure/error/skip; artifact resources và ba JVM
  library probes đạt. Support host và Notification composition vẫn cần owner nghiệm thu riêng.

H6-08.4 đóng LOCAL. H6-12.2 vẫn mở vì chưa có integrated Billing/Notification/Gateway/Support
acceptance và operator coverage/cutover. Owner queue ở active activation/Support handoffs là
điều kiện tiếp tục; không sửa hoặc tự đóng task của owner khác trong phạm vi Huy.
