# Huy — prioritize completed distributed flows (2026-10-08)

This is evidence for existing P-01/R-01/X-01 tasks, not a second task inventory or V2 approval.
User asked to prioritize distributed behavior after the architecture review. Stop domain expansion
and keep all current public V1/held delivery/clinical authority gates.

## Current implementation

- Pharmacy-owned `distributed-acceptance` Maven profile runs packaged Pharmacy, Billing, Report
  and Notification JVMs against four distinct PostgreSQL containers and one RabbitMQ broker.
  It has no cross-module Java dependency or database seeding/querying; setup and business
  assertions use existing authenticated HTTP APIs. Notification receives the mandatory invoice
  fact; an unroutable invoice predecessor is not ignored to make payment tests pass.
- Acceptance covers the CURRENT V0 prescription -> actual Billing invoice/payment -> automatic
  Pharmacy dispense -> actual Report queries, original-byte duplicate deliveries, Report JVM
  outage/durable queue catch-up and broker outage after prescription commit/Pharmacy JVM restart.
- Report V16 adds a minimal immutable receipt by `prescriptionId` for the CURRENT single-fill
  contract. Delivery claim, source receipt and both daily/drug scopes share the same transaction.
  Concurrent new deliveries apply once; changed department, exact business Instant (including
  nanoseconds), grouped drug/name/quantity or report zone conflicts rather than incrementing totals.
  It stores only a hash and source UUID, not patient/clinical/raw payload data.
- Existing historical Report totals/markers cannot establish old prescription source proofs.
  V16 is deliberately empty at upgrade, with no guessed backfill or claim of historic replay safety.
  Semantic protection starts with accepted fills after deployment; original event-ID dedupe remains.
- Notification's real CURRENT queue now binds and handles flat `invoice.created`. It persists an
  unpaid IN_APP request rather than a receipt; producer serialization and consumer use one Billing
  fixture. Versioned facts are not downgraded and separate V1 gates stay closed. This fixes the
  mandatory confirmed invoice predecessor without suppressing `NO_ROUTE` or relaxing outbox order.
- Billing absorbs a locked, already-COMPLETED prescription under a new delivery ID without further
  mutations. Original/repeat patient and numeric total must match its invoice; conflicts reject.
  The state machine and REFUNDED transition rules remain unchanged. This completion hint does not
  fingerprint all dispensing items; Report independently protects its projection source.
- The CURRENT Billing HTTP payment leg deliberately uses invoice UUID correlation. This batch
  verifies that correlation survives payment -> fill, not a continuous original prescription trace.

## Verification

- Report focused application/source concurrency/rollback/architecture: 30 PASS, no failure/error/skip.
- First three-service attempt: 3 timeouts; after adding Notification the first case still timed out.
  Captured Billing logs proved five `invoice.created` `NO_ROUTE` returns: Notification had no V0
  binding, so its quarantined predecessor blocked the later payment. These attempts were failures,
  not accepted evidence. The production subscriber was added; the final run uses fresh apps/DBs.
- Final packaged **four-service** acceptance: **3/3 PASS**, 0 failure/error/skip, 15:36–15:41 Bangkok.
  Private unpaid-request history is asserted through HTTP. Same and new delivery IDs preserve
  stock 17 from 20, one prescription, revenue 30 and drug quantity 3. All four normal queues drain
  and all four DLQs remain empty. Report outage catches up; broker outage + Pharmacy JVM restart
  recovers committed outbox. Source IDs/business data were not seeded/read through SQL.
- Report full regression: **538/538 PASS**, 0 failure/error/skip, including V15 -> V16 preserving
  history without guessed backfill and the pre-existing race suite with the real new adapter.
- Billing full regression: **318/318 PASS**, 45 reports, 0 failure/error/skip.
- Notification full regression: **180/180 PASS**, 21 reports, 0 failure/error/skip.
- Pharmacy full regression: **484/484 PASS**, 0 failure/error/skip, completed 15:45 Bangkok.
  Counts above are separate module runs, not a whole root-reactor/Gateway/Surgery or public V1
  release result. The focused 30 Report tests overlap its full suite and must not be added again.
- `git diff --check` PASS. No shared/Gateway source, environment flags, production data, commit
  or remote branch was changed. Existing dirty changes were retained.

The CURRENT Notification reader still uses event-ID idempotency: the new-ID replay assertions
prove stock/Billing completion/Report effects, not semantic single-notice history for every legacy
template. V1 source receipts, recipient/provider privacy acceptance and V1 cutover remain separate.

## Next boundaries (not completed here)

1. Pharmacy V1 authoritative charge/request producer and patient/episode/order create authority,
   then public create/dispense transport; no compatibility payment may unlock admission medication.
2. Report refund-inclusive finite rebuild, controlled source coverage/publication/catch-up.
3. Surgery actual referral/preop/readiness providers and explicit Billing refund-after-read
   consistency protocol, then guarded workflow runtime. A fresh REST read is not a distributed lock.
4. Gateway-mediated whole-system acceptance, Notification recipient/provider acceptance, clinical
   policy approval, producer V1 held release and full finance remain outside this CURRENT V0 proof.

## Reproduce

From the repository root, package the current apps first:

```powershell
mvn -pl backend/pharmacy-service,backend/billing-service,backend/report-service,backend/notification-service -am -DskipTests package
mvn -f backend/pharmacy-service/pom.xml -Pdistributed-acceptance verify
```

The explicit `-DskipUnitTests=true` property runs only this runtime profile after separately
verifying default tests. It does not skip Failsafe or downgrade Docker failures to skipped tests.
The test stops/restarts only containers it created and removes them on completion.

Current `NO_ROUTE` rows in a pre-existing deployment are **not** auto-released by this code: inspect
their original bytes and use reviewed outbox recovery after the subscriber is deployed. Historical
source coverage, external Notification delivery and V1 publication/cutover remain separate work.
