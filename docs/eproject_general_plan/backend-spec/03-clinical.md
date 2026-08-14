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
}

public interface MedicalRecordRepositoryPort {
    MedicalRecord save(MedicalRecord mr);
    Optional<MedicalRecord> findById(UUID id);
    List<MedicalRecord> findByPatient(UUID patientId);
}

/** Đọc có chịu lỗi. Bản hiện thực trả rỗng/false khi fallback — không bao giờ ném lỗi Feign thô. */
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
> không bao giờ dùng event. Xem [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md).

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
| `appointment.status.changed` | `{envelope, appointmentId, status, patientId, departmentId}` |
| `medicalrecord.created` | `{envelope, recordId, patientId, doctorId, departmentId, diagnosis, examinationDate}` |
| `diagnosis.added` | `{envelope, recordId, diagnosisCode, diagnosisName}` |

**Subscribe** — queue `clinical.q`, DLX `mediflow.events.dlx`, DLQ `clinical.dlq`

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
- Cả hai Feign client đều cần một `@Component` fallback trả `false` / `Optional.empty()`. Ánh xạ trường hợp đó thành `UPSTREAM_UNAVAILABLE` (503) trong service — **tuyệt đối không** coi "service chết" là "bệnh nhân không tồn tại".
- `LocalTime` khi ra JSON cần `@JsonFormat(pattern = "HH:mm")` trên trường DTO, nếu không Jackson sẽ xuất ra một mảng.
