# 02 — patient-service

**Module** `patient-service` · **Package** `com.mediflow.patient` · **Cổng** 8081 · **DB** `mediflow_patient` · **Tiền tố** `/api/v1/patients`

Hồ sơ bệnh nhân gốc. Sở hữu thông tin nhân khẩu và số BHYT. Gần như mọi service khác đều đọc nó;
nó không sở hữu gì về lịch hẹn, hồ sơ khám, xét nghiệm, thuốc hay tiền.

> ✅ **Module này đã được hiện thực đầy đủ theo spec này** và là **reference implementation** của cả
> dự án. Khi xây service khác mà thấy spec chưa rõ chỗ nào, hãy mở `patient-service/` ra xem nó
> làm thế nào. 30 test đang xanh; quy tắc phụ thuộc đã được kiểm chứng.

## 1. Lược đồ — `V1__init_benh_nhan.sql` (đã tồn tại)

```sql
CREATE TABLE BENH_NHAN (
    ma_benh_nhan   UUID          PRIMARY KEY,
    ho_ten         VARCHAR(100)  NOT NULL,
    ngay_sinh      DATE          NOT NULL,
    gioi_tinh      VARCHAR(1),
    so_cmnd        VARCHAR(20)   NOT NULL,
    dia_chi        VARCHAR(255),
    so_dien_thoai  VARCHAR(15),
    email          VARCHAR(100),
    bhyt_so        VARCHAR(20),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_benh_nhan_so_cmnd UNIQUE (so_cmnd)
);
CREATE INDEX idx_benh_nhan_ho_ten ON BENH_NHAN (ho_ten);
```

## 2. Enum

```java
public enum GioiTinh { M, F }
```

## 3. Domain model — `Patient`

Các trường: `maBenhNhan`, `hoTen`, `ngaySinh`, `gioiTinh`, `soCmnd` (**bất biến**), `diaChi`,
`soDienThoai`, `email`, `bhytSo`, `createdAt`, `updatedAt`.

```java
public static Patient taoMoi(String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh, String soCmnd,
                             String diaChi, String soDienThoai, String email, String bhytSo);
public static Patient khoiPhuc(UUID id, ..., Instant createdAt, Instant updatedAt);
/** soCmnd không phải tham số — nó không thể thay đổi. Trường tùy chọn nếu null thì giữ giá trị cũ. */
public void capNhat(String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh,
                    String diaChi, String soDienThoai, String email, String bhytSo);
public int tuoi();   // suy ra từ ngaySinh, tiện cho giao diện
```

Bất biến được kiểm trong `taoMoi` và `capNhat`:

| Kiểm tra | Mã lỗi |
|----------|--------|
| `hoTen` không rỗng, ≤100 | `PATIENT_HOTEN_REQUIRED` |
| `ngaySinh` không null và **không ở tương lai** | `PATIENT_NGAYSINH_FUTURE` |
| `gioiTinh` không null | `PATIENT_GIOITINH_REQUIRED` |
| `soCmnd` không rỗng, ≤20 (chỉ lúc tạo) | `PATIENT_SOCMND_REQUIRED` |
| `email` nếu có phải hợp lệ | `PATIENT_EMAIL_INVALID` |
| `soDienThoai` nếu có phải khớp `\d{10,15}` | `PATIENT_SDT_INVALID` |
| `bhytSo` nếu có phải khớp `\d{2}-\d{8}-\d` | `PATIENT_BHYT_INVALID` |

## 4. Mã lỗi

| Mã | Exception | HTTP |
|----|-----------|------|
| `PATIENT_NOT_FOUND` | `PatientNotFoundException extends ResourceNotFoundException` | 404 |
| `PATIENT_CMND_DUPLICATE` | `DuplicateResourceException` | 409 |
| `PATIENT_*` (bảng phía trên) | `InvalidPatientDataException extends BusinessRuleException` | 422 |

## 5. Port

```java
// out
public interface PatientRepositoryPort {
    Patient save(Patient p);
    Optional<Patient> findById(UUID id);
    PageResult<Patient> search(String keyword, PageQuery page);   // keyword có thể null
    boolean existsBySoCmnd(String soCmnd);
    void deleteById(UUID id);
}
public interface PatientEventPublisherPort {
    void publishCreated(PatientCreatedEvent e);
    void publishUpdated(PatientUpdatedEvent e);
}

// in
public interface CreatePatientUseCase { PatientDTO create(CreatePatientRequest r); }
public interface UpdatePatientUseCase { PatientDTO update(UUID id, UpdatePatientRequest r); }
public interface DeletePatientUseCase { void delete(UUID id); }
public interface GetPatientUseCase {
    PatientDTO getById(UUID id);
    PageResult<PatientDTO> search(String keyword, PageQuery page);
}
```

## 6. DTO

```java
public record CreatePatientRequest(
    @NotBlank @Size(max = 100) String hoTen,
    @NotNull @PastOrPresent LocalDate ngaySinh,
    @NotNull GioiTinh gioiTinh,
    @NotBlank @Size(max = 20) String soCmnd,
    @Size(max = 255) String diaChi,
    @Pattern(regexp = "\\d{10,15}") String soDienThoai,
    @Email @Size(max = 100) String email,
    @Pattern(regexp = "\\d{2}-\\d{8}-\\d") String bhytSo) {}

/** Cố ý không có soCmnd — trường này bất biến. */
public record UpdatePatientRequest(
    @NotBlank @Size(max = 100) String hoTen,
    @NotNull @PastOrPresent LocalDate ngaySinh,
    @NotNull GioiTinh gioiTinh,
    @Size(max = 255) String diaChi,
    @Pattern(regexp = "\\d{10,15}") String soDienThoai,
    @Email @Size(max = 100) String email,
    @Pattern(regexp = "\\d{2}-\\d{8}-\\d") String bhytSo) {}

public record PatientDTO(UUID maBenhNhan, String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh,
                         String soCmnd, String diaChi, String soDienThoai, String email,
                         String bhytSo, Instant createdAt, Instant updatedAt) {}
```

## 7. Tầng application

**`create`** — `existsBySoCmnd` → `PATIENT_CMND_DUPLICATE`; `Patient.taoMoi(...)`; lưu;
publish `PatientCreatedEvent` **sau khi commit**; trả DTO.

**`update`** — nạp hoặc `PATIENT_NOT_FOUND`; `capNhat(...)`; lưu; publish `PatientUpdatedEvent`.

**`search`** — `keyword` rỗng thì chuyển thành null. Câu truy vấn không phân biệt hoa thường trên
`hoTen`, khớp tiền tố trên `soCmnd`:

```java
@Query("""
    SELECT p FROM PatientJpaEntity p
    WHERE :keyword IS NULL
       OR LOWER(p.hoTen) LIKE LOWER(CONCAT('%', :keyword, '%'))
       OR p.soCmnd LIKE CONCAT(:keyword, '%')
    """)
Page<PatientJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);
```

**`delete`** — nạp hoặc 404, rồi `deleteById`. *(Sau này nên cân nhắc xóa mềm; một bệnh nhân đang
được hồ sơ tham chiếu thì không nên biến mất. Ngoài phạm vi V1.)*

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/patients/{id}` | — | `PatientDTO` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/patients?keyword&page&size` | — | `PageResult<PatientDTO>` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/patients` | `CreatePatientRequest` | 201 `PatientDTO` | ADMIN, NURSE |
| PUT | `/api/v1/patients/{id}` | `UpdatePatientRequest` | `PatientDTO` | ADMIN, NURSE |
| DELETE | `/api/v1/patients/{id}` | — | 204 | ADMIN |

## 9. Event

**Publish**

| Routing key | Payload |
|-------------|---------|
| `patient.created` | `{eventId, occurredAt, correlationId, patientId, hoTen, email, sdt}` |
| `patient.updated` | `{eventId, occurredAt, correlationId, patientId, hoTen, email, sdt, diaChi}` |

**Subscribe**

| Routing key | Xử lý |
|-------------|-------|
| `payment.completed` | Chỉ ghi log — không đổi trạng thái gì. Queue `patient.q`. Hiển nhiên idempotent. |

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-P1 | `so_cmnd` duy nhất | `create_duplicateCmnd_throwsDuplicateResource` |
| BR-P2 | `email` hợp lệ nếu có | `create_invalidEmail_throwsInvalidPatientData` |
| BR-P3 | `bhyt_so` khớp `XX-XXXXXXXX-X` | `create_malformedBhyt_throwsInvalidPatientData` |
| BR-P4 | `ngay_sinh` không ở tương lai | `create_futureBirthDate_throwsInvalidPatientData` |
| BR-P5 | `so_dien_thoai` chỉ chữ số, ≥10 | `create_shortPhone_throwsInvalidPatientData` |
| BR-P6 | `so_cmnd` bất biến | `update_doesNotChangeSoCmnd` |
| BR-P7 | Tạo thành công thì publish event | `create_valid_publishesPatientCreated` |
| BR-P8 | Tạo thất bại thì không publish gì | `create_duplicateCmnd_publishesNothing` |

## 11. Điểm dễ sai

- Hai tầng validation là **cố ý**: Bean Validation trên DTO cho ra 400 kèm chi tiết từng field; bất biến trong domain cho ra 422 và bảo vệ model trước mọi người gọi. Làm **cả hai** — chúng không thừa nhau.
- `soCmnd` và `bhytSo` là PII. Không log ở mức INFO.
- Service này đọc nhiều, ghi ít và chủ yếu do NURSE ghi; endpoint search là đường nóng. Giữ index trên `ho_ten`.
