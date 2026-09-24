# CONTRACT-CARE-BILLING-01 — Care episodes, charges, clearance and settlement

- **Status:** `DESIGN_READY`; existing outpatient fee/payment contracts are `PARTIAL`
- **Owners:** Clinical/Lab/Inpatient — Vinh; Pharmacy — Huy; Billing — Lộc
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)
- **Rule:** Billing is the only owner of money. Domain services own operational state and never
  infer payment from an invoice, patient, or unrelated payment event.

## Shared episode identity

| Field | Type | Required | Rule |
|---|---|---:|---|
| `careEpisodeType` | enum | yes | `OUTPATIENT_VISIT` or `ADMISSION` |
| `careEpisodeId` | UUID | yes | exact record/visit/admission ID chosen by the owning domain |
| `patientId` | UUID | yes | correlation only; never used to select “all unpaid charges” |
| `departmentId` | UUID | yes | department that generated the charge |
| `sourceType` | enum/string | yes | `EXAM`, `LAB_TEST`, `PRESCRIPTION`, `BED`, `SURGERY`, ... |
| `sourceId` | UUID | yes | authoritative aggregate ID from producer |
| `priceCode` | string | yes | stable catalog key; amount is not guessed by consumer |

Billing deduplicates a charge by `(sourceType, sourceId, priceCode)`. An invoice/payment request is
created from selected charge IDs within one account/episode. It must not collect all unpaid fees for
the same patient across episodes.

For `OUTPATIENT_VISIT`, `careEpisodeId` is `appointmentId` when an appointment exists; a walk-in with
no appointment uses `recordId`. Billing chooses it when the account opens and does not replace it
later. `recordId` can still be carried as a source/clinical reference.

## Operational facts that create charges

| Event | Producer | Billing action |
|---|---|---|
| `medicalrecord.created` or explicit exam-order fact | Clinical | create one EXAM charge for the outpatient episode |
| `lab.request.created` | Lab | create LAB charge at request time, not result time |
| `prescription.created` | Pharmacy | create outpatient DRUG charge or admission charge according to `careContext` |
| `admission.deposit.requested` | Inpatient | create a deposit payment request; deposit is liability until settlement |
| `admission.started` / treatment facts | Inpatient | open/continue the admission billing account |
| `surgery.requested` | Clinical/Inpatient | create planned procedure charges under `CONTRACT-SURGERY-BILLING-01` |
| `surgery.completed` | Surgery | reconcile performed items under `CONTRACT-SURGERY-BILLING-01` |

Each fact uses the common envelope and an immutable producer-sourced `sourceId`. Redelivery must not
create another charge.

## `financial.clearance.granted`

Producer: Billing. Consumers: Clinical, Lab, Pharmacy, Inpatient, Surgery.

```json
{
  "eventId": "uuid",
  "eventType": "financial.clearance.granted",
  "version": 1,
  "occurredAt": "2026-09-24T03:00:00Z",
  "correlationId": "uuid",
  "producer": "billing-service",
  "payload": {
    "clearanceId": "uuid",
    "invoiceId": "uuid",
    "accountId": "uuid",
    "patientId": "uuid",
    "careEpisodeType": "OUTPATIENT_VISIT",
    "careEpisodeId": "uuid",
    "purpose": "LAB_TEST",
    "appointmentId": null,
    "recordId": "uuid",
    "labTestIds": ["uuid"],
    "prescriptionId": null,
    "admissionId": null,
    "surgeryCaseId": null,
    "amount": 250000.00,
    "currency": "VND",
    "paymentMethod": "CASH",
    "expiresAt": null,
    "emergencyOverride": false
  }
}
```

Rules:

1. `purpose` determines the required target: EXAM → `appointmentId` or walk-in `recordId`;
   LAB_TEST → non-empty `labTestIds`; PRESCRIPTION → `prescriptionId`; ADMISSION_DEPOSIT →
   `admissionId`; SURGERY → `surgeryCaseId` plus `admissionId` when inpatient.
2. Target fields contain authoritative source IDs from charges in the payment request. Unrelated
   optional target fields are null/empty and never used as fallback identifiers.
3. Consumer accepts only a clearance whose episode, patient, purpose and target all match its
   aggregate. A mismatched event is a contract error, not a no-op success.
4. Consumer stores/claims `eventId` and applies the clearance atomically. Redelivery is a no-op.
5. Current `payment.completed` remains a financial fact for Report/Notification and a compatibility
   input for existing Pharmacy/Lab consumers. New operational gates migrate to clearance and do not
   expand the meaning of `payment.completed`.
6. Clearance v1 keeps the approved wire field `invoiceId`. During ledger migration, Billing may map
   its internal payment request to that public identifier; renaming the wire field requires v2.

## Deposit, top-up and settlement

- `admission.deposit.requested`: Inpatient → Billing; includes `admissionId`, episode fields,
  `suggestedAmount`, `reason`.
- `deposit.topup.required`: Billing → Inpatient/Notification; includes `accountId`, `admissionId`,
  current balance, requested amount and reason.
- `discharge.medically.approved`: Inpatient → Billing; freezes normal charge intake and starts final
  reconciliation. Late adjustments require explicit adjustment policy.
- `settlement.completed`: Billing → Inpatient/Report/Notification; includes gross, insurance,
  patient liability, completed payments, completed refunds, balance and outcome.
- A medical discharge does not close an admission. Inpatient closes only after settlement or an
  approved emergency/debt override.

## Emergency path

Domain service may proceed without clearance only with `emergencyOverrideId`, `approvedBy`,
`approverRole`, `reason`, `approvedAt` and episode/target IDs. It publishes an operational fact;
Billing opens/updates the receivable. The override does not forge a paid transaction.

## Migration from current contracts

- Keep current FEE/INVOICE physical tables and `payment.completed` consumers while introducing the
  logical account/charge/transaction/allocation model additively.
- Existing Billing → Lab `labTestIds` contract remains valid during migration; see
  [`HANDOFF-LAB-PAYMENT-COMPLETED`](../../../backend/billing-service/HANDOFF-LAB-PAYMENT-COMPLETED.md).
- Existing Billing ↔ Pharmacy saga remains valid for outpatient prescriptions; see
  [`backend/billing-service/HANDOFF.md`](../../../backend/billing-service/HANDOFF.md).
- Do not claim clearance implementation complete until producer and all operational consumers share
  v1 fixtures and duplicate-event tests.

## Acceptance criteria

- Two episodes for one patient never share a billing account or invoice by accident.
- Duplicate charge facts create exactly one charge.
- An EXAM clearance cannot unlock a LAB_TEST or PRESCRIPTION.
- Unpaid Lab cannot enter IN_PROGRESS except via audited emergency override.
- Deposit payment changes cash/liability projections but not earned revenue.
- Settlement can result in `PAID_IN_FULL`, `ADDITIONAL_PAYMENT_REQUIRED`, `REFUND_DUE`, or approved
  debt/waiver without mutating completed transactions.
- Producer and consumers reject missing/malformed target IDs and exercise DLQ behavior.
