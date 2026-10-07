# Surgery — ten existing closeable IDs (2026-10-07)

Scope: Huy-owned Surgery and documentation only. Gateway and other owner production code are untouched.
This batch closes ten existing IDs at their stated LOCAL acceptance level, not new checkbox microtasks.
Final verification: 623/623 Surgery tests, zero failure/error/skip. Do not infer production activation
from local fixtures. The main plan decreases from 73 to **63 non-overlapping open IDs**.

| Existing ID | Required local acceptance |
|---|---|
| S-05.4.1 | Seven guards, explicit expiry/reasons/revisions; remote preflight outside transactions; locked owned re-read |
| S-05.4.2 | READY/snapshot/history/receipt/HELD V7 ready bytes atomic; replay and new readiness fact after invalidation |
| S-05.5 | Actual PostgreSQL evaluate/checklist/consent/reschedule, START/expiry/revoke, finalize/invalidation races |
| S-06.3.2 | Half-open resource overlaps and IN_USE; bounded actual SQL lock/deadlock retries; rollback receipts |
| S-06.4.1 | Exact READY/schedule/dependencies; all-or-nothing resource reservations/status/audit/receipt |
| S-06.5 | Real two-worker resource/lifecycle matrix, partial-write rollback, stale release and overrun |
| S-07.1 | Fresh preflight; post-lock Clock and exact resources; stable start replay; committed denial/release |
| S-07.2.1 | Immutable result/lines and completion/release/history/receipt/HELD completed bytes atomic |
| S-07.3 | Gated HTTP role/strict-body tests; ADMIN cannot supply clearance or override; no V1 bypass |
| X-01.8 | Actual verification counts/versions/hashes, scope audit and explicit remaining gates |

## Implementation boundary

`SurgeryUnitOfWorkPort` keeps the application independent of Spring transaction machinery.
The adapter suspends a calling transaction for external preflight; reads and atomic writes are separate
`REQUIRES_NEW` transactions. Each write sets local PostgreSQL `lock_timeout=1000ms` and a five-second
transaction timeout. At most three fresh transactions retry genuine SQL states `55P03`/`40P01` only;
other business/infrastructure failures do not retry. Exhaustion is typed `SURGERY_COMMAND_BUSY` (409).
No event is sent to the broker during these transactions.

The committed receipt probe is non-locking and never creates a PENDING row before remote I/O. Atomic
claim still rechecks replay/conflict after preflight. V2 receipt encoding preserves blocking reasons and
nanoseconds; V1 remains readable without rewriting stored receipts. Denial returns explicit reasons,
not a partial successful READY. Existing commands without reasons still write byte-compatible V1,
avoiding a codec change for live preop/schedule/cancel receipts. Only structured denials use V2;
old nodes must not serve that new gated boundary in a mixed-version activation. Its snapshot remains
immutable evidence.

Four HTTP boundaries require BOTH `mediflow.features.surgery.enabled` and
`mediflow.surgery.lifecycle.api.enabled`; both default false. Explicit use-case beans backed by genuine
clinical/authority providers are still required. Enabling flags without those beans fails startup; no
positive dummy provider is installed. READY/START/COMPLETE admit ADMIN/DOCTOR; finalize also MANAGER.
Every request trusts JWT identity only and rejects unknown readiness/override/actor/amount fields.

## Gates not closed by this batch

Real clinical/legal/template/result-code policy adapters, referral/creation, authority source fences,
distributed refund-after-read fencing, downstream runtime acceptance, V7 dispatcher release and G1/G3
remain separate open tasks. A local authority test double is not clinical approval or a distributed lock.
HELD producer bytes are not live publishing. No Gateway changes or global-CI-green claim.

## Verification — DONE LOCAL

Final sequential command: `mvn -q -pl backend/surgery-service -am -Dapi.version=1.44 -Dlogging.level.root=ERROR test`.
Exit 0 at 11:30, **623 tests / 62 fresh Surefire reports, 0 failures / 0 errors / 0 skipped**.
All reports are from this run (11:25:54–11:30:20), not stale results. PostgreSQL/RabbitMQ tests ran,
including migration/upgrades and real workers; no unavailable-Docker skips. Logging level changes only
verbosity, never discovery/assertions. No packaged multi-service/Gateway acceptance was claimed here.

| Suite | Tests | Relevant evidence |
|---|---:|---|
| SurgeryLifecycleApplicationServiceTest | 23 | Seven guards, upstream denial, post-wait checks, replay and probe→state-read race |
| SurgeryCommandReceiptsTest | 6 | V1 byte-compatible replay, V2 reasons/nanoseconds, corruption/identity rejection |
| SurgeryLifecycleIntegrationTest | 22 | Atomic HELD/rollback, new READY after invalidation, six real resource races, exact release |
| SurgeryLifecycleRacePostgresTest | 12 | Checklist/consent/reschedule/START/expiry/finalize/complete/cancel worker matrix |
| SurgeryUnitOfWorkPostgresTest | 4 | Suspension/read-only TX, real 55P03 exhaustion/recovery, real 40P01 fresh retry, non-lock rollback |
| SurgeryLifecycleApiTest | 26 | Role/JWT identity, strict bodies, no ADMIN override, typed errors, input validation |
| SurgeryLifecycleGateTest | 3 | Missing/single flags absent; both without providers fail; explicit test wiring only |

These 96 tests include 38 earlier tests; the batch adds **58**, not 96 new cases. The remaining
527 tests are regression, including domain guards, active-use/overrun, stale release, migration,
Organization/Billing fixture HTTP adapters, Rabbit/DLQ/recovery and architecture.

Initial targeted run: 38 tests, one error in a historical fixture
that wrote consent inside remote observation relying on the old transaction. Fixed the fixture to commit
that competing mutation in an independent transaction; production preflight remains transaction-free.
The first full run exposed the new adapter missing the existing `!test` persistence profile boundary
and race-fixture wrong checklist ID/template reuse. Those were fixed. A subsequent 623-test run had
only eight baseline-count assertions in the new race fixture: seed READY/START receipts and immutable
HELD intents must remain in totals. Corrected assertions preserve that history, never delete it or
weaken winner/rollback checks. Final fresh full run passed.
The suite also logs intentionally injected SQL/write/timeout/redacted failures. A pre-existing Rabbit
fixture may log its startup poll before its mocked Clock is configured; this is not a new production
clock provider or a lifecycle failure and is not counted as distributed/rollout acceptance.
No commits/pushes are made in this request; base HEAD is `3765f4f46587ae134cbbe00f661ec7e4b83eb316`.
Pre-existing Report work and the changelog hook entry are preserved.

## Closed ID mapping

All ten rows in the opening table are closed in the main plan. Rollup parents S-06.3/S-06.4 are also
checked because all their local children are done; they are **not** counted as two extra completed tasks.
Contract-only parents/children, clinical provider policies and activation gates remain unchecked.
The historical 2026-10-05 fixed selection now has 17/50 accepted and 33 selected open: nine of this
batch's IDs belonged to it; X-01.8 is the tenth here but was not in that fixed selection.

### Database/fixture source identity

No migration or producer fixture is changed by this batch. Fresh schema remains empty→V7; the existing
upgrade suite covers V1…V6→V7 and no-op second migration with existing rows/checksums intact.
V7 SHA-256: `28d36076f35670646c52b27c23914af5632e6c63dd7c83804eaf3c394a5062c8`.

Actual local Docker images (read-only inspection):

| Image | Digest |
|---|---|
| postgres:16-alpine (16.14 reported by Flyway) | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| rabbitmq:3.13-alpine | `sha256:d7af1c87c5f1eda13fcfca06db452bf3aeab6619fc3358b68535c0c02c4e52bc` |

SHA-256 of the unchanged producer fixtures in
`backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/`:

| Fixture | SHA-256 |
|---|---|
| surgery.ready.admission.v1.json | `e21e1a21ca36873ec3042053ef9be4f4e84ae482f716a4e2f53258d1eebad81d` |
| surgery.ready.outpatient.v1.json | `857e47d25b79d390b8d4b4821505530a31cd2e26500f0ccc523053be15ee42d0` |
| surgery.completed.admission.v1.json | `20dca767c48ffbf2e0695e71b592c5605c3ee2f60da382d2699a5d3664bfe92d` |
| surgery.completed.outpatient.v1.json | `41b5e2b2d16600dfbc53d268bcfb4d0f6545d1b0ae7be4093a822b4b9ba34ff1` |
| surgery.readiness.invalidated.admission.v1.json | `dcd650f26780bc9e7f2dfac1f75827d253b3963be278b963f7bb328a075c499b` |
| surgery.readiness.invalidated.outpatient.v1.json | `896dfcc77d537f6769288dccc55a68d7fad29e2289cda39117e85c81d7e0e517` |

### Scope review

Production delta for this request is limited to Surgery DTO/receipt/UoW/lifecycle/HTTP/config code.
No Gateway, other-owner service, Common, root POM, repository script, Compose or CI edits were made.
No old SQL/JSON wire fixture was rewritten. Application does not depend on the transaction adapter;
the controller does not call persistence or domain objects. No new authority provider, role shortcut,
clinical/legal default, price calculation, cross-service DB access or V7 event dispatcher is added.
The final test count must come from fresh Surefire XML after a sequential successful run, not from
adding rerun totals or counting older reports.
