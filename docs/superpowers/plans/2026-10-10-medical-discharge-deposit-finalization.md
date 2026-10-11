# Huy V2 — medical discharge and final-settlement prerequisites, 2026-10-10

## Scope and honest progress

User decision: earned revenue is recognized only after final settlement completes. This decision
is now in CARE-BILLING/CARE-PROJECTIONS, their service docs and implementation specs. No revenue is
inferred from a payment, deposit allocation, performed care or non-final settlement stock.

Report and the already-authorized Billing override implement the prerequisites below. Gateway,
Common, root build, scripts/CI, frontend and all existing dirty changes are preserved. No push,
commit, live flag activation, production SQL or release of held financial events is performed.

The original Pharmacy/Report checklist remains **52/70 = 74.29%**, **18 open**. Another **11 original
leaves** must fully close to reach 63/70 = 90%. This batch creates no new checklist IDs and does
not close an original leaf merely because a prerequisite has tests. In particular R-03.3/.4 and
R-04.2.3 remain open; the final financial writer/projector/replay/publication are still absent.

## Report: actual medical discharge

- Reads unchanged Inpatient five-field `discharge.medically.approved` V1 producer bytes.
- V21 stores minimal immutable source/delivery evidence, not discharge clinical text.
- Exact admission/patient, approved summary/signatory, event/source hashes and chronology checked.
- START supplies actual admittedAt and generating department; approval supplies medical end time.
- Source can wait durably for START; recovery uses the same admission transaction/lock.
- Applies MEDICAL_DISCHARGES, MEDICAL_LOS_MINUTES and COMPLETED_STAY_DAYS to hospital + START
  department atomically. Minutes and 24-hour completed periods are floored per stay, not after sum.
- Fixture: 2026-09-28T02:10Z -> 2026-09-30T09:00Z = 3290 whole minutes, two whole 24-hour periods.
- `completedStayDays` is NOT staffed-bed occupancy, billable days or a midnight census. Existing
  inpatientDays is not silently redefined; those original requirements remain open.
- Same proof/new delivery, pending recovery, replicas, second-scope fault, storage retry/DLQ,
  retained-byte replay and nanosecond/minimal-evidence guards tested. Gates remain OFF.

## Billing: actual medical charge-close

- Exact Inpatient bytes on the existing single Billing dispatcher; no competing listener.
- V15 minimal source/delivery receipts + OPEN -> CHARGE_CLOSED/version commit atomically.
- Admission episode/account fencing shared with Rx and Surgery account issuance.
- Without an account the proof is PENDING_ACCOUNT, not a guessed account/department/currency.
- Financial issuers cannot replace Inpatient patient authority. Wrong patient fails closed.
- Known matches/replays do not re-price; no payment, final settlement or earned event is emitted.
- No automatic pending-account factory, recovery scheduler, replacement or late-charge/reopen policy.

## Billing: explicit deposit allocation and actual unused-cash refund

- Gated ADMIN/CASHIER POST validates signed UUID actor, key and exact original deposit/charge IDs.
- Original-only remaining budget accounts for completed original returns and prior allocations;
  other payments do not subsidize it. Each selected POSTED charge has its own unpaid bound.
- Nonzero insurance attribution is unsupported explicitly, never guessed by account department.
- V16 base allocations + immutable audit/result + HELD `deposit.allocated` V1 commit together.
- Existing original/charge pairs remain append-only; replay is bound to actor and exact intent.
- Actual cashier refund now returns unused deposit cash even after a partial allocation: 100
  deposited -> 80 allocated -> 20 refunded leaves funded lines unchanged and zero unused cash.
- Refunds cannot consume allocated money or borrow another original. The normal SERVICE_PAYMENT
  allocation-reversal path is retained. Private audit reason is not exposed in the financial event.
- Account version updates fence stale RR allocation snapshots against concurrent cash returns;
  replay does not change version. Refund/audit/version/outbox failure rolls back every effect.
- New server-recorded refund times use PostgreSQL microsecond precision and amounts use exact
  scale two, so first response and replay agree. Clinical producer instants remain unchanged.
- Actual allocation service serializer fixture is checked; diagnostic PG setup does NOT establish
  an initial admission deposit request issuer or production source acceptance.
- Report/Notification do not bind or recognize the new allocation fact; it remains HELD/non-final.

## Billing: canonical performed Surgery proof

- V17 minimal immutable source/delivery proof pins exact Surgery case/request/result, source revision,
  episode/patient/generating department and original completedAt/recordedAt ISO strings.
- Original source bytes and full-source hash are validated; clinical narrative is never persisted.
- Three default-off gates select one strict branch of the existing Billing dispatcher.
- Same case/account/delivery lock order as planned intake/cancellation; every charge reconciliation,
  processed receipt, source/delivery proof and account fence commits atomically.
- Replay validates immutable proof and actual reconciled lines, never re-prices today's catalogue.
- Existing compatibility completion without proof cannot be promoted by reconstructing history.
- Unknown price, wrong context/revision, metadata/source change, future proof and storage fault reject;
  bounded retry/DLQ retains original bytes. Listener restart tests are not JVM/process crash tests.
- Actual performed-difference fixture changes charges without rewriting a paid request or creating
  settlement. Omitted planned lines, zero/free-care grants, additional requests, late correction and
  reviewed release remain unsupported/separate work.

## Finalization domain prerequisite

AdmissionFinalizationPlan is a pure, private-construction readiness calculation, not an endpoint,
writer or event producer. It rejects unfinished money/unused cash, missing or post-medical performed
proof, wrong account state and nonzero insurance without explicit charge attribution. A supported
paid-in-full plan reconciles generating-department earned totals and original applied deposit
releases separately; account department is never substituted for the generating departments.
No debt/waiver approval, completed settlement, recognition event or Report earned projection exists
yet. This prerequisite is not counted as R-03.3 complete.

## Verification

- Report final full source: **808 tests / 88 XML suites PASS**, zero failure/error/skip, real
  PostgreSQL 16/RabbitMQ 3.13. Includes all V21 pending, transaction, replay, gate and broker guards.
- Billing before unused-cash refund/Surgery-proof follow-ups: final **731 tests / 82 suites PASS**,
  zero failure/error/skip; this older count is not presented as verification of later source edits.
- Refund follow-up targeted: **87 tests / 6 suites PASS**, zero failure/error/skip. Includes new
  eight budget rules + eight actual PG workflow/race cases and existing payment/refund/fixture/
  architecture regressions. First run exposed nanosecond/scale replay differences; production fixed.
- Surgery-proof targeted: **36 new tests / 4 suites PASS**, zero failure/error/skip, plus unchanged
  ArchitectureTest. Includes actual producer bytes, eleven PG cases and three real PG/Rabbit cases.
- Final clean full Billing: **794 tests / 88 XML suites PASS**, zero failure/error/skip, real
  PostgreSQL 16/RabbitMQ 3.13, exit 0. Includes the eleven pure finalization-readiness rules.
  After this full snapshot, four new proof-service unit cases were added and passed in a targeted
  **20-test** run (four new + eleven existing domain + five architecture), zero failure/error/skip.
  These overlapping runs are not summed or called a new full 798-test run. Production source did
  not change between the full run and this final unit-only addition.

Verification uses a separate source copy, unchanged root POM and freshly compiled owned module;
no target contamination from the editor. Repeated targeted/full runs are not added to test totals.

## Next required implementation

1. Billing final PAID_IN_FULL writer with medical + actual performed + original allocation evidence,
   immutable final-operation identity and atomic held facts; debt/waiver and insurance remain explicit.
2. Report final recognition/original-deposit release and reversal mapper/store using actual Billing
   service-produced bytes. Non-final cumulative gross never becomes an incremental earned amount.
3. Full five-metric replay, correction/shuffle/pending-original reconciliation and controlled complete
   generation publication; no manual seed or cash-only generation promotion.
4. Pharmacy order/placement/current-authority/revocation and atomic dispense contracts in the
   authorized service scopes, without crossing another service's database or changing Gateway.

Canonical contracts: [CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
[SURGERY-BILLING](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md),
[CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
