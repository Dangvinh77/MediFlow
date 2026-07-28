# 06 — billing-service (Phòng Viện phí)

**Module** `billing-service` · **Package** `com.mediflow.billing` · **Cổng** 8086 · **DB** `mediflow_billing` · **Tiền tố** `/api/v1/billing`

Sở hữu viện phí và hóa đơn, đồng thời **điều phối saga** kê đơn → hóa đơn → thanh toán → xuất thuốc,
bao gồm cả nhánh bù trừ khi xuất thuốc thất bại. Đây là service subscribe nhiều event nhất hệ thống.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE VIEN_PHI (
    ma_vien_phi      UUID          PRIMARY KEY,
    ma_benh_nhan     UUID          NOT NULL,        -- tham chiếu patient-service
    ma_ho_so         UUID,                          -- tham chiếu clinical-service, cho phép null
    ma_khoa          UUID          NOT NULL,        -- tham chiếu organization-service KHOA
    ma_tham_chieu    UUID,                          -- xét nghiệm / đơn thuốc đã sinh ra khoản phí này
    loai_phi         VARCHAR(10)   NOT NULL,        -- KHAM | XN | THUOC | DV
    ngay_phat_sinh   DATE          NOT NULL,
    so_tien          DECIMAL(15,2) NOT NULL,
    da_thanh_toan    BOOLEAN       NOT NULL DEFAULT false,
    ma_hoa_don       UUID,                          -- gán khi được đưa vào hóa đơn
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT ck_vien_phi_so_tien CHECK (so_tien >= 0)
);
CREATE INDEX idx_vien_phi_benh_nhan ON VIEN_PHI (ma_benh_nhan, da_thanh_toan);
CREATE INDEX idx_vien_phi_khoa      ON VIEN_PHI (ma_khoa, ngay_phat_sinh);
CREATE INDEX idx_vien_phi_hoa_don   ON VIEN_PHI (ma_hoa_don);
-- mỗi event nguồn chỉ sinh một khoản phí: giúp việc tạo phí idempotent (BR-B7)
CREATE UNIQUE INDEX uq_vien_phi_nguon ON VIEN_PHI (loai_phi, ma_tham_chieu)
    WHERE ma_tham_chieu IS NOT NULL;

CREATE TABLE HOADON (
    ma_hoa_don      UUID          PRIMARY KEY,
    ma_benh_nhan    UUID          NOT NULL,
    ngay_tao        DATE          NOT NULL,
    tong_tien       DECIMAL(15,2) NOT NULL,
    da_thanh_toan   BOOLEAN       NOT NULL DEFAULT false,
    hinh_thuc_tt    VARCHAR(20),
    ma_phieu_xuat   UUID,                           -- tham chiếu pharmacy-service, cho phép null
    ma_ban_ke       UUID,                           -- đơn thuốc đã khởi động saga
    trang_thai_saga VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    ngay_thanh_toan TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT ck_hoa_don_tong_tien CHECK (tong_tien >= 0)
);
CREATE INDEX idx_hoa_don_benh_nhan ON HOADON (ma_benh_nhan);
CREATE UNIQUE INDEX uq_hoa_don_ban_ke ON HOADON (ma_ban_ke) WHERE ma_ban_ke IS NOT NULL;

CREATE TABLE SU_KIEN_DA_XU_LY (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    xu_ly_luc    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

## 2. Enum

```java
public enum LoaiPhi      { KHAM, XN, THUOC, DV }
public enum HinhThucTT   { TIEN_MAT, CHUYEN_KHOAN, BHYT }
public enum TrangThaiSaga { NONE, CHO_THANH_TOAN, DA_THANH_TOAN, CHO_XUAT_THUOC, HOAN_TAT, DA_HOAN_TIEN }
```

## 3. Máy trạng thái saga

`trang_thai_saga` chỉ rời khỏi `NONE` với những hóa đơn sinh ra từ đơn thuốc.

```
prescription.created
        │
        ▼
   CHO_THANH_TOAN ──── PUT /invoices/{id}/pay ────► DA_THANH_TOAN
                                                          │ publish payment.completed
                                                          ▼
                                                   CHO_XUAT_THUOC
                                     ┌────────────────────┴────────────────────┐
                     prescription.filled                        prescription.dispense.failed
                                     ▼                                          ▼
                                 HOAN_TAT                                 DA_HOAN_TIEN
                                                            (đảo viện phí, publish payment.failed)
```

Chuyển tiếp hợp lệ — còn lại ném `BILLING_INVALID_SAGA_TRANSITION`:

| Từ | Sang |
|----|------|
| `NONE` | *(kết thúc — hóa đơn thường, không có saga)* |
| `CHO_THANH_TOAN` | `DA_THANH_TOAN` |
| `DA_THANH_TOAN` | `CHO_XUAT_THUOC` |
| `CHO_XUAT_THUOC` | `HOAN_TAT`, `DA_HOAN_TIEN` |
| `HOAN_TAT`, `DA_HOAN_TIEN` | *(kết thúc)* |

## 4. Domain model

### `VienPhi` (khoản viện phí)

`maVienPhi`, `maBenhNhan`, `maHoSo`, `maKhoa`, `maThamChieu`, `loaiPhi`, `ngayPhatSinh`,
`soTien`, `daThanhToan`, `maHoaDon`.

```java
public static VienPhi taoMoi(UUID maBenhNhan, UUID maHoSo, UUID maKhoa, UUID maThamChieu,
                             LoaiPhi loaiPhi, LocalDate ngayPhatSinh, BigDecimal soTien);
public void ganVaoHoaDon(UUID maHoaDon);
public void danhDauDaThanhToan();      // BR-B3
public void hoanTien();                // bù trừ: da_thanh_toan = false, gỡ khỏi hóa đơn
public boolean chuaThanhToan();
```

Bất biến: `soTien >= 0` (`BILLING_AMOUNT_NEGATIVE`); `loaiPhi` và `maKhoa` không null
(`BILLING_KHOA_REQUIRED` — mọi khoản phí đều phải thuộc về một khoa).

### `HoaDon` (hóa đơn — aggregate root)

`maHoaDon`, `maBenhNhan`, `ngayTao`, `tongTien`, `daThanhToan`, `hinhThucTT`, `maPhieuXuat`,
`maBanKe`, `trangThaiSaga`, `ngayThanhToan`.

```java
public static HoaDon taoMoi(UUID maBenhNhan, LocalDate ngayTao, List<VienPhi> phiChuaThanhToan);
public static HoaDon taoTuDonThuoc(UUID maBenhNhan, UUID maBanKe, List<VienPhi> phi);  // cửa vào saga
public void thanhToan(HinhThucTT hinhThuc, Instant thoiDiem);   // BR-B1
public void chuyenSaga(TrangThaiSaga moi);
public void hoanTien();
public boolean daTraRoi();
```

Bất biến:

| Kiểm tra | Mã lỗi |
|----------|--------|
| gọi `thanhToan` trên hóa đơn đã trả | `BILLING_ALREADY_PAID` (BR-B1) |
| dựng hóa đơn từ danh sách phí rỗng | `BILLING_NO_UNPAID_FEES` |
| chuyển saga không hợp lệ | `BILLING_INVALID_SAGA_TRANSITION` |

`tongTien` được **tính ra** từ danh sách phí (BR-B2), không bao giờ nhận từ request.

## 5. Mã lỗi

`INVOICE_NOT_FOUND`, `FEE_NOT_FOUND` → 404 ·
`BILLING_ALREADY_PAID`, `BILLING_NO_UNPAID_FEES`, `BILLING_AMOUNT_NEGATIVE`, `BILLING_KHOA_REQUIRED`, `BILLING_INVALID_SAGA_TRANSITION` → 422.

## 6. Port

```java
// out
public interface VienPhiRepositoryPort {
    VienPhi save(VienPhi vp);
    List<VienPhi> saveAll(List<VienPhi> list);
    Optional<VienPhi> findById(UUID id);
    List<VienPhi> findUnpaidByPatient(UUID maBenhNhan);
    List<VienPhi> findByHoaDon(UUID maHoaDon);
    boolean existsBySource(LoaiPhi loaiPhi, UUID maThamChieu);   // BR-B7
}
public interface HoaDonRepositoryPort {
    HoaDon save(HoaDon hd);
    Optional<HoaDon> findById(UUID id);
    Optional<HoaDon> findByBanKe(UUID maBanKe);
    PageResult<HoaDon> findByPatient(UUID maBenhNhan, PageQuery page);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }
public interface BillingEventPublisherPort {
    void publishInvoiceCreated(InvoiceCreatedEvent e);
    void publishPaymentCompleted(PaymentCompletedEvent e);
    void publishPaymentFailed(PaymentFailedEvent e);
}
/** Bảng giá cho các khoản phí tự sinh. V1: một bảng trong DB, không phải service riêng. */
public interface BangGiaPort {
    BigDecimal giaKham(UUID maKhoa);
    BigDecimal giaXetNghiem(String loaiXn);
}

// in
public interface ManageInvoiceUseCase {
    InvoiceDTO create(CreateInvoiceRequest r);
    InvoiceDTO getById(UUID id);
    PageResult<InvoiceDTO> byPatient(UUID maBenhNhan, PageQuery page);
    PaymentResultDTO pay(UUID id, PayInvoiceRequest r);
}
public interface AccrueFeeUseCase {         // do các event consumer điều khiển
    void onMedicalRecordCreated(MedicalRecordCreatedEvent e);
    void onLabResultCreated(LabResultCreatedEvent e);
    void onAppointmentStatusChanged(AppointmentStatusChangedEvent e);
    void onPrescriptionCreated(PrescriptionCreatedEvent e);
}
public interface SagaCompensationUseCase {
    void onDispenseFailed(PrescriptionDispenseFailedEvent e);
    void onPrescriptionFilled(PrescriptionFilledEvent e);
}
```

## 7. Thuật toán tầng application

### `pay(invoiceId, PayInvoiceRequest)` — `@Transactional`

1. nạp hóa đơn → không có thì `INVOICE_NOT_FOUND`
2. `hd.thanhToan(hinhThuc, now)` → ném `BILLING_ALREADY_PAID` nếu đã trả (BR-B1)
3. `vienPhiRepo.findByHoaDon(id)` → gọi `danhDauDaThanhToan()` trên từng khoản → `saveAll` (BR-B3)
4. nếu `trangThaiSaga == CHO_THANH_TOAN`: `chuyenSaga(DA_THANH_TOAN)` rồi `chuyenSaga(CHO_XUAT_THUOC)`
5. lưu
6. publish `PaymentCompletedEvent` **sau khi commit** — đây chính là thứ kích hoạt pharmacy xuất thuốc

### `onPrescriptionCreated(e)` — cửa vào saga

1. `processed.alreadyProcessed(e.eventId())` → return
2. `hoaDonRepo.findByBanKe(e.prescriptionId())` đã có → return *(unique index cũng chặn thêm lần nữa)*
3. tạo một `VienPhi{loaiPhi = THUOC, maThamChieu = prescriptionId, soTien = e.totalAmount(), maKhoa = e.maKhoa()}`
4. `HoaDon.taoTuDonThuoc(...)`, `chuyenSaga(CHO_THANH_TOAN)`
5. lưu, đánh dấu đã xử lý
6. publish `InvoiceCreatedEvent`

### `onDispenseFailed(e)` — bù trừ

1. khử trùng lặp theo `eventId`
2. nạp hóa đơn theo `maBanKe` → không có thì ghi log rồi return (không có gì để bù)
3. `hd.hoanTien()` — đặt `daThanhToan = false`, `chuyenSaga(DA_HOAN_TIEN)`
4. gọi `hoanTien()` trên mọi `VienPhi` đính kèm
5. lưu
6. publish `PaymentFailedEvent{invoiceId, patientId, reason: e.reason()}` → notification báo cho bệnh nhân

> Tiền được *đảo trong sổ sách*, không phải hoàn qua cổng thanh toán — hệ thống này không có cổng
> thanh toán nào. Hãy ghi đúng như vậy trong báo cáo thay vì nói mập mờ là "hoàn tiền".

### `onPrescriptionFilled(e)` — saga thành công

khử trùng lặp → nạp hóa đơn theo `maBanKe` → `chuyenSaga(HOAN_TAT)` → đặt `maPhieuXuat` → lưu.
Không publish event nào.

### Sinh viện phí từ ba event còn lại

| Event | Khoản phí được tạo |
|-------|--------------------|
| `medicalrecord.created` | `KHAM`, số tiền `bangGia.giaKham(maKhoa)`, `maThamChieu = recordId` |
| `lab.result.created` | `XN`, số tiền `bangGia.giaXetNghiem(loaiXn)`, `maThamChieu = labId` |
| `appointment.status.changed` khi `status = DA_DEN` | `KHAM` nếu hồ sơ đó chưa có phí |

Cả bốn consumer đều kiểm `existsBySource(loaiPhi, maThamChieu)` trước (BR-B7) — unique partial index
đảm bảo an toàn ngay cả khi có tương tranh.

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/billing/invoices/{id}` | — | `InvoiceDTO` | ADMIN, CASHIER |
| GET | `/api/v1/billing/patient/{patientId}?page&size` | — | `PageResult<InvoiceDTO>` | ADMIN, CASHIER |
| POST | `/api/v1/billing/invoices` | `CreateInvoiceRequest` | 201 | ADMIN, CASHIER |
| PUT | `/api/v1/billing/invoices/{id}/pay` | `PayInvoiceRequest` | `PaymentResultDTO` | ADMIN, CASHIER |
| GET | `/api/v1/billing/revenue?maKhoa&tuNgay&denNgay` | — | `List<RevenueByDeptDTO>` | ADMIN, MANAGER |

```java
public record CreateInvoiceRequest(@NotNull UUID maBenhNhan, @NotNull @PastOrPresent LocalDate ngayTao) {}
// không có số tiền — server tự cộng các khoản phí chưa trả của bệnh nhân (BR-B2)

public record PayInvoiceRequest(@NotNull HinhThucTT hinhThucTT) {}

public record InvoiceDTO(UUID maHoaDon, UUID maBenhNhan, LocalDate ngayTao, BigDecimal tongTien,
                         boolean daThanhToan, HinhThucTT hinhThucTT, UUID maPhieuXuat, UUID maBanKe,
                         TrangThaiSaga trangThaiSaga, Instant ngayThanhToan, List<FeeDTO> khoanPhi) {}

public record FeeDTO(UUID maVienPhi, LoaiPhi loaiPhi, UUID maKhoa, LocalDate ngayPhatSinh,
                     BigDecimal soTien, boolean daThanhToan) {}

public record PaymentResultDTO(UUID maHoaDon, boolean thanhCong, BigDecimal tongTien,
                               HinhThucTT hinhThucTT, Instant ngayThanhToan, TrangThaiSaga trangThaiSaga) {}

public record RevenueByDeptDTO(UUID maKhoa, BigDecimal tongDoanhThu, long soHoaDon) {}
```

## 9. Event

**Publish**

| Routing key | Payload |
|-------------|---------|
| `invoice.created` | `{envelope, invoiceId, patientId, maKhoa, totalAmount, items[]}` |
| `payment.completed` | `{envelope, invoiceId, patientId, maKhoa, maBanKe, totalAmount, paymentMethod}` |
| `payment.failed` | `{envelope, invoiceId, patientId, reason}` |

`payment.completed` mang theo `maBanKe` để pharmacy biết phải xuất đơn thuốc nào. Thiếu trường này
thì pharmacy phải đoán — đừng bỏ nó.

**Subscribe** — queue `billing.q`

| Routing key | Xử lý |
|-------------|-------|
| `prescription.created` | cửa vào saga — tạo hóa đơn |
| `prescription.filled` | saga thành công → `HOAN_TAT` |
| `prescription.dispense.failed` | **bù trừ** → `DA_HOAN_TIEN` + `payment.failed` |
| `medicalrecord.created` | sinh phí `KHAM` |
| `lab.result.created` | sinh phí `XN` |
| `appointment.status.changed` | sinh phí `KHAM` khi `DA_DEN` |

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-B1 | Không thanh toán hóa đơn đã trả | `pay_alreadyPaid_throwsBusinessRule` |
| BR-B2 | `tong_tien = Σ` các khoản phí chưa trả | `createInvoice_sumsUnpaidFeesOnly` |
| BR-B3 | Thanh toán đánh dấu mọi khoản phí liên quan | `pay_marksAllRelatedFeesPaid` |
| BR-B4 | Xuất thuốc thất bại thì đảo thanh toán | `onDispenseFailed_reversesInvoiceAndFees` |
| BR-B5 | Xuất thất bại thì publish `payment.failed` | `onDispenseFailed_publishesPaymentFailed` |
| BR-B6 | `prescription.created` chỉ tạo đúng một hóa đơn | `onPrescriptionCreated_twice_createsOneInvoice` |
| BR-B7 | Phí idempotent theo event nguồn | `onLabResult_sameEventTwice_createsOneFee` |
| BR-B8 | Mọi khoản phí đều mang `ma_khoa` | `accrueFee_alwaysSetsMaKhoa` |
| BR-B9 | Chuyển trạng thái saga được kiểm tra | `chuyenSaga_hoanTatToDaHoanTien_throwsInvalidTransition` |
| BR-B10 | Doanh thu nhóm theo khoa | `revenue_groupsByMaKhoaAndDateRange` |
| BR-B11 | Xuất thuốc thành công thì kết thúc saga | `onPrescriptionFilled_setsHoanTat` |

## 11. Điểm dễ sai

- Toàn bộ saga phụ thuộc vào việc `payment.completed` tới pharmacy **sau khi DB đã commit**. Dùng `@TransactionalEventListener(AFTER_COMMIT)` hoặc outbox. Publish bên trong transaction có thể khiến thuốc được xuất cho một lần thanh toán sau đó bị rollback.
- `uq_hoa_don_ban_ke` và `uq_vien_phi_nguon` là **partial unique index** (`WHERE ... IS NOT NULL`). Ràng buộc unique thường sẽ từ chối nhiều giá trị NULL trên một số engine — giữ nguyên dạng partial.
- `BigDecimal` ở mọi nơi; `setScale(2, HALF_UP)` cho mọi tổng.
- `BangGiaPort` cố ý là một port. V1 có thể hiện thực bằng bảng `BANG_GIA` hoặc `@ConfigurationProperties`; sau này muốn tách thành service bảng giá riêng thì thay adapter, tầng application không đổi một dòng.
- Bệnh nhân không còn khoản phí chưa trả nào phải nhận `BILLING_NO_UNPAID_FEES` (422), không phải một hóa đơn tổng bằng 0.
