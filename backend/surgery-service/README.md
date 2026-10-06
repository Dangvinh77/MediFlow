# Surgery Service

Owner: Huy (`LQHuy0210`). Port `8091`, owned DB `mediflow_surgery`, base path `/api/v1/surgery`.
Design: [bounded context](../../docs/ai/services/surgery.md), [V2 specification](../../docs/eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md).

## Implemented routes — 2026-10-05

| Method/path beneath `/api/v1/surgery/cases` | Roles | Behavior |
|---|---|---|
| GET base path | ADMIN, MANAGER, DOCTOR, NURSE | Bounded, stable board projection |
| GET `/{id}` | Same four roles | Redacted detail, planned versus actual, readiness snapshot |
| POST `/{id}/preop` | ADMIN, DOCTOR | REQUESTED → PREOP with receipt/revision |
| POST `/{id}/cancel` | ADMIN, DOCTOR | Pre-start only, exact booked revision release |
| PUT `/{id}/schedule` | ADMIN, MANAGER, DOCTOR | Prepare/replace a draft, never automatically READY or reserved |

All routes require verified human access tokens and the business gate. Mutation bodies contain
expected revisions and business input only; actors come from signed identity. Unknown fields such as
`ready`, `actorId` or amounts are rejected. Mutations require `Idempotency-Key` (maximum 160 characters).
Matching replay returns the original outcome; different intent conflicts. Account IDs are not staff
IDs. Gateway mirrors exact roles/methods and denies other Surgery paths. Examples: [surgery.http](surgery.http).

ADMIN/MANAGER read across departments. DOCTOR/NURSE require signed `staffId` and fresh active
Organization authority; scope is its current department, not a filter or JWT department. Foreign
detail is opaque 404; foreign department filter is empty. Dependency outage/stale authority is 503,
not a successful empty board. Detail omits indication/narrative, signatures, evidence IDs and
financial amounts. Actual performed codes/quantities are separate from the planned procedure.
`readiness.snapshotValidNow` describes cached evidence only, **not new permission to START**.

Filters: `departmentId`, `status`, `requestedFrom/requestedUntil`,
`scheduledFrom/scheduledUntil`, UTC half-open intervals (scheduled filtering uses overlap).
Page defaults 0/20, maximum size 100; requestedAt descending then caseId ascending.
Board has no history loading or per-row REST fan-out. Read transactions never mutate evidence.

Draft scheduling uses fresh Organization department/room/full-interval capability authority and,
for admission cases, Inpatient's exact medical window. Lookups precede locks; freshness is checked
again after waits. Replacing READY/SCHEDULED invalidates to PREOP, releases only the exact old
revision and stores new draft/history/receipt atomically. DB/validation failure rolls back all changes;
old evidence/history remains. Required clinical team composition is not inferred from job titles.

Room/capability departments must match the case; missing relationships fail 503, foreign departments
fail 422 before mutation. Shared readiness invalidation verifies the case/schedule ID and revision
pinned in the snapshot before releasing resources. Stale/foreign schedule data conflicts with no effect.

Errors use the shared envelope/correlation: 400 malformed input, 401 missing authentication,
403 forbidden, 404 missing/out-of-scope, 409 receipt/revision conflict, 422 business denial,
503 unavailable authority and redacted 500 for unexpected failures.

## Clearance intake and feature gates

All three Surgery properties default false:
`mediflow.features.surgery.enabled`, `mediflow.surgery.messaging.consumers.enabled`,
`mediflow.surgery.messaging.producer.enabled`. Consumer registration and producer dispatch each
require the business gate plus their own gate; all eight combinations are tested.
Gateway has the independent `MEDIFLOW_GATEWAY_SURGERY_ENABLED=false` route.
Disabled intake registers no queue/listener/worker; disabled publishing retains pending bytes.

Opt-in intake binds `financial.clearance.granted` to `surgery.financial-clearance.q`, with durable
`surgery.financial-clearance.dlq` via `mediflow.events.dlx`. Strict V1 decoding reads Billing's
actual producer fixtures. Fully validated other purposes are not-applicable; malformed/mismatched
facts go to DLQ. Transient failures retry at most three times. ACK follows transactional
APPLIED/REPLAYED or durable DEFERRED only, never a missing application outcome.

Early grants remain in PostgreSQL. A bounded worker polls every 5 seconds, up to 20 due rows;
missing case or processing failure defers 60 seconds durably, including after worker restart.
Pending evidence is not silently deleted; applied/quarantined winners are never reopened.
No clinical payload/token is logged. Grant intake never auto-READY/START/dispense/refund.

Referral/create, approved clinical evidence/signer policy and team composition, production
readiness/finalization/START/COMPLETE, approved charge/outcome delivery and clearance
revoke/supersession remain open. The internal lifecycle implementation below is not a live API.
Billing V1 output stays held. The gated listener is **not** live cutover or a complete workflow.

## Internal lifecycle foundation — 2026-10-06

Four internal in-ports implement evaluate readiness, finalize schedule, START and COMPLETE.
`SurgeryLifecycleApplicationService` has **no production bean or public controller**, even when
the business flag is enabled. Its authority port has no production/default-allow adapter.
Test-only wiring verifies orchestration, not approval of the clinical/legal/financial contracts.

Readiness checks seven independent guards, exact patient/department/episode/case/schedule
identity and source business revisions. Missing, unverifiable, stale, not-yet-valid or expired
proofs deny readiness. Freshness/skew are explicit constructor configuration, not clinical TTL
defaults. Snapshot validity is the minimum explicitly supplied expiry, further bounded by the
immutable finance grant. Versioned checklist-evidence, typed-consent-authority and team-cardinality
policies require explicit configuration; no production medical catalogue or signer rights are
invented. Optional N/A requires the permitted policy and exact stored attestation reference/revision.

Authority lookup precedes case/resource locks. Commands then lock case and sorted resource keys,
recheck local checklist/consent/grant and schedule pins, and obtain Clock after lock waits.
The future authority adapter must reconcile captured source revisions locally, with no remote call
under these locks. These internal methods currently start their transaction before lookup; moving
remote preflight outside the transaction remains a production-wiring requirement.
Failed finalize/START guards commit an audited PREOP invalidation, exact booking release and a
durable **denial** outcome, rather than throw an exception that undoes invalidation. START checks
the exact room/staff booking set and rejects other cases still IN_USE despite planned end time.
COMPLETE stores one immutable result, distinct performed lines, history, receipt and exact
resource release in one transaction. Same-key replay keeps the original result/time.

Additive V6 stores **private held lifecycle intents**, not approved `surgery.ready` or
`surgery.completed` wire. Database status permits HELD only; the broker dispatcher does not read
this table. Approved serialization/consumer acceptance and a separately reviewed translation are
required before delivery; enabling existing producer flags cannot publish these bytes.
V6 also stores ISO timestamps beside new readiness snapshots to preserve nanosecond boundaries
across PostgreSQL round-trips. Historical snapshots retain their original precision and rows;
there is no backfill or change to V1–V5 migrations.

## Readiness expiry recovery

`mediflow.surgery.readiness.expiry.enabled=false` is a separate opt-in gate and also requires
`mediflow.features.surgery.enabled=true`; enabling intake/publishing alone does not start this job.
The worker polls every 5 seconds, defaults to 20 candidates, and rejects batch sizes outside 1..100.
It uses only an explicitly persisted snapshot `validUntil`, with expiry at the exact boundary;
missing validity never receives an invented clinical TTL. Every candidate re-locks the case and
re-reads the exact active snapshot and Clock in its own transaction, with a 5-second transaction
timeout. Expiry changes only READY/SCHEDULED to PREOP, appends SYSTEM `READINESS_EXPIRED` audit,
and releases only the schedule revision pinned in that snapshot, atomically. Historical evidence
is retained; replaced snapshots, started and terminal winners are not changed. There is no public
expiry endpoint, outbound notification contract or permission to auto-READY/START.

Additive Flyway V4 stores operational retry metadata. Failed candidates back off from 5 seconds
to at most 300 seconds, survive worker restart and do not block other due cases. Retry storage
re-checks the same case/snapshot under lock; stale failures cannot defer a replacement decision.
Failures log correlation/case/safe class code only, never exception messages or clinical payloads.
Full READY/finalize/START/notification integration remains open; do not activate this foundation as
an approved clinical workflow. No job releases an IN_USE booking solely because time has elapsed.

## Organization authority invalidation

Organization V1 `organization.surgery.authority.changed` is an invalidation hint, never an
eligibility grant. The strict decoder consumes the producer-owned ROOM/STAFF_CAPABILITY fixtures,
keeps raw delivery bytes and validates exact reference/role/revision/actor/reason and envelope.
Intake commits inbox, immutable source evidence and snapshot/schedule-pinned jobs atomically before
ACK. Same source revision under a new event ID is a no-effect replay; changed content conflicts.

Additive Flyway V5 stores `surgery_authority_change` and `surgery_authority_invalidation` in the
Surgery database only. The worker locks case before job, rechecks exact state/snapshot/schedule,
then uses the shared pre-start invalidation protocol: READY/SCHEDULED → PREOP, SYSTEM audit and
exact reservation release commit with completion. Started/terminal/replacement winners are not
changed. Operational failures retain work with 5..300-second durable backoff, independent 5-second
transaction timeouts and batches of 20 (1..100). No exception reason/payload is logged.

Queue `surgery.organization-authority.q` and DLQ `surgery.organization-authority.dlq` bind only when
all three flags are true: business, global consumers, and
`mediflow.surgery.messaging.organization-authority.enabled` (all default false). Malformed/conflict
uses DLQ; transient intake failure retries three times, never ACKs lost jobs. Worker polling
defaults to 5 seconds. A future READY/finalize/START must reconcile fresh source authority after
waits; jobs alone do not close the distributed race window. Notification wire and live workflow
acceptance remain open. No existing Compose database, held outbox or business flag is activated.

## Verification and runtime image

### Current Billing authority (2026-10-06)

Internal lifecycle READY/finalize/START now requires `FinancialClearanceLookupPort`; the production
Feign adapter calls Billing's exact service-only clearance lookup. Historical grant intake is not
current eligibility. Mismatched IDs, stale/malformed replies, HTTP errors or Billing outage cannot
authorize readiness. Revoked, refunded and expired grants deny. Observation age is bounded to
30 seconds, including resource-lock waits; business validity uses both local/producer grant expiry.
The freshness window does not invent a 30-second READY/SCHEDULED expiry; START obtains a new read.
The read is not a
distributed lock: the race fence and preflight-outside-transaction work remain open. The internal
lifecycle is still deliberately not registered as a production bean/public API.

Billing-side switch `MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED` defaults false. Existing Surgery
business/consumer/publication switches and held V1 rows are unchanged. The REST contract and
Billing-owned fixture directory are in
[SURGERY-BILLING](../../docs/handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md).

The runtime profile also discovers `SurgeryBillingRuntimeAcceptanceIT`, which starts the current
packaged Billing JVM with separate PostgreSQL/RabbitMQ and calls it through the actual Surgery
Feign interface/adapter. This is a financial-contract slice, not complete public clinical workflow
acceptance. Package Billing before running it:

```powershell
mvn -pl backend/billing-service -am '-DskipTests' package
mvn -f backend/surgery-service/pom.xml -Pruntime-acceptance '-Dapi.version=1.44' '-DskipUnitTests=true' '-Dit.test=SurgeryBillingRuntimeAcceptanceIT' verify
```

Critical PostgreSQL/RabbitMQ tests require Docker; skipped tests are not acceptance:

```powershell
mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test
```

`SurgeryMigrationUpgradeIntegrationTest` starts each prior Flyway version V1/V2/V3/V4/V5 in
its own isolated PostgreSQL schema, inserts an existing SCHEDULED case with snapshot/history,
reservations, receipt and pending inbox/outbox bytes, then upgrades to V6. It compares every
pre-existing table row, verifies retry/financial evidence where present, validates checksums and
requires a second migration to do no work. Migration must not fabricate authority hints, jobs
or lifecycle intents.
This test fails if Docker is unavailable; compilation alone is not migration acceptance.

```powershell
mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' '-Dtest=SurgeryMigrationUpgradeIntegrationTest' test
```

The module-owned Dockerfile runs a packaged jar as UID 10001. Build context is `target`, not the
repository root. The existing shared Compose build is unchanged:

```powershell
mvn -pl backend/surgery-service -am '-DskipTests' package
docker build -f backend/surgery-service/Dockerfile -t mediflow-surgery:local backend/surgery-service/target
```

Explicit Failsafe acceptance runs **current packaged apps in separate JVMs**, real
Eureka/Gateway/Organization, owned PostgreSQL databases and RabbitMQ, all in isolated test resources.
It does not touch the user's existing Compose databases/broker. Package current dependencies first:

```powershell
mvn -pl 'backend/eureka-server,backend/gateway,backend/organization-service,backend/billing-service,backend/surgery-service' -am '-DskipTests' package
mvn -f backend/surgery-service/pom.xml -Pruntime-acceptance '-Dapi.version=1.44' '-DskipUnitTests=true' verify
```

`skipUnitTests` skips Surefire only; do not use `skipTests` for acceptance.
Default `test` does not run this IT; its explicit profile binds Failsafe discovery/verify.
Runtime case prerequisites are test-only fixtures, not referral/create acceptance or clinical approval.
The Gateway/Organization runtime class contains six scenarios: existing read/security/draft checks plus actual Organization
room withdrawal, staff-capability revocation/replay, and consumer-JVM stop/start with a producer
message queued while Surgery is down. Authority publishing/intake is enabled only in these isolated
test apps. Assertions cover exact case targeting, atomic pre-start invalidation/release, retained
history and single-effect replay; the unchanged defaults remain OFF in production. These new
scenarios do not certify READY/finalize/START reconciliation or the full clinical lifecycle.
Latest results/remaining acceptance: [50-item execution ledger](../../docs/superpowers/plans/2026-10-05-huy-50-task-execution.md).

Prior verification before the financial lookup slice — 2026-10-06: **506 Surgery Surefire tests and 6 explicit packaged-runtime
scenarios pass**, zero failures/errors/skips. Includes 10 actual PG lifecycle cases and five
existing-schema V1–V5→V6 upgrades. See the ledger for commands, intermediate harness failures
and their fixes. This is module/local/runtime-slice evidence, not full-root or clinical cutover.

Latest verification after the current-clearance slice — 2026-10-06: **539 Surgery tests, 233 Billing
tests and 11 explicit runtime scenarios pass**, zero failures/errors/skips. Runtime consists of five
packaged-Billing/real-Surgery-client financial cases and six separate-JVM Gateway/Organization/
Surgery cases. Includes exact context/refund/revocation/expiry, no stale-positive fallback, committed
denial/release/replay, outage rollback and separate observation freshness/business schedule expiry.
This verifies the financial lookup slice, not a public clinical lifecycle or a distributed race fence.
