# CONTRACT-CARE-BILLING-01 — Care episodes, charges, clearance and settlement

Integration note 2026-10-11: master's Lab/deposit V12/V13 remain unchanged; Huy's prescription SQL
now uses V19/V20 without content changes. Dated prescription V12/V13 evidence below is the earlier
Huy-only lineage. Both dispatcher groups are retained with one selected medical-discharge writer;
settlement wire fields/names/versions are unchanged.

- **Status:** `DESIGN_READY`; opt-in Rx creation/terminal local adjustment are `PARTIAL`; distributed revoke, admission late adjustment/full finance/held publication remain open
- **Owners:** Clinical/Lab/Inpatient — Vinh; Pharmacy — Huy; Billing — Lộc
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)
- **Rule:** Billing is the only owner of money. Domain services own operational state and never
  infer payment from an invoice, patient, or unrelated payment event.

## Shared episode identity

### Initial final settlement and explicit earned operation — 2026-10-10

The opt-in `POST /api/v1/billing/accounts/{id}/final-settlements` accepts no client money or clinical
approval. ADMIN/CASHIER must supply a signed access-token UUID actor and an `Idempotency-Key`.
Ledger, settlement-preview, medical-charge-close and final-settlement gates all default OFF.
One bounded repeatable snapshot must prove CHARGE_CLOSED/PENDING, exact APPLIED medical authority,
every POSTED charge's actual performed Surgery or terminal FILLED Rx source, zero account balance,
zero unapplied cash and zero unallocated deposit. Generating-department/source/fingerprint/operation
must be explicit; planned price alone is insufficient. Insurance attribution, completed reversals,
debt/waiver, reopen/corrections and existing historical SETTLEMENT rows are explicitly unsupported.

Key -> account/version fences serialize with new cash/refund/allocation writes. Only known aborted
serialization/deadlock transactions retry automatically, at most three fresh whole transactions;
unknown commit/connection failures do not. A key binds account and actor; exact replay returns stored
bytes after SETTLED. Settlement version 1, immutable V18 operation, SETTLED transition and both HELD
facts commit atomically. No Gateway route or live outbox publication is enabled.

`settlement.completed` V1 retains its existing 13-field discharge outcome contract. A **separate**
`settlement.recognition.completed` V1 (routing key equals eventType, Billing common seven-field
envelope) supplies explicit earned recognition; its source is `recognitionOperationId`, not gross
total interpreted as a delta. Payload fields:

- recognitionOperationId, recognitionRevision=1, supersedesOperationId=null;
- settlementId, settlementVersion=1, supersedesSettlementId=null;
- accountId, admissionId, patientId, accountDepartmentId, currency;
- grossAmount, insuranceAmount, patientLiability, completedPayments, completedRefunds,
  completedReversals, netAllocatedPayments, balance;
- outcome=PAID_IN_FULL, completedAt, finalSettlement=true;
- charges (max 500): chargeId, departmentId, sourceType, sourceId, performedOperationId,
  performedFingerprint, performedAt, earnedAmount;
- allocations (max 1000): allocationId, transactionId, originalTransactionId, chargeId,
  transactionType, classification, amount;
- depositReleases: originalTransactionId, receiptDepartmentId, amount.

Only allocations of recognized POSTED charges appear; fully returned VOIDED charge allocations
are omitted, while account completedPayments/completedRefunds still include their actual cash.
Generating-department earned sums equal grossAmount; hospital totals count the operation once.
Original deposit release uses its receipt department, not a proportional earned split. Non-final
reconciliation/receipt/allocation/performed facts never independently recognize revenue.
The actual FinalAdmissionSettlementService fixture is `settlement-recognition-completed.json`;
matching actual payment/refund command fixtures are `payment-final-recognition-deposit.json` and
`refund-final-recognition-deposit.json`. Generated delivery/transaction UUIDs alone normalize in
cash fixture tests. All are under Billing `src/test/resources/contracts/ledger-v1/`.

This supplies a local initial final writer, not a full admission activation/close workflow,
historical import, post-final earned reversal or accepted five-metric report publication.
Consumer implementation and limits are canonical in
[CARE-PROJECTIONS](CONTRACT-CARE-PROJECTIONS-01.md#initial-final-only-recognition-intake--2026-10-10).

### Local admission deposit allocation and unused-cash refund — 2026-10-10

Billing now owns an opt-in `POST /api/v1/billing/accounts/{id}/deposit-allocations` for ADMIN/CASHIER,
requiring a signed access-token UUID actor and `Idempotency-Key`. Ledger, settlement-preview and
deposit-allocation gates all default OFF. Request: `{originalTransactionId, allocations:
[{chargeId, amount}]}`; amounts are exact positive NUMERIC(19,2), at most 1000 distinct targets.
The original must be a completed ADMISSION_DEPOSIT payment on this exact medically closed account.
Charge/account/patient/currency, completed original returns, own prior allocations and each payable
charge balance are checked from a bounded repeatable snapshot. Nonzero account-wide insurance is
explicitly unsupported until its generating-charge attribution is implemented; it is not split by guess.

One key is bound to account, actor, original payment and sorted exact target amounts. Replay returns
the stored result, including after SETTLED; new intent after finalization rejects. Key -> account locks
and account-version writes force concurrent repeatable snapshots to retry from fresh evidence.
Base allocation rows stay append-only; an existing original/charge pair is never overwritten. V16
stores immutable operation/audit/payload with the base rows and HELD outbox in one transaction.

`deposit.allocated` V1 uses the common seven-field Billing envelope. Payload fields are
`allocationOperationId`, `accountId`, `admissionId`, `patientId`, `originalTransactionId`, `currency`,
`allocatedAmount`, `allocations` (allocationId/chargeId/amount), `allocatedAt`, `finalSettlement=false`.
The actual service serializer fixture is `billing-service/src/test/resources/contracts/ledger-v1/deposit-allocated.json`.
It is NOT a final settlement, earned-revenue or liability-release fact. Report/Notification have not
accepted this new fact; it remains HELD and unbound there. Gateway is unchanged; no public rollout is approved.

The existing actual cashier refund command now returns only unused cash from that original deposit,
even when some of it has already been allocated. Original amount - completed original REFUND/REVERSAL
- remaining original funded allocations must be nonnegative after the new refund. Thus deposit 100,
allocation 80 and actual refund 20 preserve funded charges and leave zero unused cash; refund 20.01
rejects. Other payments cannot subsidize this budget. No allocation reversal or earned credit is
created by the unused-cash path. Receipt/refund originals stay immutable; actual refund/audit,
account-version fence and redacted HELD payment.refunded commit atomically. Post-final new refunds
still require an explicit reopen/correction policy; exact key replay is no-effect.

Initial admission deposit request issuance, top-up, insurance attribution, post-final correction
and reviewed publication remain separate unfinished work. Initial finalization is specified above.

### Recognition timing — user decision, 2026-10-10

The user chose **recognition only when final settlement completes**, not on performed-service,
receipt, deposit allocation or non-final reconciliation alone. `settlement.reconciled`
(`finalSettlement=false`), `payment.completed`, `prescription.filled`, `surgery.completed` and
medical discharge MUST NOT independently generate earned revenue. Recognition date is final
completedAt in Report's configured zone. Billing must persist exact recognition operation/revision,
original charge/allocation references, currency and generating-department amounts; the account
department is not an earned-revenue split. Refund-due/additional-payment estimates stay non-final.
Debt/waiver requires real persisted approval. Correction references the original recognition and
allocations, never repeats cumulative settlement gross as another delta. This closes the timing
decision, not final writer, projection, replay, historical coverage or publication tasks.

### Local medical charge-close — 2026-10-10

The existing task-scoped Billing override implements exact Inpatient `discharge.medically.approved`
V1 on the single billing.q dispatcher. Ledger + admission-charge-close gates default OFF
(`MEDIFLOW_BILLING_ADMISSION_CHARGE_CLOSE_ENABLED=false`); no competing listener or Gateway change.
Original payload is exactly admissionId/patientId/summaryId/approvedBy/approvedAt, no clinical text,
guessed account/department, amount or revision. V15 stores minimal immutable source/delivery proof.
Admission episode -> account -> medical delivery locks serialize close against Rx and both Surgery
account issuers. OPEN -> CHARGE_CLOSED, account version and receipts commit atomically; audit time
comes from database after locks. Same source/new delivery and closed-account replay are no-effect.
Existing matched issuance replays before the new-intake fence; Surgery unchanged performed replay
is no-effect but new monetary mutation after close rejects. No final event, allocation, payment,
refund, clearance or revenue is created. This is a local financial fence, not a Pharmacy dispense lease.

Without an account, source commits PENDING_ACCOUNT before ACK without inventing financial context.
It blocks ordinary late account/charge issuance. A later Pharmacy/Surgery financial issuer cannot
replace Inpatient's patient authority or mark its proof rejected; mismatch fails closed without
account creation. Known wrong-patient facts reject before effects.
Pending proof can recover on exact medical-fact redelivery after an authoritative account exists.
No automatic account factory, pending scheduler, approval replacement, late-charge/reopen policy or
finalization is included. Unknown/changed/future facts reject; storage failure rolls back, never ACKs
partial account effects. Disabling a gate does not remove an already durable broker binding; rollout
must explicitly manage that binding. A disabled dispatcher rejects rather than silently ACKing it.

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
Authoritative order policy, distributed revocation/admission late adjustment and reviewed public/held
activation remain separate gates. The local Rx creation/terminal issuer is described below; no public V1 endpoint or
held event is enabled by this read.

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

### Opt-in prescription charge issuance — 2026-10-09

The Billing ledger and `mediflow.billing.prescription-charge-consumer.enabled` gates (default false)
select a strict V1 handler on the existing single `billing.q` dispatcher. Flat V0 facts retain their
compatibility handler; malformed V1 never falls back to it. Pharmacy's original five-event fixture
bytes remain unchanged. No new competing queue, public price/charge command, or shared module exists.

Pharmacy owns the server-captured drug catalogue snapshot in `items[].unitPrice`; Billing validates
all exact quantities, prices and their sum against `totalAmount` before accepting money. This is
an explicit prescription-specific pricing specialization, not permission to accept client amounts
or use Surgery's catalogue policy for drugs. New issuance additionally requires Billing's explicit
configured `mediflow.billing.prescription-charges.allowed-price-codes` policy (empty by default;
enabling the issuer without it fails startup). Unknown codes reject, never choose a fallback price;
matching historical replay does not revalidate today's policy. `priceCode` identifies one bundle:
`sourceType=PRESCRIPTION`, `sourceId=prescriptionId`, quantity one, unit/gross amount the checked
immutable bundle total. Durable item receipts retain drug IDs, quantities and prices, not clinical
notes or names. New zero-total prescriptions reject pending an approved free-care policy. Replay
does not re-price or rewrite closed accounts; changed source bytes require an explicit adjustment
contract, not another charge.

OUTPATIENT creates exactly one selected-charge `PRESCRIPTION` payment request, independent invoice
UUID and singular target; optional recordId is evidence, not an EXAM clearance target. ADMISSION
posts only to its exact admission account and creates no prepayment request, invoice or clearance.
The original account department is preserved; each charge keeps its generating department.
The event has no doctor/account actor field, so Billing never fabricates `created_by`.

Delivery claim, immutable source/items, exact account, charge, outpatient request/target and held
invoice notice commit atomically. Payment completion reuses the existing classified installment
writer: partial installments have no clearance, full payment grants only the exact outpatient Rx.
`invoice.created` V1 for this issuer contains `{invoiceId,paymentRequestId,accountId,patientId,
departmentId,careEpisodeType,careEpisodeId,purpose=PRESCRIPTION,prescriptionId,totalAmount,currency,
createdAt,expiresAt}`. This is a request notice, not payment, earned revenue or dispense authority.

**Local continuation:** Notification now accepts the exact PRESCRIPTION purpose through its
existing guarded request-notice reader and private template; V0 invoice consumers must never
receive it. V13 adds strict terminal intake/local adjustment as described below. V6's
held-publication constraint is unchanged. Distributed revocation, admission intake closure,
public Pharmacy V1 authority and reviewed end-to-end rollout remain separate open gates. This
slice cannot enable Pharmacy's held outbox or create earned Report facts from a posted charge.

### Prescription terminal adjustment — local opt-in continuation, 2026-10-09

The existing single Billing dispatcher accepts the original Pharmacy V1 `prescription.filled`,
`prescription.cancelled`, `prescription.expired` and `prescription.dispense.failed` bytes behind
the same ledger/Rx-issuer gates. V0 keeps its own compatibility handler; V1 failure cannot fall back.
One Rx has one immutable terminal operation across all four keys. Delivery identity is globally
fenced across Rx creation and terminal receipt tables, independent of source identity.

The decoder verifies exact context, source, items/server prices/total and business timestamp.
The common creation-snapshot hash excludes creation/terminal timestamps, reason and dispense ID,
but includes every identity, item/name/price and context field. Terminal identity separately hashes
its complete payload and key. Narrative/reason and drug names are not retained in Billing; only
minimal hash/time/source proof is stored. Existing creation hashes are unchanged. V13 appends a
nullable snapshot proof: an old receipt remains unverifiable until the actual original creation
is revalidated, never reconstructed from terminal bytes or present-day catalogue.

Early terminal evidence is durable PENDING. Arrival of exact creation recovers it in the same
creation transaction; an invalid early fact becomes REJECTED without poisoning valid charge
issuance. A known source mismatch rolls back delivery/source claims instead. Source locks use the
same Rx namespace as creation, followed by the account/charge/request locks shared with money
writers. Matching terminal replay is no-effect even after account closure; changed terminal type,
source bytes, patient, name, price or chronology conflicts. Future time over five seconds rejects.

FILLED is absorbing operational evidence only: it does not recognize revenue, grant permission,
cancel a charge or fabricate a payment. Cancellation/expiry/failure on an OPEN account void only
the exact posted bundle, cancel only its exact singular outpatient request and locally revoke its
existing grant after all financial lock waits. Admission is charge-only; a later allocated/mixed
request or closed-account adjustment rejects pending a separate late-adjustment policy.

Refund obligations are immutable per Rx + original completed payment + charge, limited to that
original's remaining allocation after actual completed refund/reversal lines. No REFUND transaction,
payment.failed or completed-refund fact is invented. The default-off direct-service GET
`/api/v1/billing/prescription-adjustments/{id}/refunds-due` is ADMIN/CASHIER only, with unavailable
ledger distinct from absent adjustment. Its single owned MVCC statement caps outstanding due by
current original-only net allocations. Actual returned cash uses the existing signed, idempotent
refund command and emits the unchanged held `payment.refunded` contract consumed by Report and
Notification. Gateway policy/routes are unchanged and not acceptance for this new GET.

Local void/revoke/due is not distributed revocation/supersession or released public V1 authority.
Creation/terminal outboxes remain held. Public medication-order/admission authority, finance
recognition/settlement, reviewed release and packaged V1 cutover remain open.

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

### Non-final versioned reconciliation — 2026-10-10

Under the existing user-scoped Billing override, the new engineering contract is
**`settlement.reconciled`**, envelope **version 1**, producer **`billing-service`**, source
**`settlementId`**. It is NOT `settlement.completed`. Inpatient and Notification must not consume
it as discharge permission or a final-settlement notice. Its sole current consumer is Report's
internal versioned account-receivable stock.

Actual issuer: `POST /api/v1/billing/accounts/{id}/settlement-reconciliations`, ADMIN/CASHIER only.
All three Billing gates `ledger.enabled`, `settlement-preview.enabled`, `reconciliation.enabled`
under `mediflow.billing` must be explicitly true; defaults remain false. `Idempotency-Key` is
required, 1..120 ASCII letters/digits/`._:-`. Actor comes from signed unexpired human access JWT
with UUID subject, never a request field. Missing header is 400, invalid key/state is 422,
conflicting key/account/actor is 409, storage/serialization contention is safe 503. Retry the
same key, not a new operation. No caller-supplied money, insurance, allocation or approval exists.

Only persisted ADMISSION accounts in CHARGE_CLOSED/SETTLEMENT_PENDING qualify. The bounded
REPEATABLE READ command derives amounts from the existing ledger. Key fence, account write fence,
immutable reconciliation and held outbox commit together. Exact replay returns the original result
without reading changed ledger rows or emitting again, even after subsequent final account-state
changes. New successful keys create consecutive revisions with exact prior UUID. No payment,
allocation, insurance, charge amount or final account status is changed.

Exact payload fields (English camelCase):

| Fields | Semantics |
|---|---|
| `settlementId`, `settlementVersion`, `supersedesSettlementId` | UUID; positive integral account-local reconciliation revision; first predecessor null, subsequent exact previous UUID |
| `accountId`, `admissionId`, `patientId`, `departmentId`, `currency` | exact persisted references and three-uppercase-letter currency; department is account department, NOT multi-department earned allocation |
| `grossAmount`, `insuranceAmount`, `patientLiability` | persisted POSTED gross and original-linked insurance; liability = gross − insurance |
| `completedPayments`, `completedRefunds`, `completedReversals` | separate completed inventories, NOT new cash operations |
| `netAllocatedPayments`, `balance` | persisted net charge allocations; balance = liability − net allocated, NOT liability − gross receipts |
| `estimatedOutcome` | ADDITIONAL_PAYMENT_REQUIRED / PAID_IN_FULL / REFUND_DUE matching sign; not approved debt/waiver |
| `reconciledAt`, `finalSettlement` | exact observed Instant equals envelope occurredAt; finalSettlement=false even when balance zero |

All amounts are exact NUMERIC(19,2), never floating-point. V14 `admission_reconciliation` is a
separate non-final namespace, NOT the final `SETTLEMENT` table. Future finalization requires a
reviewed namespace transition; never conflate equal version numbers across these event types.
V6 keeps events HELD (`publication_enabled=false`), with no automatic release.

Actual use-case serialization verifies producer-owned
`ledger-v1/settlement-reconciled-{1,2,3,4}.json`; Report reads those same files without copies.
Installments, original-linked refund and persisted insurance produce balances 40 / 0 / 15 / −5.
This implements no medical charge-close writer, deposit/top-up/allocation command, final
settlement, discharge, recognition, financial history/coverage or public cutover.
Report's revision/stock semantics are canonical in
[CARE-PROJECTIONS](CONTRACT-CARE-PROJECTIONS-01.md#versioned-non-final-receivable-stock--2026-10-10).

### Read-only admission ledger reconciliation — 2026-10-09

Under the existing user-scoped implementation override, Billing now supplies the default-off
`GET /api/v1/billing/accounts/{id}/settlement-preview`, ADMIN/CASHIER only, requiring both
`mediflow.billing.ledger.enabled` and `mediflow.billing.settlement-preview.enabled`. This is a
human diagnostic read, not an internal authority lookup, charge-close command or financial event.
Only a signed human access credential with canonical UUID subject and expiry is accepted;
refresh/service credentials are rejected before reading ledger amounts.
Gateway is unchanged; Report must never call this endpoint or use it as its event source.

The adapter reads only Billing-owned rows in one read-only REPEATABLE READ transaction. It starts
its own transaction even under a weaker outer caller; source account identity, ADMISSION episode,
closed-charge state/time, currency, original payment/refund/allocation/insurance relationships and
NUMERIC(19,2) totals are validated before returning any amount. Missing account is 404; open charges,
inconsistent evidence or a 10,000-row-per-table budget breach are 422, never a partial/zero total.
Database/time-budget failures are 503 BILLING_LEDGER_UNAVAILABLE. Each data statement is limited
to three seconds and the transaction to five; this is not a whole-request latency SLA.

`grossAmount` excludes VOIDED charges. Insurance is the persisted APPROVAL minus exact linked
REVERSAL amounts, not a caller-entered approval. `netAllocatedPayments` is completed PAYMENT
allocations minus completed REFUND/REVERSAL allocations to POSTED charges. Cumulative return and
allocation limits apply per original transaction/charge, including the original's unallocated
portion; another deposit cannot mask an invalid refund. `balance = patientLiability -
netAllocatedPayments`, with `patientLiability = grossAmount - insuranceAmount`.

Gross completed payments, physical refunds and transaction reversals are separate values.
`unappliedNetPayments` also keeps unreturned funds on VOIDED charges visible; it does not grant
permission to reallocate or return them. `unallocatedDeposit` is remaining unallocated completed
deposit cash, not a complete liability/revenue projection. An unallocated deposit never reduces
the allocation balance. `estimatedOutcome` is arithmetic only: even PAID_IN_FULL is explicitly
`snapshotOnly=true, finalSettlement=false`, and can coexist with unapplied money. No debt/waiver
approval, recognized amount, settlement revision or final event is inferred. No ledger rows,
clearances, outboxes or earlier migration checksums change on this GET.

The medical-discharge charge-close writer, authoritative deposit allocation/recognition,
immutable final settlement/version/supersedes producer, Report five-metric mapping/replay and
public/held release remain implementation work, not waiting on this preview or closed by it.
[Implementation and verification](../../superpowers/plans/2026-10-09-admission-ledger-reconciliation.md).

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

### Discharge freeze and admission settlement — 2026-10-10

Task-scoped Billing implementation by its owner (Lộc). An opt-in consumer accepts actual
inpatient-service `discharge.medically.approved` V1 bytes and freezes the exact admission account's
charge intake (OPEN → CHARGE_CLOSED); it never closes the admission itself, consistent with
"Inpatient closes only after settlement." An opt-in settlement command then computes
`grossAmount`/`allocatedPayments`/`patientLiability`/`balance` from persisted CHARGE/PAYMENT_
ALLOCATION/INSURANCE_ADJUSTMENT rows, persists a new immutable SETTLEMENT version linked to the
previous one, and: issues a SETTLEMENT PAYMENT_REQUEST for a positive no-insurance balance (the
exact remaining balance per POSTED charge, summing exactly to the request amount — paid later
through the already-generic ledger payment command, no new writer); records REFUND_DUE for a
negative balance without executing a refund; or publishes `settlement.completed` and closes the
account to SETTLED for a zero balance or an explicitly approved DEBT_APPROVED/WAIVED exception.
Gated by `mediflow.billing.ledger.enabled` and `mediflow.billing.settlement.enabled`, both false by
default. No new migration: SETTLEMENT/INSURANCE_ADJUSTMENT tables and the Settlement/
InsuranceAdjustment domain model already existed from the original V2 schema, unused until now.
Verified against inpatient-service's own discharge fixture; `settlement.completed` had no canonical
fixture from either side yet, so one was authored here matching the documented payload exactly and
proven against the actual producer output, the same way `clearance-*.json` were established earlier.

A positive balance under a nonzero approved insurance adjustment explicitly rejects
(`BILLING_SETTLEMENT_INSURANCE_PAYMENT_REQUEST_UNSUPPORTED`) instead of guessing a per-charge
distribution of the adjustment — attaching full remaining per-charge balances to the settlement
request would only sum to the pre-insurance balance, and no per-charge insurance-allocation policy
has been decided by anyone. Insurance recording itself, and the zero/negative-balance outcomes, are
unaffected. Deposit recognition (unapplied deposit cash becoming earned revenue at settlement) also
stays open, exactly matching this contract's own long-standing "final settlement/recognition" open
item: a deposit never separately allocated to a charge is simply absent from `allocatedPayments`,
per the §4 formula as written — not a shortcut taken here, the formula's literal consequence. Top-up
issuance remains separately blocked on an undecided trigger-threshold policy. This does not
implement Docker/Gateway multi-service acceptance or deletion of
[HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT](../../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md).

### ADMISSION_DEPOSIT initial request issuance — 2026-10-10

Task-scoped Billing implementation by its owner (Lộc). An opt-in handler accepts actual
Inpatient-service `admission.deposit.requested` V1 bytes and issues one PAYMENT_REQUEST per admission
for the exact `suggestedAmount` Inpatient supplied, with no `CHARGE` row — consistent with the
existing invariant that a deposit is cash/liability, not an earned charge (the generic ledger payment
command already rejects any charge allocation attempt against an `ADMISSION_DEPOSIT` request). Gated
by `mediflow.billing.ledger.enabled` and `mediflow.billing.admission-deposit-consumer.enabled`, both
false by default. Exact replay of the same `admissionId` returns the recorded request; a conflicting
replay rejects. No new writer was needed for payment/clearance: the existing generic ledger payment
command already grants `financial.clearance.granted` for `ADMISSION_DEPOSIT` once its exact request is
paid in full, unchanged. Verified against inpatient-service's own fixture, copied byte-for-byte into
Billing's test resources per the no-copied-wire-format rule. This does not implement top-up issuance
(blocked: the trigger threshold and the operational meaning of `currentBalance` are not decided by
anyone yet, not merely unbuilt) or settlement (unblocked by any decision, simply not built), Docker/
Gateway multi-service acceptance, or deletion of
[HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT](../../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md).

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
