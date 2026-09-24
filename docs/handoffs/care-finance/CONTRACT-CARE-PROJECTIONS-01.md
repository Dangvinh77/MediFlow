# CONTRACT-CARE-PROJECTIONS-01 — Notification and Report projections

- **Status:** `DESIGN_READY`; current outpatient projections are partially implemented
- **Owners:** Notification/Billing — Lộc; Report/Pharmacy/Surgery — Huy; Clinical/Lab/Inpatient — Vinh
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Projection rule

Notification and Report are consumers, not workflow authorities. Their failure must not roll back
the producer's committed care/payment transaction. Both deduplicate by `eventId`; Report projection
must be rebuildable from replay, and Notification must keep delivery history/retry state.

## Notification subscriptions

| Event | Template intent |
|---|---|
| `invoice.created` / payment request fact | amount and payment instructions |
| `payment.completed` | payment receipt; financial fact only |
| `payment.refunded` | completed refund/credit notice |
| `admission.deposit.requested` | deposit request |
| `deposit.topup.required` | additional deposit request |
| `admission.started` | admission/bed information |
| `surgery.ready` | surgery schedule/readiness notice |
| `surgery.cancelled` | cancellation/reschedule notice |
| `settlement.completed` | final settlement outcome |
| `admission.closed` | discharge instructions/follow-up |

Patient contact data must come from explicit event snapshots or a permitted Patient lookup; a
consumer must not read Patient DB. Missing contact address creates a failed/skipped notification
record with reason, not a fabricated recipient.

## Report financial projections

Report must separate:

- cash received: completed payment transaction;
- deposit liability: unearned inpatient deposit balance;
- earned revenue: settled/recognized charge amount according to Billing event;
- refunds: completed refund transactions;
- outstanding receivable/debt: settlement outcome when explicitly published.

`payment.completed` alone must not count an admission deposit as earned revenue. During migration,
current outpatient invoice revenue can continue under the existing contribution model, while every
event includes a transaction/account/episode classification that lets Report distinguish deposit.

## Report operational projections

| Event | KPI |
|---|---|
| `medicalrecord.completed` | completed outpatient visits/disposition |
| `lab.result.created` | completed lab tests by requesting department/date |
| `prescription.filled` | dispensed prescriptions/items |
| `admission.started` | admissions and bed occupancy start |
| `admission.closed` | discharges and length of stay |
| `surgery.completed` | completed surgeries, duration, complications category |
| `surgery.cancelled` | cancellation count/reason category |
| `settlement.completed` | recognized totals, balance/refund outcome |

Every contribution is keyed by the source business ID plus event ID so replay/redelivery does not
double count. Reversal/adjustment events reference the original contribution instead of guessing its
date or department.

## Acceptance criteria

- Duplicate events create one notification intent and one report contribution.
- Deposit payment increases cash and liability, not earned revenue.
- Refund reverses the original financial contribution in the correct period/department.
- Report can rebuild the same totals from an empty projection using event replay.
- Notification templates do not expose diagnosis/result details on insecure channels.
- Unknown event version or missing source ID follows retry/DLQ policy and does not silently alter a
  projection.
