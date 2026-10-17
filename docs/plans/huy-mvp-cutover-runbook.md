# Huy MVP — preparation, inventory and rollback checklist

Created 2026-10-15; refreshed 2026-10-17 for Pharmacy/Report and the explicitly assigned
Billing/Notification/Support integration. Sections 1–5 are an operator checklist; dated
verification below distinguishes isolated acceptance from a historical deployment cutover.
Canonical scope and global acceptance remain [system design](01-system-design.md),
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

## 3. Transition sequence — assigned local rehearsal recorded in section 9

1. Lock exact artifact versions, active flags, source fixtures, caller identity and gateway role/path
   manifest. Resolve clearance revocation/current-authority and real source/coverage acceptance.
2. Verify backups on disposable clones; inventory original stores and broker. Rehearse transaction,
   duplicate/restart/poison scenarios. Do not replace real acceptance with a quiet empty DB.
3. Stop/drain both standalone Report/Notification listener/jobs/rebuild writers through the assigned
   deployment mechanism. Capture unresolved state; no operator script here changes any row.
4. Confirm old process cannot write. Start the reviewed Support host with separate Report and
   Notification stores/credentials/EMF/Flyway/tx/queues/workers and explicit default-deny host policy.
5. The assigned operator switches routes using the shared runtime/CI. Exercise exact read/ownership/token
   denials and accepted source delivery, replay, pool starvation and source-effect reconciliation.
6. Publish cutover evidence only after all relevant tests execute successfully. Application startup
   or a JAR smoke pass alone cannot close H6-10.4/H6-11/H6-12.

The host/shared changes above are implemented under the user's task-scoped assignment of
2026-10-17. Runtime verification is recorded in the existing
[common handoff](../handoffs/HANDOFF-HUY-MVP.md). Do not start a second
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

## 8. Integrated backend closeout — 2026-10-17

Source: Huy, HEAD `27b8832d25daee8218250224af3f8334318a37df` plus the current uncommitted
working tree. The user explicitly assigned related Billing/Notification and Support/shared
integration. Core/Clinical/Lab production sources were not changed. Docker 29.6.2,
PostgreSQL `16-alpine` (16.14 in the packaged run), RabbitMQ `3.13-alpine` for the six-app
test; module suites also use their checked-in PostgreSQL/RabbitMQ image tags.
All stores and business records below were created in disposable test infrastructure.

### Executed verification

| Selection | Classes | Unique tests | Fail / error / skip |
|---|---:|---:|---|
| Notification full suite | 30 | 279 | 0 / 0 / 0 |
| Report full regression plus impacted refund-recovery rerun | 137 | 1,331 | 0 / 0 / 0 |
| Support wiring/security and real two-store/broker isolation | 5 | 18 | 0 / 0 / 0 |
| Billing selected Rabbit, JWT/security/web and payment-ledger suites | 6 | 133 | 0 / 0 / 0 |
| Pharmacy selected API/clearance/inventory PostgreSQL/Rabbit suites | 3 | 26 | 0 / 0 / 0 |
| Gateway full regression, including exact Pharmacy path/role matrix | 12 | 339 | 0 / 0 / 0 |
| Actual packaged Support migration/restart | 1 | 1 | 0 / 0 / 0 |
| Actual six business apps through Gateway/discovery and current-store rollback | 1 | 1 | 0 / 0 / 0 |
| Total, without counting reruns twice | **195** | **2,128** | **0 / 0 / 0** |

Report ran its full 1,330 tests first; the changed ten-case Rabbit suite replaces its earlier
nine-case result, giving 1,331 unique tests. Billing/Pharmacy figures are selected suites,
not fresh full-module totals. Five cross-service JSON checks also passed against current
compiled producers/consumers. The broader contract script's module-test run was cancelled;
it is not claimed as a completed full run. Earlier full Pharmacy counts remain
dated historical evidence, not additions to this table.

Reproduction from repository root, with Docker available (run the two acceptance profiles
separately):

```powershell
mvn -q -pl backend/support-service -am -Psupport-acceptance verify
mvn -q -pl backend/billing-service -am '-Dtest=OutpatientPublicationRabbitIntegrationTest,PrescriptionChargeRabbitIntegrationTest,JwtAuthFilterTest,SecurityConfigTest,FinancialClearanceLookupControllerTest,LedgerPaymentIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' package
mvn -q -pl backend/pharmacy-service -am '-Dtest=OutpatientApiPostgresTest,PrescriptionClearanceRabbitIntegrationTest,MvpCutoverInventoryPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -q -pl backend/gateway -am package
mvn -q -DskipTests package
mvn -q -pl backend/support-service -am -Psix-service-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

The last command intentionally selects no unit tests after the preceding module verification;
Failsafe must execute `SixServiceOutpatientAcceptanceIT`, not skip it. CI requires both
packaged acceptance XML reports, nonzero test counts and zero failures/errors/skips, and
uploads the raw evidence/logs. The test prerequisite is the current packaged reactor, not
stale local launchers.

### Actual effects and source identity

`SixServiceOutpatientAcceptanceIT` starts Core, Clinical, Lab, Pharmacy, Billing and Support,
plus Gateway/Eureka. Eureka contains exactly those six business apps and Gateway; legacy
Report/Notification and deferred Inpatient/Surgery are absent. Staff accounts and actual logins,
patient, appointment, check-in, EXAM payment, prescription, Rx payment and dispensing use
public APIs. SQL reconciles only disposable test stores; no business effects are inserted by SQL.

- Patient `d6711da5-119b-4ec6-8b5f-99773af28ed4`; outpatient episode `787de902-b35f-495f-ab64-4ae49dba9db8`; record `5309caa1-f2e4-4aaa-8c38-81efe91f9d2f`.
- Prescription `1cf2a26b-dc72-4926-b1b8-d8c5dd98ce0b`; Rx payment transaction `d2b27691-a14d-4af1-b6f4-ee9d61f4cb0c`; private notice `cda7314d-038a-4c07-92ec-346ed0f89100`.
- Filled event `73228c84-840c-47fa-a5cf-883fb7f8878b`, original-byte SHA-256 `72963cee52d2bd0bd7d5a0adc8a5513e163c32f76a0c66e093169b6ecc566126`.
- EXAM clearance `4d76791d-11d8-4a7c-8769-bae0872f3319`, original grant SHA-256 `a0cae6405486447726fc0508340d74e76fbe9fa569f7f936720d5311431a00ba`.
- Harness source SHA-256 `749bc9b465b83cfa8da7beffc002feff7356b8deadf68dc234b059d4e33f520d`.

Unpaid exam/dispense and wrong-role commands are denied. Pharmacy is stopped before the real
Rx payment and restarted against its current store: the durable grant arrives, stock remains
20 until an authenticated dispense, then becomes 17. Retrying payment/dispense preserves one
transaction/slip/stock effect. Clinical has one attachment, Report one filled contribution,
Notification one private filled notice. The Rx charge/payment/current grant counts are one
each; Report contains two cash receipts totaling 130,000 VND (EXAM 100,000 + Rx 30,000), and
the happy path contains zero completed refunds. Separate ledger/Report suites exercise real
refund, early refund and negative allocation cases; zero happy-path refunds is not refund
workflow proof by itself.

Support is stopped before either standalone writer starts. Both legacy launchers use the
same current stores; a new in-app write is accepted during rollback. Both legacy writers stop
before Support restarts. Old and new notices, Report journal bytes and existing HELD outbox
rows survive; no old snapshot is restored, no publication is enabled. This is an isolated
current-store rehearsal, not approved historical-data deployment cutover.

### Evidence-to-case map

| Case | Current evidence and boundary |
|---|---|
| T02 | Real Core/Clinical identity/context in six-app creation; Billing 28 JWT + three security + three lookup web cases; existing source-denial tests retained. |
| T07 | Pharmacy API PostgreSQL server-price/multi-drug rollback cases; actual 3 x 10,000 VND issuer path. |
| T08 | PostgreSQL dispense races plus actual payment/dispense retry: one effect. |
| T09 | Real issuer pays while Pharmacy is down; actual JVM restart recovers the durable grant. Earlier batch 8 separately records crash-after-effect/ACK/finalize scenarios. |
| T10 | Fresh Pharmacy terminal/late-grant cases and Billing 31 prescription Rabbit cases; no terminal reopen or false completed refund. |
| T11 | Billing 60 real ledger/payment/allocation/refund/race cases, including 20 current PRESCRIPTION authority scenarios; Pharmacy exact-target negatives. |
| T12 | Actual payment/grant leaves stock unchanged until the authorized command; fresh clearance Rabbit suite. |
| T13 | Full Report plus Support original-byte replay/conflict/isolation; one actual filled source contribution. |
| T14 | Actual cash 130,000 VND, zero happy-path refunds; Report PG/Rabbit early-refund/recovery and coverage tests. External historical completeness/coverage approval is not manufactured. |
| T15 | PATIENT own notice 200, other patient/MANAGER 403, refresh 401; Report replay cannot resend a notice. |
| T16 | Fresh Billing Rabbit 39 cases, Notification full suite, Report full/impacted Rabbit suite, five Support isolation/pool/DLQ cases. Prior Pharmacy confirms/broker/crash matrix remains separately dated in batch 8. |
| T17 | Current six-app discovery and default Compose topology; module deferred guards remain active and all 93 tracked SQL migration bytes match HEAD. |
| T18 | Two-store packaged history/restart plus six-app current-store rollback retaining a new write. Approved historical-clone/broker rehearsal remains open. |
| T19 | Fresh Gateway 339 cases and actual role/token/ownership/nested-path checks. No frontend smoke is claimed by this backend run. |

### Configuration and remaining operational work

The harness explicitly enables only the selected outpatient flow: Pharmacy identity/Clinical
context/API/new-event/clearance intake and current authority; Billing ledger/source discovery/
EXAM and PRESCRIPTION issuance/publication/lookup; Report outpatient intake/read; Notification
care-v1 and outpatient-prescription intake. Billing purposes are `EXAM,PRESCRIPTION`, price
code `DRUG`; the checked-in harness records the acceptance references and all exact flags.
Lab participates in topology/health only here; its separate paid workflow is not retested.
Report reads without accepted external coverage remain unavailable rather than invented zero.
Legacy-version traffic rejected by a bounded compatibility consumer stays in its durable DLQ;
this run neither purges that evidence nor certifies a historical backlog as drained.

Default Compose uses six business deployments. New activation/publication gates remain OFF
unless explicitly supplied; standalone Report/Notification and Inpatient/Surgery require
their separate profiles. No migration, fixture or original event was rewritten for acceptance.
Billing lookup now admits the exact Pharmacy service credential and validates current Rx
ledger/allocations, returning the original retained grant instant to avoid PostgreSQL rounding
mismatch. Report's scheduled outpatient refund poll now opens its own named read transaction.
Gateway enumerates Pharmacy paths/methods and preserves the controller's separate cancel/dispense
role sets; unsupported nested paths are denied before forwarding.

Raw evidence is under `backend/support-service/target/six-service-acceptance/`:
`paid-prescription-evidence.json`, `prescription-filled-original.json`,
`prescription-filled-sha256.txt` and each child JVM log. The EXAM artifact is
`backend/support-service/target/six-service-paid-exam-evidence.json`; XML is in the module
Surefire/Failsafe directories. These generated files are ignored locally and uploaded by CI.

Huy closes H6-06.2/.4, H6-11.2/.3/.4 and H6-12.1/.2: **46/48**. H6-02.3 and H6-12.3 remain
open for an approved historical clone and broker inventory/cutover rehearsal. Their complete
operator checklist is consolidated in the [single Huy handoff](../handoffs/HANDOFF-HUY-MVP.md).
No additional feature code or foreign-owner handoff is required by the exercised Rx/Support
slice. Global M6 completion and production activation are separate decisions.

## 9. Local persisted-volume inventory and cutover — 2026-10-17

The user explicitly requested execution of H6-02.3 and H6-12.3. The source is the actual
stopped development Compose volumes `mediflow_mediflow-pgdata` and
`mediflow_mediflow-rabbitdata`, not regenerated databases or the older audit/test volumes.
No native PostgreSQL/RabbitMQ service or application listener was found on the configured
local ports. This closes the two tasks for the existing local development environment;
it is not a production migration approval or a claim about another machine's data.

### Read-only inventory

The script rejects running source-volume users before copying. It mounts each source read-only,
makes cold copies, records SHA-256 fingerprints before/after, and starts only those copies.
It inventories every persisted Rabbit node identity, retaining its existing node name instead
of booting an unrelated empty node. Database observations use REPEATABLE READ / READ ONLY,
ten-second statement/one-second lock limits and ROLLBACK. Only counts and row digests are
exported; no patient records, tokens, passwords or original business event bodies are exported.

Observed source state:

| Source | Observed state / disposition |
|---|---|
| Pharmacy | Five pre-Flyway tables: drug, prescription, prescription_line, dispense_slip, processed_event; every table has zero rows. No Flyway history, reservation/outbox/receipt/scheduler tables exist in this old schema. Missing tables are `ABSENT_IN_LEGACY_SCHEMA`, not a successful query returning zero. Keep that schema unchanged; no baseline/repair/migration was applied to Pharmacy. There are no persisted Pharmacy records to classify/release. |
| Report | No public tables or Flyway history in the existing source store. No persisted Report journal, refund, coverage or rebuild state exists there. Original store remains unchanged; the copied store is initialized by the real standalone launcher. |
| Notification | No public tables or Flyway history in the existing source store. No persisted notices/outbox/receipts exist there. Original store remains unchanged; only the copy receives rehearsal writes. |
| Rabbit | Existing nodes `rabbit@95d905c52c13` and `rabbit@fb86c4142a76` both inspected under their original identities; each has vhost `/` and zero queues. No historical queue/backlog/DLQ is purged or reclassified. |

Empty observations do not prove external source completeness. Here they establish that these
specific stopped local volumes contain no historical business work to migrate. No HELD release,
deferred activation, inferred authority, guessed patient/episode relationship or fake historical
coverage was needed. The Pharmacy tables/row digests match before/after.

### Executed cutover and reconciliation

`HistoricalSupportCutoverIT` runs actual packaged applications on the cold copies:

1. Start the two standalone launchers; accept a new IN_APP notification through the actual API.
2. Stop both writers, start Support, read the earlier notice with its patient credential, deny
   another patient's credential, read Report and accept another notification.
3. Stop Support. Restart the copied RabbitMQ node; an isolated durable retention-probe queue
   still has exactly one persistent message with identical bytes. It is an administrative test
   probe, not a fabricated business event, and is left retained on the copy.
4. Start only the legacy pair against the current copied stores. Verify prior notices/Report,
   accept a write during rollback, then stop both.
5. Start only Support again; verify earlier notices, accept the fourth write and stop.
   Every previously accepted notification row retains its SHA-256. Report rows and both Flyway
   histories retain their complete row fingerprints throughout the host switches.

The launcher guard prevents overlapping Support/legacy writers. The script stops all clone
containers at completion and retains their volumes/evidence; original services/volumes are
never started or changed. Six-app topology is separately proven by section 8; no other owner's
historical schema was migrated merely to repeat the topology count.

Result: **1 test / 1 class, zero failures/errors/skips**, plus the running-source refusal probe.
Together with section 8's selection: **2,129 unique tests / 196 classes**, no double-counted
reruns. This is the related selection, not a fresh full Billing/Pharmacy/global backend suite.
The five JSON checks remain separately recorded.

Evidence run: `mediflow-h6-20261017211337-8ca8df`, completed `2026-10-17T21:17:17.2218207+07:00`.

- Original PostgreSQL volume SHA-256 before/after: `67efffd44dc0d3019151653f4bc5eb44811f5fbea84700ef23b8e54948a66b1e`.
- Original Rabbit volume SHA-256 before/after: `6b5cda89280fa1eea1fc863707740b9e1d0a9009e8e9f41e411d27b836ff0527`.
- Cutover proof SHA-256: `6606ce3ce7f460f8b6775d74200cd3dae41af956de9400a13eac2e2cb5c93dda`.
- Four accepted notice IDs: `ec547835-1d53-4818-b21a-c59a490e0df7`, `73aaf39c-d758-4685-9cd9-62a8613b25e1`, `e586e074-5433-4422-9ee8-3702959b9964`, `05269ee9-fd2b-4a79-b327-e37ddf559897`.
- Retained probe body SHA-256: `d2eed3ddf1d016da3f3b2985707ac86a0e73e6240b5fe6083263537a33974d68`.
- Harness SHA-256: `a6564ad501b9691204539f1467b098ba3cb66398f2d5911f03c0ab2cdd906fa7`; operator script SHA-256: `4d6ab1962854e4ee9656fc21b15964103b5b8cb2f26a812f6808688e640efadb`.

Raw inventory JSONL, both original-node queue inventories, source manifest, exact staged-source
hash list, XML, child logs, `cutover-proof.json` and `reconciliation.json` are under
`backend/support-service/target/historical-support-rehearsal/mediflow-h6-20261017211337-8ca8df/`.
The build snapshot uses exact current source bytes and fresh Maven output to avoid an IDE's
error-stub classes contaminating packaged acceptance. No existing build/evidence folder is
deleted. The reproduction command creates a new run; it refuses to overwrite an existing one:

```powershell
& ./backend/support-service/operations/Invoke-HistoricalSupportRehearsal.ps1
```

The script targets the repository's local Compose defaults. Do not point it at a production
volume or call the Maven profile directly without its validated clone environment. An
independent source-volume refusal check is recorded at
`backend/support-service/target/historical-source-guard-proof.json`.

**H6-02.3 and H6-12.3 close; Huy plan 48/48 for this local environment.** Existing default
activation/publication gates remain OFF. A future environment with real historical data needs
its own source inventory/reconciliation; this run neither signs its coverage nor performs
production activation. Section 8 and earlier batch statements that these two tasks were open
describe the state before this requested rehearsal.
