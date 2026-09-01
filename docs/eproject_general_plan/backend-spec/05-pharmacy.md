# 05 — pharmacy-service (Khoa Dược)

**Module:** `backend/pharmacy-service/` · **Package:** `com.mediflow.pharmacy` · **Cổng chạy:** 8085 · **DB:** `mediflow_pharmacy` · **Tiền tố:** `/api/v1/pharmacy`

## Giới thiệu (đọc trước)

Pharmacy lo 4 việc: danh mục thuốc, tồn kho, đơn thuốc và phiếu xuất thuốc. Nó là service **duy nhất có thể "hết hàng"**, nên tham gia saga *kê đơn → hóa đơn → thanh toán → xuất thuốc*:

- Kê đơn xong → phát `prescription.created` để billing tạo hóa đơn.
- Bệnh nhân trả tiền xong → billing phát `payment.completed` → pharmacy mới xuất thuốc.
- Xuất thất bại (hết hàng / hết hạn) → phát `prescription.dispense.failed` để billing bù trừ.

**Cách đọc spec này:** mỗi mục đều có phần giải thích đơn giản trước, code/chữ ký Java để sau. Nếu chỉ cần nắm nghiệp vụ thì đọc `docs/ai/services/pharmacy.md`; cần code thì đọc file này.

**Quy ước đặt tên của nhánh C (bắt buộc):** bảng/cột/class dùng **tiếng Anh**; các service khác trong hệ thống vẫn dùng tiếng Việt snake_case (xem `docs/ai/08-persistence-naming.md`). Bảng dịch chính thức:

| Việt (trước) | Anh (sau) | Dùng trong |
|---|---|---|
| THUOC | DRUG | bảng |
| ma_thuoc / ten_thuoc / hoat_chat / don_vi_tinh / gia / so_luong_ton / han_su_dung / nha_san_xuat / nguong_canh_bao | drug_id / drug_name / active_ingredient / unit / price / stock_quantity / expiry_date / manufacturer / low_stock_threshold | cột DRUG |
| BAN_KE_CP | PRESCRIPTION | bảng |
| ma_ban_ke / ma_ho_so / ma_benh_nhan / ma_bac_si / ma_khoa / ngay_ke / tong_tien | prescription_id / record_id / patient_id / doctor_id / department_id / prescribed_date / total_amount | cột PRESCRIPTION |
| CHI_TIET_BAN_KE | PRESCRIPTION_LINE | bảng |
| ma_chi_tiet / ma_thuoc / so_luong / don_gia / lieu_dung / thanh_tien | line_id / drug_id / quantity / unit_price / dosage / line_total | cột PRESCRIPTION_LINE |
| PHIEU_XUAT | DISPENSE_SLIP | bảng |
| ma_phieu_xuat / trang_thai / ngay_xuat / nguoi_xuat / ly_do_that_bai | dispense_id / status / dispensed_at / dispensed_by / failure_reason | cột DISPENSE_SLIP |
| SU_KIEN_DA_XU_LY / xu_ly_luc | PROCESSED_EVENT / processed_at | bảng/cột |
| TrangThaiPhieuXuat{CHO_XUAT, DA_XUAT, THAT_BAI} | DispenseStatus{PENDING, DISPENSED, FAILED} | enum |
| Thuoc / BanKe / ChiTietBanKe / PhieuXuat | Drug / Prescription / PrescriptionLine / DispenseSlip | class |

## 1. Lược đồ — `V1__init.sql`

> Bảng và cột dùng tiếng Anh theo quy ước trên. UUID khóa chính do Hibernate sinh; tiền = `DECIMAL(15,2)`; ngày = `DATE` / `TIMESTAMPTZ`.

```sql
CREATE TABLE DRUG (
    drug_id             UUID          PRIMARY KEY,
    drug_name           VARCHAR(150)  NOT NULL,
    active_ingredient   VARCHAR(150),
    unit                VARCHAR(20)   NOT NULL,
    price               DECIMAL(15,2) NOT NULL,
    stock_quantity      INT           NOT NULL DEFAULT 0,
    expiry_date         DATE          NOT NULL,
    manufacturer        VARCHAR(150),
    low_stock_threshold INT           NOT NULL DEFAULT 10,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT ck_drug_price_positive      CHECK (price >= 0),
    CONSTRAINT ck_drug_stock_non_negative  CHECK (stock_quantity >= 0)
);
CREATE INDEX idx_drug_name ON DRUG (drug_name);

CREATE TABLE PRESCRIPTION (
    prescription_id UUID          PRIMARY KEY,
    record_id       UUID          NOT NULL,   -- tham chiếu clinical (HO_SO_BA)
    patient_id      UUID          NOT NULL,   -- tham chiếu patient (BENH_NHAN)
    doctor_id       UUID          NOT NULL,   -- tham chiếu organization (NHAN_VIEN)
    department_id   UUID          NOT NULL,   -- tham chiếu organization (KHOA — khoa kê đơn)
    prescribed_date DATE          NOT NULL,
    total_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_prescription_patient ON PRESCRIPTION (patient_id);
CREATE INDEX idx_prescription_dept   ON PRESCRIPTION (department_id);

CREATE TABLE PRESCRIPTION_LINE (
    line_id         UUID          PRIMARY KEY,
    prescription_id UUID          NOT NULL REFERENCES PRESCRIPTION(prescription_id) ON DELETE CASCADE,
    drug_id         UUID          NOT NULL REFERENCES DRUG(drug_id),
    quantity        INT           NOT NULL,
    unit_price      DECIMAL(15,2) NOT NULL,   -- giá chụp tại thời điểm kê đơn
    dosage          VARCHAR(255),
    line_total      DECIMAL(15,2) NOT NULL,
    CONSTRAINT ck_line_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_line_prescription ON PRESCRIPTION_LINE (prescription_id);

CREATE TABLE DISPENSE_SLIP (
    dispense_id     UUID          PRIMARY KEY,
    prescription_id UUID          NOT NULL UNIQUE REFERENCES PRESCRIPTION(prescription_id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    dispensed_at    TIMESTAMPTZ,
    dispensed_by    UUID,                       -- tham chiếu nhân viên thực hiện
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_dispense_status ON DISPENSE_SLIP (status);

-- Sổ ghi các event đã xử lý — dùng để chống xử lý trùng khi RabbitMQ gửi lại tin (BR-D9).
CREATE TABLE PROCESSED_EVENT (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Giữ chỗ tồn kho (stock reservation): kê đơn = "hứa" có thuốc ngay lúc kê.
-- Vòng đời: RESERVED --xuất--> FULFILLED | --hủy/hết hạn--> RELEASED / EXPIRED.
-- Số tồn "có thể bán" = stock_quantity - Σ(quantity) của các dòng RESERVED.
CREATE TABLE STOCK_RESERVATION (
    reservation_id   UUID         PRIMARY KEY,
    drug_id          UUID         NOT NULL REFERENCES DRUG(drug_id),
    prescription_id  UUID         NOT NULL REFERENCES PRESCRIPTION(prescription_id),
    quantity         INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'RESERVED',   -- RESERVED / FULFILLED / RELEASED / EXPIRED
    expires_at       TIMESTAMPTZ,                                 -- hết hạn giữ chỗ (TTL, mặc định 24h)
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT ck_reservation_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_reservation_drug          ON STOCK_RESERVATION (drug_id);
CREATE INDEX idx_reservation_prescription  ON STOCK_RESERVATION (prescription_id);
CREATE INDEX idx_reservation_status_expiry ON STOCK_RESERVATION (status, expires_at);
```

### Ý nghĩa từng bảng (đọc nhanh)

| Bảng | Vai trò | Điểm cần nhớ |
|------|---------|--------------|
| `DRUG` | danh mục thuốc + tồn kho | `price >= 0`, `stock_quantity >= 0` là phòng thủ cuối; quy tắc thật nằm trong domain model |
| `PRESCRIPTION` | đơn thuốc | chỉ giữ UUID tham chiếu, không nối DB service khác |
| `PRESCRIPTION_LINE` | từng dòng thuốc trong đơn | `unit_price` là **ảnh chụp giá** lúc kê đơn — sau này đổi giá không ảnh hưởng đơn cũ (BR-D7) |
| `DISPENSE_SLIP` | phiếu xuất thuốc | `prescription_id` **UNIQUE** — mỗi đơn đúng một phiếu |
| `PROCESSED_EVENT` | sổ chống trùng | mỗi `eventId` chỉ được xử lý một lần (BR-D9) |
## 2. Enum

```java
public enum DispenseStatus { PENDING, DISPENSED, FAILED }
```

Giải thích: trạng thái của phiếu xuất.
- `PENDING` — đơn đã kê, chưa xuất (chờ thanh toán).
- `DISPENSED` — đã xuất thuốc (kết thúc).
- `FAILED` — xuất thất bại, kèm lý do (kết thúc).

```java
public enum ReservationStatus { RESERVED, FULFILLED, RELEASED, EXPIRED }
```

Giải thích: trạng thái của một dòng giữ chỗ tồn kho (STOCK_RESERVATION).
- `RESERVED` — đang giữ chỗ (chưa xuất, đơn chưa thanh toán) — **chỉ trạng thái này** được tính vào số tồn "có thể bán".
- `FULFILLED` — đã xuất thuốc thật (reserved → stock), kết thúc.
- `RELEASED` — trả lại chỗ (hủy / giải phóng chủ động), kết thúc.
- `EXPIRED` — trả lại chỗ do hết hạn TTL, kết thúc.

## 3. Domain model

> Tên class/field dùng tiếng Anh (đồng bộ với bảng): `Thuoc` → `Drug`, `maThuoc` → `drugId`, `BanKe` → `Prescription`, `PhieuXuat` → `DispenseSlip`, ...

**Nguyên tắc chung:** mọi quy tắc nghiệp vụ nằm trong model — domain không import Spring, không import JPA. Lớp model chỉ có 2 cách tạo: `create(...)` (khi nhận yêu cầu mới — chạy quy tắc) và `restore(...)` (dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc).

### `Drug` — thuốc

Các trường: `drugId`, `drugName`, `activeIngredient`, `unit`, `price` (`BigDecimal`), `stockQuantity` (`int`), `expiryDate` (`LocalDate`), `manufacturer`, `lowStockThreshold`, timestamps.

```java
public static Drug create(String drugName, String activeIngredient, String unit, BigDecimal price,
                          int stockQuantity, LocalDate expiryDate, String manufacturer, int lowStockThreshold);
public void updateInfo(String drugName, String activeIngredient, String unit,
                       BigDecimal price, LocalDate expiryDate, String manufacturer, int lowStockThreshold);
public void restock(int quantity);           // quantity > 0
public void dispenseStock(int quantity);     // BR-D1, BR-D2 — ném lỗi nếu không xuất được
public boolean hasStock(int quantity);       // stockQuantity >= quantity
public boolean isExpired();                  // expiryDate < hôm nay
public boolean belowLowStockThreshold();     // stockQuantity <= lowStockThreshold
```

Quy tắc trong `dispenseStock` (xuất kho):

| Kiểm tra | Mã lỗi |
|----------|--------|
| `quantity <= 0` | `DRUG_QUANTITY_INVALID` |
| `stockQuantity < quantity` | `DRUG_OUT_OF_STOCK` (BR-D1) |
| `expiryDate < hôm nay` | `DRUG_EXPIRED` (BR-D2) |

Quy tắc trong `create` / `updateInfo`: tên thuốc không rỗng; giá >= 0 (`DRUG_PRICE_NEGATIVE`); hạn dùng không được ở quá khứ (`DRUG_EXPIRY_PAST`); đơn vị không rỗng.

### `Prescription` — đơn thuốc (aggregate root)

Các trường: `prescriptionId`, `recordId`, `patientId`, `doctorId`, `departmentId`, `prescribedDate`, `totalAmount`, `lines` (`List<PrescriptionLine>`).

```java
public static Prescription create(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
                                  LocalDate prescribedDate, List<PrescriptionLine> lines);
public BigDecimal computeTotal();            // Σ lineTotal — BR-D5
public List<PrescriptionLine> getLines();    // không cho sửa (unmodifiable)
```

Quy tắc: đơn phải có ít nhất một dòng (`PRESCRIPTION_EMPTY`); `totalAmount` được **tính ra**, không bao giờ nhận từ client.

### `PrescriptionLine` — một dòng thuốc trong đơn

`lineId`, `drugId`, `quantity`, `unitPrice`, `dosage`, `lineTotal`.

```java
public static PrescriptionLine create(UUID drugId, int quantity, BigDecimal unitPrice, String dosage);
// lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP)
```

Quy tắc: `quantity > 0` (`DRUG_QUANTITY_INVALID`).

### `DispenseSlip` — phiếu xuất thuốc

`dispenseId`, `prescriptionId`, `status`, `dispensedAt`, `dispensedBy`, `failureReason`.

```java
public static DispenseSlip createPending(UUID prescriptionId);   // BR-D3 — luôn là PENDING
public void markDispensed(UUID dispensedBy, Instant timestamp);
public void markFailed(String reason);
public boolean isPending();
```

Quy tắc chuyển trạng thái: `PENDING → DISPENSED | FAILED`; cả hai đều là kết thúc, không quay lại được. Chuyển sai trạng thái → ném `DISPENSE_INVALID_TRANSITION`. Xuất một phiếu đã xuất rồi → `DISPENSE_ALREADY_DONE`.

### `StockReservation` — dòng giữ chỗ tồn kho

`reservationId`, `drugId`, `prescriptionId`, `quantity`, `status`, `expiresAt`, timestamps.

```java
public static StockReservation create(UUID drugId, UUID prescriptionId, int quantity, Instant expiresAt);
public void markFulfilled();   // RESERVED → FULFILLED
public void release();         // RESERVED → RELEASED
public void expire();          // RESERVED → EXPIRED
public boolean isReserved();
```

Quy tắc: `quantity > 0` (`RESERVATION_QUANTITY_INVALID`); `expiresAt` bắt buộc (`RESERVATION_EXPIRY_REQUIRED`); chỉ `RESERVED` mới chuyển tiếp được (`RESERVATION_INVALID_TRANSITION`).
## 4. Mã lỗi

| Mã | HTTP | Tình huống |
|----|------|------------|
| `DRUG_NOT_FOUND`, `PRESCRIPTION_NOT_FOUND`, `DISPENSE_NOT_FOUND` | 404 | không tìm thấy đối tượng |
| `DRUG_OUT_OF_STOCK`, `DRUG_EXPIRED`, `DRUG_QUANTITY_INVALID`, `DRUG_PRICE_NEGATIVE`, `DRUG_EXPIRY_PAST` | 422 | vi phạm quy tắc thuốc |
| `PRESCRIPTION_EMPTY`, `DISPENSE_INVALID_TRANSITION`, `DISPENSE_ALREADY_DONE`, `DISPENSE_NOT_PAID` | 422 | vi phạm quy tắc đơn / phiếu |
| `INSUFFICIENT_AVAILABLE_STOCK` | 422 | tồn khả dụng (sau khi trừ dự trữ) không đủ khi kê đơn |
| `RESERVATION_QUANTITY_INVALID`, `RESERVATION_EXPIRY_REQUIRED`, `RESERVATION_INVALID_TRANSITION`, `RESERVATION_MISSING` | 422 | vi phạm quy tắc giữ chỗ |

## 5. Port

> **Port là gì?** Port = bản hợp đồng giữa application và thế giới bên ngoài: danh sách các phương thức đã thống nhất, chỉ có tên và tham số, không có code làm thật.
> - **In-port (cổng vào):** bên ngoài (web, RabbitMQ) nhờ service làm gì.
> - **Out-port (cổng ra):** service nhờ bên ngoài làm gì (lưu DB, gửi event).
> - **Adapter (bộ chuyển đổi):** code thật sự nối DB/RabbitMQ, đứng sau out-port.
>
> Vì sao phải có port? Vì application giữ toàn bộ quy tắc nghiệp vụ; nếu application gọi thẳng JPA hay RabbitMQ thì đổi công nghệ phải sửa cả quy tắc. Có port thì chỉ cần thay adapter.

### 5.1 Out-port — `application/port/out/`

#### `DrugRepositoryPort` — lưu và tìm thuốc

**Vì sao cần:** mọi thay đổi về thuốc (tạo, nhập kho, trừ kho) phải ghi xuống DB, nhưng application không được phép biết JPA. Interface này là "lời hứa": *ai đó hãy lưu và tìm thuốc giúp tôi* — `DrugPersistenceAdapter` trong `infrastructure` làm thật.

```java
public interface DrugRepositoryPort {
    Drug save(Drug t);
    Optional<Drug> findById(UUID id);
    /** BẮT BUỘC khóa ghi bi quan — chỉ dùng trong luồng dispense, xem §10. */
    Optional<Drug> findByIdForUpdate(UUID id);
    PageResult<Drug> search(String keyword, PageQuery page);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `save` | lưu mới / cập nhật thuốc | mọi luồng thay đổi thuốc |
| `findById` | đọc 1 thuốc (kê đơn lấy giá, xem chi tiết) | — |
| `findByIdForUpdate` | đọc 1 thuốc **kèm khóa ghi** | BR-D10 — hai dược sĩ xuất cùng lúc không được bán vượt kho |
| `search` | tìm theo tên, phân trang | dùng `PageQuery`/`PageResult` của `common`, không dùng `Pageable` của Spring |

#### `PrescriptionRepositoryPort` — lưu và tìm đơn thuốc

**Vì sao cần:** đơn thuốc (kèm các dòng) phải được ghi nhận và đọc lại để phục vụ xem lịch sử và xuất thuốc.

```java
public interface PrescriptionRepositoryPort {
    Prescription save(Prescription p);
    Optional<Prescription> findById(UUID id);
    List<Prescription> findByPatient(UUID patientId);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `save` | lưu đơn + các dòng (một aggregate) | BR-D3 — kê đơn phải tự tạo phiếu xuất |
| `findById` | đọc đơn + các dòng | luồng `dispense` cần biết đơn kê những gì |
| `findByPatient` | danh sách đơn của 1 bệnh nhân | màn hình lịch sử kê đơn |

#### `DispenseSlipRepositoryPort` — lưu và tìm phiếu xuất

**Vì sao cần:** trạng thái phiếu phải được ghi nhận — đó là bằng chứng của saga: đã xuất chưa, thất bại vì lý do gì.

```java
public interface DispenseSlipRepositoryPort {
    DispenseSlip save(DispenseSlip s);
    Optional<DispenseSlip> findById(UUID id);
    Optional<DispenseSlip> findByPrescription(UUID prescriptionId);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `save` | lưu phiếu | BR-D3, BR-D12 — phiếu `FAILED` vẫn phải lưu được |
| `findById` | đọc 1 phiếu | xem trạng thái |
| `findByPrescription` | tìm phiếu theo đơn | cột UNIQUE — mỗi đơn đúng 1 phiếu |

#### `ProcessedEventPort` — sổ chống xử lý trùng

**Vì sao cần:** RabbitMQ có thể gửi lại cùng một event. Không kiểm tra thì một tin `payment.completed` đến hai lần sẽ xuất thuốc hai lần.

```java
public interface ProcessedEventPort {
    boolean alreadyProcessed(UUID id);
    void markProcessed(UUID id, String rk);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `alreadyProcessed` | kiểm tra event đã xử lý chưa | BR-D9 |
| `markProcessed` | đánh dấu đã xử lý | BR-D9 |

#### `StockReservationRepositoryPort` — lưu và tìm giữ chỗ tồn kho

**Vì sao cần:** kê đơn phải xác nhận "còn đủ thuốc có thể bán" (trừ dự trữ), xuất thuốc phải chuyển dự trữ → kho thật, job TTL phải tìm các dòng hết hạn để trả chỗ.

```java
public interface StockReservationRepositoryPort {
    StockReservation save(StockReservation r);
    List<StockReservation> findByPrescription(UUID prescriptionId);
    List<StockReservation> findReservedByDrug(UUID drugId);              // tính tồn khả dụng khi kê
    List<StockReservation> findExpired();                                 // job release TTL
    Optional<StockReservation> findReservedByPrescriptionForUpdate(UUID prescriptionId, UUID drugId); // khóa ghi
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `save` | tạo / cập nhật giữ chỗ | kê đơn (reserve), dispense (fulfill), job release (expire) |
| `findByPrescription` | dispense biết đơn đã giữ những gì | — |
| `findReservedByDrug` | tính tồn khả dụng khi kê | BR-D1 (mở rộng: kể cả dự trữ) |
| `findExpired` | job TTL | release |
| `findReservedByPrescriptionForUpdate` | khóa ghi giữ chỗ trong dispense | BR-D10 |

#### `PharmacyEventPublisherPort` — gửi event ra ngoài

**Vì sao cần:** application phải "báo tin" cho các service khác nhưng không được đụng RabbitMQ. Adapter trong `infrastructure/messaging` làm thật (publish **sau khi** transaction commit).

```java
public interface PharmacyEventPublisherPort {
    void publishPrescriptionCreated(PrescriptionCreatedEvent e);
    void publishPrescriptionFilled(PrescriptionFilledEvent e);
    void publishPrescriptionDispenseFailed(PrescriptionDispenseFailedEvent e);  // bù trừ saga
    void publishStockLow(StockLowEvent e);
}
```

| Phương thức | Tin gửi đi | Khi nào / vì sao |
|-------------|-----------|------------------|
| `publishPrescriptionCreated` | `prescription.created` | sau kê đơn commit — billing tạo hóa đơn (saga) |
| `publishPrescriptionFilled` | `prescription.filled` | sau xuất thuốc thành công |
| `publishPrescriptionDispenseFailed` | `prescription.dispense.failed` | xuất thất bại — kích hoạt bù trừ (BR-D6) |
| `publishStockLow` | `stock.low` | tồn kho chạm ngưỡng (BR-D11); "bắn rồi quên" — lỗi gửi tin này không được làm hỏng lần xuất thành công |

### 5.2 In-port — `application/port/in/`

#### `ManageDrugUseCase` — quản lý danh mục thuốc

**Vì sao cần:** dược sĩ/admin phải thêm thuốc, xem thuốc, tìm thuốc và nhập kho. Gộp chung vì cùng một nhóm nghiệp vụ "quản lý danh mục".

```java
public interface ManageDrugUseCase {
    DrugDTO create(CreateDrugRequest r);
    DrugDTO getById(UUID id);
    PageResult<DrugDTO> search(String keyword, PageQuery page);
    DrugDTO adjustStock(UUID id, AdjustStockRequest r);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `create` | thêm thuốc mới | tên/đơn vị bắt buộc, giá không âm, hạn dùng không ở quá khứ |
| `getById` | xem 1 thuốc | không có → 404 `DRUG_NOT_FOUND` |
| `search` | tìm theo tên, phân trang | màn hình danh sách thuốc |
| `adjustStock` | nhập kho / điều chỉnh | số lượng > 0 (`Drug.restock`); **chỉ chỉnh tay** — không dùng để xuất thuốc, tránh trừ kho hai lần |

#### `CreatePrescriptionUseCase` — kê đơn

**Vì sao cần:** bác sĩ ghi nhận ý định dùng thuốc — bước khởi đầu của saga, tách riêng để dễ kiểm soát.

```java
public interface CreatePrescriptionUseCase {
    PrescriptionDTO create(CreatePrescriptionRequest r);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `create` | tạo đơn + các dòng | đơn ≥ 1 dòng (`PRESCRIPTION_EMPTY`); server tự lấy giá — client không gửi giá (BR-D7, BR-D8); tổng = Σ (BR-D5); tự tạo phiếu `PENDING` (BR-D3); **không trừ kho** |

#### `DispensePrescriptionUseCase` — xuất thuốc

**Vì sao cần:** bước duy nhất làm biến động kho, nhiều quy tắc nhất (hết hàng, hết hạn, tương tranh, bù trừ) — cần xử lý đặc biệt (khóa dòng, bù trừ).

```java
public interface DispensePrescriptionUseCase {
    DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `dispense` | xuất thuốc theo đơn | phiếu đang `PENDING` (BR-D9); đủ hàng (BR-D1); chưa hết hạn (BR-D2); khóa dòng (BR-D10); trừ kho 1 lần (BR-D4); thất bại → phiếu `FAILED` + event bù trừ (BR-D6, BR-D12); chạm ngưỡng → `stock.low` (BR-D11); thành công → `prescription.filled` |

#### `ReactToPaymentUseCase` — phản ứng khi có tin "đã thanh toán"

**Vì sao cần:** saga quy định chỉ xuất thuốc **sau khi** bệnh nhân trả tiền — `payment.completed` chính là tín hiệu đó.

```java
public interface ReactToPaymentUseCase {
    void onPaymentCompleted(UUID prescriptionId, UUID invoiceId);
}
```

| Phương thức | Dùng cho | Quy tắc khớp |
|-------------|----------|--------------|
| `onPaymentCompleted` | gọi lại `dispense` với người thực hiện = hệ thống | chống trùng qua `ProcessedEventPort` (BR-D9); lỗi nghiệp vụ không ném ngược về RabbitMQ |

#### `ReleaseExpiredReservationsUseCase` — trả lại chỗ giữ quá hạn

**Vì sao cần:** đơn kê chưa thanh toán sẽ giữ chỗ vô thời hạn nếu không có cơ chế hết hạn. Job định kỳ gọi use case này để giải phóng.

```java
public interface ReleaseExpiredReservationsUseCase {
    int releaseExpiredReservations();
}
```

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `releaseExpiredReservations` | đổi các giữ chỗ `RESERVED` quá hạn → `EXPIRED` (trả lại chỗ) | job TTL |
## 6. DTO

> DTO là "túi đựng dữ liệu" đi qua biên giới HTTP — Java record, có Bean Validation. Client gửi request; server trả response. **Entity/domain model không bao giờ đi thẳng ra ngoài.**

```java
public record CreateDrugRequest(
    @NotBlank @Size(max = 150) String drugName,
    @Size(max = 150) String activeIngredient,
    @NotBlank @Size(max = 20) String unit,
    @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal price,
    @NotNull @Min(0) Integer stockQuantity,
    @NotNull @Future LocalDate expiryDate,
    @Size(max = 150) String manufacturer,
    @Min(0) Integer lowStockThreshold) {}

public record AdjustStockRequest(
    @NotNull Integer quantity,          // dương = nhập kho, âm = điều chỉnh giảm
    @Size(max = 255) String reason) {}

public record CreatePrescriptionRequest(
    @NotNull UUID recordId, @NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID departmentId,
    @NotNull @PastOrPresent LocalDate prescribedDate,
    @NotEmpty @Valid List<PrescriptionLineRequest> lines) {}

public record PrescriptionLineRequest(
    @NotNull UUID drugId,
    @NotNull @Min(1) Integer quantity,
    @Size(max = 255) String dosage) {}     // LƯU Ý: không có trường giá — server tự chụp giá

public record DrugDTO(UUID drugId, String drugName, String activeIngredient, String unit,
                      BigDecimal price, int stockQuantity, LocalDate expiryDate, String manufacturer,
                      int lowStockThreshold, Instant createdAt, Instant updatedAt) {}

public record PrescriptionDTO(UUID prescriptionId, UUID recordId, UUID patientId, UUID doctorId,
                              UUID departmentId, LocalDate prescribedDate, BigDecimal totalAmount,
                              List<PrescriptionLineDTO> lines,
                              DispenseStatus dispenseStatus, Instant createdAt) {}

public record PrescriptionLineDTO(UUID lineId, UUID drugId, String drugName,
                                  int quantity, BigDecimal unitPrice, String dosage, BigDecimal lineTotal) {}

public record DispenseDTO(UUID dispenseId, UUID prescriptionId, DispenseStatus status,
                          Instant dispensedAt, UUID dispensedBy, String failureReason) {}
```

Điểm dễ quên: `PrescriptionLineRequest` **không có giá** (BR-D8) — nếu thấy client gửi giá thì bỏ qua, server luôn lấy giá từ kho tại thời điểm kê đơn.

## 7. Thuật toán tầng application

> Tầng application = nơi "điều phối": nhận lệnh từ in-port, hỏi out-port, gọi domain model. Nó không import Spring Data, AMQP, HTTP.

### 7.1 Kê đơn (`createPrescription`) — `@Transactional`

1. Sắp xếp các dòng theo `drugId` (khóa đều, tránh deadlock — BR-D10).
2. Với mỗi dòng: `drugRepo.findByIdForUpdate(drugId)` → không có thì `DRUG_NOT_FOUND`.
3. **Kiểm tra tồn khả dụng + giữ chỗ:** `available = stockQuantity - Σ(reserved của drug)`; nếu `available < quantity` → ném `INSUFFICIENT_AVAILABLE_STOCK` (**422**, BR-D1 mở rộng) — bác sĩ thấy ngay lúc kê, đơn chưa tạo.
4. **Chụp giá:** `unitPrice = drug.getPrice()` — không bao giờ lấy từ request (client không được đặt giá).
5. Dựng `PrescriptionLine` cho mỗi dòng; `lineTotal = unitPrice × quantity`, làm tròn `HALF_UP` 2 chữ số.
6. `Prescription.create(...)` → tính `totalAmount` (BR-D5).
7. Lưu đơn thuốc.
8. Tạo `StockReservation{RESERVED, expiresAt = now + TTL(24h)}` cho từng dòng (BR-D3 mở rộng) — **giữ chỗ tồn kho**.
9. `DispenseSlip.createPending(prescriptionId)` → lưu (**BR-D3** — luôn tạo, luôn `PENDING`).
10. Publish `PrescriptionCreatedEvent` sau commit → billing tạo hóa đơn.

> **KHÔNG trừ kho ở đây.** Kê đơn **giữ chỗ** (reserve) nhưng không trừ kho thật; kho chỉ biến động khi xuất thuốc. Trừ kho hai lần là lỗi dễ xảy ra nhất của service này. Mục đích giữ chỗ: bệnh nhân biết trước lượng thuốc có thể bán, không còn cảnh trả tiền rồi mới hết hàng.

### 7.2 Xuất thuốc (`dispense(prescriptionId, dispensedBy)`) — `@Transactional`

Được gọi từ cả `PUT /prescriptions/{id}/dispense` lẫn consumer của `payment.completed` — dùng **chung một** use case, không viết logic hai lần.

1. `dispenseSlipRepo.findByPrescription(prescriptionId)` → không có thì `DISPENSE_NOT_FOUND`.
2. Nếu `!slip.isPending()` → `DISPENSE_ALREADY_DONE` (chống xuất 2 lần — một `payment.completed` bị gửi lại không được xuất thuốc hai lần).
3. Nạp đơn thuốc + các dòng của nó.
4. **Sắp xếp các dòng theo `drugId`** trước khi khóa — tránh deadlock khi nhiều lần xuất đồng thời.
5. Mỗi dòng: `drugRepo.findByIdForUpdate(drugId)` (khóa ghi bi quan) → `reservationRepo.findReservedByPrescriptionForUpdate(prescriptionId, drugId)` — giữ chỗ phải còn `RESERVED` (mất thì `RESERVATION_MISSING`) → `reservation.markFulfilled()` (RESERVED → FULFILLED) → `drug.dispenseStock(quantity)`; kiểm tra hết hạn (BR-D2) lúc xuất; ném `DRUG_OUT_OF_STOCK` (BR-D1) nếu dữ liệu không nhất quán.
6. **Khi có bất kỳ lỗi nào:** phần trừ kho bị hoàn tác (transaction rollback), đặt `slip.markFailed(reason)`, lưu phiếu **trong một transaction mới** (`REQUIRES_NEW`), rồi publish `PrescriptionDispenseFailedEvent` (kèm `failedItems[]`) → billing bù trừ (BR-D6).
7. Khi thành công: lưu từng thuốc; `slip.markDispensed(dispensedBy, now)`; lưu phiếu.
8. Thuốc nào `belowLowStockThreshold()` → publish `StockLowEvent`.
9. Publish `PrescriptionFilledEvent` sau commit.

> **Với mô hình reservation, "hết hàng" ở đây gần như không còn xảy ra** — đã giữ chỗ lúc kê. Còn xảy ra là lỗi hệ thống / giữ chỗ thất lạc, vẫn rơi vào nhánh bù trừ §6.

> Bước 6 là nhánh bù trừ. Phiếu xuất **vẫn phải ghi FAILED dù transaction trừ kho đã rollback** — vì thế mới cần `REQUIRES_NEW`. Làm sai chỗ này thì saga treo vĩnh viễn.

### 7.3 Nhận tin "đã thanh toán" (`onPaymentCompleted(prescriptionId, invoiceId)`) — bước tiến của saga

1. `processed.alreadyProcessed(eventId)` → đã xử lý rồi thì return.
2. Gọi `dispense(prescriptionId, SYSTEM_USER)` — người thực hiện là hệ thống.
3. `processed.markProcessed(...)` trong cùng transaction.
4. Lỗi **không** ném ngược lại cho RabbitMQ — lỗi nghiệp vụ đã được publish thành `PrescriptionDispenseFailedEvent`. Chỉ lỗi hạ tầng mới được đưa vào dead-letter.
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

> Phân quyền ghi ở controller (`@PreAuthorize`) — đây là lớp "giao hàng", không phải quy tắc nghiệp vụ (xem `docs/ai/07-security-rbac.md`).

## 9. Event

> Quy tắc chung (`docs/ai/06-events-rabbitmq.md`): mọi event bắt đầu bằng `eventId, occurredAt, correlationId`; publish **sau khi** transaction commit; consumer phải chống trùng (dedupe theo `eventId`).

### Publish

| Routing key | Payload | Khi nào |
|-------------|---------|---------|
| `prescription.created` | `{envelope, prescriptionId, patientId, recordId, departmentId, totalAmount, items[]}` | sau khi `createPrescription` commit |
| `prescription.filled` | `{envelope, prescriptionId, patientId, departmentId, totalAmount, dispensedItems[]}` | sau khi xuất thuốc thành công |
| `prescription.dispense.failed` | `{envelope, prescriptionId, invoiceId, patientId, reason, failedItems[]}` | xuất thất bại — **kích hoạt bù trừ**; `failedItems[] = [{drugId, drugName, requestedQty, availableQty}]` cho rõ thuốc nào thiếu |
| `stock.low` | `{envelope, drugId, drugName, currentStock, threshold}` | tồn kho chạm hoặc dưới `low_stock_threshold` |

### Subscribe — queue `pharmacy.q`

| Routing key | Xử lý |
|-------------|-------|
| `payment.completed` | `onPaymentCompleted` → xuất thuốc (`PENDING → DISPENSED`) |

## 10. Tương tranh — đọc kỹ trước khi viết `dispense`

Hai dược sĩ xuất cùng một loại thuốc cùng lúc sẽ bán vượt tồn kho nếu không khóa khi đọc. Ràng buộc `CHECK (stock_quantity >= 0)` chỉ là tuyến phòng thủ cuối, không phải tuyến đầu.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM DrugJpaEntity d WHERE d.drugId = :id")
Optional<DrugJpaEntity> findByIdForUpdate(@Param("id") UUID id);
```

Quy tắc:
1. Luôn khóa qua `findByIdForUpdate` trong `dispense` — không bao giờ dùng `findById` thường.
2. Luôn khóa các dòng **đã sắp xếp theo `drugId`** — khóa không sắp xếp trên đơn nhiều thuốc sẽ gây deadlock.
3. Giữ transaction ngắn: không gọi REST, không publish event trong khoảng thời gian đang giữ khóa.
## 11. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-D1 | Không xuất khi `stock_quantity < số lượng yêu cầu` | `dispense_insufficientStock_throwsOutOfStock` |
| BR-D2 | Không xuất khi `expiry_date < hôm nay` | `dispense_expiredDrug_throwsExpired` |
| BR-D3 | Tạo đơn thuốc thì tự tạo phiếu `PENDING` | `createPrescription_alsoCreatesPendingDispense` |
| BR-D4 | Xuất thuốc trừ kho đúng một lần | `dispense_valid_decrementsStockOnce` |
| BR-D5 | `total_amount = Σ(quantity × unit_price)` | `createPrescription_computesTotalFromLines` |
| BR-D6 | Xuất thất bại thì publish event bù trừ | `dispense_outOfStock_publishesDispenseFailed` |
| BR-D7 | Giá được chụp lại, không truy cứu về sau | `createPrescription_priceChangeLater_totalUnchanged` |
| BR-D8 | Giá do client gửi bị bỏ qua | `createPrescription_requestHasNoPriceField` |
| BR-D9 | `payment.completed` gửi lại chỉ xuất một lần | `onPaymentCompleted_sameEventTwice_dispensesOnce` |
| BR-D10 | Tồn kho không bao giờ âm khi tương tranh | `dispense_twoConcurrentCalls_onlyOneSucceeds` |
| BR-D11 | Chạm ngưỡng thì publish `stock.low` | `dispense_stockFallsBelowThreshold_publishesStockLow` |
| BR-D12 | Xuất thất bại vẫn ghi phiếu là `FAILED` | `dispense_failure_slipPersistedAsFailed` |

Ghi chú test:
- BR-D10 là test tương tranh **thật** (2 luồng) — phải chạy bằng `@SpringBootTest` + Testcontainers, không mock được.
- BR-D12 cần kiểm tra phiếu `FAILED` **vẫn được lưu dù transaction trừ kho rollback** — tức test nhánh `REQUIRES_NEW` ở §7.2 bước 6.

## 12. Điểm dễ sai

- Luôn dùng `BigDecimal`. `setScale(2, RoundingMode.HALF_UP)` cho mọi phép tính tiền. Không bao giờ dùng `double`.
- `DISPENSE_SLIP.prescription_id` là `UNIQUE` — mỗi đơn thuốc đúng một phiếu. Hàm `findByPrescription` dựa vào ràng buộc này.
- Consumer của `payment.completed` và endpoint `PUT .../dispense` gọi **cùng một** use case. Đừng viết logic hai lần.
- `stock.low` là kiểu bắn-rồi-quên: đừng để việc publish nó thất bại làm rollback một lần xuất thuốc đã thành công.
- **Reservation:** kê đơn phải `findByIdForUpdate` + trừ `Σ reserved` khi tính tồn khả dụng (khóa theo drugId — BR-D10); dispense phải `findReservedByPrescriptionForUpdate` rồi `markFulfilled`; job TTL chỉ chạm những giữ chỗ quá hạn. Quên khóa sẽ bán vượt kho khi tương tranh.

## 13. Coding map (chỉ dẫn hiện thực — bổ sung cho spec)

> Mục này dành cho coder: file nào tạo, nội dung gì, đặt ở đâu. Spec là **nơi** (bounded context), mục này là **cách** (cây file cụ thể).

### 13.1 Bản đồ file Java (mọi file cần tạo)

Base package `com.mediflow.pharmacy`:

```
pharmacy-service/src/main/java/com/mediflow/pharmacy/
├── PharmacyServiceApplication.java                      # có sẵn (+@EnableScheduling)
├── domain/model/
│   ├── DispenseStatus.java                              # đã có
│   ├── Drug.java                                        # đã có
│   ├── Prescription.java                                # đã có
│   ├── PrescriptionLine.java                            # đã có
│   ├── DispenseSlip.java                                # đã có
│   ├── enums/ReservationStatus.java                     # RESERVED/FULFILLED/RELEASED/EXPIRED
│   └── StockReservation.java                            # giữ chỗ tồn kho
├── domain/exception/                                    # 6 exception + StockReservationRuleException
├── application/port/in/
│   ├── ManageDrugUseCase.java
│   ├── CreatePrescriptionUseCase.java
│   ├── DispensePrescriptionUseCase.java
│   ├── ReactToPaymentUseCase.java
│   └── ReleaseExpiredReservationsUseCase.java           # job TTL
├── application/port/out/
│   ├── DrugRepositoryPort.java                          # đã có
│   ├── PrescriptionRepositoryPort.java
│   ├── DispenseSlipRepositoryPort.java
│   ├── ProcessedEventPort.java
│   ├── StockReservationRepositoryPort.java              # giữ chỗ
│   └── PharmacyEventPublisherPort.java
├── application/dto/request/
│   ├── CreateDrugRequest.java
│   ├── AdjustStockRequest.java
│   ├── CreatePrescriptionRequest.java
│   └── PrescriptionLineRequest.java
├── application/dto/response/
│   ├── DrugDTO.java
│   ├── PrescriptionDTO.java
│   ├── PrescriptionLineDTO.java
│   └── DispenseDTO.java
├── application/mapper/
│   ├── DrugDtoMapper.java                               # MapStruct: Drug ↔ DrugDTO
│   ├── PrescriptionDtoMapper.java                       # MapStruct: Prescription ↔ PrescriptionDTO
│   └── DispenseDtoMapper.java                           # MapStruct: DispenseSlip ↔ DispenseDTO
├── application/service/
│   └── PharmacyApplicationService.java                  # thực hiện 4 in-port
├── web/                                    # DRIVING adapter (HTTP) — gọi vào application
│   ├── DrugController.java                              # /api/v1/pharmacy/drugs (4 endpoints)
│   ├── PrescriptionController.java                      # POST, GET /{id}, PUT /{id}/dispense
│   └── GlobalExceptionHandler.java                      # copy từ docs/ai/reference (đổi package)
├── messaging/consumer/                   # DRIVING adapter (events) — gọi vào application
│   └── PaymentCompletedConsumer.java                    # @RabbitListener, gọi ReactToPaymentUseCase, dedupe eventId
├── infrastructure/persistence/           # DRIVEN adapter (DB) — hiện thực port out
│   ├── DrugJpaEntity.java / DrugJpaRepository.java / DrugPersistenceAdapter.java / DrugPersistenceMapper.java
│   ├── PrescriptionJpaEntity.java / PrescriptionJpaRepository.java / PrescriptionPersistenceAdapter.java / PrescriptionPersistenceMapper.java
│   ├── PrescriptionLineJpaEntity.java                   # con của PrescriptionJpaEntity (@OneToMany cascade)
│   ├── DispenseSlipJpaEntity.java / DispenseSlipJpaRepository.java / DispenseSlipPersistenceAdapter.java / DispenseSlipPersistenceMapper.java
│   ├── ProcessedEventPersistenceAdapter.java            # hiện thực ProcessedEventPort (bảng PROCESSED_EVENT)
│   └── StockReservationJpaEntity.java / StockReservationJpaRepository.java / StockReservationPersistenceAdapter.java   # giữ chỗ
└── infrastructure/scheduling/            # DRIVING adapter (job)
    └── ReservationExpiryScheduler.java                 # @Scheduled gọi ReleaseExpiredReservationsUseCase
└── infrastructure/messaging/             # DRIVEN adapter (RabbitMQ) — publisher + payload
    ├── PharmacyEventPublisherAdapter.java               # hiện thực PharmacyEventPublisherPort (publish sau commit)
    └── payload/
        ├── PrescriptionCreatedEvent.java
        ├── PrescriptionFilledEvent.java
        ├── PrescriptionDispenseFailedEvent.java
        └── StockLowEvent.java
```

Các file boilerplate (copy từ `docs/ai/reference/`):

```
infrastructure/config/RabbitConfig.java        # copy chuẩn → sửa hằng số routing key
infrastructure/security/JwtAuthFilter.java      # copy từ reference hoặc 1 service đã có
infrastructure/security/JwtProperties.java      # copy (record + @ConfigurationProperties)
infrastructure/config/SecurityConfig.java       # stateless JWT, default deny, permit actuator/OpenAPI
infrastructure/config/OpenApiConfig.java        # OpenAPI bean (tùy chọn)
src/main/resources/db/migration/V1__init.sql    # DDL §1 ở trên
```

> **Clean Architecture (hexagonal):** `web/` + `messaging/consumer/` là hai *driving adapter* (cổng gọi *vào* application). Controller/consumer **chỉ gọi in-port**, không bao giờ import persistence hay publisher. Xem `docs/ai/04-microservice-blueprint.md`.

> **Ghi chú cây con:** `Prescription` chứa `lines`. Phía JPA: `PrescriptionJpaEntity` có `@OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true) List<PrescriptionLineJpaEntity>` và `PrescriptionLineJpaEntity` có `@ManyToOne(fetch = LAZY) PrescriptionJpaEntity prescription` — một aggregate, không có repository port riêng cho dòng kê. Domain `Prescription` trả `List<PrescriptionLine>` **không cho sửa** (unmodifiable).

### 13.2 Event payloads — dạng Java record (đúng envelope chuẩn)

```java
// PrescriptionCreatedEvent — routing key "prescription.created", publish sau createPrescription commit
public record PrescriptionCreatedEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID prescriptionId, UUID patientId, UUID recordId, UUID departmentId,
    BigDecimal totalAmount, List<Item> items) {
    public record Item(UUID drugId, String drugName, int quantity, BigDecimal price) {}
}

// PrescriptionFilledEvent — routing key "prescription.filled", publish sau dispense thành công
public record PrescriptionFilledEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID prescriptionId, UUID patientId, UUID departmentId,
    BigDecimal totalAmount, List<DispensedItem> dispensedItems) {
    public record DispensedItem(UUID drugId, String drugName, int quantity) {}
}

// PrescriptionDispenseFailedEvent — routing key "prescription.dispense.failed", kích hoạt bù trừ saga
// failedItems[] cho rõ thuốc nào thiếu (không gắn với thuốc cụ thể thì để rỗng)
public record PrescriptionDispenseFailedEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID prescriptionId, UUID invoiceId, UUID patientId, String reason,
    List<FailedItem> failedItems) {
    public record FailedItem(UUID drugId, String drugName, int requestedQty, int availableQty) {}
}

// StockLowEvent — routing key "stock.low", bắn-rồi-quên khi tồn kho ≤ ngưỡng
public record StockLowEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID drugId, String drugName, int currentStock, int threshold) {}
```

> `departmentId` trên mọi event vận hành (bắt buộc — report cần group theo khoa).
> Consumer `PaymentCompletedConsumer` nhận `PaymentCompletedEvent` do billing publish (định nghĩa ở spec 06).

### 13.3 Chi tiết persistence

Entity ↔ domain (MapStruct):

| Entity field | Domain field | Kiểu |
|--------------|--------------|------|
| `drug_id` | `drugId` | UUID |
| `drug_name` | `drugName` | String |
| `active_ingredient` | `activeIngredient` | String |
| `unit` | `unit` | String |
| `price` | `price` | BigDecimal |
| `stock_quantity` | `stockQuantity` | int |
| `expiry_date` | `expiryDate` | LocalDate |
| `manufacturer` | `manufacturer` | String |
| `low_stock_threshold` | `lowStockThreshold` | int |
| `created_at` / `updated_at` | `createdAt` / `updatedAt` | Instant |

Adapter cần làm đúng 3 điều:
1. `findByIdForUpdate` → `findById` thường cho mọi luồng đọc; **chỉ `dispense` dùng bản khóa**.
2. `search` → chuyển `PageQuery → Pageable` (page, size), và `Page<DrugJpaEntity> → PageResult<Drug>` (map qua entity mapper). DTO mapper tách lớp: persistence chỉ biết domain, không biết DTO.
3. `save`/`findById`/`findByPatient`... → mapper domain ↔ entity ở hai chiều.

`PrescriptionPersistenceAdapter` / `DispenseSlipPersistenceAdapter` cùng pattern (save, findById, findByPatient/findByPrescription).

### 13.4 RabbitConfig — hằng số & binding (pharmacy)

```
EXCHANGE = "mediflow.events"        (durable topic)
DLX      = "mediflow.events.dlx"
QUEUE    = "pharmacy.q"             (durable, DLX + DLQ "pharmacy.dlq")
```

| Routing key | Hướng | Dùng cho |
|---|---|---|
| `prescription.created` | publish | saga khởi động (billing tạo hóa đơn) |
| `prescription.filled` | publish | kết thúc thành công |
| `prescription.dispense.failed` | publish | bù trừ |
| `stock.low` | publish | cảnh báo tồn kho |
| `payment.completed` | **subscribe** (bind `pharmacy.q`) | kích hoạt `dispense` |

Queue `pharmacy.q` **chỉ bind `payment.completed`**. Các routing key publish không cần binding (publish lên exchange, subscriber tự bind).

### 13.5 Test plan — map business rule → tầng + tên test

| Rule | Tầng | Tên test | Cần gì |
|---|---|---|---|
| BR-D1 (hết hàng) | application (mock repo) | `dispense_insufficientStock_throwsOutOfStock` | mock `DrugRepositoryPort.findByIdForUpdate` |
| BR-D2 (hết hạn) | application (mock repo) | `dispense_expiredDrug_throwsExpired` | — |
| BR-D3 (tự tạo phiếu PENDING) | application | `createPrescription_alsoCreatesPendingDispense` | assert `DispenseSlipRepositoryPort.save` nhận `PENDING` |
| BR-D4 (trừ kho đúng 1 lần) | persistence (`@DataJpaTest`) | `dispense_valid_decrementsStockOnce` | Testcontainers PG |
| BR-D5 (total_amount = Σ) | domain unit | `createPrescription_computesTotalFromLines` | không Spring |
| BR-D6 (publish dispense.failed) | application | `dispense_outOfStock_publishesDispenseFailed` | mock publisher port, verify `publishPrescriptionDispenseFailed` |
| BR-D7 (price snapshot) | application | `createPrescription_priceChangeLater_totalUnchanged` | mock repo trả giá cũ, sau đổi giá, assert totalAmount giữ nguyên |
| BR-D8 (client không set giá) | web slice | `createPrescription_requestHasNoPriceField` | `@WebMvcTest`, assert request record không có field `price` |
| BR-D9 (redelivery chỉ xuất 1 lần) | application | `onPaymentCompleted_sameEventTwice_dispensesOnce` | mock `ProcessedEventPort.alreadyProcessed` → true |
| BR-D10 (concurrency) | integration | `dispense_twoConcurrentCalls_onlyOneSucceeds` | `@SpringBootTest` + Testcontainers, 2 threads, 1 viên thuốc |
| BR-D11 (stock.low) | application | `dispense_stockFallsBelowThreshold_publishesStockLow` | mock publisher, verify `publishStockLow` |
| BR-D12 (phiếu FAILED) | integration | `dispense_failure_slipPersistedAsFailed` | Testcontainers, `REQUIRES_NEW` branch |

Các test mới cho reservation:

| Rule | Tầng | Tên test | Cần gì |
|---|---|---|---|
| Reserve đủ hàng khi kê | application (mock repo) | `createPrescription_valid_reservesStock` | mock `StockReservationRepositoryPort.save` |
| Reserve thiếu (tồn khả dụng) | application (mock repo) | `createPrescription_insufficientAvailable_throws422` | mock `findReservedByDrug` trả 1 dòng, `Drug.stockQuantity` nhỏ hơn yêu cầu |
| Dispense chuyển reserved→FULFILLED | application (mock repo) | `dispense_valid_fulfillsReservation` | mock `findReservedByPrescriptionForUpdate` trả RESERVED, verify `markFulfilled` |
| Release TTL hết hạn | application (mock repo) | `releaseExpiredReservations_expiresOnlyOverdue` | mock `findExpired`, assert trả về số lượng đúng |
| Domain vòng đời giữ chỗ | domain unit | `StockReservation_lifecycleTransitions` | không Spring (đã có `StockReservationTest`) |

### 13.6 Checklist hoàn thiện pharmacy (Definition of Done)

- [ ] `V1__init.sql` đủ 5 bảng + `V2__stock_reservation.sql`, đúng tên EN snake_case (DRUG, PRESCRIPTION, STOCK_RESERVATION, ...), index đúng spec.
- [ ] Domain: 4 model + 2 enum + 7 exception; quy tắc nằm trong model (`dispenseStock`, `create`, `StockReservation.markFulfilled/release/expire`...).
- [ ] Ports đủ (6 out, 5 in); application service hiện thực, **không import Spring Data/AMQP**.
- [ ] DTO records có Bean Validation; 3 MapStruct mapper.
- [ ] Controller 2 cái + `@PreAuthorize` đúng role (ADMIN/DOCTOR/PHARMACIST).
- [ ] 4 event record + publisher adapter (sau commit); consumer `payment.completed` idempotent; `PrescriptionDispenseFailedEvent` kèm `failedItems[]`.
- [ ] `ReservationExpiryScheduler` (job TTL) + `@EnableScheduling`.
- [ ] `GlobalExceptionHandler` (từ reference), `SecurityConfig`, `RabbitConfig`, `OpenApiConfig`.
- [ ] Test 5 tầng; 12 business rule + 5 test reservation được phủ.
- [ ] `mvn -pl backend/pharmacy-service -am -q -DskipTests install` xanh.