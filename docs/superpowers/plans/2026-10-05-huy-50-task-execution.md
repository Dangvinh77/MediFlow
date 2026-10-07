# Huy — execution batch of 50 existing work items

Date: 2026-10-05. Baseline: master `3103fa1` plus the preserved, uncommitted cross-service work.
Source: [active plan](2026-09-25-huy-surgery-pharmacy-report.md). This is an execution ledger, not a replacement plan or 50 newly invented subtasks.

Scope: all 42 outstanding non-overlapping Surgery items and 8 Pharmacy items. Parent entries below mean their remaining acceptance criteria after completed children; existing local tests do not close a parent automatically. The user has assigned missing cross-service dependencies for this work; permanent ownership remains unchanged. Root/Common/Compose/CI are not expanded into scope. No automatic commit or push.

Completion rule: implementation, required producer/consumer compatibility, actual discovered tests, and evidence in the active plan. Flags remain off by default. Module tests and HTTP stubs are not actual multi-service acceptance. Clinical/legal qualifications and prices are authoritative configured data, never guessed or seeded in production.

## Fixed selection — 18/50 accepted locally, 32 remaining

Creation HTTP follow-up 2026-10-07: ten LOCAL implementation/verification checks decompose remaining
S-04.1/S-04.4/S-02.3/S-02.4/S-07.6/X-01.7 work. No new global IDs or whole joint-contract closure
is counted; all ten checks pass LOCAL with **740/740** full Surgery clean regression, zero fail/error/skip.
Production authority/referral/Gateway/consumer gates remain open. Current selection stays
18/50 and global backlog 62. [API follow-up evidence](2026-10-07-surgery-creation-api-batch.md).

Latest creation follow-up 2026-10-07: S-04.2 now meets LOCAL atomic creation/replay/race/rollback/
durable pending-recovery criteria; full Surgery 665/665 pass, zero fail/error/skip. Nine other
existing IDs advanced but stay open; no ten-ID closure is claimed for this batch. Main backlog
has 62 non-overlapping open IDs, not this fixed selection's 32. [Evidence](2026-10-07-surgery-creation-batch.md).

Previous update 2026-10-07: nine existing Surgery IDs below now meet their LOCAL criteria; X-01.8
also closed in the main plan but is outside this fixed 50-item selection. Details and remaining
production/contract gates: [ten-ID closure ledger](2026-10-07-surgery-closeable-batch.md).
Historical counts in the dated evidence journal remain snapshots, not the current total.

- [x] **S-01.4** — Actual packaged Eureka/Gateway/Surgery/Organization runtime: 3 Failsafe tests pass; bootstrap handoff retired.
- [x] **S-01.6** — Business/consumer/producer activation enforced; all 8 combinations for each messaging gate tested; defaults off.
- [x] **S-01.7** — 5 ArchUnit rules, canonical fixtures, real PG/MQ, actual runtime harness, non-root Dockerfile, README/.http.
- [ ] **S-02.1.3** — acceptance remains open.
- [ ] **S-02.3** — acceptance remains open.
- [ ] **S-02.4** — acceptance remains open.
- [ ] **S-02.5** — acceptance remains open.
- [ ] **S-02.6** — acceptance remains open.
- [ ] **S-03.1** — acceptance remains open.
- [ ] **S-03.2.1** — acceptance remains open.
- [ ] **S-03.2.2** — acceptance remains open.
- [x] **S-03.3.2** — Canonical Organization authority fixtures + real Feign/Eureka read/draft/revoke; exact department/interval/freshness.
- [ ] **S-03.3.3** — acceptance remains open.
- [ ] **S-03.4** — acceptance remains open.
- [ ] **S-03.5** — acceptance remains open.
- [ ] **S-03.6** — acceptance remains open.
- [ ] **S-04.1** — acceptance remains open.
- [x] **S-04.2** — LOCAL PASS 2026-10-07; global creation receipt, pinned children/history + mandatory V7 HELD fact atomic, PG race/rollback/restart/pending recovery and full 665/665 Surgery suite. Production referral/authority/API/consumer acceptance remains open at its separate IDs.
- [x] **S-04.3** — Scoped, redacted detail/board; bounded UTC pagination; no per-row REST/history loading; real PG and runtime acceptance.
- [ ] **S-04.4** — acceptance remains open.
- [x] **S-04.6** — Current mutation DTO/replay/revision/auth/error conventions; reject caller authority; safe envelopes and matching examples.
- [ ] **S-05.1.2** — acceptance remains open.
- [ ] **S-05.2.2** — acceptance remains open.
- [ ] **S-05.3** — acceptance remains open.
- [x] **S-05.4.1** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [x] **S-05.4.2** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [ ] **S-05.4.3** — acceptance remains open.
- [x] **S-05.5** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [ ] **S-06.1.2** — acceptance remains open.
- [ ] **S-06.2** — acceptance remains open.
- [x] **S-06.3.1** — LOCAL schema/lock protocol applied to every reservation writer, including internal finalize/START/COMPLETE; actual PG lifecycle, exact-set/overrun, rollback and concurrency pass. Clinical activation and full race/retry acceptance are separate tasks.
- [x] **S-06.3.2** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [x] **S-06.4.1** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [x] **S-06.4.2** — Atomic reschedule invalidation/exact old release/new DRAFT/receipt; real PG rollback and concurrent replacements.
- [x] **S-06.5** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [x] **S-07.1** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [x] **S-07.2.1** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [ ] **S-07.2.2** — acceptance remains open.
- [x] **S-07.3** — LOCAL PASS 2026-10-07; 623/623 full Surgery suite, real PG/MQ, scoped criteria/evidence in the ten-ID ledger. Production authority/clinical/downstream activation remains separately gated.
- [ ] **S-07.4.2** — acceptance remains open.
- [ ] **S-07.5** — acceptance remains open.
- [ ] **S-07.6** — acceptance remains open.
- [ ] **P-02.2.4** — acceptance remains open.
- [ ] **P-02.5.5** — acceptance remains open.
- [ ] **P-03.1.3** — acceptance remains open.
- [ ] **P-03.2.3** — acceptance remains open.
- [ ] **P-03.3.2** — acceptance remains open.
- [ ] **P-03.4.4** — acceptance remains open.
- [ ] **P-03.5.5** — acceptance remains open.
- [ ] **P-03.6** — acceptance remains open.

## Evidence journal

<a id="financial-authority-both-sides"></a>

### Billing current-clearance -> Surgery lifecycle — 2026-10-06

User explicitly reiterated implementing dependencies in the other owners' services. Engineering
gaps are implementation tasks, not permission/owner-wait gates. This slice writes both Billing and
Surgery, with no shared/Common/root/Compose/CI changes and no automatic commit/push.

- Billing producer: independently gated service-only lookup, <=60-second scoped Surgery service
  JWT, exact correlation, current single-statement ledger eligibility, revoked/exclusive expiry,
  refund/reversal net amount and selected POSTED charge checks. No patient medical data or money
  details are exposed. Confirmed absence and unavailable authority are distinct.
- Surgery consumer: real Feign client and strict adapter verify every grant/context identity,
  envelope/header correlation and bounded observation. READY/finalize/START now require this port
  before mutation locks, verify financial observation age <=30 seconds without inventing a
  schedule expiry, cap business validity by grant expiry, recheck owned grant/freshness
  after lock waits and commit denial/invalidation/release for negative final rechecks.
- Shared producer fixtures have actual Billing use-case serializer checks and Surgery real Feign
  consumer tests. Billing HTTP/PG tests pay through the real payment use case; Surgery PG tests
  verify committed financial denial/replay and full rollback on Billing unavailability.
- `SurgeryBillingRuntimeAcceptanceIT` runs current packaged Billing with isolated PG/MQ and the
  real Surgery client/adapter: actual HTTP payment, exact active grant, refund, revoke/expiry,
  wrong case and safe storage failure. This does not enable the public clinical lifecycle.

Affected existing parents: **S-03.2.2, S-05.3, S-05.4.1, S-05.4.2, S-07.1, S-07.3**.
No parent is silently checked from this subset: issuance/catalogue, revoke/supersede writers,
transaction separation and distributed race fence still need code; clinical/legal authority
requires real configuration, not fabricated test qualification data. Counts remain 8/50 accepted,
42 open until full parent criteria pass.

Final verification on current source (2026-10-06, local Bangkok time):

| Verification | Discovered result | Evidence |
|---|---|---|
| Billing complete Surefire suite | **233 tests / 38 reports; 0 failure/error/skip** | Fresh reports 14:38–14:39; Billing unchanged after this complete run. |
| Surgery complete Surefire suite after freshness/expiry separation | **539 tests / 57 reports; 0 failure/error/skip** | Fresh reports 15:30–15:34; includes 22 lifecycle unit cases, 13 actual PG lifecycle cases and 38 shared-authority real Feign cases. |
| Current packaged Billing + real Surgery client/adapter | **5 Failsafe tests; 0 failure/error/skip** | Fresh report 15:36:04; payment through actual Billing HTTP, exact context, completed refund, revoke/expiry, foreign case and actual safe storage outage. |
| Current packaged Eureka/Gateway/Organization/Surgery | **6 Failsafe tests; 0 failure/error/skip** | Fresh report 15:38:47; also explicitly verifies Gateway rejects the internal Billing lookup path. |

The full Billing+Surgery run first passed 233+536 tests. Final freshness/expiry refinement adds
three Surgery tests, then repeats the **entire Surgery suite** (539) and **both runtime classes**
(11). Current totals are **772 module tests + 11 explicit runtime cases**, not full-root acceptance.
All Maven verification processes exited 0. Commands, executed sequentially to avoid target races:

```powershell
mvn -q -pl 'backend/billing-service,backend/surgery-service' -am '-Dapi.version=1.44' test
mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' test
mvn -q -pl 'backend/eureka-server,backend/gateway,backend/organization-service,backend/billing-service,backend/surgery-service' -am '-DskipTests' package
mvn -q -pl backend/surgery-service -am '-DskipTests' package
mvn -q -f backend/surgery-service/pom.xml -Pruntime-acceptance '-Dapi.version=1.44' '-DskipUnitTests=true' verify
```

Earlier command quoting failed in PowerShell and a new runtime-test wildcard import caused a
test-compile ambiguity; both were corrected before final passing runs. The initial implementation
also wrongly used observation freshness as persisted schedule expiry. It was corrected: one
targeted PG case proves fresh START works after 35 seconds while the real grant remains valid;
another unit case proves a financial observation aging past 30 seconds under a resource lock
denies even when the other six guards are still fresh. Producer business expiry is preserved.

Unchanged rollout: business/intake/publication switches default OFF, Billing V1 outbox remains
HELD, private Surgery intents remain HELD, no new public READY/START APIs, no shared/root/Compose
production edits, no user database reset, no commit/push. Canonical contract, registry, service
specs, active handoff and HTTP sample were updated together; local links and diff whitespace pass.

<a id="surgery-15-follow-up"></a>

### Selected 15-item follow-up — 2026-10-06 (LOCAL VERIFY PASS; 1 ACCEPTED / 14 PARTIAL)

Selection before implementation: **S-02.3, S-02.6, S-03.5, S-05.1.2, S-05.2.2,
S-05.4.1, S-05.4.2, S-05.5, S-06.1.2, S-06.3.1, S-06.3.2, S-06.4.1,
S-07.1, S-07.2.1, S-07.3**. These are 15 existing unchecked IDs, not newly invented tasks.

Build the internal evidence/policy and lifecycle orchestration after verifying the pending
V1..V4 upgrade and actual authority-message runtime slice. Each command must use immutable
source proofs, exact case/schedule revisions, case-first resource locking, durable receipts and
atomic state/history/resources/event intent. Missing clinical/legal/financial authority and
approved READY/COMPLETE bytes must fail closed; no production defaults, public lifecycle route
or invented cross-service payload is authorized by a test double. Parent acceptance is recorded
separately from a completed local slice, never claimed from the count of selected IDs.

Delivered local changes across the selected 15 IDs:

| Existing ID | Implemented in this batch | Parent acceptance / remaining work |
|---|---|---|
| S-02.3 | Transactional internal lifecycle, immutable snapshots/result, receipts and held intent adapters | PARTIAL: remote preflight outside transaction, referral race and full command failure/retry matrix |
| S-02.6 | Actual PG lifecycle rollback, finalize competition, START exact-set/overrun and completion replay; V1–V5 upgrade to V6 | PARTIAL: remaining command race/deadlock/JVM failure matrix |
| S-03.5 | Actual Organization room/capability message/replay/restart **PASS**; corrected current Docker binding and bounded HTTP health wait after JVM restart | PARTIAL: other contract bytes and READY/COMPLETE consumers are not approved |
| S-05.1.2 | Versioned checklist evidence policy: exact context/source/revision, correction/expiry, explicit manual/N/A permission | PARTIAL: approved medical catalogue, exact producer evidence/order adapter and shared fixtures |
| S-05.2.2 | Per-type signer/guardian/witness/document/recording/revocation authority policy | PARTIAL: approved legal/clinical relationships, source adapter and live authorization |
| S-05.4.1 | Seven-guard readiness engine with structured reasons, revision/context fences and minimum explicit validity | PARTIAL: production authority aggregation and preflight outside write transaction |
| S-05.4.2 | Atomic PREOP→READY/snapshot/history/receipt/private HELD intent and failure rollback | PARTIAL: approved ready serialization/consumers; held bytes are not an event |
| S-05.5 | Guard/freshness/context policy matrix, local dependency re-read, actual PG expired START/changed finalize/consent denial checks | PARTIAL: full multi-worker checklist/revoke/reschedule/finalize/START race matrix |
| S-06.1.2 | Explicit revisioned procedure/team cardinality policy; deny unconfigured roles, no job-title fallback | PARTIAL: approved clinical composition and production lookup/policy assembly |
| S-06.3.1 | Case-first sorted mutex protocol used by every reservation writer; exact schedule/revision release | **LOCAL DONE**; activation is not part of this schema/lock task |
| S-06.3.2 | START rechecks exact booking SET, blocks another case still IN_USE even with adjacent planned slots | PARTIAL: remaining multi-resource races and bounded deadlock retry |
| S-06.4.1 | Internal explicit READY→SCHEDULED, current proofs/pins, atomic reservations/audit/receipt; competing-room loser rollback | PARTIAL: full failure matrix and production authority/API acceptance |
| S-07.1 | Internal SCHEDULED→IN_PROGRESS, current local proof re-read/Clock after locks, original-time replay and committed denial/release | PARTIAL: production authority/API and actual START-versus-external-revocation race |
| S-07.2.1 | Internal immutable result/items+COMPLETED+history/receipt+exact release+private held intent, rollback/concurrent replay | PARTIAL: approved completed outbox/wire and consumer compatibility |
| S-07.3 | No override input/fallback; missing finance denies internal commands; no lifecycle bean/mapping even with business flag true | PARTIAL: future public API/admin/role request acceptance; absence of route is not security completion |

V6 is additive and does not modify V1–V5 or existing rows. Its HELD-only private intent table
is not read by the outbox dispatcher; enabling current producer flags cannot deliver those bytes.
New readiness snapshots retain exact ISO instants for PostgreSQL round-trips; old snapshots are
not rewritten. The only accepted parent from this selection is **S-06.3.1**: **8/50 accepted,
42 selected open**, main plan **73 non-overlapping open**. The 14 partial rows are not counted
as completed just because their local code was delivered. Historical counts below remain historical.

Verification journal: first full regression **498/498 PASS**, zero failures/errors/skips, after
new lifecycle/persistence/smoke changes. Five optional-N/A policy cases and three extra PG
guard-denial cases were added afterward. Final current-source module regression **506/506 PASS**
at 2026-10-06 14:14 local time: 57 fresh suite reports, zero failures/errors/skips and no stale
reports. Includes **90 new tests** over the prior 416: readiness engine 29, clinical policies 28,
lifecycle application 17, lifecycle PG 10, existing-schema upgrade 5 and registration-boundary
smoke 1. These are test cases, not 90 completed plan tasks. Packaged runtime **6/6 PASS** at
2026-10-06 14:11 local time, zero failures/errors/skips.
Runtime uses fresh jar files and actual Eureka/Gateway/Organization/Surgery JVMs with isolated
PG/Rabbit. It proves room-specific targeting, staff-capability semantic replay and recovery of a
producer message queued while the Surgery JVM is down, plus existing read/draft/security cases.
It does not certify new readiness/finalize/START/COMPLETE HTTP or clinical-policy acceptance.

Harness fixes are scoped: read current Docker bindings after restart and retry only IO exceptions
within a 90-second HTTP-health wait; all business/audit/booking assertions remain strict. An
intermediate full run had **5 migration errors** because a concurrent Maven test recompilation
replaced the shared `target/test-classes` fixture during execution. Current classes were verified
present after compilation; the final full Surefire run uses that compiled output without another
compiler writing it. Do not treat the failed intermediate run as PASS or add repeated runs together.

Commands actually used: full reactor `mvn -q -pl backend/surgery-service -am -Dapi.version=1.44 test`
(first 498 PASS; intermediate 506 run failed as recorded above); current dependency packaging;
runtime `mvn -q -f backend/surgery-service/pom.xml -Pruntime-acceptance -Dapi.version=1.44 -DskipUnitTests=true verify`
(final 6 PASS); final `mvn -q -pl backend/surgery-service -am -Dapi.version=1.44 surefire:test`
(506 PASS from the current compiled classes, exit 0). Existing V1/V2/V3/V4/V5→V6 upgrades all
PASS with unchanged rows/bytes, valid checksums, empty new intent/precision tables and a second
migration doing no work. `git diff --check` is clean. No full-root regression, production
enablement, shared production edit, commit or push was performed in this follow-up.

### Master integration — 2026-10-06

- Fast-forwarded local Huy from `3103fa1` to master `ea60ea4` (8 upstream commits), without
  committing or pushing the preserved local work. All 18 upstream-added files are retained.
- Safety snapshot: stash `a01905ab8352bb53989f6aefde7b25974766383d`, plus a 268-path SHA-256
  manifest/file backup under the machine's temporary `mediflow-master-sync-20261006-113240`
  directory. Older stashes were not applied or dropped. The pre-sync index was unstaged.
- Resolved 9 conflicted paths by integrating both sides, not a blanket ours/theirs selection.
  Gateway retains master's generic room internal-only route and role matrix alongside local
  exact command patterns, gated Surgery routes and additive authority/admission lookups.
- Retained master's `V3__room_lookup.sql` unchanged. The unpublished local Organization
  authority migration is now `V4__surgery_reference_authority.sql`, with identical SQL bytes,
  to avoid duplicate Flyway V3. No existing application database/history was changed.
- Generic `room`/job-title projection and explicit revisioned operating-room/capability
  authority are documented separately; no UUID mapping, credentials or permission fallback is
  inferred. The already-retired Surgery bootstrap handoff stays deleted; canonical runtime,
  role/security and registration evidence remain in service/contract documents.
- Immediately after conflict resolution, all 268 pre-sync paths were accounted for: 257 retained their original content/deletion (including
  the migration under its new name), and 11 overlapping code/document files were merged or
  updated for migration references. Local Surgery/Pharmacy/Report additions are byte-preserved.
- Post-integration regression **PASS**: Gateway **165**, Organization **125**, Surgery **416**
  tests, total **706**, zero failures/errors/skips and no stale suite reports. Reactor command:
  `mvn -q -pl backend/gateway,backend/organization-service,backend/surgery-service -am -Dapi.version=1.44 test`.
  Gateway was additionally rerun after removing the duplicate referral rule introduced by
  automatic merge; do not count that rerun twice. Real isolated PG/Rabbit tests ran successfully;
  no Compose/user database or broker was reset. This was not a new full-root or packaged-runtime
  acceptance run; the earlier runtime results below remain historical evidence.
- GitHub master was rechecked after tests and still resolves to `ea60ea4`. Local HEAD equals it,
  but preserved uncommitted changes intentionally remain above that baseline. Remote Huy was not
  pushed or rewritten; no conflict entries or whitespace errors remain.
- This sync closes no additional business work items; selection remains 7 accepted / 43 open.

### Follow-up — existing-data upgrade and real authority delivery (IMPLEMENTED / VERIFY PENDING, 2026-10-06)

Continue the remaining verification edges of S-02.6/S-03.5/S-05.4.3. This is not a new
business-policy decision or completion of their full acceptance criteria.

- Added `SurgeryMigrationUpgradeIntegrationTest`: four isolated-schema upgrades V1/V2/V3/V4
  to V5, with a domain-consistent SCHEDULED prerequisite. Snapshot every old table and compare
  retained case/history/readiness, exact RESERVED resources, receipt response bytes, pending
  inbox/outbox bytes and retry timing/attempts; retain financial/expiry evidence where present.
  Verify Flyway checksums, idempotent second migrate, and no fabricated authority/job rows.
- Extended the explicit packaged runtime profile from three to six scenarios. New tests mutate
  real Organization authority through its API and consume actual producer messages in Surgery:
  exact-room targeting leaves an unrelated case/bookings unchanged; staff-role revoke plus exact
  and semantic replay causes one audit/effect; stop the owned Surgery app JVM, queue the producer
  event while down, then restart and recover. Read only the isolated Surgery DB for assertions,
  never Organization's DB. Replay checks the semantic inbox marker plus zero ready/unacknowledged
  queue deliveries and no DLQ/conflict, not a sleep or ready-count-only assumption.
- The shared SCHEDULED fixture is test-only and derives state/revision histories from the domain.
  It is NOT a clinical readiness evaluator, public finalize API or referral/create acceptance.
  Only the isolated runtime apps enable the existing producer/consumer gates; production defaults,
  held outputs and shared build/Compose paths are unchanged.
- All new test sources compile. Fresh focused non-Docker regression passed **100 tests,
  zero failures/errors/skips** across nine authority/invalidation/expiry suites. This is not a
  new full-module/root run; do not sum it with the historical 416/706 counts below.
- **PostgreSQL upgrade and six-scenario runtime acceptance are NOT VERIFIED in this follow-up.**
  The first upgrade attempt failed during Testcontainers initialization because the Docker engine
  was unavailable, before any upgrade case executed. An unsandboxed Docker diagnostic also found
  no `dockerDesktopLinuxEngine` pipe. No reset/volume deletion or H2 substitution was performed.
  Rerun the README's focused upgrade command, full Surgery suite and freshly packaged runtime
  profile after the engine is running; do not treat the previous 3/3 runtime run as proof of these
  added scenarios. Their assertions may still reveal integration issues when first executed.
- No parent checkbox added: **7/50 accepted, 43 selected open**, main plan **74 non-overlapping
  open** remain unchanged. No commit/push in this implementation follow-up. The preceding user-
  requested sync already pushed baseline `ea60ea4` to Huy; these preserved/local additions remain
  uncommitted above that baseline.

### Current slice — Organization authority invalidation (LOCAL VERIFIED, 2026-10-06)

Continue S-05.4.3/S-03.5 using Organization's existing V1 authority-change event, not a new wire.
Add producer serialization fixtures and Surgery strict decoding, atomic inbox/source-hint/durable
job capture, bounded per-case invalidation/retry and separately gated Rabbit listener/DLQ/worker.
Pin the case readiness snapshot and schedule at intake; lock/re-read them before exact release.
START/terminal/replacement winners remain unchanged. No eligibility grant or automatic READY/START,
no clinical policy defaults and no guessed notification event. Verify raw bytes, semantic replay,
conflict, rollback, real PostgreSQL/RabbitMQ and feature gates before recording acceptance.

- Implemented four in-ports/services, strict wire port/decoder, additive V5 hint/jobs, case-first worker/retry and three-gated Rabbit topology/listener. Producer wire is unchanged; two new Organization-owned serialization fixtures/tests normalize only random event identity. Defaults stay OFF.
- Final full Surgery/common reactor passed **416 Surgery tests, 0 failures/errors/skips**. This includes **90 new Surgery tests**: 3 domain, 13 application boundary, 14 worker/three-gate configuration, 35 strict decoder, 6 listener and 19 actual PG/Rabbit integration cases. Organization full suite passed **112/112**, including 2 new actual producer serialization tests. Do not add the earlier 408 run, focused reruns or historical counts to these totals.
- Actual PG/Rabbit evidence: raw producer fixture bytes retained; ACK after durable jobs, fresh worker recovery, exact room and staff-role fan-out, event/semantic replay and source conflict, unsupported payload DLQ, storage failure exactly three retries + retained-byte replay, audit/final-marker rollback including release, two workers one winner, started/replacement fences, forged schedule pins, inconsistent current revision fail-closed, out-of-order source revisions and 5..300s backoff that cannot reopen completed work. V5 fresh migrations pass; existing-volume upgrade and actual producer-to-consumer multi-service event delivery are not certified by this module suite.
- Fresh packaging completed for Eureka/Gateway/Organization/Surgery, then current packaged runtime acceptance passed **3/3, 0 failures/errors/skips** (2026-10-06). Separate actual app JVMs, Eureka/Gateway and isolated PG/Rabbit are used. These three scenarios certify read/draft/revoke/security/correlation compatibility with current V5, not full clinical lifecycle or Organization-event end-to-end rollout. No full root-reactor rerun in this slice.
- Docker initially failed at startup (`dockerInference`). No reset, volume deletion or user Docker-data edits were performed; after the user reopened Docker, actual test containers ran successfully. The replacement fixture originally moved audit time backwards; only its evaluation time was corrected, not the production invariant. The integration clock harness was subsequently changed to keep a real startup clock; its 19 cases were rechecked successfully, without counting that rerun as new tests.
- Selection recorded before implementation. Preserve existing work, do not count historical tests or previously checked children as new completions. S-03.5/S-05.4.3 remain partial for other sources, clinical/finalize/START reconciliation and approved notification wire; **7/50 accepted, 43 open** is unchanged.

### Verified implementation, 2026-10-05

- Full root `mvn -q '-Dapi.version=1.44' test` completed successfully across all backend modules. Afterwards, the Surgery-only department and shared-invalidation fences were strengthened and its full module suite rerun: **291 tests, 0 failures/errors/skips**. This later module run is not a second root pass or added to the root count.
- Gateway: **162 tests**, Pharmacy: **423 tests**, zero failures/errors/skips in the root run. Pharmacy includes **4 actual PostgreSQL/RabbitMQ** clearance-intake tests (duplicate early pending, validated not-applicable, mismatch/poison rollback+DLQ, failed storage retry and retained-byte replay).
- Fresh dependency jars packaged with `mvn -q -pl backend/eureka-server,backend/gateway,backend/organization-service,backend/surgery-service -am '-DskipTests' package`.
- Explicit `mvn -q -f backend/surgery-service/pom.xml -Pruntime-acceptance '-Dapi.version=1.44' '-DskipUnitTests=true' verify` passed **3/3**. Real packaged JVMs use one isolated Docker network, separate Surgery/Organization PostgreSQL databases and RabbitMQ, no HTTP stub. Authority records are created through Organization APIs; only the isolated Surgery-owned existing-case prerequisite is seeded. This is not referral/create or full clinical lifecycle acceptance.
- Runtime fixture's lowercase department code was correctly rejected by Organization. The fixture was corrected to uppercase and the full profile rerun successfully; no production rule was weakened.
- Code added for still-open **S-02.4/S-03.2.2/S-05.3/S-06.2/P-03.2.3**: dual-gated grant listeners, strict full-purpose validation, canonical UUIDs/payment methods, bounded retry/dedicated DLQs, durable early Surgery worker, and actual PG/MQ rollback/recovery. Pharmacy is evidence-only intake, never auto-dispense. Surgery grant is evidence-only, never auto-READY/START.
- Shared readiness invalidation now validates the case/schedule ID and schedule revision pinned in readiness before release; 7 focused tests plus existing real PG consent/clearance/reschedule regressions pass. Stale/foreign schedule cannot clear readiness or release a replacement booking.
- README, API examples, canonical service/identity/clearance contracts and V2 specs updated. Retired bootstrap facts moved to service docs; active clinical/business handoffs remain.

### Remaining work is not hidden by the test counts

### Follow-up slice — readiness expiry and reliability (LOCAL VERIFIED, 2026-10-05)

Implemented the expiry/reliability portion of S-05.4.3/S-05.5 and S-02.6/S-06.5, without inventing
clinical policies or business event wire. These fixed parent IDs remain unchecked for other criteria.

- Three application in-ports/services plus one driven port/JDBC adapter, independently gated worker,
  and additive `V4__readiness_expiry_recovery.sql`. Default flags false; all four business/job gate
  combinations and test-profile exclusion verified. Batch defaults 20/max 100; transactions timeout
  after 5 seconds. No public state setter, guessed clinical TTL or new notification routing key.
- Expiry reads the persisted snapshot's explicit boundary, then re-locks/re-reads case/snapshot/Clock.
  READY/SCHEDULED only: PREOP + SYSTEM audit + exact pinned booking release commit together.
  Historical snapshots remain. Missing validity, pre-boundary, stale snapshot, committed START or
  cancellation winners do not regress state or release IN_USE. Retry storage revalidates case/snapshot
  under lock, preserves failed work with 5..300-second backoff and does not starve healthy candidates.
- **33 new expiry tests**: 10 service, 5 query/retry boundary, 7 worker/config, 11 real PostgreSQL.
  PG covers exact boundary, rollback after resource release/audit failure, two-worker single winner,
  replacement fencing, committed START, cancellation winner and actual cancel/expiry race, lock wait
  timeout/recovery, durable retry/backoff cap and healthy-case progress. These isolated prerequisites
  are not an approved clinical policy or the missing full READY/finalize/START application workflow.
- **2 new real PG/Rabbit recovery tests** in the 3-case transport suite: stop/start the exact isolated
  broker container, preserve PENDING bytes/attempt/backoff, then publish only after real confirm;
  simulate a worker crash after broker confirm but before DB mark, recover after lease expiry,
  deliver the same event ID/bytes twice and reject the stale attempt token. At-least-once delivery
  is intentional; not an exactly-once claim or a separate-JVM crash test. Docker reallocated the
  random host port on restart; the harness now reads current bindings and checks AMQP readiness.
- Focused runs exposed and fixed PostgreSQL interval parameter inference and stale test-container
  port handling. No production validation was weakened. Final full Surgery/common reactor run:
  **326 Surgery tests, 0 failures/errors/skips**, exit 0; includes 35 new tests, not summed reruns.
  Fresh runtime dependency packaging passed; the updated explicit runtime acceptance passed **3/3**,
  zero failures/errors/skips (current jars, separate JVMs, real Eureka/Gateway/Organization/Surgery).
  Its scoped read/draft/authority-revocation scenarios are still not full clinical lifecycle acceptance.
- README, canonical Surgery service doc and V2 spec describe expiry recovery and activation limits.
  Full notification/invalidation wire, readiness/finalization/START and the rest of the failure matrix
  remain open. No shared-module edits, commit or push; prior local work preserved.

**At the end of this 2026-10-05 batch, 43 selected items were still open**, including partially implemented ones. Current status is recorded in the 2026-10-06 15-item section above. No completion claim for all 50. Referral/charge creation, revocation/supersession, approved clinical evidence/signer/team policies and full producer/consumer acceptance remain separate work. Missing authoritative medical rules/prices are not invented or seeded. All production activation defaults stay off; held Billing/Pharmacy rows remain held. No commit/push performed in that batch.
