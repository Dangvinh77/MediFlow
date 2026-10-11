# Report V2 — finite initial-financial source rebuild

Owner: Huy. This continuation changes Report and its documentation only. Gateway, other production
services, Common, root build, scripts/CI and frontend are unchanged by this continuation. Existing
uncommitted work is retained; no Git synchronization, commit, push or feature activation is performed.

## Implemented

- Pure `FinancialSourceProjection` calculates cashReceived, refunds and earnedRevenue as daily
  flows; depositLiability and outstandingReceivable as closing stocks. Originals before the requested
  period supply opening stocks, not another period cash receipt. Earned remains final-settlement-only.
- Cash receipts use transaction identity, never invoice/request identity. Refunds require the actual
  original receipt/context and cumulative original budget. Initial final recognition requires exact
  local funding, charge-generating department allocation and original-deposit release. Last non-final
  debt is extinguished at final completion without another cash/revenue contribution.
- Non-final receivable replay uses every accepted revision, not only the latest current pointer;
  missing revisions, forks, conflicting identity/context/time and incomplete funding fail closed.
  Cash/final/non-final sources on one account must also share the exact patient/episode/receipt
  department context, even when no final fact has arrived.
- V23 provides a separate finite rebuild namespace, **not V20 financial publication**. Four bounded
  source reads share one REPEATABLE READ snapshot and copy minimal typed financial inputs, source IDs,
  delivery IDs, source/envelope hashes and exact snapshot hashes. SourceObservedAt is the database
  transaction observation, not a guessed MAX sequence, broker ACK or historical export watermark.
- Requested days must have ended in the configured zone. The complete retained local source set for
  VND/zone is copied, including earlier originals and PENDING/REJECTED refund/final sources. Any such
  unresolved input fails the frozen generation; later live recovery does not rewrite its manifest.
- Limits: 10,000 sources; 16 MiB manifest; 1 MiB per snapshot; 366 days; 100 department scopes plus
  hospital; batch 1..31 days. Inputs and reconciliation reads are bounded with database timeouts.
- Each batch holds a database generation fence and commits all department/hospital rows with its
  checkpoint. Storage errors roll back the entire batch; a recreated worker resumes the durable
  checkpoint. Typed manifest validation failures persist FAILED without swallowing storage failures.
- Full bidirectional day/scope/five-amount equality and unchanged manifest/provenance are required for
  local VERIFIED. Frozen inputs/header and terminal generations/amounts are database-sealed.

## Verification

- Clean full Report snapshot: **910 tests / 96 suites**, zero failure/error/skip, Maven exit 0.
  `D:/HK4/EProject/.mediflow-v2-verify-20261010/report-financial-rebuild-full.log`.
  This is before the final manifest-seal/account-context hardening and four additional cases.
- Latest affected selection: **88 tests**, zero failure/error/skip, Maven exit 0, in
  `report-financial-rebuild-hardened.log`: pure source/manifest projection 19 + application 5 +
  rebuild PostgreSQL 18 + existing final-recognition PostgreSQL 16 + existing non-final PostgreSQL
  24 + architecture 6. It includes immutable first-event identity, bounded reconciliation reads,
  sealed BUILDING manifests, recreated application worker and conflicting non-final account context.
- **42 distinct new cases** now pass; overlapping selections are not added to the full-suite total.
  No fresh full 914-test run is claimed from mixed XML reports. Recreated application worker is not
  a JVM-crash or full admission E2E test.
- Final source/runtime/test/POM hash audit: Report **310** files and Billing **393** fixture/runtime/
  test/POM files match the verification copy exactly, zero differences. Billing is not newly rerun
  by this Report continuation. `git diff --check` passes; no Gateway/Common/root build/scripts/CI/
  frontend diff is introduced.

The PostgreSQL scenarios use unchanged actual Billing producer-service fixtures. Initial golden
receipt 120 / cash refund 20 / final 100 produces gross cash 120, refunds 20, liability 0, earned
100 (60/40 generating departments), receivable 0. Additional scenarios cover pre-period originals,
actual non-final revision history, revision holes, early/pending facts and subsequent recovery,
same source/new delivery, late and uncommitted-source exclusion, concurrent workers, batch
rollback/resume, copied snapshot corruption, scope drift, database immutability and transaction
isolation. Frozen BUILDING manifests reject append/reopen, and a recreated application object resumes
the stored checkpoint. Synthetic cross-day arithmetic is domain testing, not another producer fixture.

## Original plan accounting and remaining work

**53/70 original leaves (75.71%), 17 open; 10 more original leaves required for 90%.**
No new task IDs or denominator changes are made. This is verified progress within R-01.5.3,
R-01.6.2 and the financial projection/reversal tasks, not closure of their broader acceptance gates.

Local VERIFIED means equality with the copied accepted local manifest only. Empty inputs are not
proof of historical no-events. Neither opaque original source/envelope hashes nor copied minimal
facts reconstruct raw upstream events or prove an upstream export is complete.

Still open: post-final corrections/reversals and their accepted producer facts; complete historical
export/retention/backup/coverage evidence; live catch-up/final fence; controlled V20 publication/read
switch/rollback; unsupported final insurance/debt/waiver/SETTLEMENT_PAYMENT cases; Pharmacy actual
medication order/current placement/distributed eligibility and dispense races. V20 remains empty,
feature defaults stay OFF, outbound facts stay HELD, and no source approval or clinical policy is
invented. No public HTTP command, automatic rebuild scheduler or live table reset is introduced.

Canonical technical semantics are in
[CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#finite-initial-financial-source-rebuild--2026-10-11).
