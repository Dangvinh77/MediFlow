# Huy batch — ten cash replay implementation subtasks

Scope: Report only, additive/offline. Preserve previous uncommitted receipt work. No Gateway,
other-owner production code, commit or push. These are ten implementation **subtasks** of existing
R-01 replay foundations, not ten completed parent contracts or full financial metrics.

| ID | Existing plan mapping | Deliverable and acceptance | Status |
|---|---|---|---|
| CR-01 | R-01.4 / R-01.5.3 | Finite source is committed V12 receipt evidence with first-delivery proof; no pre-activation history/retention claims or raw payload archive | DONE_LOCAL |
| CR-02 | R-01.5.3 | Strict versioned minimal snapshot codec, exact decimals/nanoseconds/IDs and existing V12 fingerprint compatibility | DONE_LOCAL |
| CR-03 | R-01.5.3 | Shared pure gross-cash scope planner used by existing receipt writer and replay | DONE_LOCAL |
| CR-04 | R-01.5.3 | V13 isolated generation/manifest/receipt/scope schema with currency/null-scope constraints; no live table reset | DONE_LOCAL |
| CR-05 | R-01.5.3 | One-statement MVCC-visible frozen manifest excludes later and old-uncommitted transactions | DONE_LOCAL |
| CR-06 | R-01.5.3 | Bounded 1..500 batch/resume/progress and terminal/no-effect behavior, DB generation lock | DONE_LOCAL |
| CR-07 | R-01.5.3 | Snapshot version/identity/hash/domain verification and per-generation semantic dedupe; reject corrupt/unsupported input before effects | DONE_LOCAL |
| CR-08 | R-01.7 / R-01.6.2 | Bidirectional fact + currency/classification/zone/scope/progress reconciliation, VERIFIED or FAILED without publication | DONE_LOCAL |
| CR-09 | R-01.6.2 | Real PG race, whole-batch rollback/retry and live/replay/generation isolation evidence | DONE_LOCAL |
| CR-10 | R-01.4 / R-01.6.2 | V12→V13 upgrade preserves real receipt rows/hash/nanoseconds; fresh/empty/shuffled/distinct-partial receipt rebuild and full regression | DONE_LOCAL |

Financial recognition, deposit release/refund/settlement/pending-reversal/correction, accepted
historical coverage, live catch-up and controlled financial read publication remain OPEN.
VERIFIED here means equality with this finite receipt manifest, not owner approval or a complete
financial dashboard. No new listener/API or held producer delivery is enabled.

## Test results

Completed 10/10 local implementation subtasks. Full Report regression finished 2026-10-07 at
10:23 local time: **361 tests, zero failures/errors/skips**, with PostgreSQL 16.14 and RabbitMQ.
Command: `mvn -q -pl backend/report-service -am -Dapi.version=1.44 test`.

Added coverage (51 cases): CashSnapshotCodecTest 12, CashProjectionPlannerTest 2,
CashReplayApplicationServiceTest 14, CashReplayPostgresTest 20, CashReplayMigrationPostgresTest 2,
and one V13 migration static test. Existing V12 receipt/kernel tests also pass after sharing the
codec/planner. The upgrade test compares a literal pre-refactor V12 canonical JSON hash, not a
hash generated only by the new codec. PostgreSQL batch-size/insertion-order checks compare both
entire fact/provenance sets and scoped money/count sets. All fault injection affects temporary
Testcontainers databases, never the user's running data.

Implementation: `CashSnapshotCodec`, `CashProjectionPlanner`, `CashReplayInput/Progress`, internal
in/out ports and `CashReplayApplicationService`, `CashReplayPersistenceAdapter`, and additive
`V13__isolated_cash_receipt_replay.sql`. Schema/planner/architecture checks pass;
`git diff --check` passes. No changes to Gateway, other-owner production modules, shared build
or CI. No commit/push. Parent R-01.4/.5/.6/.7 contracts remain open for the missing full-target
facts, history/retention/coverage/catch-up/publication; local DONE is not joint rollout approval.
