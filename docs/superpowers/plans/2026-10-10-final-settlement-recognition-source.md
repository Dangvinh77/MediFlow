# Initial final settlement and earned/original-deposit recognition — Huy continuation

Owner: Huy, with the user's existing Billing implementation override. Gateway, Common, root build,
scripts/CI and frontend are unchanged. Earned timing follows the user's final-settlement-only choice.

## Scope implemented

Billing initial finalization uses actual local medical approval and performed Surgery/FILLED Rx
proof, original ledger relationships, exact allocation IDs, account/key/version fences and bounded
fresh-transaction retries for known aborts. Its V18 immutable operation stores initial settlement,
SETTLED and both HELD facts atomically. Fully returned VOIDED charges stay in actual account cash
totals but not in recognized POSTED-charge allocations. Existing reconciliation error codes remain
unchanged; exhausted new finalizer contention returns a safe retryable 503.

Report V22 validates and retains the explicit final operation, pairs only with its own accepted
original cash/refund facts and waits durably for missing deliveries. Source/delivery fingerprints,
account then sorted original fences, bounded recovery/quarantine and atomic operation/scopes/release
prevent duplicate recognition or refunding already released cọc. The unchanged real producer golden
scenario is 120 deposit / 20 actual unused refund / 100 final, earned 60/40 at generating departments,
release 100 at original receipt department and one hospital total. No REST enrichment is introduced.

Canonical wire is defined only in
[CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#initial-final-settlement-and-explicit-earned-operation--2026-10-10);
Report rules in [CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#initial-final-only-recognition-intake--2026-10-10).

## Verification

- Corrected full Billing production snapshot: 859 tests, zero failure/error/skip, exit 0.
  `billing-final-writer-corrected-full.log`; subsequent actual cash golden fixture and application/
  architecture selection: 13 tests, zero failure/error/skip, exit 0 (`billing-final-golden-corrected.log`).
  An earlier fixture test compared a floating JSON number with a decimal node; parsing both as
  exact decimal fixes representation, without normalizing money/business IDs or weakening assertions.
- Full Report production snapshot: **869 tests / 93 suites**, zero failure/error/skip, exit 0,
  `report-final-recognition-full.log`. This is before the three final hardening cases below.
- Latest Report mapper: 33 tests against actual finalizer fixture, zero failure/error/skip.
- Latest Report PostgreSQL: 16 tests, atomic write rollback, two replicas, changed source/delivery,
  missing original/refund, quarantine, bounded recovery and released-original refund guard.
- Report config/consumer/Rabbit subset passed, including retained unchanged DLQ bytes and listener
  restart. This is listener restart, not JVM crash or full admission workflow acceptance.
- Latest full affected selection: **82 PASS**, zero failure/error/skip, exit 0,
  `report-final-recognition-hardening.log`: mapper 33 + PG 16 + gates/recovery 9 + consumer 3 +
  actual Rabbit 3 + existing envelope decoder 12 + architecture 6. It includes exact envelope
  fields, 2000–2100 date limits and cash account indexes. Overlapping reruns are not added to totals;
  no fresh full 872-test run is claimed from mixed XML reports.
- Final runtime/test source hashes match the verified copy: Report 299 and Billing 393
  Java/SQL/JSON/YAML/XML/HTTP + POM files, zero differences. `git diff --check` passes; no diff
  in Gateway/Common/root build/scripts/CI/frontend. Existing dirty work is retained.

**R-03.3 is complete for its initial local recognition/original-release criterion**, with the
same producer-service fixture and exact consumer contract passing. Original checklist is now
**53/70 (75.71%)**, **17 open**, **10 more** needed for 90% (Pharmacy 25/32, Report 28/38).
New implementation steps or test counts are not added to the original denominator. Initial-only
completion does not close the separately listed correction/reversal/replay/rollout requirements.

## Still open

- Post-final corrections/revision predecessor chains, earned reversals, insurance attribution,
  approved debt/waiver, historical final settlements and SETTLEMENT_PAYMENT cash intake.
- Full five-metric source export/rebuild, accepted V20 publication, catch-up/final fence/cutover.
- Initial admission deposit request/top-up and complete referral/admission/discharge/close acceptance.
- Pharmacy order/current placement, distributed eligibility revocation and dispense races.

All new runtime gates default OFF; outbound facts remain HELD. No commit/push, automatic live
release, invented clinical policy, staffed capacity or historical source approval is claimed.
