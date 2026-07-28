# 03 — clinical-service (Khoa Khám bệnh)

**Module** `clinical-service` · **Package** `com.mediflow.clinical` · **Cổng** 8082 · **DB** `mediflow_clinical`
**Tiền tố** `/api/v1/appointments` **và** `/api/v1/records` — một bounded context, hai tài nguyên.

Toàn bộ quy trình khám ngoại trú: đặt lịch → khám → lập hồ sơ → chẩn đoán. Phụ thuộc
`organization-service` và `patient-service` (đều là gọi REST có chịu lỗi).

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE LICH_HEN (
    ma_lich_hen    UUID          PRIMARY KEY,
    ma_benh_nhan   UUID          NOT NULL,          -- tham chiếu patient-service, UUID trần
    ma_bac_si      UUID          NOT NULL,          -- tham chiếu organization-service NHAN_VIEN
    ma_khoa        UUID          NOT NULL,          -- tham chiếu organization-service KHOA
    ngay_hen       DATE          NOT NULL,
    gio_hen        TIME          NOT NULL,
    trang_thai     VARCHAR(20)   NOT NULL DEFAULT 'CHUA_DEN',
    ly_do_kham     TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_lich_hen_benh_nhan ON LICH_HEN (ma_benh_nhan);
CREATE INDEX idx_lich_hen_khoa_ngay ON LICH_HEN (ma_khoa, ngay_hen);
-- phục vụ BR-A2 (mỗi bệnh nhân chỉ 1 lịch hẹn chờ trong ngày)
CREATE INDEX idx_lich_hen_bn_ngay_tt ON LICH_HEN (ma_benh_nhan, ngay_hen, trang_thai);

CREATE TABLE HO_SO_BA (
    ma_ho_so       UUID          PRIMARY KEY,
    ma_benh_nhan   UUID          NOT NULL,
    ma_bac_si      UUID          NOT NULL,
    ma_khoa        UUID          NOT NULL,
    ngay_kham      DATE          NOT NULL,
    trieu_chung    TEXT,
    ma_lich_hen    UUID          REFERENCES LICH_HEN(ma_lich_hen),  -- khóa ngoại thật: cùng service
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_ho_so_benh_nhan ON HO_SO_BA (ma_benh_nhan);
CREATE INDEX idx_ho_so_khoa_ngay ON HO_SO_BA (ma_khoa, ngay_kham);

CREATE TABLE CHUAN_DOAN (
    ma_chuan_doan   UUID          PRIMARY KEY,
    ma_ho_so        UUID          NOT NULL REFERENCES HO_SO_BA(ma_ho_so) ON DELETE CASCADE,
    ten_chuan_doan  VARCHAR(255)  NOT NULL,
    mo_ta           TEXT,
    icd_code        VARCHAR(10),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_chuan_doan_ho_so ON CHUAN_DOAN (ma_ho_so);
```

## 2. Enum

```java
public enum TrangThaiLichHen { CHUA_DEN, DA_DEN, HUY }
```

## 3. Domain model

**`LichHen`** — `maLichHen`, `maBenhNhan`, `maBacSi`, `maKhoa`, `ngayHen`, `gioHen`, `trangThai`, `lyDoKham`, timestamps.

```java
public static final LocalTime GIO_MO_CUA  = LocalTime.of(7, 0);
public static final LocalTime GIO_DONG_CUA = LocalTime.of(17, 0);

public static LichHen taoMoi(UUID maBenhNhan, UUID maBacSi, UUID maKhoa,
                             LocalDate ngayHen, LocalTime gioHen, String lyDoKham);
public void capNhat(LocalDate ngayHen, LocalTime gioHen, String lyDoKham);
public void doiTrangThai(TrangThaiLichHen moi);
public void danhDauDaDen();      // dùng bởi luồng lập hồ sơ — xem BR-R4
public boolean dangCho();        // trangThai == CHUA_DEN
```

Bất biến trong `taoMoi`:

| Kiểm tra | Mã lỗi |
|----------|--------|
| `ngayHen` không trước hôm nay | `APPOINTMENT_PAST_DATE` |
| `gioHen` nằm trong `[07:00, 17:00]` | `APPOINTMENT_TIME_OUT_OF_HOURS` |
| các id đều không null | `APPOINTMENT_REF_REQUIRED` |

`doiTrangThai` chỉ cho phép các chuyển tiếp sau — còn lại ném `APPOINTMENT_INVALID_TRANSITION`:

```
CHUA_DEN → DA_DEN | HUY
DA_DEN   → (kết thúc)
HUY      → (kết thúc)
```

**`HoSoBenhAn`** — `maHoSo`, `maBenhNhan`, `maBacSi`, `maKhoa`, `ngayKham`, `trieuChung`,
`maLichHen` (cho phép null), `chuanDoans` (`List<ChuanDoan>`), timestamps.

```java
public static HoSoBenhAn taoMoi(UUID maBenhNhan, UUID maBacSi, UUID maKhoa, LocalDate ngayKham,
                                String trieuChung, UUID maLichHen, List<ChuanDoan> chuanDoanBanDau);
public void themChuanDoan(ChuanDoan cd);
public void capNhat(String trieuChung);
public List<ChuanDoan> getChuanDoans();   // không cho sửa
```

Bất biến: **ít nhất một chẩn đoán** (`RECORD_NO_DIAGNOSIS`) — kiểm trong `taoMoi`, và
`themChuanDoan` chỉ được thêm.

**`ChuanDoan`** — `maChuanDoan`, `tenChuanDoan` (không rỗng), `moTa`, `icdCode` (tùy chọn, `^[A-Z]\d{2}(\.\d{1,2})?$`).

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
public interface LichHenRepositoryPort {
    LichHen save(LichHen lh);
    Optional<LichHen> findById(UUID id);
    List<LichHen> findByBenhNhan(UUID maBenhNhan);
    PageResult<LichHen> search(UUID maKhoa, LocalDate ngayHen, PageQuery page);
    boolean existsPendingSameDay(UUID maBenhNhan, LocalDate ngayHen);   // BR-A2
}

public interface HoSoRepositoryPort {
    HoSoBenhAn save(HoSoBenhAn hs);
    Optional<HoSoBenhAn> findById(UUID id);
    List<HoSoBenhAn> findByBenhNhan(UUID maBenhNhan);
}

/** Đọc có chịu lỗi. Bản hiện thực trả rỗng/false khi fallback — không bao giờ ném lỗi Feign thô. */
public interface PatientLookupPort  { boolean exists(UUID maBenhNhan); }
public interface StaffLookupPort    { Optional<UUID> departmentOf(UUID maNhanVien); }

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
    AppointmentDTO changeStatus(UUID id, TrangThaiLichHen trangThai);
    AppointmentDTO getById(UUID id);
    List<AppointmentDTO> byPatient(UUID maBenhNhan);
    PageResult<AppointmentDTO> search(UUID maKhoa, LocalDate ngayHen, PageQuery page);
}
public interface ManageRecordUseCase {
    MedicalRecordDTO create(CreateRecordRequest r);
    MedicalRecordDTO update(UUID id, UpdateRecordRequest r);
    MedicalRecordDTO getById(UUID id);
    List<MedicalRecordDTO> byPatient(UUID maBenhNhan);
    DiagnosisDTO addDiagnosis(UUID recordId, AddDiagnosisRequest r);
}
public interface AttachExternalResultUseCase {          // do các event consumer gọi
    void attachLabResult(UUID maHoSo, UUID labId, String ketLuan);
    void attachPrescription(UUID maHoSo, UUID prescriptionId);
}
```

## 6. DTO

```java
public record CreateAppointmentRequest(
    @NotNull UUID maBenhNhan, @NotNull UUID maBacSi, @NotNull UUID maKhoa,
    @NotNull @FutureOrPresent LocalDate ngayHen,
    @NotNull LocalTime gioHen,
    @Size(max = 1000) String lyDoKham) {}

public record UpdateAppointmentRequest(
    @NotNull @FutureOrPresent LocalDate ngayHen, @NotNull LocalTime gioHen,
    @Size(max = 1000) String lyDoKham) {}

public record ChangeStatusRequest(@NotNull TrangThaiLichHen trangThai) {}

public record CreateRecordRequest(
    @NotNull UUID maBenhNhan, @NotNull UUID maBacSi, @NotNull UUID maKhoa,
    @NotNull @PastOrPresent LocalDate ngayKham,
    @Size(max = 4000) String trieuChung,
    UUID maLichHen,
    @NotEmpty @Valid List<AddDiagnosisRequest> chuanDoans) {}   // BR-R1 chặn ngay ở biên

public record AddDiagnosisRequest(
    @NotBlank @Size(max = 255) String tenChuanDoan,
    @Size(max = 2000) String moTa,
    @Pattern(regexp = "^[A-Z]\\d{2}(\\.\\d{1,2})?$") String icdCode) {}

public record AppointmentDTO(UUID maLichHen, UUID maBenhNhan, UUID maBacSi, UUID maKhoa,
                             LocalDate ngayHen, LocalTime gioHen, TrangThaiLichHen trangThai,
                             String lyDoKham, Instant createdAt, Instant updatedAt) {}

public record MedicalRecordDTO(UUID maHoSo, UUID maBenhNhan, UUID maBacSi, UUID maKhoa,
                               LocalDate ngayKham, String trieuChung, UUID maLichHen,
                               List<DiagnosisDTO> chuanDoans, Instant createdAt, Instant updatedAt) {}

public record DiagnosisDTO(UUID maChuanDoan, String tenChuanDoan, String moTa, String icdCode) {}
```

## 7. Thuật toán tầng application

**`createAppointment`**
1. `patientLookup.exists(maBenhNhan)` → false → `PATIENT_NOT_FOUND_REMOTE`
2. `staffLookup.departmentOf(maBacSi)` → rỗng → `DOCTOR_NOT_FOUND_REMOTE`; khác `maKhoa` → `DOCTOR_WRONG_DEPARTMENT` (BR-A4)
3. `existsPendingSameDay(maBenhNhan, ngayHen)` → true → `APPOINTMENT_DUPLICATE_PENDING` (BR-A2)
4. `LichHen.taoMoi(...)` — chặn BR-A1, BR-A3
5. lưu
6. publish `AppointmentCreatedEvent` **sau khi commit**

**`changeStatus`** — nạp hoặc 404 → `doiTrangThai` (tự kiểm chuyển tiếp) → lưu → publish
`AppointmentStatusChangedEvent`.

**`createRecord`** — phần quan trọng nhất:
1. `patientLookup.exists` → không thì `PATIENT_NOT_FOUND_REMOTE` (BR-R3)
2. `staffLookup.departmentOf(maBacSi)` phải bằng `maKhoa`
3. map `chuanDoans` sang domain `ChuanDoan`; `HoSoBenhAn.taoMoi(...)` chặn **BR-R1** (≥1 chẩn đoán)
4. **nếu `maLichHen != null`:** nạp nó hoặc `APPOINTMENT_NOT_FOUND`; kiểm đúng bệnh nhân; `lichHen.danhDauDaDen()`; `lichHenRepo.save(...)` — **trong cùng một method `@Transactional`** (BR-R4)
5. lưu hồ sơ
6. publish `MedicalRecordCreatedEvent` sau commit; nếu bước 4 chạy thì publish thêm `AppointmentStatusChangedEvent`

> Bước 4 chính là lý do lịch hẹn và hồ sơ nằm chung một service. Nó phải là **một transaction**,
> không bao giờ dùng event. Xem [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md).

**`addDiagnosis`** — nạp hồ sơ hoặc 404 → `themChuanDoan` → lưu → publish `DiagnosisAddedEvent`.

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/appointments/{id}` | — | `AppointmentDTO` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments/patient/{patientId}` | — | `List<AppointmentDTO>` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments?maKhoa&ngayHen&page&size` | — | `PageResult<AppointmentDTO>` | ADMIN, MANAGER, DOCTOR, NURSE |
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
| `appointment.created` | `{envelope, appointmentId, patientId, doctorId, maKhoa, ngayHen, gioHen}` |
| `appointment.status.changed` | `{envelope, appointmentId, status, patientId, maKhoa}` |
| `medicalrecord.created` | `{envelope, recordId, patientId, doctorId, maKhoa, diagnosis, ngayKham}` |
| `diagnosis.added` | `{envelope, recordId, diagnosisCode, diagnosisName}` |

**Subscribe** — queue `clinical.q`, DLX `mediflow.events.dlx`, DLQ `clinical.dlq`

| Routing key | Xử lý |
|-------------|-------|
| `lab.result.created` | `attachLabResult(recordId, labId, ketLuan)` — khử trùng lặp theo `eventId` |
| `prescription.filled` | `attachPrescription(recordId, prescriptionId)` — khử trùng lặp theo `eventId` |

> Gắn kết quả từ ngoài vào thì phải có chỗ chứa. Cách đơn giản nhất cho V1: nối thêm vào
> `HO_SO_BA.trieu_chung` là **sai** — thay vào đó thêm `V2__ket_qua_dinh_kem.sql` với bảng nhỏ
> `KET_QUA_DINH_KEM(ma_ho_so, loai, ma_tham_chieu, tom_tat, created_at)`, và làm cho lệnh insert
> idempotent theo `(ma_ho_so, loai, ma_tham_chieu)`.

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-A1 | Không đặt lịch cho ngày quá khứ | `createAppointment_pastDate_throwsBusinessRule` |
| BR-A2 | Mỗi bệnh nhân 1 lịch `CHUA_DEN` mỗi ngày | `createAppointment_secondPendingSameDay_throwsDuplicate` |
| BR-A3 | `gio_hen` trong 07:00–17:00 | `createAppointment_at18h_throwsBusinessRule` |
| BR-A4 | Bác sĩ tồn tại và thuộc `ma_khoa` | `createAppointment_doctorFromOtherDept_throwsBusinessRule` |
| BR-A5 | Chỉ cho phép chuyển trạng thái hợp lệ | `changeStatus_fromHuyToDaDen_throwsInvalidTransition` |
| BR-R1 | Hồ sơ phải có ≥1 chẩn đoán | `createRecord_noDiagnosis_throwsBusinessRule` |
| BR-R2 | Một hồ sơ hoạt động mỗi lần khám | `createRecord_sameAppointmentTwice_reusesOrRejects` |
| BR-R3 | Bệnh nhân phải tồn tại | `createRecord_unknownPatient_throwsBusinessRule` |
| BR-R4 | Lập hồ sơ từ lịch hẹn đặt `DA_DEN` **trong cùng transaction** | `createRecord_withAppointment_marksDaDenAtomically` |
| BR-R5 | Không dùng event để đặt `DA_DEN` | `createRecord_withAppointment_doesNotPublishToSelf` |
| BR-X1 | patient-service chết → suy giảm, không lan lỗi | `createAppointment_patientServiceDown_returns503NotHang` |
| BR-X2 | Consumer idempotent | `labResultConsumer_sameEventTwice_attachesOnce` |

## 11. Điểm dễ sai

- `HO_SO_BA.ma_lich_hen` là **khóa ngoại thật** — cả hai bảng nằm ở đây. Đừng mô hình hóa nó thành UUID trần.
- `CHUAN_DOAN` là con trong aggregate `HoSoBenhAn`: phía JPA dùng `@OneToMany(cascade = ALL, orphanRemoval = true)`, còn domain trả về danh sách không cho sửa.
- Cả hai Feign client đều cần một `@Component` fallback trả `false` / `Optional.empty()`. Ánh xạ trường hợp đó thành `UPSTREAM_UNAVAILABLE` (503) trong service — **tuyệt đối không** coi "service chết" là "bệnh nhân không tồn tại".
- `LocalTime` khi ra JSON cần `@JsonFormat(pattern = "HH:mm")` trên trường DTO, nếu không Jackson sẽ xuất ra một mảng.
