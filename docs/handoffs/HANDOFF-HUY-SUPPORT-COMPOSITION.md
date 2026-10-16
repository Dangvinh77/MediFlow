# Huy → Lộc / shared owner / Hoàng Anh: Support composition

2026-10-15. Source: master `e9480758` + Huy worktree changes, not committed/pushed.
Status: Report export implemented locally; Support/Notification/Gateway/runtime acceptance OPEN.
User scope in this batch: Huy modules only; no foreign/shared production changes.

Canonical scope: [six-service design](../plans/01-system-design.md),
[contracts](../plans/03-contracts.md), [Huy plan](../plans/2026-10-14-huy-backend-mvp-adjustment-plan.md).
This is the single technical composition gate; other Huy activation gates remain in
[outpatient activation](HANDOFF-HUY-MVP-OUTPATIENT-ACTIVATION.md).

## Report export supplied by Huy

- Dependency: `com.mediflow:report-service:0.0.1-SNAPSHOT`, classifier **lib**. Never depend
  on the default Boot executable artifact. It remains available for standalone rollback.
- Import only `com.mediflow.report.infrastructure.config.ReportModuleConfiguration`.
  Active profiles: **report-module,outpatient-mvp**. Earlier `mvp-outpatient` remains an alias.
  The module rejects composition without either explicit outpatient profile.
- No broad scan of ReportServiceApplication or all com.mediflow. The export scans Report only,
  with qualified component names and excludes standalone/Boot/persistence bootstrap duplication.
- Report credentials must be explicit: `mediflow.report.datasource.url/username/password`.
  No fallback to host spring.datasource or Notification database. PostgreSQL URL only;
  `maximum-pool-size` defaults 4, allowed 1..16, minimum idle 0, connection timeout 3 seconds.
- Beans: reportDataSource, reportFlyway, reportEntityManagerFactory (unit report),
  reportTransactionManager, reportJdbcTemplate. No @Primary manager or generic transactionManager
  alias in module mode. All owned transaction boundaries and native JDBC injection are qualified.
- Report Flyway uses **classpath:mediflow/report/db/migration**; packaging copies original bytes
  there while retaining original standalone resources. Do not scan generic db/migration in host:
  Notification and Report migration versions overlap. No checksum repair/renumber/data copy.
- Report security chain name reportSecurityFilterChain, order 20, matches only /api/v1/reports/**
  in module mode. reportJwtAuthFilter/registration names are independent; servlet auto-registration
  is disabled. Report advice is reportGlobalExceptionHandler and scoped to Report web package.
- Report Rabbit beans have own names, including reportEventsExchange/reportDeadLetterExchange,
  reportRabbitJsonMessageConverter/reportLegacyListenerFactory. Existing exchange/queue wire names
  remain unchanged. New selected queue is report.outpatient-v1.q with its own .dlq and listener.
- Report jobs explicitly use reportTaskScheduler, 1..2 threads, prefix report-recovery-.
  Optional Report reader clock is qualified reportClock; no accidental use of a foreign Clock.
  Zone injection uses reportZoneId. Supply ONE host-configured common ObjectMapper for envelope
  compatibility; do not introduce competing unqualified mappers or full-V2 customizers in MVP.
- All V1 API/intake/read gates remain OFF until corresponding authority/source/coverage acceptance.
  Empty coverage is unavailable, not zero. HELD deliveries do not become enabled by profile switch.

## Owner tasks (not implemented in this batch)

| Owner | Required work | Acceptance |
|---|---|---|
| Lộc | Export Notification as a plain module; namespace beans/advice/filter and /api/v1/notifications security; own DS/Flyway/EMF/tx/JDBC/listeners/delivery scheduler | No bean overriding, Report has no Notification DB/contact access; owner-only human reads remain enforced |
| Shared build + Huy/Lộc | Assign/create backend/support-service, explicitly import each module, register ONE support-service discovery/process; exclude unscoped default persistence/repository bootstrap | Packaged host with two real stores, same original SQL checksums, no fallback or @Primary masking wrong store |
| Shared runtime | Replace separate Report/Notification runtime entries with Support, use six-app MVP profile, preserve DBs/queues/pending; update reactor/Compose/CI and stop standalone writers before starting host | Fresh/upgrade, read/write/rollback and restart tests; no duplicate workers/writers |
| Hoàng Anh | Gateway /api/v1/reports/** and /api/v1/notifications/** → lb://support-service; exact method/path/roles; deferred routes off in MVP | Full Gateway security tests, no role broadening or service-token/human-token substitution |
| Vinh/shared | Map Clinical/Lab profile outpatient-mvp and their distinct feature/env names explicitly with Huy profiles | No V0 fallback or implicit deferred producer/listener activation |

Notification jobs must bind their OWN scheduler. Do not leave them to borrow Report's named
scheduler via type-based default selection. Host management/default-deny security must be explicit
and ordered after module chains; Report does not provide a catch-all host chain in module mode.

Current Compose still builds organization-service/patient-service (removed source modules),
Report and Notification separately and includes Inpatient/Surgery. Current Gateway Report/Notification
targets remain standalone. Those shared/foreign paths were intentionally NOT edited by Huy.

## Required integrated proof

2026-10-15 follow-up: [owned cutover runbook](../plans/huy-mvp-cutover-runbook.md) supplies separate
read-only Pharmacy/Report inventory resources and operator/rollback checks. Report artifact tests
now fork real JVMs from the lib JAR and compare all 24 SQL copies (latest version V27, not 27 files).
These local probes do not establish real store/broker/host acceptance or execute a cutover.

1. Package/start host against two disposable PG16 stores and RabbitMQ; only Report migrations run
   on Report DB and Notification migrations on Notification DB. Reject missing/wrong credentials.
2. Exercise each module with both transaction managers present, unknown URLs denied, ADMIN/MANAGER
   Report reads and PATIENT owner-only notices; refresh/service JWTs denied on human endpoints.
3. Replay Report evidence while Notification works/fails: no notice resend, financial writes or
   shared worker/pool starvation. Failure domain remains one JVM; do not claim absolute isolation.
4. Preserve original producer IDs/event bytes and all HELD/pending state; stop/drain old writers,
   exercise rollback on current DB, never restore an old snapshot over accepted writes.
5. Run Gateway-to-backend outpatient source/clearance/dispense/cash/refund/notice acceptance.

Batch 6 supersedes the earlier Docker-unavailable status: ReportModulePostgresTest now passes
with two isolated test-owned PostgreSQL stores. ReportModuleUpgradePostgresTest verifies V26 →
namespaced V27 → standalone resource-location validation without changing historical checksums
or losing a new write. Five artifact/JVM tests also pass. See the
[batch 6 evidence](../plans/huy-mvp-batch-6-acceptance.md) for exact scenarios and counts.
These establish Report-owned preparation, not Notification composition or H6-11 host acceptance.
Remove this handoff only after both owners' checks pass and lasting configuration rules are
promoted into canonical docs.
