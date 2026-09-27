# 10 — inpatient-service — Core V1 implementation specification

**Owner:** Vinh (`Dangvinh77` / `Harori`)

**Module:** `backend/inpatient-service`

**Package:** `com.mediflow.inpatient`

**Port / database:** `8090` / `mediflow_inpatient`

**Base path:** `/api/v1/inpatient`

**Status:** implementation-ready Core V1; external integrations remain gated by shared fixtures

## 1. Sources and bounded context

This specification implements the P3 vertical slice in
[`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html):

```text
referral → bed → deposit → admit → treatment → medical discharge → settlement → close
```

Mandatory contracts:

- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md);
- [`CONTRACT-INPATIENT-SURGERY-01`](../../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md);
- [`CONTRACT-SURGERY-BILLING-01`](../../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md);
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md);
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

Inpatient owns admissions, hospital beds, bed assignments, treatment entries, external clinical
order references, medical discharge summaries and administrative close. It does not own identities,
outpatient records, Lab tests, prescriptions, surgery cases, charges, payments or settlements.

Core V1 records exact external IDs and consumes their facts. Surgery internals, Lab execution,
Pharmacy stock and Billing ledger behavior remain in their owner services.

## 2. Package map

```text
com.mediflow.inpatient
├── domain/model
│   ├── Admission.java, Bed.java, BedAssignment.java
│   ├── TreatmentEntry.java, ClinicalOrderReference.java, DischargeSummary.java
│   └── enums...
├── domain/exception
├── application/dto/{request,response,command}
├── application/port/in
├── application/port/out
├── application/service
└── infrastructure
    ├── web, persistence, messaging, client, config, security, correlation
```

Dependencies point inward. Domain and application packages contain no Spring/JPA/Rabbit imports.

## 3. Database — `V1__inpatient_core.sql`

Database identifiers follow Vietnamese snake_case; Java and JSON names are English camelCase.
External aggregate IDs are bare UUIDs and have no cross-database foreign keys.

```sql
CREATE TABLE dot_noi_tru (
    admission_id UUID PRIMARY KEY,
    admission_request_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    source_record_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    diagnosis_summary TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    attending_doctor_id UUID,
    department_id UUID NOT NULL,
    priority VARCHAR(20) NOT NULL,
    emergency BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    deposit_clearance_id UUID,
    deposit_requested_at TIMESTAMPTZ,
    emergency_override_id UUID,
    settlement_id UUID,
    close_override_id UUID,
    admitted_at TIMESTAMPTZ,
    medically_discharged_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dot_noi_tru_priority
        CHECK (priority IN ('ROUTINE', 'URGENT', 'EMERGENCY')),
    CONSTRAINT ck_dot_noi_tru_status
        CHECK (status IN ('REQUESTED', 'AWAITING_BED', 'AWAITING_DEPOSIT', 'READY',
                          'ADMITTED', 'MEDICALLY_DISCHARGED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_dot_noi_tru_close
        CHECK (status <> 'CLOSED' OR
              (medically_discharged_at IS NOT NULL AND closed_at IS NOT NULL
               AND (settlement_id IS NOT NULL OR close_override_id IS NOT NULL))),
    CONSTRAINT ck_dot_noi_tru_cancel
        CHECK (status <> 'CANCELLED' OR
              (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL))
);
CREATE INDEX idx_dot_noi_tru_patient ON dot_noi_tru(patient_id, created_at DESC);
CREATE INDEX idx_dot_noi_tru_department_status ON dot_noi_tru(department_id, status);

CREATE TABLE giuong_benh (
    bed_id UUID PRIMARY KEY,
    department_id UUID NOT NULL,
    ward_code VARCHAR(32) NOT NULL,
    room_code VARCHAR(32) NOT NULL,
    bed_code VARCHAR(32) NOT NULL,
    bed_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_giuong_benh_code UNIQUE(department_id, ward_code, room_code, bed_code),
    CONSTRAINT ck_giuong_benh_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'OUT_OF_SERVICE'))
);
CREATE INDEX idx_giuong_benh_available
    ON giuong_benh(department_id, ward_code, status) WHERE active = TRUE;

CREATE TABLE phan_giuong (
    assignment_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    bed_id UUID NOT NULL REFERENCES giuong_benh(bed_id),
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    released_by UUID,
    released_at TIMESTAMPTZ,
    release_reason TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_phan_giuong_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_phan_giuong_release CHECK (
        (status = 'ACTIVE' AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND released_by IS NOT NULL)
    ),
    CONSTRAINT ck_phan_giuong_time CHECK (released_at IS NULL OR released_at >= assigned_at)
);
CREATE UNIQUE INDEX uq_phan_giuong_active_bed
    ON phan_giuong(bed_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_phan_giuong_active_admission
    ON phan_giuong(admission_id) WHERE status = 'ACTIVE';

CREATE TABLE dien_bien_dieu_tri (
    entry_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    entry_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    authored_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    correction_of_entry_id UUID REFERENCES dien_bien_dieu_tri(entry_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dien_bien_type CHECK (entry_type IN
        ('NOTE', 'OBSERVATION', 'PROCEDURE', 'LAB', 'MEDICATION', 'SURGERY', 'CORRECTION')),
    CONSTRAINT ck_dien_bien_correction CHECK (
        (entry_type = 'CORRECTION' AND correction_of_entry_id IS NOT NULL)
        OR (entry_type <> 'CORRECTION' AND correction_of_entry_id IS NULL)
    )
);
CREATE INDEX idx_dien_bien_admission_time
    ON dien_bien_dieu_tri(admission_id, recorded_at, entry_id);

CREATE TABLE tham_chieu_y_lenh (
    order_ref_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    order_type VARCHAR(24) NOT NULL,
    external_order_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary TEXT,
    event_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tham_chieu_y_lenh UNIQUE(order_type, external_order_id),
    CONSTRAINT ck_y_lenh_type CHECK (order_type IN ('LAB_TEST', 'PRESCRIPTION', 'SURGERY')),
    CONSTRAINT ck_y_lenh_status CHECK (status IN
        ('REQUESTED', 'READY', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'FAILED'))
);
CREATE INDEX idx_y_lenh_admission ON tham_chieu_y_lenh(admission_id, order_type, status);

CREATE TABLE tom_tat_ra_vien (
    summary_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL UNIQUE REFERENCES dot_noi_tru(admission_id),
    diagnosis_summary TEXT NOT NULL,
    treatment_summary TEXT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    follow_up_plan TEXT NOT NULL,
    approved_by UUID NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tom_tat_outcome CHECK
        (outcome IN ('RECOVERED', 'IMPROVED', 'UNCHANGED', 'TRANSFERRED', 'DECEASED', 'OTHER'))
);

CREATE TABLE lich_su_trang_thai_noi_tru (
    history_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id UUID,
    reason TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lich_su_admission
    ON lich_su_trang_thai_noi_tru(admission_id, changed_at, history_id);

CREATE TABLE xac_nhan_tai_chinh_noi_tru (
    clearance_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    patient_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE quyet_toan_noi_tru (
    settlement_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    account_id UUID NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    insurance_amount NUMERIC(19,2) NOT NULL,
    patient_liability NUMERIC(19,2) NOT NULL,
    completed_payments NUMERIC(19,2) NOT NULL,
    completed_refunds NUMERIC(19,2) NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_quyet_toan_outcome CHECK (outcome IN
        ('PAID_IN_FULL', 'ADDITIONAL_PAYMENT_REQUIRED', 'REFUND_DUE', 'DEBT_APPROVED', 'WAIVED'))
);
CREATE INDEX idx_quyet_toan_admission
    ON quyet_toan_noi_tru(admission_id, completed_at DESC);

CREATE TABLE yeu_cau_bo_sung_tam_ung (
    topup_request_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    account_id UUID NOT NULL,
    current_balance NUMERIC(19,2) NOT NULL,
    requested_amount NUMERIC(19,2) NOT NULL CHECK (requested_amount > 0),
    reason TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE phe_duyet_ngoai_le_noi_tru (
    override_id UUID PRIMARY KEY,
    admission_id UUID NOT NULL REFERENCES dot_noi_tru(admission_id),
    override_type VARCHAR(24) NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ngoai_le_type CHECK (override_type IN ('EMERGENCY_ADMIT', 'DEBT_CLOSE', 'WAIVER_CLOSE'))
);

CREATE TABLE su_kien_da_xu_ly (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE su_kien_outbox_noi_tru (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_noi_tru_unpublished
    ON su_kien_outbox_noi_tru(occurred_at) WHERE published_at IS NULL;

ALTER TABLE dot_noi_tru
    ADD CONSTRAINT fk_dot_noi_tru_clearance
        FOREIGN KEY (deposit_clearance_id) REFERENCES xac_nhan_tai_chinh_noi_tru(clearance_id),
    ADD CONSTRAINT fk_dot_noi_tru_settlement
        FOREIGN KEY (settlement_id) REFERENCES quyet_toan_noi_tru(settlement_id),
    ADD CONSTRAINT fk_dot_noi_tru_emergency_override
        FOREIGN KEY (emergency_override_id) REFERENCES phe_duyet_ngoai_le_noi_tru(override_id),
    ADD CONSTRAINT fk_dot_noi_tru_close_override
        FOREIGN KEY (close_override_id) REFERENCES phe_duyet_ngoai_le_noi_tru(override_id);
```

## 4. Enums

```java
public enum AdmissionStatus {
    REQUESTED, AWAITING_BED, AWAITING_DEPOSIT, READY,
    ADMITTED, MEDICALLY_DISCHARGED, CLOSED, CANCELLED
}
public enum AdmissionPriority { ROUTINE, URGENT, EMERGENCY }
public enum BedStatus { AVAILABLE, OCCUPIED, OUT_OF_SERVICE }
public enum BedAssignmentStatus { ACTIVE, RELEASED }
public enum TreatmentEntryType {
    NOTE, OBSERVATION, PROCEDURE, LAB, MEDICATION, SURGERY, CORRECTION
}
public enum ClinicalOrderType { LAB_TEST, PRESCRIPTION, SURGERY }
public enum ExternalOrderStatus { REQUESTED, READY, IN_PROGRESS, COMPLETED, CANCELLED, FAILED }
public enum DischargeOutcome { RECOVERED, IMPROVED, UNCHANGED, TRANSFERRED, DECEASED, OTHER }
public enum SettlementOutcome {
    PAID_IN_FULL, ADDITIONAL_PAYMENT_REQUIRED, REFUND_DUE, DEBT_APPROVED, WAIVED
}
public enum OverrideType { EMERGENCY_ADMIT, DEBT_CLOSE, WAIVER_CLOSE }
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
```

## 5. Admission state machine

```text
REQUESTED → AWAITING_BED → AWAITING_DEPOSIT → READY → ADMITTED
                                                    → MEDICALLY_DISCHARGED → CLOSED

REQUESTED | AWAITING_BED | AWAITING_DEPOSIT | READY → CANCELLED
```

Guard rules:

1. A normal admission reaches `READY` only with one active bed assignment and a non-expired matching
   `ADMISSION_DEPOSIT` clearance.
2. A paid deposit without a bed stays `AWAITING_BED`.
3. A bed without clearance stays `AWAITING_DEPOSIT`.
4. Emergency admission still requires an active bed and a complete `EMERGENCY_ADMIT` override; it
   does not create a fake paid clearance.
5. `ADMITTED` requires an explicit admit command and publishes `admission.started`.
6. Medical discharge requires an approved summary and changes only to `MEDICALLY_DISCHARGED`.
7. `CLOSED` requires an acceptable settlement or approved close override, plus no active bed.
8. `ADDITIONAL_PAYMENT_REQUIRED` and `REFUND_DUE` cannot close. Billing must publish a later final
   settlement outcome after the extra payment/refund is completed.

Every transition inserts `lich_su_trang_thai_noi_tru` in the aggregate transaction.

## 6. Domain invariants

- `admissionRequestId` creates at most one admission under redelivery and concurrency.
- A bed and an admission each have at most one active assignment.
- Transfer locks both bed rows in UUID order, releases the old assignment, then creates the new one.
- Treatment entries are append-only. Corrections create a new entry pointing to the original.
- An external order reference is unique by `(orderType, externalOrderId)` and must match the same
  admission on all subsequent events.
- External IDs are never found by patient, latest record or timestamp proximity.
- Medical and financial close remain separate operations.
- Money values received from Billing are projections only; Inpatient never recalculates them.

## 7. Error codes

| Code | HTTP | Condition |
|---|---:|---|
| `INPATIENT_ADMISSION_NOT_FOUND` | 404 | admission absent |
| `INPATIENT_BED_NOT_FOUND` | 404 | bed absent |
| `INPATIENT_DUPLICATE_REFERRAL` | 409 | request already materialized |
| `INPATIENT_INVALID_STATUS_TRANSITION` | 422 | state-machine violation |
| `INPATIENT_BED_UNAVAILABLE` | 409 | inactive, occupied or out of service |
| `INPATIENT_ACTIVE_BED_REQUIRED` | 422 | admission/admit lacks active bed |
| `INPATIENT_DEPOSIT_CLEARANCE_REQUIRED` | 422 | normal admission lacks clearance |
| `INPATIENT_CLEARANCE_TARGET_MISMATCH` | 422 | patient/episode/admission mismatch |
| `INPATIENT_EMERGENCY_OVERRIDE_INVALID` | 422 | missing approver/role/reason/time |
| `INPATIENT_DISCHARGE_SUMMARY_REQUIRED` | 422 | medical discharge lacks summary |
| `INPATIENT_SETTLEMENT_REQUIRED` | 422 | close lacks acceptable settlement/override |
| `INPATIENT_ACTIVE_BED_MUST_BE_RELEASED` | 422 | administrative close with active assignment |
| `INPATIENT_EXTERNAL_ORDER_MISMATCH` | 422 | external event references another admission |
| `INPATIENT_UPSTREAM_UNAVAILABLE` | 503 | required identity lookup unavailable |

## 8. Application ports

```java
public interface ManageAdmissionUseCase {
    AdmissionDTO create(CreateAdmissionRequest request);
    AdmissionDTO get(UUID admissionId);
    PageResult<AdmissionDTO> search(AdmissionSearchQuery query);
    AdmissionDTO admit(UUID admissionId, AdmitRequest request);
    AdmissionDTO cancel(UUID admissionId, CancelAdmissionRequest request);
}

public interface ManageBedUseCase {
    BedDTO create(CreateBedRequest request);
    BedDTO update(UUID bedId, UpdateBedRequest request);
    PageResult<BedDTO> search(BedSearchQuery query);
    AdmissionDTO assign(UUID admissionId, AssignBedRequest request);
    AdmissionDTO transfer(UUID admissionId, TransferBedRequest request);
    AdmissionDTO release(UUID admissionId, ReleaseBedRequest request);
}

public interface ManageTreatmentUseCase {
    TreatmentEntryDTO append(UUID admissionId, CreateTreatmentEntryRequest request);
    TreatmentEntryDTO correct(UUID admissionId, UUID entryId, CorrectTreatmentEntryRequest request);
    ClinicalOrderReferenceDTO registerOrder(UUID admissionId, RegisterOrderReferenceRequest request);
}

public interface ManageDischargeUseCase {
    AdmissionDTO approveMedicalDischarge(UUID admissionId, MedicalDischargeRequest request);
    AdmissionDTO close(UUID admissionId, CloseAdmissionRequest request);
}

public interface ReactToAdmissionReferralUseCase { void onAdmissionRequested(AdmissionRequestedCommand command); }
public interface ReactToFinancialClearanceUseCase { void onFinancialClearance(FinancialClearanceCommand command); }
public interface ReactToSettlementUseCase { void onSettlementCompleted(SettlementCompletedCommand command); }
public interface ReactToExternalOrderUseCase { void onExternalOrderFact(ExternalOrderFactCommand command); }
```

Required out-ports:

```java
public interface AdmissionRepositoryPort {
    Optional<Admission> findById(UUID id);
    Optional<Admission> findByIdForUpdate(UUID id);
    Optional<Admission> findByAdmissionRequestId(UUID requestId);
    Admission save(Admission admission);
}
public interface BedRepositoryPort {
    Optional<Bed> findByIdForUpdate(UUID id);
    Bed save(Bed bed);
}
public interface BedAssignmentRepositoryPort {
    Optional<BedAssignment> findActiveByAdmissionId(UUID admissionId);
    Optional<BedAssignment> findActiveByBedId(UUID bedId);
    BedAssignment save(BedAssignment assignment);
}
public interface ProcessedEventPort { boolean tryClaim(UUID eventId, String eventType); }
public interface InpatientOutboxPort { void append(DomainEventEnvelope<?> event); }
public interface DepositSuggestionPolicyPort {
    DepositSuggestion suggest(AdmissionPriority priority, UUID departmentId);
}
```

Deposit suggestion is configurable policy data (`amount`, `priceCode`, `reason`), not an
authoritative charge. Billing owns the final payment request and ledger.

## 9. Request and response DTOs

```java
public record CreateAdmissionRequest(
    @NotNull UUID admissionRequestId,
    @NotNull UUID sourceRecordId,
    @NotNull UUID patientId,
    @NotNull UUID departmentId,
    @NotNull UUID requestedBy,
    @NotBlank @Size(max = 4000) String diagnosisSummary,
    @NotNull AdmissionPriority priority,
    boolean emergency,
    @NotNull Instant requestedAt
) {}

public record AssignBedRequest(@NotNull UUID bedId, @NotNull UUID assignedBy) {}
public record TransferBedRequest(
    @NotNull UUID targetBedId, @NotNull UUID transferredBy,
    @NotBlank @Size(max = 1000) String reason
) {}
public record ReleaseBedRequest(
    @NotNull UUID releasedBy, @NotBlank @Size(max = 1000) String reason
) {}

public record CancelAdmissionRequest(
    @NotNull UUID cancelledBy,
    @NotBlank @Size(max = 1000) String reason
) {}

public record CreateBedRequest(
    @NotNull UUID departmentId,
    @NotBlank @Size(max = 32) String wardCode,
    @NotBlank @Size(max = 32) String roomCode,
    @NotBlank @Size(max = 32) String bedCode,
    @NotBlank @Size(max = 32) String bedType
) {}

public record UpdateBedRequest(
    @NotBlank @Size(max = 32) String bedType,
    @NotNull BedStatus status,
    boolean active
) {}

public record AdmissionSearchQuery(
    UUID departmentId, UUID patientId, AdmissionStatus status,
    LocalDate from, LocalDate to, PageQuery page
) {}
public record BedSearchQuery(
    UUID departmentId, String wardCode, BedStatus status, PageQuery page
) {}

public record EmergencyOverrideRequest(
    @NotNull UUID overrideId, @NotNull UUID approvedBy,
    @NotBlank @Size(max = 32) String approverRole,
    @NotBlank @Size(max = 1000) String reason,
    @NotNull @PastOrPresent Instant approvedAt
) {}
public record AdmitRequest(
    @NotNull UUID admittedBy, EmergencyOverrideRequest emergencyOverride
) {}

public record CreateTreatmentEntryRequest(
    @NotNull TreatmentEntryType entryType,
    @NotBlank @Size(max = 10000) String content,
    @NotNull UUID authoredBy,
    @NotNull @PastOrPresent Instant recordedAt
) {}
public record CorrectTreatmentEntryRequest(
    @NotBlank @Size(max = 10000) String correctedContent,
    @NotNull UUID authoredBy,
    @NotNull @PastOrPresent Instant recordedAt
) {}

public record RegisterOrderReferenceRequest(
    @NotNull ClinicalOrderType orderType,
    @NotNull UUID externalOrderId,
    @NotNull ExternalOrderStatus status,
    @Size(max = 2000) String summary
) {}

public record MedicalDischargeRequest(
    @NotNull UUID summaryId,
    @NotBlank @Size(max = 4000) String diagnosisSummary,
    @NotBlank @Size(max = 10000) String treatmentSummary,
    @NotNull DischargeOutcome outcome,
    @NotBlank @Size(max = 4000) String followUpPlan,
    @NotNull UUID approvedBy,
    @NotNull @PastOrPresent Instant approvedAt
) {}

public record CloseAdmissionRequest(
    @NotNull UUID closedBy,
    CloseOverrideRequest override
) {}
public record CloseOverrideRequest(
    @NotNull UUID overrideId,
    @NotNull OverrideType type,
    @NotNull UUID approvedBy,
    @NotBlank @Size(max = 32) String approverRole,
    @NotBlank @Size(max = 1000) String reason,
    @NotNull @PastOrPresent Instant approvedAt
) {}
```

`AdmissionDTO` contains admission identity/state/timestamps, active bed projection, clearance ID,
settlement ID, discharge summary and external order references. It never embeds authoritative
Patient/Organization/Billing objects.

## 10. Consumer commands

```java
public record AdmissionRequestedCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID admissionRequestId, UUID recordId, UUID patientId, UUID departmentId,
    UUID requestedBy, String diagnosisSummary, AdmissionPriority priority,
    boolean emergency, Instant requestedAt
) {}

public record FinancialClearanceCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId, ClearancePurpose purpose,
    UUID admissionId, BigDecimal amount, String currency, String paymentMethod,
    Instant expiresAt, boolean emergencyOverride
) {}

public record SettlementCompletedCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID settlementId, UUID admissionId, UUID accountId,
    BigDecimal grossAmount, BigDecimal insuranceAmount, BigDecimal patientLiability,
    BigDecimal completedPayments, BigDecimal completedRefunds, BigDecimal balance,
    SettlementOutcome outcome, Instant completedAt
) {}

public record DepositTopupRequiredCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID accountId, UUID admissionId,
    BigDecimal currentBalance, BigDecimal requestedAmount, String reason
) {}

public sealed interface ExternalOrderFactCommand
    permits LabResultFactCommand, PrescriptionFilledFactCommand,
            SurgeryReadyFactCommand, SurgeryCompletedFactCommand, SurgeryCancelledFactCommand {
    UUID eventId();
    UUID externalOrderId();
    UUID admissionId();
    Instant occurredAt();
}

public record LabResultFactCommand(
    UUID eventId, UUID externalOrderId, UUID admissionId, UUID patientId,
    int resultVersion, String conclusion, Instant occurredAt
) implements ExternalOrderFactCommand {}

public record PrescriptionFilledFactCommand(
    UUID eventId, UUID externalOrderId, UUID admissionId, UUID patientId,
    Instant filledAt, Instant occurredAt
) implements ExternalOrderFactCommand {}

public record SurgeryReadyFactCommand(
    UUID eventId, UUID externalOrderId, UUID admissionId, UUID scheduleId,
    UUID readinessSnapshotId, Instant readyAt, Instant occurredAt
) implements ExternalOrderFactCommand {}

public record SurgeryCompletedFactCommand(
    UUID eventId, UUID externalOrderId, UUID admissionId, UUID resultId,
    String complicationsSummary, Instant completedAt, Instant occurredAt
) implements ExternalOrderFactCommand {}

public record SurgeryCancelledFactCommand(
    UUID eventId, UUID externalOrderId, UUID admissionId,
    String cancellationStage, String reason, Instant cancelledAt, Instant occurredAt
) implements ExternalOrderFactCommand {}

public record DepositSuggestion(
    @NotBlank String priceCode,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String reason
) {}
```

Every external-order variant carries `eventId`, exact `externalOrderId`, `admissionId` and occurred
time. Missing admission ID is a contract error, never a cue to search by patient.

## 11. Application algorithms

### Consume `admission.requested`

1. Validate envelope version and all required payload fields.
2. Atomically claim `eventId`.
3. Insert by unique `admissionRequestId`; concurrent redelivery returns the existing admission.
4. Create `REQUESTED`, append history, then move to `AWAITING_BED` in the same transaction.
5. Do not publish `admission.started`; no bed or financial guard exists yet.

Manual `POST /admissions` uses the same application path for recovery/admin entry and the same
producer-generated request ID.

### Assign or transfer bed

1. Lock admission and target bed; require admission before `MEDICALLY_DISCHARGED`.
2. Require target bed active and `AVAILABLE`; unique active indexes are the final concurrency guard.
3. Transfer locks old/new bed IDs in sorted UUID order, releases old assignment, marks old bed
   available, creates new assignment and marks new bed occupied in one transaction.
4. First active assignment appends one `admission.deposit.requested` outbox event using the
   configured suggestion policy.
5. Recalculate waiting state: valid clearance → `READY`; otherwise `AWAITING_DEPOSIT`.

### Consume deposit clearance

1. Require `version=1`, `purpose=ADMISSION_DEPOSIT`, `careEpisodeType=ADMISSION` and
   `careEpisodeId=admissionId`.
2. Claim event, lock admission and compare patient and admission IDs.
3. Save financial projection and clearance ID.
4. Active bed present → `READY`; no bed → `AWAITING_BED`.

### Admit

1. Lock admission and active assignment.
2. Normal path requires `READY` and a valid clearance.
3. Emergency path requires active bed and full override audit; persist override and preserve
   receivable semantics.
4. Set `ADMITTED`, `admittedAt=now`, append history and `admission.started` outbox event atomically.

Cancellation is allowed only before `ADMITTED`. It releases any active assignment, marks its bed
available, records actor/reason/time and moves the admission to `CANCELLED` in one transaction.

### Treatment and external order timeline

1. Treatment entries are allowed only in `ADMITTED`.
2. Correction verifies the original belongs to the admission and inserts a new `CORRECTION` row.
3. External facts claim event ID, require exact admission/order IDs and upsert only forward status
   according to each external contract. Duplicate or older event versions do not append notes twice.

### Medical discharge and close

1. Medical discharge locks `ADMITTED`, requires one summary and records approval.
2. Move to `MEDICALLY_DISCHARGED`; append `discharge.medically.approved`.
3. Settlement consumer stores Billing's immutable projection. It does not close automatically.
4. Close requires `MEDICALLY_DISCHARGED`, no active bed and either:
   - acceptable settlement (`PAID_IN_FULL`, `DEBT_APPROVED`, `WAIVED`); or
   - audited `DEBT_CLOSE|WAIVER_CLOSE` override.
5. Set `CLOSED` and append `admission.closed` atomically.

## 12. REST endpoints

| Method | Path | Request | Roles |
|---|---|---|---|
| POST | `/api/v1/inpatient/admissions` | `CreateAdmissionRequest` | ADMIN, DOCTOR |
| GET | `/api/v1/inpatient/admissions/{id}` | — | ADMIN, DOCTOR, NURSE, CASHIER |
| GET | `/api/v1/inpatient/admissions?departmentId&patientId&status&from&to&page&size` | — | ADMIN, MANAGER, DOCTOR, NURSE, CASHIER |
| PUT | `/api/v1/inpatient/admissions/{id}/bed` | `AssignBedRequest` | ADMIN, NURSE |
| PUT | `/api/v1/inpatient/admissions/{id}/bed/transfer` | `TransferBedRequest` | ADMIN, NURSE |
| PUT | `/api/v1/inpatient/admissions/{id}/bed/release` | `ReleaseBedRequest` | ADMIN, NURSE |
| POST | `/api/v1/inpatient/admissions/{id}/admit` | `AdmitRequest` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/inpatient/admissions/{id}/treatments` | `CreateTreatmentEntryRequest` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/inpatient/admissions/{id}/treatments/{entryId}/corrections` | `CorrectTreatmentEntryRequest` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/inpatient/admissions/{id}/order-references` | `RegisterOrderReferenceRequest` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/inpatient/admissions/{id}/medical-discharge` | `MedicalDischargeRequest` | ADMIN, DOCTOR |
| POST | `/api/v1/inpatient/admissions/{id}/close` | `CloseAdmissionRequest` | ADMIN, CASHIER |
| POST | `/api/v1/inpatient/admissions/{id}/cancel` | `CancelAdmissionRequest` | ADMIN, DOCTOR |
| POST | `/api/v1/inpatient/beds` | `CreateBedRequest` | ADMIN, MANAGER |
| PUT | `/api/v1/inpatient/beds/{id}` | `UpdateBedRequest` | ADMIN, MANAGER |
| GET | `/api/v1/inpatient/beds?departmentId&wardCode&status&page&size` | — | ADMIN, MANAGER, DOCTOR, NURSE |

All responses use `ApiResponse`; list endpoints use the shared page envelope. The implementation PR
creates `backend/inpatient-service/inpatient.http` with one authenticated request per endpoint.

## 13. Published events

All events use the standard envelope and `version=1`.

| Routing key | Required payload |
|---|---|
| `admission.deposit.requested` | `admissionId`, `patientId`, `departmentId`, `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`, `sourceType=ADMISSION_DEPOSIT`, `sourceId=admissionId`, `priceCode`, `suggestedAmount`, `reason` |
| `admission.started` | `admissionId`, `patientId`, `bedId`, `departmentId`, `admittedAt`, `emergency`, `emergencyOverrideId?` |
| `discharge.medically.approved` | `admissionId`, `patientId`, `summaryId`, `approvedBy`, `approvedAt` |
| `admission.closed` | `admissionId`, `patientId`, `settlementId?`, `approvedOverrideId?`, `closedAt` |

Future `surgery.requested` is emitted only after the shared Surgery fixture exists. Core V1 may
record a Surgery reference but does not invent Surgery payload fields outside the canonical contract.

## 14. Subscribed events

| Routing key | Action |
|---|---|
| `admission.requested` | create one admission by `admissionRequestId` |
| `financial.clearance.granted` | apply only `ADMISSION_DEPOSIT` for exact admission |
| `deposit.topup.required` | store immutable top-up request projection |
| `settlement.completed` | store exact Billing settlement projection |
| `lab.result.created` | update exact Lab reference when `careEpisodeType=ADMISSION` |
| `prescription.filled` | update exact prescription reference with explicit `admissionId` |
| `surgery.ready` | update exact Surgery reference |
| `surgery.completed` | update reference and append one treatment timeline entry |
| `surgery.cancelled` | mark exact reference cancelled and append one timeline entry |

Bindings for an event are enabled only when its canonical fixture can be deserialized and its owner
producer is ready. Unsupported or malformed payloads go through bounded retry to `inpatient.dlq`.

## 15. RabbitMQ topology

```text
exchange: mediflow.events (topic, durable)
queue: inpatient.q (durable)
DLX: mediflow.events.dlx
DLQ: inpatient.dlq
dead-letter routing key: inpatient.dead-letter
```

Producer writes aggregate and outbox in one transaction. Dispatcher uses publisher confirms and
marks `published_at` only after acknowledgement. Consumer claims inbox event and applies side
effects in one local transaction.

## 16. Identity and security

- Public requests arrive through Gateway; direct service ports are not used by frontend/mobile.
- Every endpoint has `@PreAuthorize`; default deny remains active.
- Internal lookups use short-lived `type=service`, `role=SYSTEM` tokens and propagate correlation ID.
- Patient absence, staff/department absence and upstream outage are distinct results.
- `sub` is account identity, never assumed to be patient/staff ID.
- Human access/refresh tokens are not forwarded as service credentials.

Admission referral already carries producer-validated IDs. A synchronous lookup is used only when
a command requires current eligibility (for example assigning an attending doctor); consumers do
not query other services merely to enrich an event.

## 17. Concurrency and idempotency

- Unique `admission_request_id` plus inbox claim protects referral redelivery/concurrency.
- Partial unique indexes protect active bed and active admission assignment.
- `@Version` protects admission/bed updates; transfer also uses pessimistic locks in sorted order.
- Discharge summary is unique per admission. Settlement events form immutable history; only the
  accepted final settlement ID is copied onto the admission.
- Outbox event IDs are stable across publisher retries.
- External status events cannot regress a completed/cancelled order.
- No command uses “find active admission by patient” as an identifier-selection mechanism.

## 18. Required tests

### Domain/application

| Rule | Required test |
|---|---|
| one referral creates one admission | `onAdmissionRequested_duplicateOrConcurrent_createsOnce` |
| paid deposit without bed is not admitted | `onDepositClearance_withoutBed_staysAwaitingBed` |
| bed without deposit waits | `assignBed_withoutClearance_movesAwaitingDeposit` |
| both guards make ready | `assignBed_withClearance_movesReady` |
| normal admit requires both guards | `admit_withoutClearance_rejects` |
| emergency override is audited | `admit_emergency_persistsOverrideAndReceivableFact` |
| a bed has one active assignment | `assignBed_concurrentAdmissions_oneWins` |
| transfer releases old first | `transferBed_valid_releasesOldAndAssignsNew` |
| treatment correction is append-only | `correctTreatment_keepsOriginalAndAddsCorrection` |
| external IDs are exact | `externalFact_wrongAdmission_rejects` |
| medical discharge does not close | `medicalDischarge_valid_staysMedicallyDischarged` |
| unpaid settlement cannot close | `close_additionalPaymentRequired_rejects` |
| close needs released bed | `close_activeBed_rejects` |
| duplicate settlement applies once | `onSettlement_duplicateEvent_appliesOnce` |

### Slices/integration

- Web tests cover validation, envelope and complete role matrix.
- PostgreSQL Testcontainers prove partial unique indexes, checks and lock behavior.
- RabbitMQ Testcontainers prove outbox publication, redelivery, retry and DLQ.
- Contract fixtures prove Clinical referral, Billing clearance/settlement and external order facts.
- Docker E2E covers referral → bed → deposit clearance → admit → treatment → medical discharge →
  settlement → bed release → close.

## 19. Delivery slices

Each slice is a focused commit/PR and leaves the module bootable:

1. **INPATIENT-01:** DDL, domain enums/models and domain tests.
2. **INPATIENT-02:** persistence adapters, repositories and concurrency tests.
3. **INPATIENT-03:** referral consumer, inbox/outbox and duplicate fixture test.
4. **INPATIENT-04:** bed CRUD, assignment/transfer/release and HTTP examples.
5. **INPATIENT-05:** deposit clearance consumer and guarded admit.
6. **INPATIENT-06:** append-only treatment entries and order references.
7. **INPATIENT-07:** medical discharge, settlement consumer and administrative close.
8. **INPATIENT-08:** external order consumers, contract fixtures and Docker E2E.

Do not combine Surgery internals or Billing ledger implementation into these slices. Missing producer
contracts create owner handoffs and keep the dependent binding disabled.

## 20. Definition of Done

- Exact DDL, domain rules, API DTOs, event fixtures and tests above are implemented.
- `mvn -pl backend/inpatient-service -am verify` passes with Testcontainers available.
- Gateway route handoff is complete and authorization/correlation tests pass.
- Shared fixtures pass on both producer and consumer before a contract is marked `IMPLEMENTED`.
- Emergency admit creates a fully audited override and never forges payment.
- Medical discharge and administrative close remain visibly separate.
- Duplicate/out-of-order events do not duplicate admission, timeline, charges or projections.
- No cross-service DB/JPA relationship, inferred identifier or direct frontend service-port call exists.
