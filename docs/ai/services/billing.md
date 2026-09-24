# Service: billing

**Sources of truth:** current behavior in `docs/eproject_general_plan/billing-service.html`; target
care-finance behavior in [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html).
**Module:** `backend/billing-service/` · **Base path:** `/api/v1/billing` · **Owner:** Lộc (`locgit-89`)

## Bounded context

Billing is the only owner of money: care-episode accounts, charges, payment requests, immutable
payment/refund transactions, allocations, deposits, insurance adjustments, financial clearance and
settlement. It does not own patients, records, Lab tests, prescriptions, admissions or surgeries.

Current `FEE`/`INVOICE` tables and outpatient saga remain a compatibility implementation. The target
logical model is `BILLING_ACCOUNT`, `CHARGE`, `PAYMENT_REQUEST`, `PAYMENT_TRANSACTION`,
`PAYMENT_ALLOCATION` and `SETTLEMENT`; migration is additive and must not rewrite completed history.

## Core invariants

1. Every account belongs to exactly one `careEpisodeType + careEpisodeId` and patient.
2. Charge identity is `(sourceType, sourceId, priceCode)`; redelivery cannot create another charge.
3. Payment/refund/reversal transactions are append-only. A refund references the original payment.
4. Allocations cannot exceed completed transaction value or payable charge balance.
5. Invoice/payment request contains selected charges from one account, never every unpaid fee for a patient.
6. Deposit is cash plus liability until charges are earned/reconciled; it is not revenue on receipt.
7. Financial clearance is purpose- and target-specific. A payment does not unlock unrelated care.
8. Settlement uses persisted totals and can require extra payment, produce refund due, or record an
   approved debt/waiver; completed transactions are never edited to force balance zero.

## Current endpoints and target additions

Current invoice read/create/pay endpoints remain available during migration. Target APIs add account,
payment request/transaction, deposit and settlement operations under `/api/v1/billing`. Exact DTOs
must be defined in the Billing implementation spec/PR before production code; no client may call a
service port directly instead of Gateway.

## Events

**Subscribe:**

- current: `medicalrecord.created`, `appointment.status.changed`, `lab.result.created`,
  `prescription.created`, pharmacy saga completion/failure;
- target: `lab.request.created`, `admission.deposit.requested`, `admission.started`,
  `discharge.medically.approved`, `surgery.requested`, `surgery.completed`, `surgery.cancelled`.

**Publish:**

- current compatibility: `invoice.created`, `payment.completed`, `payment.failed`;
- target: `financial.clearance.granted`, `deposit.topup.required`, `payment.refunded`,
  `settlement.completed`.

`payment.completed` is a financial fact for receipts/projections and the current Lab/Pharmacy
compatibility consumers. New operational gates consume `financial.clearance.granted` with explicit
purpose and target IDs.

## Care-finance integration gate

- Mandatory: [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [`CONTRACT-SURGERY-BILLING-01`](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md) and
  [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
- Preserve the implemented [Lab reference contract](../../../backend/billing-service/HANDOFF-LAB-PAYMENT-COMPLETED.md)
  and [Pharmacy saga contract](../../../backend/billing-service/HANDOFF.md) during migration.
- Producer changes require Billing fixture/outbox tests plus every consumer fixture/test. If another
  owner cannot update in the same PR, keep the registry status blocked and retain compatibility.

## Acceptance gates

- Same patient, two episodes: charges and payments never mix.
- Duplicate source event: one charge and one processed marker.
- Same payment callback/event: one transaction, allocation and clearance.
- Admission deposit: cash/liability changes, earned revenue does not.
- Settlement refund: a new refund transaction references the original payment.
- Malformed/missing target reference: bounded retry then DLQ, no guessed identifier.
- Outbox and aggregate commit atomically; consumer claim and side effect commit atomically.
