# HANDOFF — Inpatient deposit, top-up and settlement facts

- **Status:** `PARTIAL`; implementation update 2026-10-05, working tree based on `3103fa1`.
- **Producer / owner:** Billing — Lộc (`locgit-89`).
- **Consumer:** Inpatient — Vinh (`Dangvinh77` / `Harori`).
- **Canonical contract:**
  [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md).
- **Blocked behavior:** normal admission authorization, deposit top-up visibility and
  administrative close after final settlement.

Billing now records immutable classified deposit receipts and an exact ADMISSION_DEPOSIT grant
after full payment of a persisted request, atomically with held V1 outbox rows. Inpatient tests
read Billing's actual producer fixture. Deposit cash is not allocated to service charges or
treated as earned revenue. Notification records the private deposit receipt without claiming
final settlement. Huy implemented this slice under the user-authorized dependency override;
permanent ownership is unchanged.

`deposit.topup.required` and `settlement.completed` producers, authoritative request issuance,
catalogue/expected-total reconciliation and actual broker E2E remain absent. Billing V1 rows are
DB-fenced from publication; all new intake/API flags default OFF. A deposit grant alone does not
close this integration or authorize administrative admission close.

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

