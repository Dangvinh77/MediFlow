# Pharmacy/Report V2 — financial pending acceptance and historical evidence protection

Owner: Huy. Production changes in this continuation are limited to Report, Pharmacy and the
previously user-authorized Billing override. Gateway/Common/root build/scripts/CI/frontend are
unchanged. Existing work is preserved; no Git sync/commit/push, held release or feature activation.

## Completed code and acceptance

- Report cash receipt, refund intake/recovery, initial-final recognition/recovery and non-final
  receivable intake share the same account database fence. Account precedes original transaction
  locks; final multi-original locks are sorted. Refund recovery resolves immutable original identity
  before locks, then re-reads under the account/original fences, avoiding inverted lock acquisition.
- Actual PostgreSQL wait-state tests prove all six writer/recovery paths block behind that fence.
  SQL lock/statement timeouts remain bounded; no JVM mutex or cross-service lookup is introduced.
- Final recognition writes the hospital scope before sorted departments, rather than HashSet
  iteration. A real database trigger test asserts hospital-first ordering. Failure at a later scope
  still rolls back source, delivery, APPLIED state, release and every contribution.
- Final funding also requires the exact receipt/account department for service payments without
  deposit releases. Pure positive/negative cases exercise that guard; mismatched retained cash is
  not reallocated or used to recognize earned amounts.
- All six receipt/refund/final delivery orders run through actual Billing fixture bytes on PostgreSQL
  and independently through real RabbitMQ/PostgreSQL. Early sources retain PENDING, recovery uses
  durable evidence, restart/redelivery does not duplicate contributions. The 120 receipt / 20 actual
  refund / 100 final operation yields cash 120, refunds 20, liability 0, earned 100 (60/40 generating
  departments), receivable 0. Final earnings are not receipt cash and V20 remains empty.
- Actual two Rx installments (750+1250) and three original-linked refunds (250+500+1250) reconcile
  cash/refunds 2000/2000 under payment-first, refund-first and interleaved orders, including repeated
  delivery. No final operation exists: earned/liability/receivable are zero for these supported facts,
  not an assertion that all historical financial data are complete.

These checks close original **R-01.3.2**, the local financial contribution/original-linked pending
criterion. They do NOT close post-final reversal/correction, historical coverage, full replay/source
acceptance or publication criteria in the separate original tasks.

## Verification

Verification checkout: `D:/HK4/EProject/.mediflow-v2-verify-20261010`; Java 21, PostgreSQL 16,
RabbitMQ 3.13, actual Docker engine and Testcontainers API 1.44.

- Clean full Report snapshot: **932 tests / 96 suites**, zero failure/error/skip, Maven exit 0,
  `report-account-fence-full.log`. This full run precedes the final hospital-order hardening,
  two service-funding department cases and six new Rabbit permutation cases.
- Latest affected Report selection: **117 tests / 7 suites**, zero failure/error/skip, Maven exit 0,
  `report-account-fence-latest.log`: FinancialSourceProjection 21 + CashRefundApplicationService 6
  + FinancialRebuildPostgres 33 + FinalSettlementRecognitionPostgres 18 +
  SettlementReceivablePostgres 24 + FinalSettlementRecognitionRabbitIntegration 9 + Architecture 6.
- **27 distinct new Report cases** pass. Full and selected runs overlap; no fresh full 941-test
  execution is claimed from mixed reports. Listener/application-object restart is not a JVM crash.
- Pharmacy **32 tests / 7 suites** and Billing **16 tests / 4 suites**, zero failure/error/skip,
  Maven exit 0, in `history-retention-safety-corrected.log`. This covers property defaults/opt-in,
  operator replay, dispatch, admin API, PostgreSQL Pharmacy repository/lease behavior and architecture.
  Billing's maintenance property checks are isolated contexts; no new Billing database-backed
  archival proof is claimed. This targeted run is not a new full Billing/Pharmacy regression claim.
- The first retention selection failed because the isolated test context lacked Boot's Duration
  conversion service. The corrected context uses the same Boot converter; production behavior and
  assertions were not weakened.
- Final runtime/fixture/test/POM SHA-256 audit: Report **310**, Pharmacy **330**, Billing **394**
  files match the verification copy, **zero differences**. `git diff --check` passes. No pending
  Maven/Java process, Gateway/Common/root build/scripts/CI/frontend change or Git mutation remains
  from this continuation.

## History requirement and safeguard

User explicitly requires history before V2. Activation-only coverage is not an accepted substitute.
The repo scan found no production dump/backup/event export; this does not prove none exists outside
the repo. Test fixtures are not production history.

Billing and Pharmacy previously scheduled published-outbox cleanup after a default 168 hours. Their
maintenance workers now have independent `purge-published-enabled` flags, default false, while
dispatch/replay remain available. Pending/quarantined/HELD semantics are unchanged. This preserves
future remaining source bytes; it does not restore already deleted events or prove completeness.
Re-enabling purge requires reviewed archive completeness, recovery/backup and retention policy;
the opt-in property by itself is not an archive receipt or history approval.

Before historical acceptance/publication:

1. Supply actual producer archive/export or restored isolated backups for the requested historical
   period. Each producer reads only its own source; Report must not read another service DB/REST.
2. Reconcile source inventory and historical horizon, including omitted/failed/pending/corrected
   operations. Broker ACK, current latest revision and an empty journal are not completeness proof.
3. Preserve exact original transaction/refund/allocation/settlement-operation IDs, business times,
   account/patient/episode, generating/receipt departments, classifications, currency and revision/
   predecessor chains. The legacy flat payment event has invoiceId, but not the V2 transaction/account/
   episode/allocation tuple. No synthetic V2 identity or guessed mapping is permitted.
4. Approved producer export schemas/fixtures and replay/import tests must handle legacy formats
   explicitly. Preserve legacy compatibility reports; never relabel their aggregate revenue as all
   five V2 financial metrics. Missing evidence remains unavailable, not a successful zero.
5. Verify redaction/access/backup/restore, final catch-up/fence, cross-owner totals, controlled
   publication and rollback before cutover. Unsupported post-final corrections remain separately open.

## Original accounting

**54/70 (77.14%), 16 original leaves open**: Pharmacy 25/32, Report 29/38.
Exactly one original leaf closes in this continuation; new tests/safeguards are not new checklist
credit. **6 more** original closures are needed for 60/70, **9 more** for 63/70 (90%).
Historical export/retention/backup R-01.4.2 and full publication R-01.7.3 remain open under the user's
full-history requirement. Pharmacy medication order/placement/eligibility, post-final financial
corrections/reversals and complete operational metrics are not marked complete.
