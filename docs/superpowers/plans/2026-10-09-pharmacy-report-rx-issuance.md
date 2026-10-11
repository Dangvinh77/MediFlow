# Pharmacy–Report V2 — exact Rx issuance continuation, 2026-10-09

Owner Huy (`LQHuy0210`). User request: continue completing Pharmacy/Report V2. Baseline `c4172761`
contains the previous priority batch. The existing `.changelog/entries.jsonl` change is preserved.
No fetch/merge/commit/push is performed. Pharmacy/Report are owned; Billing changes use the existing
explicit task-scoped dependency override. Gateway/Organization/Patient, Common/root/Compose/CI/
scripts, Surgery, Clinical/Inpatient and Notification production code are unchanged by this batch.

## Completed code and acceptance slices

Existing P-03.2.3a / P-02.5 / R-03.1/.2/.7 record this work; no extra task IDs or inflated 100%.

- Billing strict V1 original-byte decoder, pure application use case/ports, mandatory transactional
  JDBC adapter and dual default-off configuration. The existing billing.q dispatcher has exactly
  one writer per fact: flat V0 retains compatibility, malformed V1 never falls back. Unsupported
  V1 terminal facts cannot enter legacy compensation. No competing queue or arbitrary charge API.
- V12 additive immutable source/item/delivery receipts, exact episode account and selected bundle
  charge. Prescription prices are Pharmacy's validated server snapshot, explicitly specialized in
  the canonical contract before code. No guessed price, patient-selected episode or actor UUID.
  Billing additionally requires an explicit approved bundle-code policy; defaults are empty and
  an enabled issuer without policy fails startup. Unknown codes have no fallback, while matched
  historical replay does not depend on today's policy.
- Outpatient creates one selected PRESCRIPTION request, independent invoice identity and singular
  target; recordId remains evidence, never an EXAM clearance. Admission is charge-only, not prepaid.
  Source/amount/item/identity conflicts roll back the claim and all effects; matched replay preserves
  original IDs after account closure. Account department stays fixed; generating charge department
  remains explicit. Admission current charge-close integration is still open, not made authoritative
  by this internal charge writer.
- Actual installment writer produces separate receipts for 750 + 1250 and exactly one full grant.
  The producer-service test validates complete golden invoice/receipt/grant envelopes/payloads;
  only generated event/transaction IDs normalize. Pharmacy and Report read those exact files, not
  copied fixtures. Pharmacy rejects notices/receipts as authority; Report counts both transactions
  once and verifies finite cash replay without inventing earned revenue.
- Report optional target validation rejects abbreviated/nonstring Rx IDs, malformed/duplicate Lab
  IDs, mixed Rx/Lab and deposit service targets before any claim/scope. Positive Lab receipt stays
  cash-only. Broker tests verify retained poison bytes/DLQ and zero effects. Historical normalized
  proof-hash representation, legacy totals and all five financial equations are unchanged.
- No default flag or held-delivery constraint changes. Notification's new Rx invoice purpose is
  explicitly BLOCKED in its existing registered handoff; that is missing implementation, not a
  request for new user permission. No live notice/released event is claimed.

Canonical rules: [CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#opt-in-prescription-charge-issuance--2026-10-09),
[CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md),
[Notification blocker](../../handoffs/HANDOFF-NOTIFICATION-CARE-PROJECTIONS.md).

## Verification

Docker 29.6.2 reachable; real PostgreSQL 16 and RabbitMQ containers, no disabled-without-Docker
skip accepted as evidence. TDD receipt-target checks first reproduced eight acceptance failures,
then passed after validation. First decoder compile was red before implementation.

Targeted evidence: initial Rx 44/44 PASS (12 real PG/Rabbit cases), then updated decoder/architecture
50/50 PASS. New golden producer service test 1/1 PASS. Report targeted mapper/broker 72/72 PASS
before the final additional installment-fixture test. These overlap full suites and must not be
added to final totals. Fresh Pharmacy full suite: **595/595 PASS**, zero failures/errors/skips.
The final Billing policy/race changes were followed by a separate fresh full Billing run:
**465/465 PASS**, zero failures/errors/skips; the earlier combined Billing 459/459 is superseded,
not added. Fresh full Report: **594/594 PASS**, zero failures/errors/skips; combined reactor
BUILD SUCCESS. Final owned-module total is **1,189** (Pharmacy + Report), plus **465** Billing
dependency tests, **1,654** overall. These are full local module suites with real PG/Rabbit,
not packaged public V1 distributed acceptance or production rollout.

Final packaging of all three modules plus required common dependency: **BUILD SUCCESS** after
the test runs. `git diff --check` and whitespace checks for new untracked source/docs pass.
No released migration was edited; V12 is additive. Final scoped-path checks confirm no production
diff in Gateway, Common/root/Compose/CI/scripts or unassigned services. No commit/push performed.

The first full Billing run found a package cycle introduced by directly injecting the new
infrastructure handler into its existing compatibility messaging package. Fixed by an inward
application intake port; no moving legacy code or weakening architecture tests. The five existing
architecture rules subsequently pass. This failed historical run is not claimed green.

```powershell
mvn -pl backend/billing-service,backend/pharmacy-service,backend/report-service -am "-Dapi.version=1.44" test
mvn -pl backend/billing-service -am "-Dapi.version=1.44" test
mvn -pl backend/billing-service,backend/pharmacy-service,backend/report-service -am -DskipTests package
git diff --check
```

## Remaining full V2 gates

Historical checkpoint below is superseded for local terminal adjustment/private Rx notices by
the [Rx adjustment continuation](2026-10-09-pharmacy-report-rx-adjustment.md). Distributed authority,
admission late compensation, full finance and activation remain open; historical suite counts are
not added to subsequent full regressions.

Pharmacy still needs terminal cancel/expiry/failure finance adjustment, revoke/supersede authority,
authoritative order/current admission placement and close/discharge-versus-stock fences, reviewed
public adapters and release. Creation issuance no longer belongs to the missing-code list.

Report still needs actual earned recognition/deposit release/settlement/receivable facts and
equations, medical-discharge/LOS/occupancy semantics, complete source coverage/history and controlled
financial read publication/live catch-up/rollback. A posted bundle charge or cash receipt does not
prove earned revenue. Finite VERIFIED does not prove historical/live completeness.

Notification Rx notice and packaged public V1 workflow acceptance remain open. Prior CURRENT V0
3/3 distributed acceptance is historical baseline, not rerun or upgraded to V1 evidence here.
