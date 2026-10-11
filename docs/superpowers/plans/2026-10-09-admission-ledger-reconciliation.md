# Pharmacy–Report V2 prerequisite: admission ledger reconciliation

Date: 2026-10-09. Baseline: local Huy `c4172761`. Owner: Huy; Billing implementation uses the
existing user-scoped cross-owner override. All previous uncommitted Pharmacy/Report/Billing/
Notification work is preserved. No Gateway, shared infrastructure, frontend, push or release.

## Outcome and scope

Billing now has a pure, tested reconciliation of persisted admission charges, completed cash
transactions, original-linked allocations/refunds/reversals and insurance approvals/reversals.
The guarded ADMIN/CASHIER settlement-preview GET returns one bounded read-only database snapshot.
It is a prerequisite for a future authoritative settlement producer, not that producer itself.
Report remains event-only: no REST enrichment or cross-service database access was introduced.

Production implementation:

- `AdmissionLedgerSnapshot`: pure immutable evidence and monetary invariants; immutable row lists,
  existing Billing enums, UUID/Instant/BigDecimal. Identity, episode/currency, precision, chronology,
  duplicate IDs and per-original cumulative return/allocation bounds must all reconcile.
- `AdmissionSettlementPreviewService`: in-port calling the read out-port, exact requested-account
  check and not-found behavior. No framework/database calls or financial writes in the service.
- `AdmissionLedgerReadAdapter`: Billing-owned tables only; MANDATORY existing read transaction with
  repeatable snapshot validation. Five bounded data reads, at most 10,000 rows per table, fail closed
  on overflow rather than truncating totals. Cross-account allocations are deliberately included
  for validation, not filtered out of plausible amounts.
- `AdmissionSettlementPreviewConfiguration`: independent default-off ledger/preview gates; new
  read-only REPEATABLE READ transaction with a five-second budget, even under a weaker outer caller.
  Data statements have a three-second timeout. These are resource bounds, not a whole HTTP SLA.
- `AdmissionSettlementPreviewController`: thin GET with ADMIN/CASHIER roles; real signed access
  token with canonical UUID subject/expiry required, no refresh/service-token access. Missing account
  is 404; unclosed/inconsistent/over-budget ledger is 422; storage/transaction failure is redacted 503.

No new migration, receipt/outbox schema, competing queue or event payload/fixture is introduced.
The code uses the existing approved Billing English ledger schema/DTO convention, not a rename of
another owner's legacy fields. Exact endpoint/field meanings are in
[CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#read-only-admission-ledger-reconciliation--2026-10-09).

## Verified monetary semantics

| Persisted situation | Expected diagnostic result |
| --- | --- |
| Deposit 100, posted charges 80, no allocation | cash receipts 100; unallocated deposit 100; allocation balance 80; no earned revenue |
| Deposit 100, allocation 80, actual surplus refund 20 | gross receipts 100; refunds 20; net allocation 80; unallocated deposit/balance 0 |
| Two service installments 60 + 40, original-linked allocated refund 15 | gross receipts 100; refunds 15; net allocation 85; balance 15 |
| Completed transaction REVERSAL 10 | separate reversal amount; not a fabricated physical cash refund |
| Insurance approval 40, linked reversal 15, payments/allocation 75 against gross 100 | approved insurance net 25; patient liability/net allocation 75; balance 0 |
| VOIDED charge still holding unreturned payment | excluded from gross/posted allocation; unapplied cash remains visible, not silently reusable or returned |
| Invalid refund exceeds original's unallocated portion | reject even if another deposit could mask the overall amount |
| Another payment commits during the read | current preview retains its original MVCC snapshot; a new preview sees the new commit |

An estimated PAID_IN_FULL result remains `snapshotOnly=true, finalSettlement=false`; it cannot
approve discharge, suppress unapplied money or authorize a financial event. Completed refunds and
reversals are not conflated. Unallocated deposit evidence is not a complete deposit-liability or
earned-revenue projection. No payment, allocation, approval, grant, account state, settlement,
notification or outbox is changed by a preview.

## Verification

Initial focused run: **69/69 PASS**, including 11 real PostgreSQL tests, zero failures/errors/skips.
First full clean Billing regression: **584/584 PASS**, including the account-mismatch regression,
actual PostgreSQL 16.14/RabbitMQ 3.13, and architecture checks. These overlapping counts are not
added. Later hardening adds actual signed credential checks, full two-gate adapter checks, an
actual configured read-only transaction test and existing enum use. Final verification is recorded
below after testing the final exact source; earlier counts are historical only.

### Final exact-source verification

Fresh clean Billing regression after enum/helper/resource-budget hardening: **593/593 PASS**,
**68 Surefire XML reports**, **0 failures / 0 errors / 0 skipped**, BUILD SUCCESS at
**2026-10-09 15:38:42 Asia/Bangkok**. Actual PostgreSQL 16.14 and RabbitMQ 3.13 were used.
The 79 new cases are part of this 593, not added a second time:

| New suite | Cases |
| --- | ---: |
| Pure admission ledger invariants | 36 |
| Application exact-account/non-final boundary | 4 |
| Both-gate configuration/adapter/controller registration | 5 |
| Real PostgreSQL read-only snapshot/limits/cross-account reconciliation | 12 |
| Controller roles/errors plus real signed access/refresh/service credential checks | 22 |

All five existing Billing architecture checks pass. The previously passing, unchanged Pharmacy
595/595 (90 XML) and Report 620/620 (74 XML) reports remain available; they were not rerun by this
Billing-only regression and are not presented as new test executions in this batch.

Final package of Billing/Pharmacy/Report plus Common: **BUILD SUCCESS**, completed
**2026-10-09 15:40:09 Asia/Bangkok**. Packaging deliberately skipped tests after the regression;
it is not another test run. Source proof compares **2097 backend Java/SQL/JSON/YAML/XML/HTTP
files**, **zero differences** between workspace and isolated verification copy, with matching root
POM hash. `git diff --check` and new-source trailing-whitespace checks pass. No diff exists in
Gateway, Common/root/Compose/CI/scripts, Clinical/Inpatient, Organization/Patient or frontend.
Earlier uncommitted Notification and Pharmacy/Report work is retained, not overwritten.

Final checklist recomputation remains **25/32 Pharmacy + 24/38 Report = 49/70, 21 open**;
no whole acceptance criterion is marked complete by this diagnostic prerequisite. Canonical
CARE-BILLING/service/spec and the existing registered deposit/settlement handoff now identify
the implemented prerequisite versus remaining producers/consumers. No Git commit/push/merge,
feature enablement or held-publication release was performed.

Reproduction in the isolated matching source copy:

```powershell
mvn -pl backend/billing-service -am clean test "-Dapi.version=1.44"
mvn -pl backend/billing-service,backend/pharmacy-service,backend/report-service -am -DskipTests package
```

Tests use private ephemeral databases, not the configured development database. Seeded ledger
rows in the diagnostic PostgreSQL tests are deliberately not called producer fixtures, actual
medical-discharge source acceptance or completed admission E2E. The new endpoint has no event
to publish, so no new Rabbit listener is claimed. Existing full-module broker tests protect
compatibility/Rx/Surgery payment behavior. The isolated copy avoids editor-generated target files;
fresh clean compilation is required after source refresh, not a zero-test selector success.

## Plan status and concrete next work

This advances the prerequisites of R-03.3/R-03.5, not their whole acceptance. No new checklist IDs
or denominator changes: **Pharmacy 25/32 + Report 24/38 = 49/70 (70%), 21 existing leaves open**.
The 90% threshold remains 63/70, requiring 14 more fully completed existing leaves, not partial
arithmetic/verification slices counted as tasks.

Next implementation sequence:

1. Actual `discharge.medically.approved` → Billing charge-close/pending identity/race handling.
2. Authoritative original-linked deposit allocation and explicit recognition/liability-release facts;
   use this reconciliation rather than treating all receipts or a posted Rx as earned revenue.
3. Audited immutable final settlement/version/supersedes producer and actual application-generated
   fixtures. Additional-payment/refund obligations must not publish final completion.
4. Report event mappers, original-allocation reversal, versioned receivable stock, five-metric
   generation/replay/read API and controlled publication acceptance. Report never calls the preview.

These are implementation work under the existing override, not a newly invented request for owner
permission. Medical/legal approval, historical coverage and Gateway ownership are not overridden by
arithmetic or local test results. All default-off and held-release fences remain unchanged.
