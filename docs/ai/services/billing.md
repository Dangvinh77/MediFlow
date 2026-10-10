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
  `discharge.medically.approved`, `surgery.case.created`, `surgery.completed`, `surgery.cancelled`.

**Publish:**

- current compatibility: `invoice.created`, `payment.completed`, `payment.failed`;
- target: `financial.clearance.granted`, `deposit.topup.required`, `payment.refunded`,
  `settlement.completed`.

`payment.completed` is a financial fact for receipts/projections and the current Lab/Pharmacy
compatibility consumers. New operational gates consume `financial.clearance.granted` with explicit
purpose and target IDs.

## Care-finance integration gate

### Current clearance lookup (2026-10-06)

The additive internal `GET /api/v1/billing/financial-clearances/{id}/lookup` implements current
SURGERY financial eligibility, not a generic human invoice API. It requires a <=60-second signed
`surgery-service`/`SYSTEM` service credential and exact correlation, and is independently gated by
`MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED=false`. No Gateway route is allowed. Lookup uses one
Billing-owned PostgreSQL snapshot to verify exact grant/request/account/selected charges and
completed net payments, including revocation and exclusive expiry. A historical PAID status alone
is not enough after refunds or voided charges. No medical information or amount is returned.
The exact fields, absence/503 semantics, producer fixtures and bounded observation/race limitation
are authoritative in [SURGERY-BILLING](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md#current-financial-authority--additive-rest-contract-2026-10-06).
This user-scoped implementation is performed on both sides, not deferred to Lộc. Other charge,
refund and settlement writers remain implementation tasks; lookup does not claim them delivered.

### Opt-in Surgery planned-charge issuer (2026-10-08)

The opt-in strict handler accepts actual Surgery `surgery.case.created` V1, never the upstream
referral as a charge command. It requires ledger and surgery-charge-consumer gates, both false by
default. V9 adds immutable delivery/source/item receipts and widens quantity to NUMERIC(19,4);
master's V7 reconciliation and V8 refund migrations are unchanged. The existing `billing.q`
dispatcher selects exactly one creation handler, never both: enabled uses this issuer, disabled
preserves master's charge-only path. No competing queue/listener or failure fallback is registered.
Claim, exact-episode account, catalog-derived charge snapshots, selected SURGERY request/target and
held V1 invoice fact commit atomically. Matching replay does not re-price; changed source conflicts.
Unknown catalog/zero-total policy cases reject. One episode can span generating departments while
preserving the account department used by cash receipts. There is no public arbitrary-charge API.
Actual payment then produces an exact-purpose clearance usable by the internal current-authority
lookup; performed reconciliation, refunds/settlement and cutover stay separate.
Canonical invoice fixtures are read directly by Notification; its new private notice is a request,
not a paid receipt or booking. V6 continues to hold all V1 events. Full evidence is recorded in
[cross-service closure](../../superpowers/plans/2026-10-08-cross-service-closure.md).

- Mandatory: [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [`CONTRACT-SURGERY-BILLING-01`](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md) and
  [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
- Preserve the implemented `labTestIds` projection and outpatient Pharmacy saga described in
  `CONTRACT-CARE-BILLING-01`, the event catalog, and the Billing/Pharmacy service documents during
  migration.
- Producer changes require Billing fixture/outbox tests plus every consumer fixture/test. If another
  owner cannot update in the same PR, keep the registry status blocked and retain compatibility.

## Acceptance gates

### Opt-in discharge freeze and admission settlement (2026-10-10)

Both gated by `mediflow.billing.ledger.enabled` + `mediflow.billing.settlement.enabled` (default
off). `discharge.medically.approved` freezes the exact admission account (OPEN → CHARGE_CLOSED).
`POST /api/v1/billing/accounts/{id}/settlements` (ADMIN/CASHIER) computes gross/liability/balance
from persisted rows per the ledger equations, persists a new immutable settlement version, issues a
settlement PAYMENT_REQUEST for a positive no-insurance balance (paid through the existing generic
ledger payment command, unchanged), records REFUND_DUE for a negative balance without acting on it,
and publishes `settlement.completed` plus closes the account to SETTLED only for a zero balance or
an explicitly approved DEBT_APPROVED/WAIVED override. A positive balance under a nonzero insurance
adjustment explicitly rejects (`BILLING_SETTLEMENT_INSURANCE_PAYMENT_REQUEST_UNSUPPORTED`) rather
than guessing how to distribute the adjustment across charges; deposit recognition at settlement is
likewise left open per CONTRACT-CARE-BILLING-01's own "final settlement/recognition" item. No new
migration: SETTLEMENT/INSURANCE_ADJUSTMENT and the domain model already existed. Verified against
inpatient-service's real discharge fixture and a hand-authored settlement.completed producer
fixture (none existed from either side yet). See
[HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT](../../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md).

### Opt-in ADMISSION_DEPOSIT initial request issuance (2026-10-10)

The opt-in `admission.deposit.requested` issuer (both `mediflow.billing.ledger.enabled` and
`mediflow.billing.admission-deposit-consumer.enabled` false by default) creates one PAYMENT_REQUEST
per admission from Inpatient's own `suggestedAmount`, with no CHARGE row — a deposit is cash/liability,
never an earned charge. Clearance granting reuses the existing generic `LedgerPaymentService`
unchanged. Verified against inpatient-service's real producer fixture, copied into
`src/test/resources/contracts/admission-deposit-v1/`. Top-up issuance remains open, blocked on an
undecided trigger-threshold policy (not an implementation gap); settlement remains open and unbuilt
(no undecided policy blocks it). See
[HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT](../../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md).

### Opt-in LAB_TEST planned-request issuance (2026-10-10)

The opt-in `lab.request.created` issuer (both `mediflow.billing.ledger.enabled` and
`mediflow.billing.lab-test-charge-consumer.enabled` false by default) posts one LAB_TEST charge and
one PAYMENT_REQUEST per lab test from the configured price catalog, mirroring the Surgery
planned-request pattern (V9). No new migration columns were needed: `PAYMENT_REQUEST_TARGET.record_id`/
`lab_test_ids` already existed. Clearance granting reuses the existing generic `LedgerPaymentService`
unchanged. Verified against lab-service's real producer fixture, copied into
`src/test/resources/contracts/lab-request-v1/`. EXAM issuance remains open pending Clinical's own
producer fixture commit; see [HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE](../../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md).

### Opt-in pre-start cancellation adjustment (2026-10-08)

The issuer gates now select strict `surgery.cancelled` on the single billing.q dispatcher.
V10 durable early cancellation, immutable source/delivery dedupe, exact-context checks and
case/account/charge locks commit voided charges, cancelled exact requests, revoked grants and
original-only refund-due evidence. Cancellation does NOT execute or fabricate a completed refund.
ADMIN/CASHIER can read the remaining due through the gated surgery-cancellations endpoint and
record actual returned money through the existing refund command. Read amounts reflect linked
refund allocations; narrative is not retained/exposed. Known invalid facts reject; invalid early
facts quarantine without poisoning later issuance. Replays never reopen or reprice.
Gateway and all false/held defaults are unchanged. Full performed finance, START fencing and
production cutover remain open. Canonical semantics are in SURGERY-BILLING-01.

### CURRENT outpatient fill redelivery (2026-10-08)

The existing invoice-by-prescription row lock serializes completion. A new delivery ID for an
already COMPLETED prescription records the delivery marker with no invoice/fee/payment mutation;
the domain state machine is not relaxed. Both original and repeat fills must match invoice patient
and numeric total, otherwise `BILLING_PRESCRIPTION_FILL_CONFLICT` rejects without a marker. This is
a completion hint, not a stored fingerprint of every dispensing item; Report independently checks
its immutable projection source. REFUNDED and other invalid transitions remain rejected.

### Standalone completed-refund slice — 2026-10-08

Ledger + refund flags gate signed ADMIN/CASHIER recording of completed CASH/TRANSFER refunds.
V8 preserves originals and appends local reason evidence; idempotency/account/original locking
bounds cumulative refunds/reversals and reverses only the original installment's allocations.
Unsatisfied grants revoke locally with the held fact. Exact replay is no-effect after account
closure; new CLOSED/SETTLED refunds or allocated-deposit policy gaps reject. No provider
instruction, Surgery cancellation reconciliation, supersession or distributed START fence.
Full PG/Rabbit module 314/314. [Canonical wire and both consumers](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#completed-refund-fact--v1-paymentrefunded-2026-10-08).

### Current additive ledger payment slice (2026-10-05)

Existing authoritative ledger requests can be paid through the gated ADMIN/CASHIER endpoint
`POST /api/v1/billing/payment-requests/{id}/payments`. V5 persists exact request targets/actor audit;
V6 enforces held V1 delivery in the existing outbox. Domain/application/JDBC transaction wiring covers
installments, idempotency, concurrent account locking, allocation and full-purpose clearance.
Every installment has a classified receipt; partial payments have no clearance. Deposits never create
earned allocations. The endpoint is off by default (`MEDIFLOW_BILLING_LEDGER_ENABLED=false`), and
there is no public arbitrary charge/account/price creation. The precise slice and open issuance,
catalogue/refund/revocation/settlement/recognition/cutover work are recorded in CARE-BILLING-01.

- Same patient, two episodes: charges and payments never mix.
- Duplicate source event: one charge and one processed marker.
- Same payment callback/event: one transaction, allocation and clearance.
- Admission deposit: cash/liability changes, earned revenue does not.
- Settlement refund: a new refund transaction references the original payment.
- Malformed/missing target reference: bounded retry then DLQ, no guessed identifier.
- Outbox and aggregate commit atomically; consumer claim and side effect commit atomically.
