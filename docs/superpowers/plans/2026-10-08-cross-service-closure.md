# Cross-service closure — 2026-10-08

User assigned Clinical, Inpatient, Billing and Notification for the remaining dependencies.
Huy's Surgery/Pharmacy/Report remain writable. Gateway and shared build/CI/Common/Compose stay
read-only. Preserve earlier uncommitted work and branch state; this batch does not push or commit.

## Completed local engineering checkpoints

- [x] Report cash-only broker intake: full clean **490/490**, 60 reports, no fail/error/skip;
  [cash evidence and manifest](2026-10-08-report-cash-intake.md).
- [x] Register exact Surgery provisional/suppression semantics in the canonical projection contract.
- [x] Decode the actual eight Surgery producer fixtures (four event keys, two episode contexts).
- [x] Persist exact case/request/patient/department/episode and immutable source evidence in V3.
- [x] Pin snapshot/schedule revision and reject identity reassignment or changed same-source bytes.
- [x] READY stays private/provisional, not confirmed booking; no insecure clinical template/contact.
- [x] Invalidation-before-READY survives consumer restart; terminal-before-READY suppresses new sends.
- [x] New nonterminal snapshots can notify without resurrecting invalidated snapshots.
- [x] Cancellation/completion terminal evidence cannot be replaced by contradictory later sources.
- [x] Claim/state/history/held sent outbox roll back together; original failed bytes can be replayed.
- [x] Full clean Notification **120/120**, 17 fresh reports, no failures/errors/skips;
  PostgreSQL 16 and RabbitMQ 3.13, 2026-10-08 10:16:07–10:17:41 Bangkok; Maven exit 0.
- [x] Billing actual Surgery planned-charge issuer, selected request/target and held invoice fact.
- [x] Billing same-byte fixtures, duplicate/conflict, catalogue/precision, concurrency and rollback.
- [x] One episode across generating departments preserves charge and account attribution separately.
- [x] Actual issued rows → payment/allocation → exact clearance → signed internal HTTP authority,
  both admission and outpatient contexts; no seeded charge relationships in this proof.
- [x] Version-aware Notification intake for actual Billing held Surgery payment-request fixtures.
- [x] Notification final full clean **145/145**, 19 reports, 0 fail/error/skip,
  10:34:36–10:36:42 Bangkok; supersedes 120-test slice, not an additive count.
- [x] Billing final full clean **280/280**, 42 reports, 0 fail/error/skip,
  10:37:56–10:39:39 Bangkok, Maven exit 0, actual PG16/Rabbit3.13. All five architecture rules pass.
- [x] Final packaging of current Surgery/Pharmacy/Report/Billing/Notification artifacts with common
  dependency: Maven package exit 0 (tests skipped only for this packaging command; full suites above
  executed separately). Existing Huy production files are preserved; no baseline file is missing.

These are evidence checkpoints, not new global business-task IDs. The first Notification Surgery
slice has 42 tests (23 contract/application, 6 configuration, 13 broker/PG); they were part of 120,
not additional tests. The final invoice follow-up adds 25 tests; all 67 are inside the final 145.
Both real Surgery reminder consumers run in the broker tests. Existing SENT history is not
rewritten. No external reminder worker is claimed. Billing has 44 new scoped tests (26 contract/
application, 6 configuration, 12 PG/Rabbit/HTTP), included in the 280 total.

## Exact source/producer fixture manifest

Billing charge tests read Surgery's original `surgery.case.created.admission.v1.json` and
`surgery.case.created.outpatient.v1.json` directly. Notification reads all eight original READY,
readiness-invalidation, cancellation and completion fixtures, without copying them. Actual Billing
issuer serializer tests verify the new fixtures below; only random envelope event ID is normalized.
Runtime generated IDs and catalog amounts remain Billing-owned. `PRICE=100.00` is synthetic test data,
not a production catalog approval or a default. Production catalog stays empty unless configured.

| Raw Billing producer file under `contracts/ledger-v1` | SHA-256 |
|---|---|
| `invoice-surgery-admission.json` | `0742083dda73360eea8034c69d0056bd4423f1d1e79eac3f0c9715dfc31dd414` |
| `invoice-surgery-outpatient.json` | `42000e8a01aa4d4961749a7e89b7683fa14336f384a720df6a61f96316b0f4f0` |

Notification decoder and broker tests read those Billing files directly. Its semantic request receipt
and held sent event commit with delivery/history; duplicates/new delivery IDs do not send twice.
Changed request amount/context rejects; SQL failure rolls all effects back and exact retained DLQ
bytes can be replayed. No request fact is reinterpreted as receipt/clearance/revenue or finalized surgery.

The first Billing focused run failed one assertion because admission/outpatient producer fixtures
represent different patients while the test intended the same patient. The test now sets the second
explicit patient reference to the first; no production invariant was relaxed. A subsequent review
corrected account reuse across departments (episode/patient, not department, is account identity)
and added a dedicated attribution test before the final 280-test clean run. Notification initially
had a test-only package-visibility compile error; topology is now inspected through actual Spring
context wiring. No tests were disabled to obtain green results.

## Still not production completion

Clinical/Inpatient referral and pre-op authority, Surgery policy/fences, performed-charge/cancellation
reconciliation, refunds/top-up/settlement, full Report finance and reviewed release/cutover remain
unfinished until implemented and verified. No missing history, approved clinical policy, pricing or
source relationship may be guessed. All new intake flags default false and held producer rows stay held.
No Clinical/Inpatient production changes were made yet in this slice despite the granted override;
those implementation dependencies remain next work, not a request to wait for the old owners.
No Gateway edits, root reactor or end-to-end production activation are claimed. No commit/push.
