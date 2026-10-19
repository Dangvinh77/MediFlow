# Payment receipt V1: raw-byte identity and upgrade

Notification keeps the existing normalized `PROCESSED_EVENT.payload_hash` for old-reader
compatibility. V5 adds `receipt_raw_hash` (SHA-256 of the exact incoming body). New V1 payment
receipts claim both hashes in the same transaction as history and outbound delivery facts.
An identical replay has no effects; reuse of an eventId with different bytes is rejected to
the DLQ. V0, other projections and activation gates retain their existing behavior.

Historical V1 rows without a raw hash reject replays until verified archive import. Never
initialize these rows from the next delivery: normalized JSON cannot establish original
whitespace, member order or encoding. Notification history/outbox and DLQ are not archives
of all accepted inbound messages.

## Before deployment

1. Stop Notification writers/listeners, including Support instances hosting Notification;
   retain the current gates and backup the Notification store. Rehearse on a restored copy.
2. Apply the append-only V5 migration with listeners disabled. Do not edit V1–V4 or reset
   existing receipts, history or outbox rows. Inventory only the Notification database:

   ```sql
   SELECT count(*) AS legacy_v1_receipts
   FROM processed_event
   WHERE routing_key='payment.completed' AND payload_hash IS NOT NULL
     AND receipt_raw_hash IS NULL;
   ```

3. If any legacy rows exist, obtain an authenticated export of original producer body bytes
   for every eventId in that inventory. The producer owner supplies the archive and provenance
   (source snapshot, eventId mapping and file digests) through the operational handoff.
   Notification never queries another context's database. Each file must be the original
   UTF-8 body in a `.json` file; do not parse/reserialize, format, add a BOM or append a newline.
   Keep the archive private and access controlled. Its directory contains only these files.

The importer verifies normalized contents against committed receipts, full coverage of
unbackfilled V1 rows, duplicate/unknown IDs and any already pinned raw hashes. These checks
cannot distinguish a reformatted equivalent body from the true original. Authentic archive
provenance is an operator precondition. If original bytes cannot be established, leave the
affected consumer disabled and resolve the producer archive handoff; do not invent a hash.
An empty directory is valid only when no legacy V1 receipt needs backfill.

## Offline import

Build the Notification executable jar from the release revision. Set these variables through
the deployment secret mechanism: `MEDIFLOW_NOTIFICATION_DB_URL` (JDBC Notification URL),
`MEDIFLOW_NOTIFICATION_DB_USER`, `MEDIFLOW_NOTIFICATION_DB_PASSWORD`. Use a dedicated
Notification connection with permission to lock/update its receipt table and insert the ledger.
No application listeners or Spring application context start in this command.

From the repository root, first dry-run (default), then apply the same verified archive:

```powershell
java '-Dloader.main=com.mediflow.notification.infrastructure.recovery.CareReceiptByteIdentityBackfill' -cp backend/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher '--archive-dir=C:/secure/notification-original-receipts'
java '-Dloader.main=com.mediflow.notification.infrastructure.recovery.CareReceiptByteIdentityBackfill' -cp backend/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher '--archive-dir=C:/secure/notification-original-receipts' --apply
```

Require exit 0. Dry-run rolls back; apply commits all pending raw hashes and
`care_receipt_byte_backfill` entries together. A writer lock prevents concurrent receipt
claims; lock timeout is 10 seconds and any validation/SQL failure rolls back the batch.
The ledger stores eventId, old/new hashes, database user and verification timestamp.
The command emits a count only and never replays business effects. Reapplying the same
archive succeeds with zero pending changes. Keep command exit/count and ledger export
with the independently verified archive manifest in private operational evidence.

## Verify and resume

The legacy inventory above must now be zero. Compare receipt IDs, normalized hashes,
Notification history and outbox to the backup: only raw hashes and ledger rows may change.
Check ledger hashes against the archive manifest. Start the new binary with the intended
existing gates, then verify original-byte duplicate delivery creates no additional history
or outbox. A same-event altered body must dead-letter without effects. Any manual DLQ
recovery needs bounded, verified original messages; this tool never releases a queue.

Old binaries continue comparing unchanged normalized hashes and can read the expanded table.
This is data compatibility, not raw-byte enforcement in old code. Rolling back restores the
old integrity limitation; old writers may create further raw-NULL rows. Before returning to
the new consumer, repeat the inventory and authenticated archive import for those rows.
Do not drop V5 or overwrite the normalized hashes during rollback.

Automated coverage and release-source evidence live in
[Notification byte identity acceptance](../../../docs/acceptance/notification-v1-byte-identity-2026-10-19/README.md).
That evidence does not establish the inventory or provenance of a deployment database.

Re-run the packaged CLI rehearsal on a Docker-enabled verification machine:

```powershell
mvn -q -pl backend/notification-service -am -Preceipt-identity-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

This builds the executable jar first and runs the operator command against an isolated
PostgreSQL V4→V5 database. Run the full default Notification suite separately; the CLI
profile command above intentionally selects no unit tests.
