# 05 — pharmacy-service — Care & Finance V2 target

**Owner:** Huy (`LQHuy0210`)

**Module:** `backend/pharmacy-service`

**Base path:** `/api/v1/pharmacy`

**Status:** implementation-ready target; outpatient compatibility retained

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-INPATIENT-SURGERY-01`](../../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
- [CURRENT Pharmacy spec](../05-pharmacy.md)

Pharmacy owns prescriptions, stock reservation and dispensing. Billing owns money and clearance;
Inpatient owns admission state. Pharmacy stores exact external UUIDs and never selects an admission
or medical record by patient.

## 2. Migration boundary

1. Existing prescriptions are version `0`, `care_context=OUTPATIENT` and retain the
   `payment.completed` compatibility saga.
2. New prescriptions are version `1` and carry an explicit care context and episode.
3. OUTPATIENT dispensing requires a matching `PRESCRIPTION` clearance.
4. ADMISSION medication posts a charge to the admission account and follows inpatient dispensing
   policy; an outpatient payment event cannot unlock it.
5. No legacy prescription is backfilled with a guessed admission.

## 3. Target DDL — `V14__care_finance_v2.sql`

The Pharmacy/Billing/Report branch keeps its approved English persistence naming convention.

```sql
ALTER TABLE PRESCRIPTION
    ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN care_context VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT',
    ADD COLUMN care_episode_type VARCHAR(32),
    ADD COLUMN care_episode_id UUID,
    ADD COLUMN admission_id UUID,
    ADD COLUMN price_code VARCHAR(64),
    ADD COLUMN clearance_id UUID;

ALTER TABLE PRESCRIPTION
    ADD CONSTRAINT ck_prescription_contract_version
        CHECK (care_contract_version IN (0, 1)),
    ADD CONSTRAINT ck_prescription_care_context
        CHECK (care_context IN ('OUTPATIENT', 'ADMISSION')),
    ADD CONSTRAINT ck_prescription_v1_episode CHECK (
        care_contract_version = 0 OR (
            care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')
            AND care_episode_id IS NOT NULL
            AND price_code IS NOT NULL
            AND ((care_context = 'OUTPATIENT' AND admission_id IS NULL
                  AND care_episode_type = 'OUTPATIENT_VISIT')
              OR (care_context = 'ADMISSION' AND admission_id IS NOT NULL
                  AND care_episode_type = 'ADMISSION'
                  AND care_episode_id = admission_id))
        )
    );

CREATE INDEX idx_prescription_episode
    ON PRESCRIPTION(care_episode_type, care_episode_id);
CREATE INDEX idx_prescription_admission
    ON PRESCRIPTION(admission_id) WHERE admission_id IS NOT NULL;

CREATE TABLE PRESCRIPTION_CLEARANCE (
    clearance_target_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    clearance_id UUID NOT NULL,
    account_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    prescription_id UUID NOT NULL REFERENCES PRESCRIPTION(prescription_id),
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_prescription_clearance_event UNIQUE(event_id, prescription_id),
    CONSTRAINT uq_prescription_clearance_id UNIQUE(clearance_id, prescription_id)
);

CREATE TABLE ADMISSION_MEDICATION_CONTEXT (
    admission_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    last_event_id UUID NOT NULL UNIQUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_admission_medication_context_time
        CHECK (closed_at IS NULL OR closed_at >= started_at)
);
```

The existing `PROCESSED_EVENT` and transactional `PHARMACY_EVENT_OUTBOX` remain the inbox/outbox.

### Local migration specialization (2026-10-02)

The implemented additive sequence is V14 prescription metadata, V15 admission lifecycle context,
then V16 `prescription_clearance`, target fences and `prescription_clearance_event`. The clearance
target references only a local fence, not the prescription table: authoritative grants can arrive
before a prescription and persist as PENDING. Known prescriptions are matched before storage;
pending grants must be re-matched under the prescription/authorization transaction before use.
Clearance ID is immutable and unique for its singular prescription target; event ID separately
deduplicates deliveries. Conflicting snapshots roll back the delivery claim. ISO source timestamps
preserve exact nanoseconds independently of PostgreSQL timestamps.

Huy's local choice is AUTHORIZE ONLY: consuming clearance never auto-dispenses or creates a legacy
payment receipt. The canonical envelope `occurredAt` currently supplies grant time, and optional
expiry is exclusive. Billing must confirm immutable grant time across republish, nullable expiry
and any revocation/replacement policy before binding. The amount is a Billing snapshot, not an
amount Pharmacy recalculates from the prescription. V16 PostgreSQL verification and the V1
stock/lifecycle writer remain open; all V2 activation remains disabled.

### Local held lifecycle writer (2026-10-02)

V17 adds nullable historical `PRESCRIPTION_LINE.drug_name_snapshot` and nullable
`lifecycle_at_iso` on prescription/slip. Names are captured server-side on new lines; legacy rows
are not populated from today's catalogue. Terminal transitions preserve an explicit exact Instant
independently of mutable `updated_at`; V1 events never substitute an audit timestamp for missing
business evidence. The factory requires a persisted V1 prescription, matching lifecycle state,
exact care tuple, stored name/price/items and its recorded business timestamp. Capture joins the
caller's mutation transaction, locks prescription before slip, verifies terminal state/time/reason,
and derives filled `dispenseId` from the stored slip, never a caller-supplied or generated source ID.

The existing `PHARMACY_EVENT_OUTBOX` now holds separate version-1 rows. Their `delivery_enabled`
is false and a DB check forbids enabling them in this schema. Legacy defaults stay version 0/enabled.
`care_lifecycle_order=0` identifies creation; order 1 identifies exactly one terminal outcome;
the partial unique key prevents two terminal facts for one prescription. Duplicate bytes/ID are
idempotent; altered bytes, new ID for the same lifecycle and identity collisions are conflicts,
not overwrites. A terminal write requires its creation already in the outbox. The dispatcher excludes
held candidates but retains held critical predecessors as causal blockers. Admin replay cannot
remove the hold; pending held rows are not removed by published-row retention.

The internal outpatient `DispenseCarePrescriptionUseCase` now calls this hook in one transaction.
It requires a staff/account command (never auto-dispenses on a grant), locks Rx/slip then every
drug/reservation in sorted drug order, requires exact matching outpatient clearance and rechecks
all grant/TTL/drug expiry guards after authorization waits/writes. All lines validate before effects.
Stock, reservations, Rx/slip terminal proof, held filled bytes and existing stock-low outbox commit
together; any error rolls back without V0 filled/payment receipt/refund. Retry requires matching
persisted Rx/slip AND held filled proof and cannot decrement stock or mint another event ID.
There is no public API, legacy workflow or listener invoking this internal transaction. V0 cancel/
failure/late-payment reject V1; V0 expiry skips V1. Internal V1 outpatient create/cancel/expiry/stock
failure now exists as described below. Admission/adjustment/public cutover remain open. Unit tests are not substitutes for
V17 PostgreSQL compatibility, stock/outbox rollback/race, timestamp reload or owner acceptance.

### Internal held lifecycle completion slice (2026-10-02)

V18 persists a local command receipt/fence. Create validates trusted DOCTOR self/ADMIN delegation
before replay, requires exact V1 outpatient intent, sorted drug locks, fresh expiry/availability and
server price/name snapshots. Prescription, whole reservations, pending slip, held CREATED and receipt
commit together. Duplicate/concurrent intent returns the original Rx; changed actor/intent conflicts;
failed outbox capture rolls back the receipt too. The public create use case remains fail-closed.
This internal API does not assert patient/episode authority or admission medication eligibility.

Internal cancel and expiry release a whole order and store matching exact terminal business proof
with held bytes atomically. Cancel requires the prescribing DOCTOR/ADMIN and bounded reason;
retry actor/reason must match. Expiry reads the clock after locks and requires all TTLs expired.
Stock-failure recovery runs in REQUIRES_NEW after rollback and rechecks current locked business
stock/expiry plus exact clearance. Denial, malformed reservation evidence or infrastructure exception
cannot become FAILED; healthy stock or a competing terminal outcome is not overwritten. No actual
stock is incremented/decremented in terminal failure, and no V0 receipt/refund/invoice is created.
Real PG lifecycle/receipt/concurrency/rollback tests run with Docker; latest evidence is in the plan.
No listener/public scheduler/route activates these paths; held delivery and feature flag remain OFF.

## 4. Enums and invariants

```java
public enum CareContext { OUTPATIENT, ADMISSION }
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
```

- Every V2 prescription has exactly one episode.
- ADMISSION requires `admissionId=careEpisodeId` and an active admission projection.
- OUTPATIENT dispense requires non-expired matching clearance; ADMISSION dispense does not pretend
  to be prepaid and records the charge against the admission account.
- Stock reservation, stock decrement, dispense slip and outbox event commit atomically.
- `prescriptionId` creates at most one dispense slip.

## 5. Ports and DTOs

```java
public interface ReactToCareFinanceUseCase {
    void onFinancialClearance(FinancialClearanceCommand command);
    void onAdmissionStarted(AdmissionStartedCommand command);
    void onAdmissionClosed(AdmissionClosedCommand command);
}

public interface PrescriptionClearanceRepositoryPort {
    boolean claimAndSave(UUID eventId, PrescriptionClearance clearance);
    Optional<PrescriptionClearance> findValid(UUID prescriptionId, Instant at);
}

public interface AdmissionMedicationContextPort {
    Optional<AdmissionMedicationContext> find(UUID admissionId);
    void upsert(AdmissionMedicationContext context);
}

public record CreatePrescriptionRequest(
    @NotNull UUID patientId,
    @NotNull UUID doctorId,
    @NotNull UUID departmentId,
    UUID recordId,
    UUID admissionId,
    @NotNull CareContext careContext,
    @NotNull CareEpisodeType careEpisodeType,
    @NotNull UUID careEpisodeId,
    @NotBlank @Size(max = 64) String priceCode,
    @NotEmpty List<PrescriptionLineRequest> lines
) {}

public record FinancialClearanceCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId, ClearancePurpose purpose,
    UUID prescriptionId, BigDecimal amount, String currency, Instant expiresAt
) {}
```

## 6. Application algorithms

### Create prescription

1. Validate patient/staff and exact episode fields.
2. For ADMISSION, require an active context whose patient and department match.
3. Lock drug rows in UUID order, validate quantities/expiry and reserve stock.
4. Snapshot item prices and total; save prescription and reservations.
5. Append `prescription.created` with exact context, episode, target and priced items.

### Consume OUTPATIENT clearance

1. Require envelope version `1`, purpose `PRESCRIPTION` and non-null prescription ID.
2. Claim `eventId`, lock prescription and require `careContext=OUTPATIENT`.
3. Match patient, episode and target; store clearance projection.
4. Duplicate event returns without repeating dispense.

### Dispense

1. Lock prescription, reservations and drug rows.
2. OUTPATIENT requires valid clearance; ADMISSION requires active admission projection.
3. Decrement stock and mark reservations fulfilled.
4. Create/complete one dispense slip and append `prescription.filled` in the same transaction.
5. On stock failure, release reservations and append `prescription.dispense.failed`; Billing decides
   credit/refund policy.

## 7. Events

| Event | Required payload |
|---|---|
| `prescription.created` | `prescriptionId`, `recordId?`, `admissionId?`, `patientId`, `departmentId`, `careContext`, `careEpisodeType`, `careEpisodeId`, `sourceType=PRESCRIPTION`, `sourceId=prescriptionId`, `priceCode`, `items[]`, `totalAmount`, `createdAt` |
| `prescription.filled` | same identity/context plus `dispenseId`, `filledAt`, `items[]` |
| `prescription.dispense.failed` | same identity/context plus `reason`, `failedAt` |

Subscribe to `financial.clearance.granted`, `admission.started`, `admission.closed` and compatibility
`payment.completed`. Compatibility payment accepts only its explicit `prescriptionId`.

## 8. REST and roles

Existing endpoints remain. V2 create accepts the new request contract; dispense remains restricted
to `ADMIN, PHARMACIST`. Reads retain their CURRENT role matrix. No endpoint accepts a client-provided
`dispensedBy`; it comes from verified identity claims.

Key errors: `PHARMACY_CARE_CONTEXT_INVALID` (422), `PHARMACY_ADMISSION_INACTIVE` (422),
`PHARMACY_CLEARANCE_REQUIRED` (422), `PHARMACY_CLEARANCE_TARGET_MISMATCH` (422),
`PHARMACY_INSUFFICIENT_STOCK` (409), `PHARMACY_ALREADY_DISPENSED` (409) and
`PHARMACY_UPSTREAM_UNAVAILABLE` (503).

## 9. Required tests

| Rule | Required test |
|---|---|
| outpatient requires exact clearance | `dispense_outpatientWithoutClearance_rejects` |
| admission never uses outpatient clearance | `onClearance_admissionPrescription_rejects` |
| admission ID is exact | `create_admissionContextMismatch_rejects` |
| closed admission cannot dispense | `dispense_closedAdmission_rejects` |
| duplicate clearance applies once | `onClearance_duplicateEvent_appliesOnce` |
| duplicate dispense decrements once | `dispense_repeatedCommand_singleStockEffect` |
| stock and outbox atomic | `dispense_commitPersistsStockSlipAndOutbox` |
| context carried on all events | `eventFixtures_roundTripCareContext` |
| compatibility ID explicit | `onPaymentCompleted_targetsOnlyPrescriptionId` |

## 10. Rollout and Definition of Done

Add the columns/projections behind `mediflow.features.care-finance-v2=false`. Enable OUTPATIENT V2
after Billing clearance fixtures pass; enable ADMISSION after Inpatient lifecycle fixtures pass.
Done requires concurrency, duplicate delivery, DLQ, role and Docker flows for both contexts.
