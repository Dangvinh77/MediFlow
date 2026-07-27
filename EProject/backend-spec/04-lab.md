# 04 — lab-service (Khoa Xét nghiệm)

**Module** `lab-service` · **Package** `com.mediflow.lab` · **Cổng** 8084 · **DB** `mediflow_lab` · **Tiền tố** `/api/v1/lab`

Sở hữu chỉ định xét nghiệm và kết quả của chúng. Không gọi ai đồng bộ — mọi thứ đến qua event hoặc
qua REST từ bác sĩ lâm sàng.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE XET_NGHIEM (
    ma_xn             UUID          PRIMARY KEY,
    ma_ho_so          UUID          NOT NULL,     -- tham chiếu clinical-service
    ma_benh_nhan      UUID          NOT NULL,     -- tham chiếu patient-service
    ma_khoa_chi_dinh  UUID          NOT NULL,     -- tham chiếu organization-service KHOA
    loai_xn           VARCHAR(50)   NOT NULL,
    ngay_yeu_cau      DATE          NOT NULL,
    ngay_thuc_hien    DATE,
    trang_thai        VARCHAR(20)   NOT NULL DEFAULT 'CHO',
    ket_luan          TEXT,
    da_thanh_toan     BOOLEAN       NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);
CREATE INDEX idx_xn_benh_nhan ON XET_NGHIEM (ma_benh_nhan);
CREATE INDEX idx_xn_ho_so     ON XET_NGHIEM (ma_ho_so);
CREATE INDEX idx_xn_khoa      ON XET_NGHIEM (ma_khoa_chi_dinh);

CREATE TABLE KET_QUA_XN (
    ma_ket_qua          UUID          PRIMARY KEY,
    ma_xn               UUID          NOT NULL REFERENCES XET_NGHIEM(ma_xn) ON DELETE CASCADE,
    chi_so              VARCHAR(100)  NOT NULL,
    gia_tri             VARCHAR(50)   NOT NULL,
    don_vi              VARCHAR(20),
    chi_so_binh_thuong  VARCHAR(50),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_ket_qua_xn ON KET_QUA_XN (ma_xn);

-- Sổ khử trùng lặp cho event consumer (xem §9)
CREATE TABLE SU_KIEN_DA_XU_LY (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    xu_ly_luc    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

## 2. Enum

```java
public enum TrangThaiXetNghiem { CHO, DANG, HOAN_THANH, HUY }
```

## 3. Domain model

**`XetNghiem`** — `maXn`, `maHoSo`, `maBenhNhan`, `maKhoaChiDinh`, `loaiXn`, `ngayYeuCau`,
`ngayThucHien`, `trangThai`, `ketLuan`, `daThanhToan`, `ketQuas` (`List<KetQua>`), timestamps.

```java
public static XetNghiem taoMoi(UUID maHoSo, UUID maBenhNhan, UUID maKhoaChiDinh,
                               String loaiXn, LocalDate ngayYeuCau);

/** BR-L1 + BR-L2 + BR-L3 đều nằm ở đây. Ghi kết quả là hoàn tất xét nghiệm. */
public void ghiKetQua(List<KetQua> ketQuas, String ketLuan, LocalDate ngayThucHien);

public void doiTrangThai(TrangThaiXetNghiem moi);
public void danhDauDaThanhToan();
public boolean daKetThuc();   // HOAN_THANH hoặc HUY
```

Bất biến:

| Kiểm tra | Mã lỗi |
|----------|--------|
| `loaiXn` không rỗng | `LAB_LOAI_XN_REQUIRED` |
| gọi `ghiKetQua` khi `daKetThuc()` | `LAB_ALREADY_FINISHED` (BR-L1) |
| `ghiKetQua` với danh sách rỗng | `LAB_RESULT_EMPTY` |
| `ngayThucHien` trước `ngayYeuCau` | `LAB_DATE_BEFORE_REQUEST` (BR-L3) |
| chuyển trạng thái không hợp lệ | `LAB_INVALID_TRANSITION` |

`ghiKetQua` tự đặt `trangThai = HOAN_THANH` (BR-L2) — người gọi không bao giờ tự đặt thủ công.

Chuyển tiếp: `CHO → DANG | HUY`, `DANG → HOAN_THANH | HUY`, `HOAN_THANH`/`HUY` là kết thúc.

**`KetQua`** — `maKetQua`, `chiSo` (không rỗng), `giaTri` (không rỗng), `donVi`, `chiSoBinhThuong`.

## 4. Mã lỗi

`LAB_NOT_FOUND` → 404 · `LAB_ALREADY_FINISHED`, `LAB_RESULT_EMPTY`, `LAB_DATE_BEFORE_REQUEST`, `LAB_INVALID_TRANSITION`, `LAB_LOAI_XN_REQUIRED` → 422.

## 5. Port

```java
// out
public interface XetNghiemRepositoryPort {
    XetNghiem save(XetNghiem xn);
    Optional<XetNghiem> findById(UUID id);
    List<XetNghiem> findByBenhNhan(UUID maBenhNhan);
    List<XetNghiem> findByHoSo(UUID maHoSo);
    PageResult<XetNghiem> search(UUID maKhoa, TrangThaiXetNghiem tt, PageQuery page);
}
public interface ProcessedEventPort {          // tiện ích khử trùng lặp dùng chung
    boolean alreadyProcessed(UUID eventId);
    void markProcessed(UUID eventId, String routingKey);
}
public interface LabEventPublisherPort {
    void publishRequestCreated(LabRequestCreatedEvent e);
    void publishResultCreated(LabResultCreatedEvent e);
}

// in
public interface ManageLabTestUseCase {
    LabTestDTO create(CreateLabRequest r);
    LabTestDTO getById(UUID id);
    List<LabTestDTO> byPatient(UUID maBenhNhan);
    LabTestDTO addResults(UUID id, AddResultRequest r);
    LabTestDTO changeStatus(UUID id, TrangThaiXetNghiem tt);
}
public interface ReactToClinicalUseCase {
    void autoCreateFromRecord(UUID maHoSo, UUID maBenhNhan, UUID maKhoa, String loaiXn);
    void markPaid(UUID maXn);
}
```

## 6. DTO

```java
public record CreateLabRequest(
    @NotNull UUID maHoSo, @NotNull UUID maBenhNhan, @NotNull UUID maKhoaChiDinh,
    @NotBlank @Size(max = 50) String loaiXn,
    @NotNull @PastOrPresent LocalDate ngayYeuCau) {}

public record AddResultRequest(
    @NotEmpty @Valid List<KetQuaItem> ketQuas,
    @Size(max = 4000) String ketLuan,
    @NotNull LocalDate ngayThucHien) {}

public record KetQuaItem(
    @NotBlank @Size(max = 100) String chiSo,
    @NotBlank @Size(max = 50) String giaTri,
    @Size(max = 20) String donVi,
    @Size(max = 50) String chiSoBinhThuong) {}

public record LabTestDTO(UUID maXn, UUID maHoSo, UUID maBenhNhan, UUID maKhoaChiDinh,
                         String loaiXn, LocalDate ngayYeuCau, LocalDate ngayThucHien,
                         TrangThaiXetNghiem trangThai, String ketLuan, boolean daThanhToan,
                         List<KetQuaDTO> ketQuas, Instant createdAt, Instant updatedAt) {}

public record KetQuaDTO(UUID maKetQua, String chiSo, String giaTri, String donVi, String chiSoBinhThuong) {}
```

## 7. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/lab/{id}` | — | `LabTestDTO` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/lab/patient/{patientId}` | — | `List<LabTestDTO>` | ADMIN, DOCTOR |
| GET | `/api/v1/lab?maKhoa&trangThai&page&size` | — | `PageResult<LabTestDTO>` | ADMIN, MANAGER, LAB_TECH |
| POST | `/api/v1/lab` | `CreateLabRequest` | 201 | ADMIN, DOCTOR |
| PUT | `/api/v1/lab/{id}/results` | `AddResultRequest` | 200 | ADMIN, LAB_TECH |
| PUT | `/api/v1/lab/{id}/status` | `{trangThai}` | 200 | ADMIN, LAB_TECH |

## 8. Event

**Publish**

| Routing key | Payload |
|-------------|---------|
| `lab.request.created` | `{envelope, labId, patientId, recordId, maKhoa, loaiXn, ngayYeuCau}` |
| `lab.result.created` | `{envelope, labId, patientId, recordId, maKhoa, ketQua, ketLuan}` |

`lab.result.created` bắn từ `addResults`, sau khi commit. Trường `maKhoa` lấy từ `maKhoaChiDinh`.

**Subscribe** — queue `lab.q`

| Routing key | Xử lý |
|-------------|-------|
| `medicalrecord.created` | Chỉ tự tạo xét nghiệm **khi hồ sơ có chỉ định**. V1: bỏ qua nếu payload không mang chỉ định rõ ràng — đừng tạo xét nghiệm cho mọi hồ sơ một cách mù quáng. |
| `payment.completed` | `markPaid(maXn)` — đặt `da_thanh_toan = true` cho các xét nghiệm thuộc hóa đơn đó |

## 9. Khử trùng lặp

Cả hai consumer đều đi qua `ProcessedEventPort`:

```java
@RabbitListener(queues = "lab.q")
public void on(LabRelevantEvent e) {
    if (processed.alreadyProcessed(e.eventId())) return;   // gửi lại — không làm gì
    useCase.handle(...);
    processed.markProcessed(e.eventId(), routingKey);
}
```

Phải làm việc này **trong cùng transaction với hiệu ứng**, nếu không sổ ghi và hiệu ứng sẽ lệch nhau.

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-L1 | Không ghi kết quả khi đã `HOAN_THANH` hoặc `HUY` | `addResults_alreadyCompleted_throwsBusinessRule` |
| BR-L2 | Ghi kết quả tự động hoàn tất xét nghiệm | `addResults_valid_setsTrangThaiHoanThanh` |
| BR-L3 | `ngay_thuc_hien >= ngay_yeu_cau` | `addResults_dateBeforeRequest_throwsBusinessRule` |
| BR-L4 | Ghi kết quả thì publish `lab.result.created` | `addResults_valid_publishesResultCreated` |
| BR-L5 | Danh sách kết quả không được rỗng | `addResults_emptyList_throwsBusinessRule` |
| BR-L6 | Consumer idempotent | `paymentCompletedConsumer_sameEventTwice_marksPaidOnce` |
| BR-L7 | Từ chối chuyển trạng thái không hợp lệ | `changeStatus_completedToCho_throwsInvalidTransition` |

## 11. Điểm dễ sai

- `KET_QUA_XN` là một phần của aggregate `XetNghiem` — dùng cascade + `orphanRemoval`, **không** tạo repository port riêng cho nó.
- `gia_tri` là `VARCHAR`, không phải số: giá trị xét nghiệm hoàn toàn có thể là `"<0.01"`, `"âm tính"`, `"3+"`. Đừng "sửa" nó thành kiểu số.
- `SU_KIEN_DA_XU_LY` là bảng riêng của từng service. Chép cùng một bảng đó sang mọi service có tiêu thụ event (lab, pharmacy, billing, clinical, notification, report).
