# Huy Report V2 — offline gross cash receipt continuation

Date: 2026-10-07. Scope: `backend/report-service/**` and relevant docs only.
Gateway and all other owners' production modules are unchanged. No commit/push in this turn.

## Completed local slices

- R-03.1.1: strict actual Billing service/deposit V1 fixture mapping into immutable cash receipt
  evidence, preserving monetary decimal precision from JSON parsing, explicit source/episode/
  classification and business completion time. Unknown/missing/replacement contracts fail closed.
- R-03.2.1: internal use case and V12 isolated source/delivery/gross daily receipt tables.
  `transactionId`, not invoice/request/event ID, is semantic identity. Both hospital and Billing
  account-department effects commit atomically; currency, timezone and classification are separate.
  Normalized source and exact delivery hashes fence changed facts, including noncash fields.
  No raw payload archive is retained; explicit completed-at ISO text preserves nanoseconds.
- R-03.7.1: local cash-only unit, PostgreSQL dedupe/concurrency/rollback and additive upgrade tests.
  The 60/40, currency and negative scenarios mutate real fixture inputs for consumer testing;
  they are not newly approved Billing producer fixtures or joint acceptance evidence.

Canonical rules: [CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#report-offline-gross-receipt-evidence--2026-10-07).

## Verification

Final full Report regression passed at 10:10 local time: **310 tests, zero failures/errors/skips**,
including actual PostgreSQL 16.14 and RabbitMQ integration. New coverage is 29 mapper tests,
4 application tests, 10 cash PostgreSQL tests and 1 V11→V12 upgrade test (44 added cases).
Nanosecond completion reload, republished business dates and bounded unknown numbers also pass.
Architecture tests and `git diff --check` pass. No root/Gateway green-CI claim is made.

Command: `mvn -q -pl backend/report-service -am -Dapi.version=1.44 test`.

## Remaining target work

These are **gross completed cash inflows**, not net cash, earned revenue or deposit balance.
Receipt department is the account department, not multi-department charge allocation. Parent
R-03.1/.2/.7 remain open for full-target acceptance. No allocated-earned amount, unallocated
deposit amount, recognition/release, refund/settlement revision or receivable movement is inferred.
SETTLEMENT_PAYMENT needs an actual producer fixture/acceptance before mapping. Unknown metrics
are not published as zero. Legacy five bindings and reports stay unchanged; no financial listener,
HTTP API, replay/backfill, publication/read switch or held producer release is activated.
