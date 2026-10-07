# 03 — clinical-service (Khoa Khám bệnh)

**Module** `clinical-service` · **Package** `com.mediflow.clinical` · **Cổng** 8082 · **DB** `mediflow_clinical`
**Tiền tố** `/api/v1/appointments` **và** `/api/v1/records` — một bounded context, hai tài nguyên.

Toàn bộ quy trình khám ngoại trú: đặt lịch → khám → lập hồ sơ → chẩn đoán. Phụ thuộc
`organization-service` và `patient-service` (đều là gọi REST có chịu lỗi).

> Quy ước đặt tên chuẩn toàn hệ thống: bảng/column English snake_case, field Java/JSON English camelCase,
> enum English UPPER_SNAKE. Prose (mô tả) vẫn tiếng Việt.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE APPOINTMENT (
    appointment_id    UUID          PRIMARY KEY,
    patient_id        UUID          NOT NULL,          -- tham chiếu patient-service, UUID trần
    doctor_id         UUID          NOT NULL,          -- tham chiếu organization-service STAFF
    department_id     UUID          NOT NULL,          -- tham chiếu organization-service DEPARTMENT
    appointment_date  DATE          NOT NULL,
    appointment_time  TIME          NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reason            TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);
CREATE INDEX idx_appointment_patient ON APPOINTMENT (patient_id);
CREATE INDEX idx_appointment_department_date ON APPOINTMENT (department_id, appointment_date);
-- phục vụ BR-A2 (mỗi bệnh nhân chỉ 1 lịch hẹn chờ trong ngày)
CREATE INDEX idx_appointment_patient_date_status ON APPOINTMENT (patient_id, appointment_date, status);

CREATE TABLE MEDICAL_RECORD (
    record_id         UUID          PRIMARY KEY,
    patient_id        UUID          NOT NULL,
    doctor_id         UUID          NOT NULL,
    department_id     UUID          NOT NULL,
    examination_date  DATE          NOT NULL,
    symptoms          TEXT,
    appointment_id    UUID          REFERENCES APPOINTMENT(appointment_id),  -- khóa ngoại thật: cùng service
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);
CREATE INDEX idx_medical_record_patient ON MEDICAL_RECORD (patient_id);
CREATE INDEX idx_medical_record_department_date ON MEDICAL_RECORD (department_id, examination_date);

CREATE TABLE DIAGNOSIS (
    diagnosis_id    UUID          PRIMARY KEY,
    record_id       UUID          NOT NULL REFERENCES MEDICAL_RECORD(record_id) ON DELETE CASCADE,
    diagnosis_name  VARCHAR(255)  NOT NULL,
    description     TEXT,
    icd_code        VARCHAR(10),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_diagnosis_record ON DIAGNOSIS (record_id);
```

## 2. Enum

```java
public enum AppointmentStatus { PENDING, ARRIVED, CANCELLED }
```

## 3. Domain model

**`Appointment`** — `appointmentId`, `patientId`, `doctorId`, `departmentId`, `appointmentDate`, `appointmentTime`, `status`, `reason`, timestamps.

```java
public static final LocalTime OPENING_TIME = LocalTime.of(7, 0);
public static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);

public static Appointment create(UUID patientId, UUID doctorId, UUID departmentId,
                                 LocalDate appointmentDate, LocalTime appointmentTime, String reason);
public void update(LocalDate appointmentDate, LocalTime appointmentTime, String reason);
public void changeStatus(AppointmentStatus next);
public void markArrived();   // dùng bởi luồng lập hồ sơ — xem BR-R4
public boolean isPending();  // status == PENDING
```

Bất biến trong `create`:

| Kiểm tra | Mã lỗi |
|----------|--------|
| `appointmentDate` không trước hôm nay | `APPOINTMENT_PAST_DATE` |
| `appointmentTime` nằm trong `[07:00, 17:00]` | `APPOINTMENT_TIME_OUT_OF_HOURS` |
| các id đều không null | `APPOINTMENT_REF_REQUIRED` |

`changeStatus` chỉ cho phép các chuyển tiếp sau — còn lại ném `APPOINTMENT_INVALID_TRANSITION`:

```
PENDING   → ARRIVED | CANCELLED
ARRIVED   → (kết thúc)
CANCELLED → (kết thúc)
```

**`MedicalRecord`** — `recordId`, `patientId`, `doctorId`, `departmentId`, `examinationDate`,
`symptoms`, `appointmentId` (cho phép null), `diagnoses` (`List<Diagnosis>`), timestamps.

```java
public static MedicalRecord create(UUID patientId, UUID doctorId, UUID departmentId, LocalDate examinationDate,
                                   String symptoms, UUID appointmentId, List<Diagnosis> initialDiagnoses);
public void addDiagnosis(Diagnosis d);
public void update(String symptoms);
public List<Diagnosis> getDiagnoses();   // không cho sửa
```

Bất biến: **ít nhất một chẩn đoán** (`RECORD_NO_DIAGNOSIS`) — kiểm trong `create`, và
`addDiagnosis` chỉ được thêm.

**`Diagnosis`** — `diagnosisId`, `diagnosisName` (không rỗng), `description`, `icdCode` (tùy chọn, `^[A-Z]\d{2}(\.\d{1,2})?$`).

## 4. Mã lỗi

| Mã | HTTP |
|----|------|
| `APPOINTMENT_NOT_FOUND`, `RECORD_NOT_FOUND` | 404 |
| `APPOINTMENT_PAST_DATE`, `APPOINTMENT_TIME_OUT_OF_HOURS`, `APPOINTMENT_INVALID_TRANSITION`, `APPOINTMENT_REF_REQUIRED` | 422 |
| `APPOINTMENT_DUPLICATE_PENDING` | 409 |
| `RECORD_NO_DIAGNOSIS`, `DIAGNOSIS_ICD_INVALID` | 422 |
| `PATIENT_NOT_FOUND_REMOTE`, `DOCTOR_NOT_FOUND_REMOTE`, `DOCTOR_WRONG_DEPARTMENT` | 422 |
| `UPSTREAM_UNAVAILABLE` | 503 — fallback bị kích hoạt; nhớ viết handler cho nó |

## 5. Port

```java
// out
public interface AppointmentRepositoryPort {
    Appointment save(Appointment a);
    Optional<Appointment> findById(UUID id);
    List<Appointment> findByPatient(UUID patientId);
    PageResult<Appointment> search(UUID departmentId, LocalDate appointmentDate, PageQuery page);
    boolean existsPendingSameDay(UUID patientId, LocalDate appointmentDate);   // BR-A2
    boolean existsPendingSameDayExcludingId(UUID patientId, LocalDate appointmentDate, UUID excludedId);
}

public interface MedicalRecordRepositoryPort {
    MedicalRecord save(MedicalRecord mr);
    Optional<MedicalRecord> findById(UUID id);
    List<MedicalRecord> findByPatient(UUID patientId);
    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId); // BR-R2
}

/** Chỉ trả rỗng/false khi xác nhận không tồn tại/không đủ điều kiện; lỗi hạ tầng ném UpstreamUnavailableException, không ném lỗi Feign thô. */
public interface PatientLookupPort  { boolean exists(UUID patientId); }
public interface StaffLookupPort    { Optional<UUID> departmentOf(UUID staffId); }

public interface ClinicalEventPublisherPort {
    void publishAppointmentCreated(AppointmentCreatedEvent e);
    void publishAppointmentStatusChanged(AppointmentStatusChangedEvent e);
    void publishMedicalRecordCreated(MedicalRecordCreatedEvent e);
    void publishDiagnosisAdded(DiagnosisAddedEvent e);
}

// in
public interface ManageAppointmentUseCase {
    AppointmentDTO create(CreateAppointmentRequest r);
    AppointmentDTO update(UUID id, UpdateAppointmentRequest r);
    AppointmentDTO changeStatus(UUID id, AppointmentStatus status);
    AppointmentDTO getById(UUID id);
    List<AppointmentDTO> byPatient(UUID patientId);
    PageResult<AppointmentDTO> search(UUID departmentId, LocalDate appointmentDate, PageQuery page);
}
public interface ManageRecordUseCase {
    MedicalRecordDTO create(CreateRecordRequest r);
    MedicalRecordDTO update(UUID id, UpdateRecordRequest r);
    MedicalRecordDTO getById(UUID id);
    List<MedicalRecordDTO> byPatient(UUID patientId);
    DiagnosisDTO addDiagnosis(UUID recordId, AddDiagnosisRequest r);
}
public interface AttachExternalResultUseCase {          // do các event consumer gọi
    void attachLabResult(UUID recordId, UUID labTestId, String conclusion);
    void attachPrescription(UUID recordId, UUID prescriptionId);
}
```

## 6. DTO

```java
public record CreateAppointmentRequest(
    @NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID departmentId,
    @NotNull @FutureOrPresent LocalDate appointmentDate,
    @NotNull LocalTime appointmentTime,
    @Size(max = 1000) String reason) {}

public record UpdateAppointmentRequest(
    @NotNull @FutureOrPresent LocalDate appointmentDate, @NotNull LocalTime appointmentTime,
    @Size(max = 1000) String reason) {}

public record ChangeStatusRequest(@NotNull AppointmentStatus status) {}

public record CreateRecordRequest(
    @NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID departmentId,
    @NotNull @PastOrPresent LocalDate examinationDate,
    @Size(max = 4000) String symptoms,
    UUID appointmentId,
    @NotEmpty @Valid List<AddDiagnosisRequest> diagnoses) {}   // BR-R1 chặn ngay ở biên

public record AddDiagnosisRequest(
    @NotBlank @Size(max = 255) String diagnosisName,
    @Size(max = 2000) String description,
    @Pattern(regexp = "^[A-Z]\\d{2}(\\.\\d{1,2})?$") String icdCode) {}

public record AppointmentDTO(UUID appointmentId, UUID patientId, UUID doctorId, UUID departmentId,
                             LocalDate appointmentDate, LocalTime appointmentTime, AppointmentStatus status,
                             String reason, Instant createdAt, Instant updatedAt) {}

public record MedicalRecordDTO(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
                               LocalDate examinationDate, String symptoms, UUID appointmentId,
                               List<DiagnosisDTO> diagnoses, Instant createdAt, Instant updatedAt) {}

public record DiagnosisDTO(UUID diagnosisId, String diagnosisName, String description, String icdCode) {}
```

## 7. Thuật toán tầng application

**`createAppointment`**
1. `patientLookup.exists(patientId)` → false → `PATIENT_NOT_FOUND_REMOTE`
2. `staffLookup.departmentOf(doctorId)` → rỗng → `DOCTOR_NOT_FOUND_REMOTE`; khác `departmentId` → `DOCTOR_WRONG_DEPARTMENT` (BR-A4)
3. `existsPendingSameDay(patientId, appointmentDate)` → true → `APPOINTMENT_DUPLICATE_PENDING` (BR-A2)
4. `Appointment.create(...)` — chặn BR-A1, BR-A3
5. lưu
6. publish `AppointmentCreatedEvent` **sau khi commit**

**`changeStatus`** — nạp hoặc 404 → `changeStatus` (tự kiểm chuyển tiếp) → lưu → publish
`AppointmentStatusChangedEvent`.

**`createRecord`** — phần quan trọng nhất:
1. `patientLookup.exists` → không thì `PATIENT_NOT_FOUND_REMOTE` (BR-R3)
2. `staffLookup.departmentOf(doctorId)` phải bằng `departmentId`
3. map `diagnoses` sang domain `Diagnosis`; `MedicalRecord.create(...)` chặn **BR-R1** (≥1 chẩn đoán)
4. **nếu `appointmentId != null`:** nạp nó hoặc `APPOINTMENT_NOT_FOUND`; kiểm đúng bệnh nhân; `appointment.markArrived()`; `appointmentRepo.save(...)` — **trong cùng một method `@Transactional`** (BR-R4)
5. lưu hồ sơ
6. publish `MedicalRecordCreatedEvent` sau commit; nếu bước 4 chạy thì publish thêm `AppointmentStatusChangedEvent`

> Bước 4 chính là lý do lịch hẹn và hồ sơ nằm chung một service. Nó phải là **một transaction**,
> không bao giờ dùng event. Xem [`docs/ai/06-events-rabbitmq.md`](../../ai/06-events-rabbitmq.md).

**`addDiagnosis`** — nạp hồ sơ hoặc 404 → `addDiagnosis` → lưu → publish `DiagnosisAddedEvent`.

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/appointments/{id}` | — | `AppointmentDTO` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments/patient/{patientId}` | — | `List<AppointmentDTO>` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments?departmentId&appointmentDate&page&size` | — | `PageResult<AppointmentDTO>` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/appointments` | `CreateAppointmentRequest` | 201 | ADMIN, NURSE |
| PUT | `/api/v1/appointments/{id}` | `UpdateAppointmentRequest` | 200 | ADMIN, DOCTOR, NURSE |
| PUT | `/api/v1/appointments/{id}/status` | `ChangeStatusRequest` | 200 | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/records/{id}` | — | `MedicalRecordDTO` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/records/patient/{patientId}` | — | `List<MedicalRecordDTO>` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/records` | `CreateRecordRequest` | 201 | ADMIN, DOCTOR |
| PUT | `/api/v1/records/{id}` | `UpdateRecordRequest` | 200 | ADMIN, DOCTOR |
| POST | `/api/v1/records/{id}/diagnoses` | `AddDiagnosisRequest` | 201 `DiagnosisDTO` | ADMIN, DOCTOR |

Hai controller trong `infrastructure/web/`: `AppointmentController`, `MedicalRecordController`.

## 9. Event

**Publish**

| Routing key | Payload |
|-------------|---------|
| `appointment.created` | `{envelope, appointmentId, patientId, doctorId, departmentId, appointmentDate, appointmentTime}` |
| `appointment.status.changed` | `{envelope, appointmentId, recordId, status, patientId, departmentId}` |
| `medicalrecord.created` | `{envelope, recordId, patientId, doctorId, departmentId, diagnosis, examinationDate}` |
| `diagnosis.added` | `{envelope, recordId, diagnosisCode, diagnosisName}` |

**Subscribe** — queue `clinical.q`, DLX `mediflow.events.dlx`, DLQ `clinical.dlq`

`recordId` của `appointment.status.changed` nullable: có giá trị khi tạo hồ sơ đồng thời đặt
`ARRIVED`, chưa có khi chỉ đổi trạng thái lịch hẹn. Billing phải chờ `medicalrecord.created`
nếu chưa có mã hồ sơ; không dùng `appointmentId` thay `recordId`. Hai event phải cùng tham chiếu
hồ sơ để tránh thu phí khám hai lần. Contract canonical nằm trong
[`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md) và
[`06-events-rabbitmq.md`](../../ai/06-events-rabbitmq.md).

| Routing key | Xử lý |
|-------------|-------|
| `lab.result.created` | `attachLabResult(recordId, labTestId, conclusion)` — khử trùng lặp theo `eventId` |
| `prescription.filled` | `attachPrescription(recordId, prescriptionId)` — khử trùng lặp theo `eventId` |

> Gắn kết quả từ ngoài vào thì phải có chỗ chứa. Cách đơn giản nhất cho V1: nối thêm vào
> `MEDICAL_RECORD.symptoms` là **sai** — thay vào đó thêm `V2__attached_result.sql` với bảng nhỏ
> `ATTACHED_RESULT(record_id, type, reference_id, summary, created_at)`, và làm cho lệnh insert
> idempotent theo `(record_id, type, reference_id)`.

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-A1 | Không đặt lịch cho ngày quá khứ | `createAppointment_pastDate_throwsBusinessRule` |
| BR-A2 | Mỗi bệnh nhân 1 lịch `PENDING` mỗi ngày | `createAppointment_secondPendingSameDay_throwsDuplicate` |
| BR-A3 | `appointment_time` trong 07:00–17:00 | `createAppointment_at18h_throwsBusinessRule` |
| BR-A4 | Bác sĩ tồn tại và thuộc `department_id` | `createAppointment_doctorFromOtherDept_throwsBusinessRule` |
| BR-A5 | Chỉ cho phép chuyển trạng thái hợp lệ | `changeStatus_fromCancelledToArrived_throwsInvalidTransition` |
| BR-R1 | Hồ sơ phải có ≥1 chẩn đoán | `createRecord_noDiagnosis_throwsBusinessRule` |
| BR-R2 | Một hồ sơ hoạt động mỗi lần khám | `createRecord_sameAppointmentTwice_reusesOrRejects` |
| BR-R3 | Bệnh nhân phải tồn tại | `createRecord_unknownPatient_throwsBusinessRule` |
| BR-R4 | Lập hồ sơ từ lịch hẹn đặt `ARRIVED` **trong cùng transaction** | `createRecord_withAppointment_marksArrivedAtomically` |
| BR-R5 | Không dùng event để đặt `ARRIVED` | `createRecord_withAppointment_doesNotPublishToSelf` |
| BR-X1 | patient-service chết → suy giảm, không lan lỗi | `createAppointment_patientServiceDown_returns503NotHang` |
| BR-X2 | Consumer idempotent | `labResultConsumer_sameEventTwice_attachesOnce` |

## 11. Điểm dễ sai

- `MEDICAL_RECORD.appointment_id` là **khóa ngoại thật** — cả hai bảng nằm ở đây. Đừng mô hình hóa nó thành UUID trần.
- `DIAGNOSIS` là con trong aggregate `MedicalRecord`: phía JPA dùng `@OneToMany(cascade = ALL, orphanRemoval = true)`, còn domain trả về danh sách không cho sửa.
- Cả hai Feign client đều cần fallback chuyển timeout/circuit-open/5xx thành `UpstreamUnavailableException` (`UPSTREAM_UNAVAILABLE`, HTTP 503). Chỉ một phản hồi xác nhận không tồn tại mới trả `false` / `Optional.empty()`; **tuyệt đối không** coi "service chết" là "bệnh nhân không tồn tại".
- `LocalTime` khi ra JSON cần `@JsonFormat(pattern = "HH:mm")` trên trường DTO, nếu không Jackson sẽ xuất ra một mảng.
## Care-Finance V2 — additive Clinical contract

**Rollout status**

| Layer | Status | Meaning |
|---|---|---|
| Compatibility API and events | COMPATIBILITY_LIVE | Existing V1 behavior remains supported. |
| Additive schema, commands, consumers and tests | IMPLEMENTED | Source exists in the owned service and is covered locally. |
| Runtime activation | FEATURE_GATED | Care-Finance messaging remains disabled by configuration. |
| Distributed workflow | EXTERNAL_BLOCKED | Billing must issue and publish exact EXAM financial clearance, then the broker-backed Docker slice must pass. |

The sections below are part of this canonical specification. They must be deployed additively. Do
not remove compatibility endpoints or consumers, infer cross-service identifiers, or enable the
feature before the named contract fixtures and Docker acceptance flow pass.
### 2. Migration boundary

1. Existing appointment/record APIs continue to work while V2 tables and fields are added.
2. `appointment.status.changed`, `medicalrecord.created` and `payment.completed` remain compatibility
   facts until the producer/consumer clearance fixture is green.
3. New V2 commands never infer an episode or target from `patientId`. An appointment episode uses
   `appointmentId`; a walk-in episode uses `recordId`.
4. Existing rows are `care_contract_version=0`. New or explicitly migrated rows are version `1`.
   Version 1 rows must satisfy all episode and clearance constraints below.
5. No destructive column/table rename is part of this rollout.

### 3. Target DDL — `V4__care_finance_v2.sql`

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

### 4. Enums and state machines

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

### 5. Error codes

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

### 6. Ports

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

### 7. DTOs

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

### 8. Application algorithms

#### Check in and open payment gate

1. Lock appointment.
2. Require `PENDING`; change to `ARRIVED` and set `checkedInAt`.
3. Resolve and persist the configured exam `priceCode` for the department.
4. Append the `appointment.status.changed` charge fact with `sourceId=appointmentId`.
5. Change to `AWAITING_PAYMENT`; do not create a record or start examination yet.

#### Consume EXAM clearance

1. Validate envelope/version/purpose and exactly one valid EXAM target.
2. Atomically claim `eventId`; duplicate delivery returns without side effects.
3. Lock target appointment or walk-in record.
4. Verify `patientId`, `careEpisodeType=OUTPATIENT_VISIT`, `careEpisodeId` and target ID.
5. Save clearance projection. If appointment is `AWAITING_PAYMENT`, move it to `READY_FOR_EXAM`.
6. A mismatch is a contract error and follows bounded retry/DLQ policy; it is not acknowledged as
   successful processing.

#### Start examination

1. Lock appointment and require `ARRIVED|AWAITING_PAYMENT|READY_FOR_EXAM`.
2. Accept a non-expired matching clearance, or validate and persist the full emergency override.
3. Create/reuse exactly one medical record for the appointment and set `IN_EXAM` in the same
   transaction; an open record may have no diagnosis, but completion may not.
4. Append `medicalrecord.created` with `sourceId=appointmentId` so Billing deduplicates it against
   the check-in EXAM charge, then append the status event.
5. Emergency flow retains the override ID in the operational fact so Billing can open receivable.

#### Complete record and request admission

1. Lock record and its appointment; require record `OPEN`, appointment `IN_EXAM`, and at least one diagnosis.
2. Persist disposition, note and `completedAt`; transition appointment to `COMPLETED`.
3. Append `medicalrecord.completed` exactly once.
4. For `ADMISSION`, require a separate admission-referral command. Clinical generates and persists
   `admissionRequestId`; `requestedBy` comes from the authenticated staff claim. Append
   `admission.requested`; never call Inpatient DB/API to create it.

### 9. REST endpoints

| Method | Path | Request | Roles |
|---|---|---|---|
| PUT | `/api/v1/appointments/{id}/check-in` | — | ADMIN, NURSE |
| PUT | `/api/v1/appointments/{id}/start-exam` | `StartExamRequest` | ADMIN, DOCTOR |
| PUT | `/api/v1/records/{id}/complete` | `CompleteRecordRequest` | ADMIN, DOCTOR |
| POST | `/api/v1/records/{id}/admission-referrals` | `CreateAdmissionReferralRequest` | ADMIN, DOCTOR |

Existing CRUD endpoints remain. Every endpoint returns the common `ApiResponse` envelope and must be
added to `backend/clinical-service/clinical.http` in the implementation PR. Every controller method
declares the listed roles with `@PreAuthorize`; default deny remains active.

### 10. Events

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

### 11. Required tests

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

### 12. Rollout and Definition of Done

1. Merge additive DDL and domain tests without changing existing behavior.
2. Add outbox and clearance consumer behind `mediflow.features.care-finance-v2=false`.
3. Billing publishes a shared clearance fixture; Clinical contract tests consume it.
4. Enable the gate in Docker E2E: login → check-in → fee → clearance → start exam → complete.
5. Enable `admission.requested` only after the Inpatient consumer fixture passes.
6. Retire compatibility behavior only in a separate PR with migration and rollback evidence.

Done means state transitions, role tests, duplicate delivery, target mismatch, upstream failure,
outbox publication and the system acceptance tests all pass without cross-service DB access.
