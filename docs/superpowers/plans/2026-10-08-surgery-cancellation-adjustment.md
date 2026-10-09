# Surgery cancellation financial adjustment — 2026-10-08

Implementation under the user's existing Billing/Notification dependency override. Gateway,
Common/root build/Compose/CI and other source changes are untouched. Existing dirty work is preserved.
This advances S-03.4/.5, S-07.4.2/.5 and X-01, not a new task inventory or a 100% claim.

## Delivered production code

- Strict Surgery-owned V1 cancellation bytes -> one optional helper on `billing.q`, never a second
  queue/listener or compatibility fallback. Exact patient/request/episode/department/case/recorder,
  sourceRevision=1, stage and business timestamp checks; bounded wire size, canonical UUIDs,
  duplicate JSON-key/trailing-body rejection and immutable source/delivery fingerprints.
- Additive V10 cancellation receipt/early inbox and per-original/per-charge refund-due records;
  V11 preserves exact producer request-time nanoseconds for new sources, without inventing lost
  precision in old history. Historical chronology uncertainty fails closed.
  V7/V8/V9 checksums and historical money/outbox rows stay unchanged. No retrospective cancellation
  inferred from a processed marker, no developer database drop/repair or backfill.
- Exact planned issuance prerequisite; case -> account -> sorted charge/request locking. Foreign
  patient/episode/record/request, performed charges, closed account and mixed request reject.
  Charge void + cancelled request + grant revocation + residual refund budget + APPLIED receipt
  commit together. Cash payments/allocations/request amounts remain immutable history.
- Early cancellation commits PENDING; new issuance joins its adjustment in the same transaction,
  before payment can observe a payable request, and suppresses the unpaid invoice notice. An
  adjustment storage failure rolls back creation while preserving the prior pending receipt.
  Bad early context is quarantined without poisoning valid issuance. Replay recovery and a durable
  20-case due worker resume previous-writer history independently; missing source defers 60 seconds.
- Revocation audit time is obtained after financial locks, not before waiting for a winning payment.
- ADMIN/CASHIER access-token-only direct Billing read returns current grouped refund budgets from
  one owned snapshot. Actual linked partial/full reversals reduce only that original's due; no
  inferred refund on another payment and no narrative in persisted/public projection.
- Existing cashier refund writer handles actual returned money, keeps canonical `payment.refunded`
  unchanged and HELD. Cancellation itself creates **no** completed refund or cash-out event.
- Owner completion repository now joins the same case/account/charge lock hierarchy, preventing a
  stale completion from reposting a voided charge. Price-group quantity aggregation validates exact
  four-decimal storage bounds/distinct items. Exact reconciled replay does not reprice from a changed
  catalogue. This does not fix full selected-request vs performed-value financial reconciliation.
- The default compatibility handler no longer fabricates completed refunds from cancellation.
  Paid cases reject before any void or processed marker; missing creation/performed care reject,
  and unpaid voids use a controlled reason rather than narrative. Strict adjustment remains gated.

## Verification

Final Billing full suite + packaged jar: **413/413 PASS** at 22:41 local time, zero failure/error/skip,
including atomic early issuance rollback, valid/bad early receipt isolation, post-lock audit time,
exact nanosecond chronology, historical uncertainty rejection and V10 -> V11 migration preservation.
The five-service full reactor also passed Pharmacy **484**, Report **560**, Surgery **908** and
Notification **200**; their source did not change during the subsequent Billing-only hardening.
Huy's three owned suites total **1,952 PASS**. The earlier reactor's Billing 401 and focused 103
counts are superseded by 413, not added to it. Actual PostgreSQL 16 and RabbitMQ 3.13 were used.
Five-service packaging passed before the final Billing hardening; Billing packaging passed again
with the final code. Docker 29 requires local `-Dapi.version=1.44`; repository dependencies/CI are
unchanged. Logs are under Pharmacy `target/huy-close-full-regression.log` and
`target/huy-close-billing-final.log` (ignored build evidence, not committed artifacts).

Verified scenarios: both producer outpatient/admission fixtures;
paid two-price case -> cancellation -> revoked current lookup -> actual cashier refund; previous
partial refund, source/new-delivery retry after account close, early cancellation/late creation,
invalid early patient quarantine, changed immutable source, known wrong patient and performed-case
rejection, whole-adjustment SQL rollback/retained-byte recovery, two cancellation workers vs payment,
ADMIN/CASHIER role boundary and SYSTEM/clinical-role denial, completion/cancellation race,
grouped quantities/no repricing, durable mixed pending recovery, mixed request rejection and
V9 -> V10 migration. Source timestamps retain ISO nanos.

## Remaining limits

The default compatibility unpaid-void handler is not the reviewed strict rollout/rollback path. No flags or HELD
rows are opened. No bank/provider execution, fee-retention policy, post-start abort, settlement,
distributed START fence or clinical/legal approval is fabricated. Gateway has not been changed to
route the new read. Performed reconciliation still needs authoritative line/source receipts,
immutable monetary adjustments and selected-request/allocation accounting; current grouping/locking
fixes alone do not close S-07.2.2. Pharmacy admission authority/public V1 and Report full
recognition/liability/settlement/publication remain open. Canonical source:
[SURGERY-BILLING](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md).

Reproduce with Docker:

```powershell
mvn -pl backend/billing-service -am "-Dapi.version=1.44" "-Dtest=SurgeryCancellation*Test,SurgeryRequestedTimeMigrationPostgresTest,SurgeryCharge*Test,SurgerySingleWriterDispatchTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl "backend/pharmacy-service,backend/billing-service,backend/notification-service,backend/report-service,backend/surgery-service" -am "-Dapi.version=1.44" test
```
