# Huy — completed-refund implementation evidence, 2026-10-08

**Status:** local Billing → Notification/Report producer-byte acceptance, not backend 100%.
**Scope:** user-authorized Billing/Notification plus Huy Report; Gateway/shared paths unchanged.
No commit/push, new flag activation or V1 outbox release.

## Completed implementation

- Billing now accepts an explicitly gated ADMIN/CASHIER completed-refund record at
  `POST /api/v1/billing/transactions/{id}/refunds`. Signed access-account identity is mandatory.
  This records completed CASH/TRANSFER evidence; it does not instruct a bank or payment provider.
- Idempotency → account → original-payment locks serialize payments/refunds. Replay verifies actor,
  original identity, amount, reason, method and stored context. Completed original evidence is immutable;
  cumulative refunds/reversals cannot exceed its amount. Closed/settled accounts reject new refunds.
- V8 appends private refund reason audit. A SERVICE_PAYMENT reverses only the original payment's own
  remaining allocations, not another installment. Unallocated deposits create no earned allocation
  reversal; already-allocated deposit refunds require a later policy and are rejected.
- Newly unsatisfied grants are revoked in the same local transaction; the current-clearance REST
  lookup immediately denies them after commit. This is **not** a distributed START fence, a
  `financial.clearance.revoked` producer or grant supersession implementation.
- A held `payment.refunded` V1 carries an immutable refund/original reference and redacted categorical
  reason. The caller's free-text reason stays Billing-local. HTTP storage failure is safe 503 with
  correlation; transaction rollback permits a same-key recovery attempt.
- Notification's independent two-gated refund consumer validates those actual Billing bytes, claims
  delivery + semantic refund source and commits one private `PAYMENT_REFUNDED` IN_APP history plus a
  held sent fact. Exact money formatting, duplicate/conflict, rollback, DLQ and retained-byte recovery
  are tested. It never claims settlement, clinical authority or external SMS/email delivery.
- Report V15 stores minimal immutable refund/delivery evidence and separate actual-refund-day cash-out
  aggregates. It matches the original Report-owned receipt's exact context, currency, report zone and
  classification; no Billing DB/HTTP enrichment or deposit→earned inference occurs.
- Original receipt and refund share an advisory-lock protocol. Known-invalid evidence rejects before
  effects; early delivery remains durable PENDING. A later receipt recovers at most 20 pending refunds;
  invalid early evidence becomes REJECTED without poisoning that receipt. A gated durable worker handles
  remaining originals in batches of 20 and persists a 60-second deferral after failed recovery.
- Gross inflows remain unchanged. Cash refunds retain the original business date/classification and
  separately use the refund completion day for cash-out; no original-period earned/liability reversal
  is fabricated. Existing V13 finite replay remains **gross-only**, not full refund/finance replay.

## Exact wire provenance

The canonical envelope/payload is registered in CARE-PROJECTIONS. All consumers read original Billing
fixture files directly, rather than maintaining copies. Actual producer service/serializer tests
normalize only generated refund/event IDs before whole-envelope comparison. Test prices and identifiers
are synthetic, not production seeds or price approvals.

| Billing producer fixture under `src/test/resources/contracts/ledger-v1/` | Raw SHA-256 |
|---|---|
| `refund-service.json` | `3e7cb5c777b819b92d8ba2811be2f7cf3ade95b8935d17c6aa174a90aed4a683` |
| `refund-deposit.json` | `eac2fdadbf31165530813523f899e8698f42d8ace698a878e4481f1ac7a54664` |

## Verification

Fresh complete module suites on current source, PostgreSQL 16 and RabbitMQ 3.13:

| Module | Tests / reports | Failures / errors / skipped |
|---|---|---|
| Billing | 314 / 45 | 0 / 0 / 0 |
| Notification, final correction-marker hardening | 171 / 21 | 0 / 0 / 0 |
| Report, including bounded recovery follow-up | 526 / 65 | 0 / 0 / 0 |

Each final Maven invocation exited 0. Counts supersede earlier module totals, including Notification
169 and Report 519; they are not additive reruns. Report finished at 11:41:19 and Notification at
11:42:10 Bangkok on 2026-10-08 (fresh Surefire report modification times).
The Report follow-up adds five worker tests and two real PG/Rabbit cases for batches larger than
20, rollback/backoff and eventual recovery. All seven passed focused and then full-module execution.
Notification adds two strict correction/replacement-marker cases; unsupported source revisions or
superseding-payment markers now reject consistently with Report. No fixture bytes changed.

Final packaging of Billing/Notification/Report plus the unchanged Common dependency exited 0.
Tests were skipped only in this packaging invocation; the complete suites above ran separately.
Final `git diff --check` is clean; relative documentation links resolve, all 30 preserved baseline
files still exist, and Gateway/Common/root build/Compose/CI/scripts have no working-tree changes.
This is not a full repository/Gateway reactor or live multi-service clinical workflow certification.

The initial Notification run exposed decimal rendering (20 versus 20.00), corrected without changing
money validation. The initial Report full run exposed missing refund beans in isolated slice tests and
an index-only migration test accidentally migrating to the new V15. The tests now wire the real refund
service/adapters, pin the V13→V14 index assertion to 14 and independently test V14→V15 preservation.
No test was disabled or policy weakened to obtain passing results.

## Remaining work mapped to the main plan

- R-01.3.2/R-03: refund cash input is implemented, but recognition/allocation, deposit liability/release,
  settlement/debt equations, full finite financial replay and accepted read publication remain open.
- H-01/S-05/S-07: local refund revocation is implemented; revoked/superseded event intake, source-fenced
  distributed transitions and activated multi-service acceptance remain open.
- Surgery cancellation/performed reconciliation is not implemented by the cashier refund command.
  Charges, request cancellation, credits due and completed refunds are distinct facts.
- Notification admission/top-up/settlement templates and external-delivery acceptance remain open.
- Surgery clinical authority and phased safety policy are tracked in the
  [official-source research/design](../../architecture/surgery-safety-policy-research-2026-10-08.md).

No global parent/leaf checkbox is closed solely by this partial finance slice. The local engineering
checkpoints above do not inflate the existing 216 unique main-plan IDs or the selected 50-task subset.
