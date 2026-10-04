# HANDOFF — Inpatient deposit, top-up and settlement facts

- **Status:** `OPEN`; source audit 2026-10-04 at `2e8f024`.
- **Producer / owner:** Billing — Lộc (`locgit-89`).
- **Consumer:** Inpatient — Vinh (`Dangvinh77` / `Harori`).
- **Canonical contract:**
  [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md).
- **Blocked behavior:** normal admission authorization, deposit top-up visibility and
  administrative close after final settlement.

Billing contains the V2 ledger, `FinancialClearance` and `Settlement` domain models, but the source
does not yet publish `financial.clearance.granted`, `deposit.topup.required` or
`settlement.completed`. Inpatient already has guarded consumers and application rules for these
facts. Domain tables/classes alone therefore do not close this integration.

## Required producer contracts

All events use the common versioned envelope, `producer=billing-service`, a transactional outbox
and immutable replay bytes for the same `eventId`.

### Admission deposit clearance

Publish `financial.clearance.granted` version 1 only after the matching admission-deposit payment
is committed. The payload must contain:

- `clearanceId`, `invoiceId`, `accountId`, `patientId`;
- `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`;
- `purpose=ADMISSION_DEPOSIT` and the same exact `admissionId`;
- `amount`, `currency`, `paymentMethod`, nullable `expiresAt`, and `emergencyOverride`.

Do not infer an admission from patient, latest invoice or latest open account. A clearance for
EXAM, LAB_TEST, PRESCRIPTION or SURGERY is not an admission-deposit authorization.

### Deposit top-up

Publish `deposit.topup.required` with exact `accountId`, `admissionId`, `currentBalance`,
`requestedAmount` and non-blank `reason`. Re-delivery must preserve the operation identity and must
not create repeated top-up effects or notifications.

### Final settlement

Publish `settlement.completed` only after the settlement is committed. The payload must contain
`settlementId`, `admissionId`, `accountId`, `grossAmount`, `insuranceAmount`,
`patientLiability`, `completedPayments`, `completedRefunds`, `balance`, `outcome` and
`completedAt`. `outcome` uses the canonical settlement enum. The fact authorizes Inpatient to
validate an explicit close command; consuming the fact must not silently close the admission.

## Acceptance criteria

- Billing has canonical producer fixtures for all three facts; Inpatient deserializes the exact
  same bytes with no alternate field aliases.
- A wrong patient, episode type, episode ID, purpose, admission ID or account ID is rejected and
  cannot advance an admission.
- Duplicate commands/outbox replay publish one immutable business fact and Inpatient applies each
  event once by `eventId`.
- A deposit changes cash/liability projections without being counted as earned revenue.
- Settlement covers `PAID_IN_FULL`, `ADDITIONAL_PAYMENT_REQUIRED`, `REFUND_DUE` and approved
  debt/waiver outcomes according to the canonical contract; completed payments are never mutated.
- Producer and consumer tests cover out-of-order delivery, malformed required fields and DLQ
  behavior before the integration flags are enabled.
- After both sides pass, move the lasting payload rules into the canonical contract/service docs,
  update their status and delete this handoff plus its active-registry entry.

