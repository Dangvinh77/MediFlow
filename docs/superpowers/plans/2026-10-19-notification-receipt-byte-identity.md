# Notification V1 receipt byte identity

Goal: reject same-event/different-body deliveries without losing committed receipts on upgrade.
Scope: Notification production, module tests and audit/handoff documentation only. Based on
the user's instruction to fix the remaining Vinh V1 finding. Audit PR #441 is now merged;
the follow-up PR targets master.

Design: keep `payload_hash` with its existing normalized algorithm for old-binary rollback;
add a separate raw SHA-256 for V1 payment receipts. Both hashes are computed from the same
validated body and committed with receipt/history/outbox. Existing receipts have no raw hash;
reject replay until an offline import verifies original producer archive bodies against
their existing normalized digests. Never learn historical raw identity from an arbitrary replay.
Other event types, V0 and activation gates keep their existing behavior.

The offline importer uses only the Notification database and an operator-provided archive,
validates complete legacy coverage before mutation, takes a table writer lock, defaults to
dry-run and commits all hashes/provenance atomically only with explicit `--apply`. It cannot
reconstruct original bytes from normalized hashes; authentic archive provenance is an
operator precondition. No live cross-context DB access or automatic queue release.

Alternatives rejected: replacing the old hash breaks rollback/original replay; accepting a
normalized fallback keeps the defect; accepting first replay as historical truth invents evidence.

Execution:

- [x] Reproduce original whitespace collision against PostgreSQL before production edits.
- [x] Add raw/legacy fingerprints, append-only V5 migration and atomic receipt claim.
- [x] Add offline, dry-run-first archive backfill with provenance and all-or-nothing coverage.
- [ ] Verify fresh duplicate/conflict, race/rollback, real queue DLQ and V4-to-V5 upgrade,
      original replay, changed bytes, missing/conflicting archives and old-reader compatibility.
- [ ] Run full Notification suite, fresh classifier build and Support regression; verify CLI packaging.
- [x] Independent core and CLI review; record partial results/source hashes and draft PR material.
- [ ] Finish Docker runtime gates, then remove resolved handoff and mark PR ready; do not merge automatically.

Current verification: final non-container Notification slice 228/228 and Support slice 13/13
pass; Notification install and Support package pass. Full default and packaged CLI attempts
failed Docker initialization. Handoff stays open, PR stays draft until these gates pass.
