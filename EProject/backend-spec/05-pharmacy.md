# 05 — pharmacy-service (Khoa Dược)

**Module** `pharmacy-service` · **Package** `com.mediflow.pharmacy` · **Cổng** 8085 · **DB** `mediflow_pharmacy` · **Tiền tố** `/api/v1/pharmacy`

Sở hữu danh mục thuốc, tồn kho, đơn thuốc và phiếu xuất. **Thành phần tham gia saga** kê đơn → hóa
đơn → thanh toán → xuất thuốc. Đây là service nhiều trạng thái nhất hệ thống: nó là service duy nhất
có thể *hết hàng*.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE THUOC (
    ma_thuoc       UUID          PRIMARY KEY,
    ten_thuoc      VARCHAR(150)  NOT NULL,
    hoat_chat      VARCHAR(150),
    don_vi_tinh    VARCHAR(20)   NOT NULL,
    gia            DECIMAL(15,2) NOT NULL,
    so_luong_ton   INT           NOT NULL DEFAULT 0,
    han_su_dung    DATE          NOT NULL,
    nha_san_xuat   VARCHAR(150),
    nguong_canh_bao INT          NOT NULL DEFAULT 10,   -- ngưỡng bắn stock.low
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT ck_thuoc_gia_duong    CHECK (gia >= 0),
    CONSTRAINT ck_thuoc_ton_khong_am CHECK (so_luong_ton >= 0)
);
CREATE INDEX idx_thuoc_ten ON THUOC (ten_thuoc);

CREATE TABLE BAN_KE_CP (
    ma_ban_ke     UUID          PRIMARY KEY,
    ma_ho_so      UUID          NOT NULL,          -- tham chiếu clinical-service
    ma_benh_nhan  UUID          NOT NULL,          -- tham chiếu patient-service
    ma_bac_si     UUID          NOT NULL,          -- tham chiếu organization-service
    ma_khoa       UUID          NOT NULL,          -- tham chiếu organization-service KHOA
    ngay_ke       DATE          NOT NULL,
    tong_tien     DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);
CREATE INDEX idx_ban_ke_benh_nhan ON BAN_KE_CP (ma_benh_nhan);
CREATE INDEX idx_ban_ke_khoa      ON BAN_KE_CP (ma_khoa);

CREATE TABLE CHI_TIET_BAN_KE (
    ma_chi_tiet   UUID          PRIMARY KEY,
    ma_ban_ke     UUID          NOT NULL REFERENCES BAN_KE_CP(ma_ban_ke) ON DELETE CASCADE,
    ma_thuoc      UUID          NOT NULL REFERENCES THUOC(ma_thuoc),
    so_luong      INT           NOT NULL,
    don_gia       DECIMAL(15,2) NOT NULL,          -- ẢNH CHỤP giá tại thời điểm kê đơn
    lieu_dung     VARCHAR(255),
    thanh_tien    DECIMAL(15,2) NOT NULL,
    CONSTRAINT ck_chi_tiet_so_luong CHECK (so_luong > 0)
);
CREATE INDEX idx_chi_tiet_ban_ke ON CHI_TIET_BAN_KE (ma_ban_ke);

CREATE TABLE PHIEU_XUAT (
    ma_phieu_xuat  UUID          PRIMARY KEY,
    ma_ban_ke      UUID          NOT NULL UNIQUE REFERENCES BAN_KE_CP(ma_ban_ke),
    trang_thai     VARCHAR(20)   NOT NULL DEFAULT 'CHO_XUAT',
    ngay_xuat      TIMESTAMPTZ,
    nguoi_xuat     UUID,                            -- tham chiếu organization-service NHAN_VIEN
    ly_do_that_bai VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_phieu_xuat_trang_thai ON PHIEU_XUAT (trang_thai);

CREATE TABLE SU_KIEN_DA_XU_LY (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    xu_ly_luc    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

> `don_gia` trên dòng chi tiết là **ảnh chụp**. Không bao giờ join sang `THUOC.gia` để tính lại một
> đơn thuốc cũ — giá trong danh mục thay đổi, còn tổng tiền lịch sử thì không được đổi.

## 2. Enum

```java
public enum TrangThaiPhieuXuat { CHO_XUAT, DA_XUAT, THAT_BAI }
```

## 3. Domain model

### `Thuoc`

Các trường: `maThuoc`, `tenThuoc`, `hoatChat`, `donViTinh`, `gia` (`BigDecimal`), `soLuongTon` (`int`),
`hanSuDung` (`LocalDate`), `nhaSanXuat`, `nguongCanhBao`, timestamps.

```java
public static Thuoc taoMoi(String tenThuoc, String hoatChat, String donViTinh, BigDecimal gia,
                           int soLuongTon, LocalDate hanSuDung, String nhaSanXuat, int nguongCanhBao);
public void capNhatThongTin(String tenThuoc, String hoatChat, String donViTinh,
                            BigDecimal gia, LocalDate hanSuDung, String nhaSanXuat, int nguongCanhBao);
public void nhapKho(int soLuong);          // soLuong > 0
public void xuatKho(int soLuong);          // BR-D1, BR-D2 — ném lỗi nếu không thể
public boolean conHang(int soLuong);       // soLuongTon >= soLuong
public boolean conHan();                   // hanSuDung >= hôm nay
public boolean duoiNguongCanhBao();        // soLuongTon <= nguongCanhBao
```

Bất biến của `xuatKho`:

| Kiểm tra | Mã lỗi |
|----------|--------|
| `soLuong <= 0` | `DRUG_QUANTITY_INVALID` |
| `soLuongTon < soLuong` | `DRUG_OUT_OF_STOCK` (BR-D1) |
| `hanSuDung < hôm nay` | `DRUG_EXPIRED` (BR-D2) |

`taoMoi`/`capNhatThongTin`: `tenThuoc` không rỗng; `gia >= 0` (`DRUG_PRICE_NEGATIVE`);
`hanSuDung` không ở quá khứ lúc tạo (`DRUG_EXPIRY_PAST`); `donViTinh` không rỗng.

### `BanKe` (đơn thuốc — aggregate root)

Các trường: `maBanKe`, `maHoSo`, `maBenhNhan`, `maBacSi`, `maKhoa`, `ngayKe`, `tongTien`,
`chiTiets` (`List<ChiTietBanKe>`).

```java
public static BanKe taoMoi(UUID maHoSo, UUID maBenhNhan, UUID maBacSi, UUID maKhoa,
                           LocalDate ngayKe, List<ChiTietBanKe> chiTiets);
public BigDecimal tinhTongTien();          // Σ thanhTien — BR-D5
public List<ChiTietBanKe> getChiTiets();   // không cho sửa
```

Bất biến: ít nhất một dòng (`PRESCRIPTION_EMPTY`); `tongTien` được **tính ra**, không bao giờ nhận
từ client.

### `ChiTietBanKe`

`maChiTiet`, `maThuoc`, `soLuong`, `donGia`, `lieuDung`, `thanhTien`.

```java
public static ChiTietBanKe taoMoi(UUID maThuoc, int soLuong, BigDecimal donGia, String lieuDung);
// thanhTien = donGia.multiply(BigDecimal.valueOf(soLuong)).setScale(2, RoundingMode.HALF_UP)
```

`soLuong > 0` (`DRUG_QUANTITY_INVALID`).

### `PhieuXuat` (phiếu xuất thuốc)

`maPhieuXuat`, `maBanKe`, `trangThai`, `ngayXuat`, `nguoiXuat`, `lyDoThatBai`.

```java
public static PhieuXuat taoChoXuat(UUID maBanKe);   // BR-D3 — luôn là CHO_XUAT
public void danhDauDaXuat(UUID nguoiXuat, Instant thoiDiem);
public void danhDauThatBai(String lyDo);
public boolean choXuat();
```

Chuyển tiếp: `CHO_XUAT → DA_XUAT | THAT_BAI`; cả hai đều là kết thúc. Còn lại ném
`DISPENSE_INVALID_TRANSITION`. Xuất một phiếu đã xuất rồi → `DISPENSE_ALREADY_DONE`.

## 4. Mã lỗi

| Mã | HTTP |
|----|------|
| `DRUG_NOT_FOUND`, `PRESCRIPTION_NOT_FOUND`, `DISPENSE_NOT_FOUND` | 404 |
| `DRUG_OUT_OF_STOCK`, `DRUG_EXPIRED`, `DRUG_QUANTITY_INVALID`, `DRUG_PRICE_NEGATIVE`, `DRUG_EXPIRY_PAST` | 422 |
| `PRESCRIPTION_EMPTY`, `DISPENSE_INVALID_TRANSITION`, `DISPENSE_ALREADY_DONE`, `DISPENSE_NOT_PAID` | 422 |

## 5. Port

```java
// out
public interface ThuocRepositoryPort {
    Thuoc save(Thuoc t);
    Optional<Thuoc> findById(UUID id);
    /** BẮT BUỘC khóa ghi bi quan — xem §10 về tương tranh. */
    Optional<Thuoc> findByIdForUpdate(UUID id);
    PageResult<Thuoc> search(String keyword, PageQuery page);
}
public interface BanKeRepositoryPort {
    BanKe save(BanKe bk);
    Optional<BanKe> findById(UUID id);
    List<BanKe> findByBenhNhan(UUID maBenhNhan);
}
public interface PhieuXuatRepositoryPort {
    PhieuXuat save(PhieuXuat px);
    Optional<PhieuXuat> findById(UUID id);
    Optional<PhieuXuat> findByBanKe(UUID maBanKe);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }

public interface PharmacyEventPublisherPort {
    void publishPrescriptionCreated(PrescriptionCreatedEvent e);
    void publishPrescriptionFilled(PrescriptionFilledEvent e);
    void publishPrescriptionDispenseFailed(PrescriptionDispenseFailedEvent e);   // bù trừ saga
    void publishStockLow(StockLowEvent e);
}

// in
public interface ManageDrugUseCase {
    DrugDTO create(CreateDrugRequest r);
    DrugDTO getById(UUID id);
    PageResult<DrugDTO> search(String keyword, PageQuery page);
    DrugDTO adjustStock(UUID id, AdjustStockRequest r);
}
public interface CreatePrescriptionUseCase { PrescriptionDTO create(CreatePrescriptionRequest r); }
public interface DispensePrescriptionUseCase { DispenseDTO dispense(UUID maBanKe, UUID nguoiXuat); }
public interface ReactToPaymentUseCase { void onPaymentCompleted(UUID maBanKe, UUID invoiceId); }
```

## 6. DTO

```java
public record CreateDrugRequest(
    @NotBlank @Size(max = 150) String tenThuoc,
    @Size(max = 150) String hoatChat,
    @NotBlank @Size(max = 20) String donViTinh,
    @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal gia,
    @NotNull @Min(0) Integer soLuongTon,
    @NotNull @Future LocalDate hanSuDung,
    @Size(max = 150) String nhaSanXuat,
    @Min(0) Integer nguongCanhBao) {}

public record AdjustStockRequest(
    @NotNull Integer soLuong,           // dương = nhập, âm = điều chỉnh giảm
    @Size(max = 255) String lyDo) {}

public record CreatePrescriptionRequest(
    @NotNull UUID maHoSo, @NotNull UUID maBenhNhan, @NotNull UUID maBacSi, @NotNull UUID maKhoa,
    @NotNull @PastOrPresent LocalDate ngayKe,
    @NotEmpty @Valid List<PrescriptionLineRequest> chiTiets) {}

public record PrescriptionLineRequest(
    @NotNull UUID maThuoc,
    @NotNull @Min(1) Integer soLuong,
    @Size(max = 255) String lieuDung) {}     // LƯU Ý: không có giá — server tự chụp giá

public record DrugDTO(UUID maThuoc, String tenThuoc, String hoatChat, String donViTinh,
                      BigDecimal gia, int soLuongTon, LocalDate hanSuDung, String nhaSanXuat,
                      int nguongCanhBao, Instant createdAt, Instant updatedAt) {}

public record PrescriptionDTO(UUID maBanKe, UUID maHoSo, UUID maBenhNhan, UUID maBacSi, UUID maKhoa,
                              LocalDate ngayKe, BigDecimal tongTien,
                              List<PrescriptionLineDTO> chiTiets,
                              TrangThaiPhieuXuat trangThaiXuat, Instant createdAt) {}

public record PrescriptionLineDTO(UUID maChiTiet, UUID maThuoc, String tenThuoc,
                                  int soLuong, BigDecimal donGia, String lieuDung, BigDecimal thanhTien) {}

public record DispenseDTO(UUID maPhieuXuat, UUID maBanKe, TrangThaiPhieuXuat trangThai,
                          Instant ngayXuat, UUID nguoiXuat, String lyDoThatBai) {}
```

## 7. Thuật toán tầng application

### `createPrescription` — `@Transactional`

1. Với mỗi dòng: `thuocRepo.findById(maThuoc)` → không có thì `DRUG_NOT_FOUND`
2. **Chụp giá**: `donGia = thuoc.getGia()` (không bao giờ lấy từ request — client không được đặt giá)
3. Dựng `ChiTietBanKe` cho mỗi dòng; `thanhTien = donGia × soLuong`, làm tròn `HALF_UP` 2 chữ số
4. `BanKe.taoMoi(...)` → tính `tongTien` (BR-D5)
5. Lưu đơn thuốc
6. `PhieuXuat.taoChoXuat(maBanKe)` → lưu (**BR-D3** — luôn tạo, luôn ở `CHO_XUAT`)
7. Publish `PrescriptionCreatedEvent` sau commit → billing tạo hóa đơn

> **KHÔNG trừ kho ở đây.** Kê đơn chỉ là ghi nhận ý định. Kho chỉ biến động lúc xuất thuốc, sau khi
> đã thanh toán. Trừ kho hai lần là lỗi dễ xảy ra nhất trong service này.

### `dispense(maBanKe, nguoiXuat)` — `@Transactional`

Được gọi từ cả `PUT /prescriptions/{id}/dispense` lẫn consumer của `payment.completed`.

1. `phieuXuatRepo.findByBanKe(maBanKe)` → không có thì `DISPENSE_NOT_FOUND`
2. nếu `!px.choXuat()` → `DISPENSE_ALREADY_DONE` *(idempotent: một `payment.completed` bị gửi lại không được xuất thuốc hai lần)*
3. Nạp đơn thuốc và các dòng của nó
4. **Sắp xếp các dòng theo `maThuoc`** trước khi khóa — tránh deadlock khi có nhiều lượt xuất đồng thời
5. Với mỗi dòng: `thuocRepo.findByIdForUpdate(maThuoc)` (khóa ghi bi quan), rồi `thuoc.xuatKho(soLuong)`
   - ném `DRUG_OUT_OF_STOCK` (BR-D1) hoặc `DRUG_EXPIRED` (BR-D2)
6. **Khi có bất kỳ lỗi nào:** phần trừ kho được hoàn tác (transaction lo việc đó), đặt
   `px.danhDauThatBai(lyDo)`, lưu phiếu **trong một transaction mới** (`REQUIRES_NEW`), rồi publish
   `PrescriptionDispenseFailedEvent` → billing bù trừ (BR-D6)
7. Khi thành công: lưu từng thuốc; `px.danhDauDaXuat(nguoiXuat, now)`; lưu
8. Với mỗi thuốc giờ `duoiNguongCanhBao()` → publish `StockLowEvent`
9. Publish `PrescriptionFilledEvent` sau commit

> Bước 6 là nhánh bù trừ. Phiếu xuất **vẫn phải ghi được trạng thái thất bại dù transaction trừ kho
> đã rollback** — vì thế mới cần `REQUIRES_NEW`. Làm sai chỗ này thì saga treo vĩnh viễn.

### `onPaymentCompleted(maBanKe, invoiceId)` — bước tiến của saga

1. `processed.alreadyProcessed(eventId)` → return
2. gọi `dispense(maBanKe, SYSTEM_USER)`
3. `processed.markProcessed(...)` trong cùng transaction
4. Lỗi **không** được ném ngược lại cho Rabbit — chúng đã được publish thành
   `PrescriptionDispenseFailedEvent`. Chỉ lỗi hạ tầng mới được cho vào dead-letter.

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/pharmacy/drugs?keyword&page&size` | — | `PageResult<DrugDTO>` | ADMIN, DOCTOR, PHARMACIST |
| GET | `/api/v1/pharmacy/drugs/{id}` | — | `DrugDTO` | ADMIN, DOCTOR, PHARMACIST |
| POST | `/api/v1/pharmacy/drugs` | `CreateDrugRequest` | 201 | ADMIN, PHARMACIST |
| PUT | `/api/v1/pharmacy/drugs/{id}/stock` | `AdjustStockRequest` | 200 | ADMIN, PHARMACIST |
| POST | `/api/v1/pharmacy/prescriptions` | `CreatePrescriptionRequest` | 201 | ADMIN, DOCTOR |
| GET | `/api/v1/pharmacy/prescriptions/{id}` | — | `PrescriptionDTO` | ADMIN, DOCTOR, PHARMACIST |
| PUT | `/api/v1/pharmacy/prescriptions/{id}/dispense` | — | `DispenseDTO` | ADMIN, PHARMACIST |

## 9. Event

**Publish**

| Routing key | Payload | Khi nào |
|-------------|---------|---------|
| `prescription.created` | `{envelope, prescriptionId, patientId, recordId, maKhoa, totalAmount, items[]}` | sau khi `createPrescription` commit |
| `prescription.filled` | `{envelope, prescriptionId, patientId, maKhoa, totalAmount, dispensedItems[]}` | sau khi xuất thuốc thành công |
| `prescription.dispense.failed` | `{envelope, prescriptionId, invoiceId, patientId, reason}` | xuất thất bại — **kích hoạt bù trừ** |
| `stock.low` | `{envelope, drugId, drugName, currentStock, threshold}` | tồn kho chạm hoặc dưới `nguong_canh_bao` |

**Subscribe** — queue `pharmacy.q`

| Routing key | Xử lý |
|-------------|-------|
| `payment.completed` | `onPaymentCompleted` → xuất thuốc (`CHO_XUAT → DA_XUAT`) |

## 10. Tương tranh — đọc kỹ trước khi viết `dispense`

Hai dược sĩ xuất cùng một loại thuốc cùng lúc sẽ bán vượt tồn kho nếu không khóa khi đọc. Ràng buộc
`CHECK (so_luong_ton >= 0)` là tuyến phòng thủ cuối cùng, không phải tuyến đầu.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT t FROM ThuocJpaEntity t WHERE t.maThuoc = :id")
Optional<ThuocJpaEntity> findByIdForUpdate(@Param("id") UUID id);
```

Quy tắc:
1. Luôn khóa qua `findByIdForUpdate` bên trong `dispense` — không bao giờ dùng `findById` thường.
2. Luôn khóa các dòng **đã sắp xếp theo `maThuoc`**. Khóa không sắp xếp trên đơn nhiều thuốc sẽ gây deadlock.
3. Giữ transaction ngắn: không gọi REST, không publish event trong khoảng thời gian đang giữ khóa.

## 11. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-D1 | Không xuất khi `so_luong_ton < số lượng yêu cầu` | `dispense_insufficientStock_throwsOutOfStock` |
| BR-D2 | Không xuất khi `han_su_dung < hôm nay` | `dispense_expiredDrug_throwsExpired` |
| BR-D3 | Tạo đơn thuốc thì tự tạo phiếu `CHO_XUAT` | `createPrescription_alsoCreatesPendingDispense` |
| BR-D4 | Xuất thuốc trừ kho đúng một lần | `dispense_valid_decrementsStockOnce` |
| BR-D5 | `tong_tien = Σ(so_luong × don_gia)` | `createPrescription_computesTotalFromLines` |
| BR-D6 | Xuất thất bại thì publish event bù trừ | `dispense_outOfStock_publishesDispenseFailed` |
| BR-D7 | Giá được chụp lại, không tra cứu về sau | `createPrescription_priceChangeLater_totalUnchanged` |
| BR-D8 | Giá do client gửi bị bỏ qua | `createPrescription_requestHasNoPriceField` |
| BR-D9 | `payment.completed` gửi lại chỉ xuất một lần | `onPaymentCompleted_sameEventTwice_dispensesOnce` |
| BR-D10 | Tồn kho không bao giờ âm khi tương tranh | `dispense_twoConcurrentCalls_onlyOneSucceeds` |
| BR-D11 | Chạm ngưỡng thì publish `stock.low` | `dispense_stockFallsBelowThreshold_publishesStockLow` |
| BR-D12 | Xuất thất bại vẫn ghi phiếu là `THAT_BAI` | `dispense_failure_slipPersistedAsThatBai` |

BR-D10 cần test tương tranh thật: hai luồng, `@SpringBootTest` + Testcontainers, một thuốc có
`so_luong_ton = 1`, hai lượt xuất mỗi lượt 1 → đúng một lượt thành công.

## 12. Điểm dễ sai

- Luôn dùng `BigDecimal`. `setScale(2, RoundingMode.HALF_UP)` cho mọi phép tính tiền. Không bao giờ dùng `double`.
- `PHIEU_XUAT.ma_ban_ke` là `UNIQUE` — mỗi đơn thuốc đúng một phiếu. Hàm `findByBanKe` dựa vào ràng buộc này.
- Consumer của `payment.completed` và endpoint `PUT .../dispense` gọi **cùng một** use case. Đừng viết logic hai lần.
- `stock.low` là kiểu bắn-rồi-quên: đừng để việc publish nó thất bại làm rollback một lượt xuất thuốc đã thành công.
