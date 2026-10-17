# Support host — Report + Notification

Support is one Spring Boot executable and Eureka registration (`support-service`, port 8087).
It imports the existing Report and Notification `lib` artifacts through explicit module entry
points. Business packages and public wire contracts remain in their original modules. Clients
continue through Gateway: `/api/v1/reports/**` and `/api/v1/notifications/**`.

## Store and security boundaries

| Context | Required environment | Named infrastructure | Flyway resources |
|---|---|---|---|
| Report | `MEDIFLOW_REPORT_DB_URL`, `MEDIFLOW_REPORT_DB_USER`, `MEDIFLOW_REPORT_DB_PASSWORD` | `reportDataSource`, `reportEntityManagerFactory`, `reportTransactionManager`, `reportJdbcTemplate` | `classpath:mediflow/report/db/migration` |
| Notification | `MEDIFLOW_NOTIFICATION_DB_URL`, `MEDIFLOW_NOTIFICATION_DB_USER`, `MEDIFLOW_NOTIFICATION_DB_PASSWORD` | `notificationDataSource`, `notificationEntityManagerFactory`, `notificationTransactionManager`, `notificationJdbcTemplate` | `classpath:mediflow/notification/db/migration` |

Each context keeps its existing PostgreSQL store, Flyway history and unchanged SQL bytes.
There is no default datasource/transaction manager, schema merge, migration repair or cross-store
transaction. Each Hikari pool defaults to four connections (configurable 1–16 using its
`MEDIFLOW_*_DB_POOL_SIZE`). Production credentials must be separately restricted to their store;
Compose's postgres credentials are development defaults.

Profiles `report-module,notification-module,outpatient-mvp` are included by the host. Standalone
launchers and OpenAPI configurations are excluded from module scans. JWT filters, security chains,
exception advice, repositories and messaging beans are scoped/named; method roles and patient
ownership remain unchanged. A final host chain denies unmatched paths; only `/actuator/health`
is public. Report permissions do not grant Notification permissions.

Existing queues/DLQs remain durable and separate. Notification retains `notification.q`, bounded
three-attempt retry and its own listener factory (1–2 consumers, prefetch 10). Report retains its
legacy/specialized factories, queues and `reportTaskScheduler`; its legacy factory also bounds
consumers to 1–2 with prefetch 10. No queue purge, automatic DLQ replay or HELD release occurs
when switching hosts. One JVM still means a shared process failure domain.

## Build and run

From the repository root:

```powershell
mvn -q -pl backend/support-service -am -DskipTests package
java -jar backend/support-service/target/support-service-0.0.1-SNAPSHOT.jar
```

Supply the six store variables above, `MEDIFLOW_JWT_SECRET`, RabbitMQ
`MEDIFLOW_RABBIT_HOST/PORT/USER/PASSWORD` and `EUREKA_DEFAULT_ZONE` as needed. Build emits
both module plain libraries and standalone executables, plus the Support executable.

Compose now selects **Core, Clinical, Lab, Pharmacy, Billing, Support** by default, alongside
infrastructure/frontend. Gateway forwards both logical Support routes to `lb://support-service`
without changing public paths. The source retains legacy Report/Notification launchers for
rollback; Inpatient/Surgery require the separate `deferred` profile.

New outpatient intake/read gates remain OFF. Enable only after source/coverage acceptance:
`MEDIFLOW_CARE_FINANCE_V2`, `MEDIFLOW_REPORT_OUTPATIENT_CONSUMER_ENABLED`,
`MEDIFLOW_REPORT_OUTPATIENT_READ_ENABLED`, `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED`,
`MEDIFLOW_NOTIFICATION_OUTPATIENT_PRESCRIPTION_CONSUMER_ENABLED` and
`MEDIFLOW_NOTIFICATION_REFUND_CONSUMER_ENABLED`. Their individual prerequisites still apply.
Deferred Report V2/Admission/Surgery consumers cannot run in this host, even with override flags.

## Cutover and rollback

1. Use an approved current clone first. Record inventory, source commit, flags, Flyway histories,
   queue depths and pending/HELD evidence using the [existing runbook](../../docs/plans/huy-mvp-cutover-runbook.md).
2. Stop/drain both standalone Report/Notification writers, consumers and jobs before starting
   Support against the same stores. Never run the old and new writers concurrently. Do not
   delete volumes, purge queues or restore an older database snapshot.
3. Start Support, verify both module APIs/roles/ownership and reconciliation, then switch Gateway.
   The default route destinations are Support; configure legacy destinations while preparing
   a staged switch if the old apps are still running.
4. Rollback: stop/drain Support first, retain current stores/queues and accepted writes. Start only
   the two explicit legacy services with
   `docker compose --profile legacy-support up -d report-service notification-service`.
   Set `MEDIFLOW_REPORT_DESTINATION=report-service` and
   `MEDIFLOW_NOTIFICATION_DESTINATION=notification-service`, then recreate Gateway. Do not run
   an unqualified `up` with `legacy-support`: that also selects the default Support service.

## Verification

Non-container tests load both real component graphs with mocked external infrastructure,
exercise both JWT policies/advice together, named transaction selection and startup guards.
They establish wiring/security behavior; real store/broker acceptance is separate:

```powershell
# Docker required: module suites plus two PostgreSQL stores and RabbitMQ host isolation tests
mvn -q -pl backend/support-service -am test
# Docker required: also launch the actual packaged host, upgrade existing histories and restart
mvn -q -pl backend/support-service -am -Psupport-acceptance verify
# Build all launchers, then verify the actual six deployments through Gateway/discovery.
mvn -q -DskipTests package
mvn -q -pl backend/support-service -am -Psix-service-acceptance -Dtest=__NoUnitTests__ -Dsurefire.failIfNoSpecifiedTests=false verify
```

`SupportStoreIsolationTest` covers original producer bytes consumed by both modules, private
history ownership, Report replay without another notice and Notification rollback/DLQ recovery
without rolling back Report. `SupportPackagedAcceptanceIT` launches the real JAR against stores
first migrated from the original standalone locations and checks accepted writes after restart.
Two pool-exhaustion cases also hold every connection of each context in turn, checking that
the other commits and the blocked delivery recovers from its DLQ without duplicate effects.
The `Service integration` workflow requires Docker, executes the packaged profile and rejects skips.

`SixServiceOutpatientAcceptanceIT` launches the six business executables plus Gateway/Eureka
against eight disposable context databases and RabbitMQ. It creates accounts, staff, patient,
appointment, actual EXAM payment and an outpatient prescription through public APIs, uses the
actual Billing payment/grant to authorize dispensing, and reconciles Clinical/Report/private
Notification effects, stock and ledger/cash/refund counts. Billing pays while Pharmacy is stopped;
the restarted Pharmacy must recover the durable grant without automatically dispensing.
It checks roles, patient ownership, refresh tokens, nested paths, retry
idempotency, no automatic dispensing and the discovery topology. It also stops Support, uses
both standalone launchers against the current stores, accepts a new write during rollback,
then restarts Support without losing that write or earlier effects. This does not replace an
approved historical-data/broker inventory or claim the separate Lab paid-workflow acceptance.
Logs and source IDs are retained in `target/six-service-acceptance/`; the EXAM grant evidence
is `target/six-service-paid-exam-evidence.json`. Run the two acceptance profiles separately.

**2026-10-17 runtime refresh:** Docker is available. Report's full regression plus the
impacted refund-recovery rerun covers 1,331 unique tests;
Notification's full 279 tests, Support's 18 tests (including five real store/broker isolation
cases) and the actual packaged restart test passed with zero failures/errors/skips.
The packaged health check was corrected to retry connection refusal during startup while
still failing on process exit or assertion failures. Historical Docker-unavailable notes in
the [single Huy handoff](../../docs/handoffs/HANDOFF-HUY-MVP.md) describe earlier runs only.

The actual six-app acceptance also passed, including paid EXAM/Rx, Pharmacy restart and
Support → legacy launchers → Support rollback on the current disposable stores, retaining a
new write. The complete verification selection totals **2,128 unique tests / 195 classes**,
zero failures/errors/skips; Billing/Pharmacy are selected suites, not full module reruns.
Five current JSON contract checks also passed. Commands, actual source IDs and original-byte
hashes are recorded in the [closeout runbook](../../docs/plans/huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17).
The subsequent persisted-volume rehearsal closes Huy's plan at **48/48 for the existing local
development environment**. It inventories cold copies of the actual stopped Compose volumes,
including both original Rabbit node identities. Pharmacy's five legacy tables have zero rows;
Report/Notification have no public tables, and both broker nodes have zero queues. No historical
records were invented and no pre-Flyway Pharmacy schema was baselined or migrated.

Actual standalone → Support → broker restart → legacy rollback → Support preserves four new
API writes, complete Report rows/Flyway histories and one persistent administrative probe's
bytes. Original-volume fingerprints remain identical. The new IT passed without skips;
combined related selection is **2,129 tests / 196 classes**, zero failures/errors/skips.
The operator script also passed its running-source refusal check. All clone containers stop;
clone volumes and evidence remain. Reproduce from the repository root with Docker available:

```powershell
& ./backend/support-service/operations/Invoke-HistoricalSupportRehearsal.ps1
```

This script targets only local Compose defaults and refuses a source with running writers.
It uses a fresh, byte-identical source build snapshot to avoid IDE output contamination;
the `historical-support-acceptance` Maven profile requires its validated clone environment.
See [runbook section 9](../../docs/plans/huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17)
for source fingerprints, actual evidence and limitations. The retained pre-Flyway Pharmacy
schema is not certified to run unchanged with the new artifact. Production activation,
another environment's historical data, coverage and the separate paid Lab workflow remain
outside this local rehearsal.
