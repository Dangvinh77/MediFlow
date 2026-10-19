# Notification V1 receipt byte identity — 19/10/2026

Follow-up to audit PR #441 (merged). Base implementation revision:
`6f0776cf45db5216960cfa3e43a22f9bd41451a2`.

The audit's whitespace-only regression failed before the fix (1 test, 1 failure). The
regression now lives in the default `CareReceiptIntegrationTest`. The historical patch
remains evidence of the pre-fix revision; do not apply it on the fix branch. The handoff
stays open until final runtime acceptance. Historical audit inventories remain unchanged.

## Resulting behavior

New V1 payment receipts atomically persist the exact body SHA-256 alongside the unchanged
normalized digest, history and delivery outbox. Identical bytes replay without effects;
same eventId/different bytes reject and dead-letter. Historical V1 receipts cannot infer
original bytes from normalized hashes: missing raw hashes fail closed until the offline
Notification-store-only importer verifies a trusted original producer archive.

V5 is additive. Import is dry-run by default, validates complete unbackfilled coverage,
rejects unknown/duplicate/conflicting archive identities and commits hashes plus ledger in
one transaction. It never dispatches a business command. Old readers retain their existing
normalized comparison; old binaries do not acquire the new byte integrity guarantee.

## Verification scope

**Current gate: PARTIAL / NO-GO merge.** Docker Desktop was unavailable for the final full
rerun and failed startup at a stale local socket. The full Notification attempt exited 1
with five container-initialization errors and five existing Docker-conditional skips;
the packaged CLI profile compiled/packaged successfully but failed PostgreSQL container
initialization (one reported error). These are pending runtime checks, not passing tests.
Automatic command review blocked removal of the stale Docker socket outside the workspace;
standard Docker stop/start did not recover the daemon. No target database was accessed.

Build/install of Notification and packaging of Support succeeded. Final non-container test
results are **228 tests, zero failures/errors/skips**, with exact exclusions in the JSON
inventory. Support's non-store slice
passed 13 tests; its five store-isolation cases and six-service packaged acceptance remain
pending. Earlier targeted upgrade/queue results precede the final minor diagnostic guard
and CLI IT and must not be counted as a final full rerun.

The final result inventory and source hashes are recorded in `verification-results.json`.
Do not publish private logs, XML system properties, database credentials or receipt bodies.

- Fresh PostgreSQL integration: identical replay, raw-byte conflict, concurrent duplicates,
  rollback and unchanged notification/history/outbox effects.
- Real RabbitMQ: original replay plus whitespace-conflicting body; exact conflicting body
  reaches DLQ once, with one committed notification/receipt/outbox.
- Actual V4 database migrated to V5: original replay after backfill through reconstructed
  readers, unchanged normalized hashes/history/outbox, new writes and old claim algorithm
  compatibility. This is not an old application binary deployment test.
- Archive failure cases: incomplete coverage, changed business data, unknown/duplicate IDs,
  malformed bodies, pinned raw mismatch, ledger failure rolling back hash updates, V0 and
  other projection preservation.
- Packaged CLI execution and Support host verification are separate acceptance stages.

Deployment requires a target-store inventory, authentic archive provenance and a private
operational rehearsal using the [upgrade runbook](../../../backend/notification-service/docs/receipt-byte-identity.md).
This PR does not backfill or deploy any target database. Missing archive provenance remains
an activation blocker for affected historical receipts. Other owners' audit findings remain
open in the [handoff registry](../../handoffs/README.md); this is not global backend closeout.
