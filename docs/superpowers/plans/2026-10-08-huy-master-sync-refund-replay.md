# Huy — master sync and finite cash-refund rebuild, 2026-10-08

Evidence for existing S-03.1/.5, R-01.5.3/.6.2 and X-01.7, not a new task inventory or release approval.

## Source integration

- Fetched origin/master `d6638f5b`, eight new commits; merged at `36ba3106` on local Huy.
  Gateway and frontend match master and were not edited. Master is an ancestor of local HEAD.
- Full dirty tracked/untracked work is preserved in safety snapshot
  `1f63cd63abf8c0beec0526c7ed85507e4d5ad6f1`; the hook record is additionally preserved in
  `81bb7398c5b563171b86881ebcfb96edafa9a09f`. Earlier stashes remain untouched.
- Kept master's three Billing Surgery charge classes. Recovered the previously untracked planned
  request implementation as `SurgeryPlannedRequestService`, `SurgeryPlannedRequestRepositoryPort`
  and `SurgeryPlannedRequestRepositoryAdapter`; source bytes/behavior preserved except names.
- Moved the unreleased local planned-issuance V7 to V9; master's committed V7 reconciliation
  checksum/version remains unchanged and local V8 refund audit is preserved. Clean Billing build
  removes the old generated target resource. One master `PaymentRefundedPayload` definition is
  retained, not the duplicate produced by automatic stash merging.
  Verification uses fresh isolated test databases. If a separate existing developer database has
  already applied the earlier uncommitted V7 issuance, its history requires reviewed migration/data
  reconciliation before this branch runs there; never blindly repair Flyway, replace checksums or
  drop that database. No persistent developer/production database was reset by this operation.
- Notification retains all five new master care bindings plus the local flat invoice subscriber.
  Its 12-key dispatcher test covers the combined topology. V1 gates remain default false.
- origin/Huy was deleted remotely. This operation neither recreates that branch nor pushes work.

## Surgery creation single writer

Master now binds `surgery.case.created` to `billing.q`. The prior local gated queue would also create
the same `(SURGERY,case,priceCode)` charges. Integration now keeps one physical dispatcher and
selects exactly one creation writer: both issuer flags true use the strict original-byte planned
issuer; otherwise the master charge-only handler remains. Decode/storage failure cannot fall back
to a second writer. Completion/cancellation still call the owner's existing handlers.

The gated helper has no Rabbit listener, topology or parallel binding. Payment/request/source
receipt and held invoice atomicity, replay without repricing and catalog/rollback/DLQ recovery are
verified on this real single queue. Do not toggle off a previously activated issuer as a substitute
for backward semantic compatibility: held-event release/cutover remains unapproved. Any deployment
of the earlier uncommitted topology must stop that binary and deliberately drain/unbind its queue
before rollout; this code never deletes queues/messages or releases held rows automatically.

Owner outcome code being present is not full financial acceptance: performed lines vs selected
request totals, allocation reversals, cancelled request/grant state, early outcomes, source-content
conflicts and remote START fencing still need their existing tasks. Notification's master care reader
and opt-in semantic Surgery reminder reader also require an explicit single-intent cutover review;
both should not be activated as independent senders for the same fact.

## Report V17 finite cash-refund rebuild

- Internal `ReplayCashRefundsUseCase`, no public route/listener. New V17 tables are additive;
  V12/V13/V15/V16 and their hash formats are unchanged. Existing V13 gross-only runs still resume.
- Paired V13 receipt and V17 refund manifests freeze in one REPEATABLE READ transaction. A caller
  already in READ COMMITTED is rejected rather than mixing snapshots. First-delivery proofs are
  mandatory; missing proof rolls back both generations, never silently filters a bad source.
- Refund format 1 is local: typed minimal exact IDs/money/ISO nanoseconds/business date/zone,
  classification/original date or explicit pending/rejected reason. It is not a producer revision.
  PostgreSQL JSONB text SHA-256 protects this new copied snapshot; V15 source and first-envelope
  hashes are opaque provenance, not a claim to reconstruct/authenticate raw redacted Billing bytes.
- Batches are 1..500 per phase: first receipt rebuild completes, then refund inputs restore into
  isolated facts/scopes. A finishing receipt batch and up to one refund batch share the transaction.
  Generation row locks fence replicas, not JVM mutexes. Fact, two scopes, markers and progress
  rollback together on error; terminal retries do nothing.
- Applied refunds recheck exact frozen original identity/chronology/classification/original day
  and the sum of all frozen APPLIED refunds against that original, independent of iteration order.
  Live/replay share the pure hospital-first scope planner. Cash-out is refund day, not ingest day;
  original date remains evidence for future recognition/liability reversal.
- Frozen PENDING and REJECTED are rebuilt as inventory with no cash-out or retry/notification.
  A later live original/recovery does not rewrite an old snapshot; a new run captures the new state.
  This is accepted cash-state rebuilding, not replay of all historical arrival/rejection decisions.
- Bidirectional full fact/proof and scope equality plus inventory/progress/version checks against
  the frozen manifest yield VERIFIED/FAILED. VERIFIED also requires its receipt generation VERIFIED.
  Comparison never uses advancing live totals. No reset of live inbox/tables or publication occurs.

Gross receipt and completed cash-out totals are available internally as separate dimensions, not
earned revenue, deposit liability release or settlement. No finite VERIFIED snapshot proves export
coverage, pre-activation history, live catch-up or financial read approval. R-01.5.3/.6.2 parents
remain open for those full-target portions; no task percentage is inflated from this slice.

## Verification

- Post-merge initial five-service package PASS (Pharmacy/Billing/Notification/Report/Surgery).
- Billing focused single-writer/producer/owner-outcome/Rabbit suite: 79 PASS; Notification combined
  dispatcher: 15 PASS, all zero failure/error/skip. Includes 13 real PG/Rabbit single-queue cases.
- Initial Report focused: 38 PASS (gross replay 20, refund replay 11, previous refund upgrade 1,
  architecture 6), real PostgreSQL, zero failure/error/skip. Later added cases and full regression
  are recorded below after completion; these focused counts overlap full suites, not extra totals.
- Complete post-merge regression: **Billing 353 / Notification 200 / Report 560 PASS**, all
  zero failure/error/skip, finished 21:22 Bangkok. Report includes 13 refund replay PG cases,
  8 application cases and V16 -> V17 upgrade. MVCC orchestration commits a live refund between
  the two freeze statements and proves it stays outside the earlier paired manifest. Two valid
  partial refunds/new delivery IDs, exact nanos, pending->live-recovery isolation, rejected inventory,
  corrupt hash, missing proof, old gross-only resume, extra-scope reconcile failure, replica race
  and whole-transaction rollback are verified. Focused counts overlap these full suites.
- Final fresh five-service package PASS, finished 21:25 Bangkok, including restored Surgery work.
  This is compile/package evidence, not a rerun of the full Surgery or Gateway suites.
- Fresh packaged four-JVM acceptance **3/3 PASS**, zero failure/error/skip, finished 21:31:04
  Bangkok. Pharmacy/Billing/Report/Notification use separate owned databases and RabbitMQ:
  actual HTTP prescription/invoice/payment/dispense/report, same/new-delivery dedupe, Report JVM
  outage/catch-up and broker outage + Pharmacy JVM restart/recovery. Queues drain and DLQs are
  empty. No Surgery/Gateway runtime or V1/publication acceptance is implied.

## Reproduce

```powershell
mvn -pl backend/billing-service,backend/notification-service,backend/report-service -am test
mvn -pl backend/pharmacy-service,backend/billing-service,backend/notification-service,backend/report-service,backend/surgery-service -am -DskipTests package
mvn -f backend/pharmacy-service/pom.xml -Pdistributed-acceptance -DskipUnitTests=true verify
```

Docker is required. Packaged runtime acceptance must use fresh jars. No Gateway source changes,
clinical policy invention, source history purge, V1 held release or remote push is authorized here.
