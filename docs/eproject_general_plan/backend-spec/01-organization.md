# 01 — organization-service

**Module** `organization-service` · **Package** `com.mediflow.organization` · **Cổng** 8089 · **DB** `mediflow_organization` · **Tiền tố** `/api/v1/org`

Sở hữu cơ cấu tổ chức của bệnh viện: có những khoa nào, ai làm ở đó, họ đăng nhập ra sao. Trả lời
*ai* và *ở đâu* — không bao giờ trả lời *điều gì đã xảy ra với bệnh nhân*.

Xây **đầu tiên**. `clinical` và `gateway` phụ thuộc vào nó.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE DEPARTMENT (
    department_id       UUID          PRIMARY KEY,
    department_name     VARCHAR(100)  NOT NULL,
    abbreviation        VARCHAR(20)   NOT NULL,
    department_type     VARCHAR(20)   NOT NULL,
    department_head_id  UUID,
    location            VARCHAR(255),
    is_active           BOOLEAN       NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uq_department_abbreviation UNIQUE (abbreviation)
);

CREATE TABLE STAFF (
    staff_id       UUID          PRIMARY KEY,
    full_name      VARCHAR(100)  NOT NULL,
    department_id  UUID          NOT NULL REFERENCES DEPARTMENT(department_id),
    job_title      VARCHAR(20)   NOT NULL,
    specialization VARCHAR(100),
    license_number VARCHAR(50),
    phone_number   VARCHAR(15),
    email          VARCHAR(100),
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_staff_department_id ON STAFF (department_id);
CREATE INDEX idx_staff_job_title ON STAFF (job_title);

-- department_head_id tham chiếu STAFF, thêm sau khi cả hai bảng đã tồn tại
ALTER TABLE DEPARTMENT ADD CONSTRAINT fk_department_head
    FOREIGN KEY (department_head_id) REFERENCES STAFF(staff_id);

CREATE TABLE ACCOUNT (
    account_id     UUID          PRIMARY KEY,
    username       VARCHAR(50)   NOT NULL,
    password_hash  VARCHAR(255)  NOT NULL,
    staff_id       UUID          REFERENCES STAFF(staff_id),
    role           VARCHAR(20)   NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT true,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_account_username UNIQUE (username)
);
```

## 2. Enum — `domain/model/`

```java
public enum LoaiKhoa    { LAM_SANG, CAN_LAM_SANG, HANH_CHINH }
public enum ChucDanh    { BAC_SI, DIEU_DUONG, KY_THUAT_VIEN, DUOC_SI, THU_NGAN, QUAN_LY, HANH_CHINH }
public enum TrangThaiNhanVien { DANG_LAM, NGHI_VIEC }
public enum VaiTro      { ADMIN, DOCTOR, NURSE, PHARMACIST, CASHIER, LAB_TECH, MANAGER, PATIENT, SYSTEM }
```

`VaiTro` phải khớp chính xác với `com.mediflow.common.security.Roles`.

## 3. Domain model

**`Khoa`** — `maKhoa`, `tenKhoa`, `maVietTat`, `loaiKhoa`, `truongKhoa` (UUID, cho phép null), `diaDiem`, `hoatDong`, timestamps.

```java
public static Khoa taoMoi(String tenKhoa, String maVietTat, LoaiKhoa loaiKhoa, String diaDiem);
public void capNhat(String tenKhoa, LoaiKhoa loaiKhoa, String diaDiem);
public void datTruongKhoa(UUID maNhanVien);   // người gọi phải kiểm tra cùng khoa trước
public void ngungHoatDong();                  // BR-K3 kiểm ở tầng application service
```

Bất biến trong `taoMoi`: `tenKhoa` không rỗng; `maVietTat` không rỗng, tối đa 20 ký tự, viết hoa;
`loaiKhoa` không null.

**`NhanVien`** — `maNhanVien`, `hoTen`, `maKhoa`, `chucDanh`, `chuyenKhoa`, `soChungChi`, `soDienThoai`, `email`, `trangThai`, timestamps.

```java
public static NhanVien taoMoi(String hoTen, UUID maKhoa, ChucDanh chucDanh,
                              String chuyenKhoa, String soChungChi,
                              String soDienThoai, String email);
public void capNhat(String hoTen, ChucDanh chucDanh, String chuyenKhoa,
                    String soChungChi, String soDienThoai, String email);
/** @return khoa cũ, để dựng StaffDepartmentChangedEvent */
public UUID chuyenKhoa(UUID maKhoaMoi);
public void nghiViec();
public boolean dangLamViec();
```

Bất biến: `hoTen` không rỗng; `maKhoa` không null; `chucDanh` không null; **nếu `chucDanh == BAC_SI`
thì `soChungChi` bắt buộc phải có** (BR-N2); `soDienThoai` nếu có phải khớp `\d{10,15}`; `email`
nếu có phải là địa chỉ hợp lệ.

**`TaiKhoan`** — `maTaiKhoan`, `tenDangNhap`, `matKhauHash`, `maNhanVien` (cho phép null), `vaiTro`, `kichHoat`, `lanDangNhapCuoi`, timestamps.

```java
public static TaiKhoan taoMoi(String tenDangNhap, String matKhauHash, UUID maNhanVien, VaiTro vaiTro);
public void doiTrangThai(boolean kichHoat);
public void ghiNhanDangNhap(Instant thoiDiem);
public boolean coTheDangNhap();   // kichHoat == true
```

Bất biến: `tenDangNhap` không rỗng, 3–50 ký tự, khớp `^[a-zA-Z0-9._-]+$`; `matKhauHash` không rỗng;
**nếu `vaiTro == PATIENT` thì `maNhanVien` phải null**, ngược lại bắt buộc phải có (BR-T2).

> Domain lưu **hash**, không bao giờ lưu mật khẩu thô. Việc băm nằm ở
> `infrastructure/security/BCryptPasswordHasher`, sau một out-port `PasswordHasherPort` — BCrypt là
> lớp của Spring Security nên không được xuất hiện trong `application/` hay `domain/`.

## 4. Mã lỗi

| Mã | Exception | HTTP |
|----|-----------|------|
| `KHOA_NOT_FOUND` | `KhoaNotFoundException extends ResourceNotFoundException` | 404 |
| `NHAN_VIEN_NOT_FOUND` | `NhanVienNotFoundException extends ResourceNotFoundException` | 404 |
| `TAI_KHOAN_NOT_FOUND` | `TaiKhoanNotFoundException extends ResourceNotFoundException` | 404 |
| `KHOA_MA_VIET_TAT_DUPLICATE` | `DuplicateResourceException` | 409 |
| `TAI_KHOAN_USERNAME_DUPLICATE` | `DuplicateResourceException` | 409 |
| `NHAN_VIEN_BAC_SI_REQUIRES_CERT` | `InvalidNhanVienDataException extends BusinessRuleException` | 422 |
| `KHOA_TRUONG_KHOA_WRONG_DEPT` | `BusinessRuleException` | 422 |
| `KHOA_HAS_ACTIVE_STAFF` | `BusinessRuleException` | 422 |
| `TAI_KHOAN_PATIENT_HAS_STAFF` | `BusinessRuleException` | 422 |
| `KHOA_INACTIVE` | `BusinessRuleException` | 422 |
| `AUTH_INVALID_CREDENTIALS` | `BusinessRuleException` | 422 |

## 5. Port

```java
// application/port/out
public interface KhoaRepositoryPort {
    Khoa save(Khoa khoa);
    Optional<Khoa> findById(UUID id);
    List<Khoa> findAll(boolean chiHoatDong);
    boolean existsByMaVietTat(String maVietTat);
    long countActiveStaff(UUID maKhoa);
}

public interface NhanVienRepositoryPort {
    NhanVien save(NhanVien nv);
    Optional<NhanVien> findById(UUID id);
    PageResult<NhanVien> search(UUID maKhoa, ChucDanh chucDanh, PageQuery page);
}

public interface TaiKhoanRepositoryPort {
    TaiKhoan save(TaiKhoan tk);
    Optional<TaiKhoan> findById(UUID id);
    Optional<TaiKhoan> findByTenDangNhap(String tenDangNhap);
    boolean existsByTenDangNhap(String tenDangNhap);
}

public interface PasswordHasherPort {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hash);
}

public interface OrganizationEventPublisherPort {
    void publishDepartmentCreated(DepartmentCreatedEvent e);
    void publishStaffCreated(StaffCreatedEvent e);
    void publishStaffDepartmentChanged(StaffDepartmentChangedEvent e);
}

// application/port/in
public interface ManageKhoaUseCase {
    KhoaDTO create(CreateKhoaRequest r);
    KhoaDTO update(UUID id, UpdateKhoaRequest r);
    KhoaDTO getById(UUID id);
    List<KhoaDTO> list(boolean chiHoatDong);
}
public interface ManageNhanVienUseCase {
    NhanVienDTO create(CreateNhanVienRequest r);
    NhanVienDTO update(UUID id, UpdateNhanVienRequest r);
    NhanVienDTO getById(UUID id);
    PageResult<NhanVienDTO> search(UUID maKhoa, ChucDanh chucDanh, PageQuery page);
    NhanVienDTO changeDepartment(UUID id, UUID maKhoaMoi);
    StaffExistsDTO exists(UUID id);
}
public interface ManageTaiKhoanUseCase {
    TaiKhoanDTO create(CreateTaiKhoanRequest r);
    TaiKhoanDTO changeStatus(UUID id, boolean kichHoat);
}
public interface VerifyCredentialsUseCase {
    VerifiedAccountDTO verify(VerifyCredentialsRequest r);
}
```

## 6. DTO

```java
// request
public record CreateKhoaRequest(
    @NotBlank @Size(max = 100) String tenKhoa,
    @NotBlank @Size(max = 20) String maVietTat,
    @NotNull LoaiKhoa loaiKhoa,
    @Size(max = 255) String diaDiem) {}

public record CreateNhanVienRequest(
    @NotBlank @Size(max = 100) String hoTen,
    @NotNull UUID maKhoa,
    @NotNull ChucDanh chucDanh,
    @Size(max = 100) String chuyenKhoa,
    @Size(max = 50) String soChungChi,
    @Pattern(regexp = "\\d{10,15}") String soDienThoai,
    @Email @Size(max = 100) String email) {}

public record ChangeDepartmentRequest(@NotNull UUID maKhoa) {}

public record CreateTaiKhoanRequest(
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9._-]+$") String tenDangNhap,
    @NotBlank @Size(min = 8, max = 72) String matKhau,   // thô; được băm trong service
    UUID maNhanVien,
    @NotNull VaiTro vaiTro) {}

public record VerifyCredentialsRequest(@NotBlank String tenDangNhap, @NotBlank String matKhau) {}

// response
public record KhoaDTO(UUID maKhoa, String tenKhoa, String maVietTat, LoaiKhoa loaiKhoa,
                      UUID truongKhoa, String diaDiem, boolean hoatDong,
                      Instant createdAt, Instant updatedAt) {}

public record NhanVienDTO(UUID maNhanVien, String hoTen, UUID maKhoa, ChucDanh chucDanh,
                          String chuyenKhoa, String soChungChi, String soDienThoai, String email,
                          TrangThaiNhanVien trangThai, Instant createdAt, Instant updatedAt) {}

public record StaffExistsDTO(boolean exists, UUID maKhoa) {}

/** Không bao giờ lộ matKhauHash. */
public record TaiKhoanDTO(UUID maTaiKhoan, String tenDangNhap, UUID maNhanVien,
                          VaiTro vaiTro, boolean kichHoat, Instant lanDangNhapCuoi) {}

public record VerifiedAccountDTO(UUID maTaiKhoan, UUID maNhanVien, UUID maKhoa, VaiTro vaiTro) {}
```

## 7. Thuật toán tầng application

**`create(CreateNhanVienRequest)`**
1. `khoaRepo.findById(maKhoa)` → không có thì `KHOA_NOT_FOUND`
2. nếu `!khoa.isHoatDong()` → `KHOA_INACTIVE`
3. `NhanVien.taoMoi(...)` — ném `NHAN_VIEN_BAC_SI_REQUIRES_CERT` nếu bác sĩ không có chứng chỉ
4. lưu
5. publish `StaffCreatedEvent`
6. map sang `NhanVienDTO`

**`changeDepartment(id, maKhoaMoi)`**
1. nạp nhân viên → không có thì `NHAN_VIEN_NOT_FOUND`
2. nạp khoa đích → không có thì `KHOA_NOT_FOUND`; nếu ngừng hoạt động thì `KHOA_INACTIVE`
3. `UUID cu = nv.chuyenKhoa(maKhoaMoi)`; nếu `cu.equals(maKhoaMoi)` thì trả về nguyên trạng, **không publish gì**
4. lưu, publish `StaffDepartmentChangedEvent{maNhanVien, maKhoaCu: cu, maKhoaMoi}`

**`update(id, UpdateKhoaRequest)` — khi đặt `truongKhoa`**
1. nạp khoa
2. nạp nhân viên được đề cử → không có thì `NHAN_VIEN_NOT_FOUND`
3. nếu `nv.getMaKhoa() != id` → `KHOA_TRUONG_KHOA_WRONG_DEPT`
4. `khoa.datTruongKhoa(...)`, lưu

**`ngungHoatDong` (qua update với `hoatDong=false`)**
1. `khoaRepo.countActiveStaff(id) > 0` → `KHOA_HAS_ACTIVE_STAFF`
2. ngược lại thì ngừng hoạt động

**`verify(VerifyCredentialsRequest)`**
1. `findByTenDangNhap` → rỗng thì ném `AUTH_INVALID_CREDENTIALS`
2. nếu `!tk.coTheDangNhap()` → `AUTH_INVALID_CREDENTIALS`
3. `passwordHasher.matches(raw, hash)` → false → `AUTH_INVALID_CREDENTIALS`
4. lấy `maKhoa` từ `NhanVien` liên kết (null với tài khoản PATIENT)
5. `ghiNhanDangNhap(Instant.now())`, lưu
6. trả `VerifiedAccountDTO`

> Dùng **cùng một mã lỗi và cùng một thông điệp** cho ba trường hợp: không có user, sai mật khẩu, và
> tài khoản bị khóa. Phân biệt chúng là chỉ cho kẻ tấn công biết username nào tồn tại.

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/org/departments?chiHoatDong` | — | `List<KhoaDTO>` | ADMIN, MANAGER, DOCTOR, NURSE |
| GET | `/api/v1/org/departments/{id}` | — | `KhoaDTO` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/org/departments` | `CreateKhoaRequest` | 201 `KhoaDTO` | ADMIN |
| PUT | `/api/v1/org/departments/{id}` | `UpdateKhoaRequest` | `KhoaDTO` | ADMIN |
| GET | `/api/v1/org/staff?maKhoa&chucDanh&page&size` | — | `PageResult<NhanVienDTO>` | ADMIN, MANAGER, DOCTOR, NURSE |
| GET | `/api/v1/org/staff/{id}` | — | `NhanVienDTO` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/org/staff` | `CreateNhanVienRequest` | 201 `NhanVienDTO` | ADMIN |
| PUT | `/api/v1/org/staff/{id}` | `UpdateNhanVienRequest` | `NhanVienDTO` | ADMIN |
| PUT | `/api/v1/org/staff/{id}/department` | `ChangeDepartmentRequest` | `NhanVienDTO` | ADMIN |
| GET | `/api/v1/org/staff/{id}/exists` | — | `StaffExistsDTO` | ADMIN, DOCTOR, NURSE, SYSTEM |
| POST | `/api/v1/org/accounts` | `CreateTaiKhoanRequest` | 201 `TaiKhoanDTO` | ADMIN |
| PUT | `/api/v1/org/accounts/{id}/status` | `{kichHoat}` | `TaiKhoanDTO` | ADMIN |
| POST | `/api/v1/org/accounts/verify` | `VerifyCredentialsRequest` | `VerifiedAccountDTO` | SYSTEM |

## 9. Event

**Publish** (`infrastructure/messaging/payload/`), đều kèm envelope chuẩn:

| Routing key | Payload |
|-------------|---------|
| `department.created` | `{eventId, occurredAt, correlationId, maKhoa, tenKhoa, loaiKhoa}` |
| `staff.created` | `{eventId, occurredAt, correlationId, maNhanVien, hoTen, maKhoa, chucDanh}` |
| `staff.department.changed` | `{eventId, occurredAt, correlationId, maNhanVien, maKhoaCu, maKhoaMoi}` |

**Subscribe:** không có. Không tạo class nào trong `consumer/` — để trống package.

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-K1 | `abbreviation` duy nhất | `createKhoa_duplicateMaVietTat_throwsDuplicate` |
| BR-K2 | `department_head_id` phải thuộc chính khoa đó | `setTruongKhoa_staffFromOtherDept_throwsBusinessRule` |
| BR-K3 | Không ngừng hoạt động khoa còn nhân viên | `deactivateKhoa_hasActiveStaff_throwsBusinessRule` |
| BR-N1 | Nhân viên phải thuộc một khoa đang hoạt động | `createNhanVien_inactiveKhoa_throwsBusinessRule` |
| BR-N2 | `BAC_SI` bắt buộc có `license_number` | `createNhanVien_doctorWithoutCert_throwsBusinessRule` |
| BR-N3 | Chuyển khoa phải publish event, không xóa rồi tạo lại | `changeDepartment_valid_publishesStaffDepartmentChanged` |
| BR-N4 | Chuyển về đúng khoa cũ là no-op | `changeDepartment_sameKhoa_publishesNothing` |
| BR-T1 | `username` duy nhất | `createTaiKhoan_duplicateUsername_throwsDuplicate` |
| BR-T2 | Tài khoản `PATIENT` không có `staff_id` | `createTaiKhoan_patientWithStaffId_throwsBusinessRule` |
| BR-T3 | Mật khẩu lưu dạng BCrypt hash | `createTaiKhoan_storesHashNotPlaintext` |
| BR-T4 | Tài khoản bị khóa không đăng nhập được | `verify_disabledAccount_throwsInvalidCredentials` |
| BR-T5 | Sai user và sai mật khẩu trả lỗi giống hệt nhau | `verify_unknownUser_sameErrorCodeAsWrongPassword` |

## 11. Điểm dễ sai

- `DEPARTMENT.department_head_id` và `STAFF.department_id` tham chiếu **vòng tròn**. Phải tạo cả hai bảng trước rồi mới thêm khóa ngoại (DDL ở trên đã làm vậy). Trong JPA, thuộc tính `truongKhoa` ánh xạ vào cột `department_head_id` dưới dạng `UUID` thuần, **không** dùng `@ManyToOne` — nếu không Hibernate không giải được vòng lặp khi insert.
- `POST /accounts/verify` **tuyệt đối không** được route ra ngoài. Gateway gọi nó theo kiểu service-to-service; bảng route của gateway chỉ mở `/api/v1/auth/*` cho client.
- Dữ liệu mẫu (vài khoa, một tài khoản ADMIN) đặt ở `V2__seed.sql`, không đặt vào `V1`. Migration chỉ được thêm mới.
