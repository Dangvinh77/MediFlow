# CONTRACT-SURGERY-BILLING-01 — Surgery charge, clearance and financial adjustment

- **Status:** `DESIGN_READY`, blocked by Surgery module and Billing ledger foundation
- **Owners:** Surgery — Huy; Billing — Lộc
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Price and charge source

Surgery publishes a request with `surgeryCaseId`, `admissionId`, `patientId`, `departmentId`,
`procedureCode`, planned item/price codes and priority. Billing owns pricing and creates charges with
`sourceType=SURGERY` and `sourceId=surgeryCaseId`. Surgery never stores authoritative paid amount or
queries Billing tables.

## Clearance

Billing publishes `financial.clearance.granted` with `purpose=SURGERY`, the admission episode,
`admissionId` and `surgeryCaseId`. Surgery accepts it only when patient, admission and case all match. The
clearance satisfies the financial guard only; consent, pre-op, team and schedule guards remain
independent.

## Performed items and completion

`surgery.completed` carries the actual performed item/price codes. Billing reconciles planned and
performed charges idempotently by `(surgeryCaseId, itemCode)`. A price code not recognized by Billing
is a contract/catalog error and must not default to zero.

## Cancellation and refund

`surgery.cancelled` identifies stage (`BEFORE_PREOP`, `AFTER_PREOP`, `BEFORE_START`,
`IN_PROGRESS_ABORTED`), exact case/admission IDs and reason. Billing applies policy to void unearned
charges or create refund/credit transactions. It never mutates or deletes a completed payment.
Billing publishes `payment.refunded` when a real ledger refund completes.

## Acceptance criteria

- Duplicate surgery request/completion/cancellation events do not duplicate charges or refunds.
- Financial clearance for another case or admission is rejected.
- Surgery READY requires the financial guard plus all clinical/resource guards.
- Cancellation after payment creates an auditable adjustment; no event is described as a bank
  refund unless a completed refund transaction exists.
- Producer and consumer fixtures cover planned vs performed item differences and unknown price code.
