# 03 — clinical-service — Care & Finance V2 target

**Owner:** Vinh (`Dangvinh77` / `Harori`)

**Module:** `backend/clinical-service`

**Base paths:** `/api/v1/appointments`, `/api/v1/records`

**Status:** implementation-ready target; deploy additively beside the current compatibility flow

## 1. Sources and scope

This specification implements the Clinical part of:

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html);
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md);
- [`CONTRACT-INPATIENT-SURGERY-01`](../../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md);
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md);
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

The existing [Clinical spec](../03-clinical.md) remains the `CURRENT` contract during migration.
This file is the `TARGET` contract. It owns outpatient appointments, examination records,
diagnoses, examination financial gates, disposition and admission referrals. It stores only UUID
references to Patient, Organization, Lab, Pharmacy and Inpatient aggregates.

## 2. Migration boundary

1. Existing appointment/record APIs continue to work while V2 tables and fields are added.
2. `appointment.status.changed`, `medicalrecord.created` and `payment.completed` remain compatibility
   facts until the producer/consumer clearance fixture is green.
3. New V2 commands never infer an episode or target from `patientId`. An appointment episode uses
   `appointmentId`; a walk-in episode uses `recordId`.
4. Existing rows are `care_contract_version=0`. New or explicitly migrated rows are version `1`.
   Version 1 rows must satisfy all episode and clearance constraints below.
5. No destructive column/table rename is part of this rollout.

## 3. Target DDL — `V4__care_finance_v2.sql`

```sql
ALTER TABLE appointment
    ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN exam_clearance_id UUID,
    ADD COLUMN exam_clearance_at TIMESTAMPTZ,
    ADD COLUMN emergency_override_id UUID,
    ADD COLUMN exam_price_code VARCHAR(64),
    ADD COLUMN checked_in_at TIMESTAMPTZ,
    ADD COLUMN examination_started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE appointment
    ADD CONSTRAINT ck_appointment_contract_version
        CHECK (care_contract_version IN (0, 1)),
    ADD CONSTRAINT ck_appointment_v1_price
        CHECK (care_contract_version = 0 OR exam_price_code IS NOT NULL),
    ADD CONSTRAINT ck_appointment_exam_gate
        CHECK (care_contract_version = 0 OR status NOT IN ('READY_FOR_EXAM', 'IN_EXAM', 'COMPLETED')
               OR exam_clearance_id IS NOT NULL OR emergency_override_id IS NOT NULL);

ALTER TABLE medical_record
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN disposition VARCHAR(32),
    ADD COLUMN disposition_note TEXT,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE medical_record
    ADD CONSTRAINT ck_medical_record_status
        CHECK (status IN ('OPEN', 'COMPLETED')),
    ADD CONSTRAINT ck_medical_record_completion
        CHECK ((status = 'OPEN' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND disposition IS NOT NULL AND completed_at IS NOT NULL));

CREATE TABLE exam_clearance (
    clearance_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    appointment_id UUID,
    record_id UUID,
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT'),
    care_episode_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exam_clearance_target CHECK (appointment_id IS NOT NULL OR record_id IS NOT NULL)
);
CREATE INDEX idx_exam_clearance_appointment
    ON exam_clearance(appointment_id) WHERE appointment_id IS NOT NULL;
CREATE INDEX idx_exam_clearance_record
    ON exam_clearance(record_id) WHERE appointment_id IS NULL AND record_id IS NOT NULL;

CREATE TABLE clinical_emergency_override (
    override_id UUID PRIMARY KEY,
    appointment_id UUID REFERENCES appointment(appointment_id),
    record_id UUID REFERENCES medical_record(record_id),
    patient_id UUID NOT NULL,
    care_episode_id UUID NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinical_override_target CHECK (appointment_id IS NOT NULL OR record_id IS NOT NULL)
);

CREATE TABLE admission_referral (
    admission_request_id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record(record_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    diagnosis_summary TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    emergency BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_admission_referral_record UNIQUE(record_id)
);

CREATE TABLE clinical_outbox_event (
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
CREATE INDEX idx_clinical_outbox_unpublished
    ON clinical_outbox_event(occurred_at) WHERE published_at IS NULL;
```

Do not backfill version-1 episode IDs from patient or latest record. A separate reviewed migration
may promote a legacy row only when its authoritative appointment/record relationship is known.

## 4. Enums and state machines

```java
public enum AppointmentStatus {
    PENDING, ARRIVED, AWAITING_PAYMENT, READY_FOR_EXAM, IN_EXAM, COMPLETED, CANCELLED
}
public enum MedicalRecordStatus { OPEN, COMPLETED }
public enum RecordDisposition {
    OUTPATIENT_FOLLOW_UP, PRESCRIPTION, ADMISSION, TRANSFER, OTHER
}
public enum AdmissionPriority { ROUTINE, URGENT, EMERGENCY }
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
```

Appointment transitions:

```text
PENDING → ARRIVED → AWAITING_PAYMENT → READY_FOR_EXAM → IN_EXAM → COMPLETED
   └────────────────────────────────────────────────────────────→ CANCELLED
```

- Creating a record for an appointment changes `PENDING → ARRIVED` in the same local transaction.
- Opening the V2 exam gate changes `ARRIVED → AWAITING_PAYMENT`.
- Matching EXAM clearance changes `AWAITING_PAYMENT → READY_FOR_EXAM`.
- An audited emergency override may change `ARRIVED|AWAITING_PAYMENT → READY_FOR_EXAM`.
- Generic status endpoints cannot set `READY_FOR_EXAM`, `IN_EXAM` or `COMPLETED` directly.
- An appointment-backed record may be opened without diagnoses at examination start. At least one
  diagnosis is mandatory before `MedicalRecordStatus.COMPLETED`.

## 5. Error codes

| Code | HTTP | Condition |
|---|---:|---|
| `CLINICAL_APPOINTMENT_NOT_FOUND` | 404 | appointment absent |
| `CLINICAL_RECORD_NOT_FOUND` | 404 | record absent |
| `CLINICAL_INVALID_STATUS_TRANSITION` | 422 | transition outside the state machine |
| `CLINICAL_EXAM_CLEARANCE_REQUIRED` | 422 | start requested without clearance/override |
| `CLINICAL_CLEARANCE_TARGET_MISMATCH` | 422 | patient, episode or target differs |
| `CLINICAL_CLEARANCE_EXPIRED` | 422 | clearance expired before examination starts |
| `CLINICAL_OVERRIDE_INVALID` | 422 | incomplete emergency audit |
| `CLINICAL_DISPOSITION_REQUIRED` | 422 | complete command lacks disposition |
| `CLINICAL_DIAGNOSIS_REQUIRED` | 422 | record has no diagnosis at completion |
| `CLINICAL_ADMISSION_REFERRAL_CONFLICT` | 409 | referral already exists for record |
| `CLINICAL_UPSTREAM_UNAVAILABLE` | 503 | identity lookup timeout/5xx/malformed response |

## 6. Ports

```java
public interface ManageExamGateUseCase {
    AppointmentDTO checkIn(UUID appointmentId);
    AppointmentDTO startExam(UUID appointmentId, StartExamRequest request);
}

public interface CompleteMedicalRecordUseCase {
    MedicalRecordDTO complete(UUID recordId, CompleteRecordRequest request);
    AdmissionReferralDTO requestAdmission(UUID recordId, CreateAdmissionReferralRequest request);
}

public interface ReactToFinancialClearanceUseCase {
    void onFinancialClearance(FinancialClearanceCommand command);
}

public interface ExamClearanceRepositoryPort {
    boolean claimAndSave(ExamClearance clearance, UUID eventId);
    Optional<ExamClearance> findValidForAppointment(UUID appointmentId, Instant at);
    Optional<ExamClearance> findValidForRecord(UUID recordId, Instant at);
}

public interface AdmissionReferralRepositoryPort {
    boolean existsByRecordId(UUID recordId);
    AdmissionReferral save(AdmissionReferral referral);
}

public interface ExamPricePolicyPort {
    String resolvePriceCode(UUID departmentId);
}

public interface ClinicalOutboxPort {
    void append(DomainEventEnvelope<?> event);
}
```

Existing appointment, record, identity lookup and external-result ports remain as defined by the
current implementation. Application ports do not import Spring, JPA or RabbitMQ types.

## 7. DTOs

```java
public record EmergencyOverrideRequest(
    @NotNull UUID overrideId,
    @NotNull UUID approvedBy,
    @NotBlank @Size(max = 32) String approverRole,
    @NotBlank @Size(max = 1000) String reason,
    @NotNull @PastOrPresent Instant approvedAt
) {}

public record StartExamRequest(EmergencyOverrideRequest emergencyOverride) {}

public record CompleteRecordRequest(
    @NotNull RecordDisposition disposition,
    @Size(max = 2000) String dispositionNote
) {}

public record CreateAdmissionReferralRequest(
    @NotBlank @Size(max = 4000) String diagnosisSummary,
    @NotNull AdmissionPriority priority,
    boolean emergency
) {}

public record AdmissionReferralDTO(
    UUID admissionRequestId, UUID recordId, UUID patientId, UUID departmentId,
    UUID requestedBy, String diagnosisSummary, AdmissionPriority priority,
    boolean emergency, Instant requestedAt
) {}

public record FinancialClearanceCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId, ClearancePurpose purpose,
    UUID appointmentId, UUID recordId, BigDecimal amount, String currency,
    Instant expiresAt, boolean emergencyOverride
) {}
```

The clearance consumer rejects versions other than `1`, any purpose other than `EXAM`, and any
payload whose target, patient or episode does not match the local aggregate.

## 8. Application algorithms

### Check in and open payment gate

1. Lock appointment.
2. Require `PENDING`; change to `ARRIVED` and set `checkedInAt`.
3. Resolve and persist the configured exam `priceCode` for the department.
4. Append the `appointment.status.changed` charge fact with `sourceId=appointmentId`.
5. Change to `AWAITING_PAYMENT`; do not create a record or start examination yet.

### Consume EXAM clearance

1. Validate envelope/version/purpose and exactly one valid EXAM target.
2. Atomically claim `eventId`; duplicate delivery returns without side effects.
3. Lock target appointment or walk-in record.
4. Verify `patientId`, `careEpisodeType=OUTPATIENT_VISIT`, `careEpisodeId` and target ID.
5. Save clearance projection. If appointment is `AWAITING_PAYMENT`, move it to `READY_FOR_EXAM`.
6. A mismatch is a contract error and follows bounded retry/DLQ policy; it is not acknowledged as
   successful processing.

### Start examination

1. Lock appointment and require `ARRIVED|AWAITING_PAYMENT|READY_FOR_EXAM`.
2. Accept a non-expired matching clearance, or validate and persist the full emergency override.
3. Create/reuse exactly one medical record for the appointment and set `IN_EXAM` in the same
   transaction; an open record may have no diagnosis, but completion may not.
4. Append `medicalrecord.created` with `sourceId=appointmentId` so Billing deduplicates it against
   the check-in EXAM charge, then append the status event.
5. Emergency flow retains the override ID in the operational fact so Billing can open receivable.

### Complete record and request admission

1. Lock record and its appointment; require record `OPEN`, appointment `IN_EXAM`, and at least one diagnosis.
2. Persist disposition, note and `completedAt`; transition appointment to `COMPLETED`.
3. Append `medicalrecord.completed` exactly once.
4. For `ADMISSION`, require a separate admission-referral command. Clinical generates and persists
   `admissionRequestId`; `requestedBy` comes from the authenticated staff claim. Append
   `admission.requested`; never call Inpatient DB/API to create it.

## 9. REST endpoints

| Method | Path | Request | Roles |
|---|---|---|---|
| PUT | `/api/v1/appointments/{id}/check-in` | — | ADMIN, NURSE |
| PUT | `/api/v1/appointments/{id}/start-exam` | `StartExamRequest` | ADMIN, DOCTOR |
| PUT | `/api/v1/records/{id}/complete` | `CompleteRecordRequest` | ADMIN, DOCTOR |
| POST | `/api/v1/records/{id}/admission-referrals` | `CreateAdmissionReferralRequest` | ADMIN, DOCTOR |

Existing CRUD endpoints remain. Every endpoint returns the common `ApiResponse` envelope and must be
added to `backend/clinical-service/clinical.http` in the implementation PR. Every controller method
declares the listed roles with `@PreAuthorize`; default deny remains active.

## 10. Events

All events use the envelope from `docs/ai/16-care-finance-integration-contracts.md`; routing keys do
not carry a version suffix and `version=1` lives in the envelope.

| Event | Required payload |
|---|---|
| `medicalrecord.created` | `recordId`, `appointmentId?`, `patientId`, `doctorId`, `departmentId`, `careEpisodeType`, `careEpisodeId`, `sourceType=EXAM`, `sourceId=appointmentId` for appointments or `recordId` for walk-ins, `priceCode`, `examinationDate` |
| `medicalrecord.completed` | `recordId`, `appointmentId?`, `patientId`, `departmentId`, `disposition`, `admissionRequired`, `completedAt` |
| `admission.requested` | `admissionRequestId`, `recordId`, `patientId`, `departmentId`, `requestedBy`, `diagnosisSummary`, `priority`, `emergency`, `requestedAt` |
| `appointment.status.changed` | `appointmentId`, `recordId?`, `patientId`, `departmentId`, `oldStatus`, `newStatus`, `careEpisodeType=OUTPATIENT_VISIT`, `careEpisodeId=appointmentId`, `sourceType=EXAM`, `sourceId=appointmentId`, `priceCode`, `changedAt`, `emergencyOverrideId?` |

Subscribe to `financial.clearance.granted`, `lab.result.created` and `prescription.filled`. External
result events must carry exact `recordId`; selecting the latest record for a patient is forbidden.

## 11. Required tests

| Rule | Required test |
|---|---|
| checked in is not financial authorization | `startExam_arrivedWithoutClearance_rejects` |
| matching clearance unlocks exact appointment | `onClearance_matchingExam_movesReady` |
| EXAM clearance cannot unlock Lab/other episode | `onClearance_wrongPurposeOrEpisode_rejects` |
| duplicate clearance has one side effect | `onClearance_duplicateEvent_appliesOnce` |
| emergency path records full audit | `startExam_emergencyOverride_persistsAudit` |
| record completion needs diagnosis and disposition | `complete_missingDiagnosisOrDisposition_rejects` |
| admission referral is one per record | `requestAdmission_duplicateRecord_conflicts` |
| duplicate command does not publish twice | `complete_repeatedCommand_singleOutboxEvent` |
| patient absence differs from outage | `createRecord_patientLookupOutage_returns503` |
| each endpoint enforces roles | `clinicalV2Endpoints_roleMatrix` |

Persistence tests must prove the partial uniqueness and check constraints. Contract tests serialize
producer fixtures and deserialize them with Billing, Inpatient, Report and Notification projections.

## 12. Rollout and Definition of Done

1. Merge additive DDL and domain tests without changing existing behavior.
2. Add outbox and clearance consumer behind `mediflow.features.care-finance-v2=false`.
3. Billing publishes a shared clearance fixture; Clinical contract tests consume it.
4. Enable the gate in Docker E2E: login → check-in → fee → clearance → start exam → complete.
5. Enable `admission.requested` only after the Inpatient consumer fixture passes.
6. Retire compatibility behavior only in a separate PR with migration and rollback evidence.

Done means state transitions, role tests, duplicate delivery, target mismatch, upstream failure,
outbox publication and the system acceptance tests all pass without cross-service DB access.
