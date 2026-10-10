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

### Additive Clinical prescription-context lookup — 2026-10-09

Clinical owns `GET /api/v1/records/{recordId}/prescription-context`, independently gated by
`mediflow.clinical.prescription-context-lookup.enabled=false`. It accepts only GET with a signed
`sub=pharmacy-service`, `type=service`, `role=SYSTEM` JWT, mandatory iat/exp, positive lifetime
at most 60 seconds and future-issued skew at most 5 seconds. Human access/refresh credentials
and service credentials on human mutation endpoints are rejected. Gateway is unchanged/read-only;
this is a direct internal lookup, not a newly approved public route.

The standard envelope contains `{exists, recordId, patientId, doctorId, departmentId,
careEpisodeType, careEpisodeId, recordStatus, disposition, observedAt}`. Clinical selects the
episode from its own exact persisted relationship: appointment-backed uses appointmentId, walk-in
uses recordId. It never selects by patient/latest record. IDs must be consistent with the joined
appointment patient/department. A missing record returns 200 with echoed recordId, exists=false
and every other business field null; DB failure/inconsistent relationship is 503
`CLINICAL_CONTEXT_UNAVAILABLE`, not absence. One owned MVCC statement supplies both context and
observation time; no diagnoses, symptoms, notes, contacts or invented source revision are returned.
Required nonblank X-Correlation-Id (at most 120 characters, no controls) is echoed unchanged.

Pharmacy's `care-finance-v2 AND pharmacy.clinical-context.enabled` gates, both false by default,
select real Feign/Eureka, 2s connect/3s read timeouts, circuit breaker and unavailable fallback.
The consumer reads original Clinical `contracts/prescription-context-v1/` fixture bytes, checks
canonical IDs, strict booleans/state, duplicate JSON keys/trailing tokens, <=1MB payload,
header/envelope correlation and <=30s observation age / <=5s future skew. 404/5xx/malformed
responses never become absence. Network reads are rejected inside mutation transactions.

The new internal context-checked creation caller performs remote preflight outside transactions,
then calls the existing atomic stock/held writer. Doctor self/ADMIN delegation is checked before
lookup and replay. The writer matches exact record/patient/doctor/department/episode before
receipt claim, after receipt-lock waits (including replay), after all drug locks and before save.
No failure falls back to the unverified internal kernel. Existing trusted low-level kernel,
V0 endpoints and V1 public rejection remain unchanged.

**These are necessary relationship facts, not complete medication permission.** OPEN or a
PRESCRIPTION disposition is not a medication order; no status is treated as a current license,
financial grant, inpatient placement or distributed lease. Current Patient existence and descriptive
Organization active-job/department preflight are now required by the checked caller, independently
gated and rechecked after receipt/stock waits. They do not replace doctor-eligibility/order policy.
Authoritative order policy, Billing Rx request issuance/terminal adjustment and reviewed public/held
activation remain separate gates. No public V1 endpoint or held event is enabled by this read.

Same-byte producer/consumer tests and owned PostgreSQL verification:
[execution evidence](../../superpowers/plans/2026-10-09-pharmacy-clinical-context.md).
Identity follow-up: [canonical lookup boundary](CONTRACT-IDENTITY-LOOKUP-01.md#pharmacy-necessary-identity-preflight--2026-10-09)
and [current verification](../../superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).

| Event | Producer | Billing action |
|---|---|---|
| `medicalrecord.created` or explicit exam-order fact | Clinical | create one EXAM charge for the outpatient episode |
| `lab.request.created` | Lab | create LAB charge at request time, not result time |
| `prescription.created` | Pharmacy | create outpatient DRUG charge or admission charge according to `careContext` |
| `admission.deposit.requested` | Inpatient | create a deposit payment request; deposit is liability until settlement |
| `admission.started` / treatment facts | Inpatient | open/continue the admission billing account |
| `surgery.case.created` | Surgery | create planned procedure charges under `CONTRACT-SURGERY-BILLING-01`; upstream `surgery.requested` is referral only |
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
- Existing Billing → Lab `labTestIds` is an implemented compatibility contract: Billing builds the
  deduplicated list from paid LAB fee source references and Lab consumes only those explicit IDs.
- Existing Billing ↔ Pharmacy `prescription.created` → `payment.completed` →
  `prescription.filled|prescription.dispense.failed` remains the outpatient compatibility path.
  Its durable field definitions live in the event catalog and Billing/Pharmacy service docs.
- Do not claim clearance implementation complete until producer and all operational consumers share
  v1 fixtures and duplicate-event tests.

## Acceptance criteria

### Implemented payment/clearance slice — 2026-10-05

Task-scoped cross-owner implementation is authorized by Huy. Billing now has a transactional
ledger payment command for **existing authoritative** `PAYMENT_REQUEST`/selected `CHARGE` rows,
an additive persisted target table, account/key locking, immutable completed transactions,
bounded charge allocation and held V1 receipt/clearance outbox rows. No endpoint lets a caller
invent an account, charge, price or source relationship.

- `POST /api/v1/billing/payment-requests/{id}/payments`, ADMIN/CASHIER, access token only;
  `idempotencyKey`, positive two-decimal `amount`, `currency`, `paymentMethod`, optional
  `providerReference`. Actor is the signed account subject; correlation is preserved/generated.
  `MEDIFLOW_BILLING_LEDGER_ENABLED=false` by default. CASH/TRANSFER are recorded cashier receipts;
  INSURANCE is not silently turned into a cash receipt. Provider capture/callback is not implemented.
- Every completed installment emits a held `payment.completed` V1 receipt for that installment's
  amount. An incomplete payment never grants clearance. Only the full exact payment request grants
  one clearance; the clearance amount is the total request amount, not the last installment.
- `SERVICE_PAYMENT`, `ADMISSION_DEPOSIT`, `SETTLEMENT_PAYMENT` are explicit receipt classifications.
  A deposit is unallocated cash/liability, not earned revenue. Allocation never exceeds selected
  posted charges in this account; target source IDs must match those charges. A settlement installment
  receipt is **not** `settlement.completed`.
- EXAM may carry appointment plus record context; its selected outpatient episode is the appointment,
  otherwise the walk-in record. LAB_TEST may carry record context plus exact unique test IDs.
  PRESCRIPTION has only its prescription target; ADMISSION_DEPOSIT binds the admission episode;
  SURGERY binds case plus admission when inpatient. Other target fields are null/empty.
- Grant time is envelope `occurredAt`; nullable grant expiry is the authoritative request's expiry.
  Reusing an idempotency key with another request, actor, amount, method, currency or provider reference
  conflicts. Matching retry returns the original immutable receipt even after request expiry/payment.
- Billing fixtures at `backend/billing-service/src/test/resources/contracts/ledger-v1/` are verified
  against the actual producer service. Clinical, Lab, Inpatient, Pharmacy and Surgery read these files
  directly in their tests. No copied/aliased consumer wire format is accepted.
- V5/V6 reuse `BILLING_EVENT_OUTBOX`: `publication_enabled=false`, `contract_version=1` and a DB
  constraint hold V1 events. Legacy dispatch/admin replay cannot release them. Legacy producers keep
  contract version 0 and their original flat bytes. Release requires a reviewed migration/cutover.

Still open beyond the new Surgery planned-charge slice: other authoritative event→account/charge/request issuance and versioned catalogue, refunds and
grant revocation/supersession, deposit top-up, final settlement/recognition, Report projections,
live consumers and actual broker/Gateway multi-service E2E. This slice does not close the whole
contract or approve operational activation.

### Surgery planned-charge issuance follow-up — 2026-10-08

**Refund follow-up (same date):** a separately gated ADMIN/CASHIER command records completed
CASH/TRANSFER refunds against exact original payments. V8 local reason audit, bounded cumulative
refunds/reversals, original-only allocation reversal and unsatisfied-grant revocation commit with a
HELD `payment.refunded` V1. Originals/paid requests are not rewritten or reopened; replay is exact
and no-effect even after account close. Fresh CLOSED/SETTLED-account refunds and allocated-deposit
refunds requiring an unimplemented policy reject. This is not automatic Surgery cancellation,
bank/provider execution, revocation-event publication, supersession, settlement or distributed fencing.
Notification/Report both consume actual fixtures in real local PG/Rabbit tests. Wire and semantic
limits are canonical in [CARE-PROJECTIONS](CONTRACT-CARE-PROJECTIONS-01.md#completed-refund-fact--v1-paymentrefunded-2026-10-08);
[verification](../../superpowers/plans/2026-10-08-billing-refund-closure.md).

Actual Surgery case-created V1 bytes now drive the opt-in Billing issuer: immutable delivery/source
receipt, exact episode/patient account, explicit catalogue price snapshots, grouped price charges
with preserved item lines, one selected SURGERY request/target and held invoice-created V1 all commit
together. V9 widens quantity without rounding; unknown/invalid prices and zero-total policy gaps reject.
After the latest master merge, this opt-in issuer is selected by the existing `billing.q` dispatcher,
not a parallel binding. With both flags enabled it replaces only the creation handler; disabled
preserves the master's charge-only path. Strict-reader or storage failure never falls back to that
other writer. Owner completion now uses the same case/account/charge locks and price-group
quantity validation, without repricing exact reconciled replay. Strict cancellation now has durable
early evidence and atomic void/cancel/revoke/refund-due adjustment; the compatibility paid path
rejects instead of inventing a completed cash refund. Full performed selected-request/allocation
reconciliation acceptance is not implied by these changes; details are in SURGERY-BILLING-01.
Matching source under a new event ID replays the original request even after account close/catalog
change. No HTTP caller can invent arbitrary charges, account/source references or paid status.
Actual PostgreSQL/RabbitMQ payment flow and internal current-clearance HTTP lookup validate the
issued request, not hand-seeded charge relationships. Notification reads the actual held invoice
fixtures and persists a distinct private request notice. Both feature families stay default off;
V6 still prevents released V1 outboxes. This does not complete performed reconciliation, refunds,
deposit issuance/top-up, settlements, historical finance or whole-care E2E.

### LAB_TEST planned-request issuance — 2026-10-10

Task-scoped Billing implementation by its owner (Lộc). An opt-in handler accepts actual Lab-service
`lab.request.created` V1 bytes (never a reconstructed/guessed wire shape) and issues one LAB_TEST
charge plus one `PAYMENT_REQUEST` per lab test, reusing the existing episode account and the
Surgery planned-request pattern (claim delivery, lock recorded source, exact-episode account,
catalog-derived price snapshot). Target is `recordId` (context) plus the single `labTestIds` entry;
no admission/appointment/prescription/surgery field is set. Gated by `mediflow.billing.ledger.enabled`
and `mediflow.billing.lab-test-charge-consumer.enabled`, both false by default; disabled leaves
`lab.request.created` unbound on Billing's queue, same as before this slice. Exact replay of the same
`labId` returns the recorded request without re-pricing; a conflicting replay (same `labId`, different
`sourceOrderId` or bytes) rejects. No new writer was needed for payment/clearance: the existing generic
ledger payment command already grants `financial.clearance.granted` for `LAB_TEST` once its exact
request is paid in full, unchanged. Verified against lab-service's own fixture, copied byte-for-byte
into Billing's test resources per the no-copied-wire-format rule. This does not implement EXAM
issuance (blocked: Clinical has not committed a producer fixture for its V2 `medicalrecord.created`/
`appointment.status.changed` payloads yet), Docker/Gateway multi-service acceptance, or deletion of
[HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE](../../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md).

### Opt-in Surgery and Pharmacy grant intake — 2026-10-05

Both consumers decode the actual Billing `ledger-v1/clearance-*.json` producer fixtures, not copied
wire definitions. A fully validated grant for another purpose is NOT_APPLICABLE; unknown producer/
version, malformed targets, duplicate JSON keys, conflicting identities and short/noncanonical UUIDs
cannot be acknowledged as unrelated input. Grant time remains immutable envelope occurredAt; expiry
is exclusive. This is grant-only evidence; no revocation/supersession or cash refund is inferred.

- Surgery: BOTH `mediflow.features.surgery.enabled` and
  `mediflow.surgery.messaging.consumers.enabled` enable `surgery.financial-clearance.q` and its
  dedicated durable DLQ. Early exact grants commit a PENDING inbox entry before ACK. A bounded durable
  worker retries due rows, 20 per 5-second poll, with 60-second deferral after missing case/failure;
  failed-processing backoff has an independent transaction and never reopens an applied/quarantined
  winner. New matching evidence can invalidate pre-start readiness and exact old booking atomically;
  no grant automatically transitions READY/START.
- Pharmacy: BOTH `mediflow.features.care-finance-v2` and
  `mediflow.pharmacy.clearance-consumer.enabled` enable `pharmacy.financial-clearance.q` and its
  dedicated durable DLQ. Clearance delivery claim and VERIFIED/PENDING proof commit before ACK.
  Pending proofs need no in-memory callback: the explicit stock authorizer rechecks them against the
  exact late prescription under its existing transaction. Intake never invokes dispensing or creates
  a payment receipt/refund/outbox fact. Known wrong patient/episode/version or immutable identity
  conflict rolls back the claim and goes to DLQ.
- Both queue families bind `financial.clearance.granted` on `mediflow.events`; DLQ routing uses the
  respective DLQ name on `mediflow.events.dlx`. Three bounded attempts handle infrastructure errors,
  permanent invalid/conflicting input is rejected. Original bytes remain in durable DLQ; operator
  replay is deliberate only after correction, not an automatic loop. No clinical/token payload log.

All intake defaults remain disabled; enabling a queue does not release Billing's held V1 outbox or
Pharmacy's held lifecycle bytes/public V1 create/dispense fence. Actual PostgreSQL/RabbitMQ tests now
cover duplicate, early proof, wrong-purpose/malformed/conflict, rollback and retained-message replay.
This does not complete whole-care E2E, request issuance, revoke or reviewed cutover acceptance.

- Two episodes for one patient never share a billing account or invoice by accident.
- Duplicate charge facts create exactly one charge.
- An EXAM clearance cannot unlock a LAB_TEST or PRESCRIPTION.
- Unpaid Lab cannot enter IN_PROGRESS except via audited emergency override.
- Deposit payment changes cash/liability projections but not earned revenue.
- Settlement can result in `PAID_IN_FULL`, `ADDITIONAL_PAYMENT_REQUIRED`, `REFUND_DUE`, or approved
  debt/waiver without mutating completed transactions.
- Producer and consumers reject missing/malformed target IDs and exercise DLQ behavior.
