# 05 — pharmacy-service (Khoa Dược)

**Module** `pharmacy-service` · **Package** `com.mediflow.pharmacy` · **Cổng** 8085 · **DB** `mediflow_pharmacy` · **Tiền tố** `/api/v1/pharmacy`

Sở hữu danh mục thuốc, tồn kho, đơn thuốc và phiếu xuất. **Thành phần tham gia saga** kê đơn → hóa
đơn → thanh toán → xuất thuốc. Đây là service nhiều trạng thái nhất hệ thống: nó là service duy nhất
có thể *hết hàng*.

## 1. Lược đồ — `V1__init.sql`

> **Bảng/cột dùng tiếng Anh** (theo thống nhất riêng cho các service của nhánh C — pharmacy/billing/report).
> Xem mapping ở đầu mục này. Rest của hệ thống vẫn dùng tiếng Việt snake_case như `08-persistence-naming.md`.

```sql
CREATE TABLE DRUG (
    drug_id        UUID          PRIMARY KEY,
    drug_name      VARCHAR(150)  NOT NULL,
    active_ingredient VARCHAR(150),
    unit           VARCHAR(20)   NOT NULL,
    price          DECIMAL(15,2) NOT NULL,
    stock_quantity INT           NOT NULL DEFAULT 0,
    expiry_date    DATE          NOT NULL,
    manufacturer   VARCHAR(150),
    low_stock_threshold INT      NOT NULL DEFAULT 10,   -- threshold that fires stock.low
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT ck_drug_price_positive  CHECK (price >= 0),
    CONSTRAINT ck_drug_stock_non_negative CHECK (stock_quantity >= 0)
);
CREATE INDEX idx_drug_name ON DRUG (drug_name);

CREATE TABLE PRESCRIPTION (
    prescription_id UUID          PRIMARY KEY,
    record_id       UUID          NOT NULL,          -- ref clinical-service HO_SO_BA
    patient_id      UUID          NOT NULL,          -- ref patient-service BENH_NHAN
    doctor_id       UUID          NOT NULL,          -- ref organization-service NHAN_VIEN
    department_id   UUID          NOT NULL,          -- ref organization-service KHOA
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
    unit_price      DECIMAL(15,2) NOT NULL,           -- price snapshot at prescription time
    dosage          VARCHAR(255),
    line_total      DECIMAL(15,2) NOT NULL,
    CONSTRAINT ck_line_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_line_prescription ON PRESCRIPTION_LINE (prescription_id);

CREATE TABLE DISPENSE_SLIP (
    dispense_id   UUID          PRIMARY KEY,
    prescription_id UUID        NOT NULL UNIQUE REFERENCES PRESCRIPTION(prescription_id),
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    dispensed_at  TIMESTAMPTZ,
    dispensed_by  UUID,                                -- ref organization-service NHAN_VIEN
    failure_reason VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);
CREATE INDEX idx_dispense_status ON DISPENSE_SLIP (status);

-- Idempotency ledger for event consumers.
CREATE TABLE PROCESSED_EVENT (
    event_id    UUID          PRIMARY KEY,
    routing_key VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

> **Mapping (tiếng Việt → tiếng Anh)** — đây là bảng dịch chính thức cho 3 service nhánh C:

| Việt (trước) | Anh (sau) | Dùng trong |
|---|---|---|
| THUOC | DRUG | bảng |
| ma_thuoc | drug_id | cột |
| ten_thuoc | drug_name | cột |
| hoat_chat | active_ingredient | cột |
| don_vi_tinh | unit | cột |
| gia | price | cột |
| so_luong_ton | stock_quantity | cột |
| han_su_dung | expiry_date | cột |
| nha_san_xuat | manufacturer | cột |
| nguong_canh_bao | low_stock_threshold | cột |
| BAN_KE_CP | PRESCRIPTION | bảng |
| ma_ban_ke | prescription_id | cột |
| ma_ho_so | record_id | cột |
| ma_benh_nhan | patient_id | cột |
| ma_bac_si | doctor_id | cột |
| ma_khoa | department_id | cột |
| ngay_ke | prescribed_date | cột |
| tong_tien | total_amount | cột |
| CHI_TIET_BAN_KE | PRESCRIPTION_LINE | bảng |
| ma_chi_tiet | line_id | cột |
| so_luong | quantity | cột |
| don_gia | unit_price | cột |
| lieu_dung | dosage | cột |
| thanh_tien | line_total | cột |
| PHIEU_XUAT | DISPENSE_SLIP | bảng |
| ma_phieu_xuat | dispense_id | cột |
| trang_thai | status | cột |
| ngay_xuat | dispensed_at | cột |
| nguoi_xuat | dispensed_by | cột |
| ly_do_that_bai | failure_reason | cột |
| SU_KIEN_DA_XU_LY | PROCESSED_EVENT | bảng |
| xu_ly_luc | processed_at | cột |
| TrangThaiPhieuXuat | DispenseStatus | enum |
| CHO_XUAT | PENDING | enum value |
| DA_XUAT | DISPENSED | enum value |
| THAT_BAI | FAILED | enum value |

> `unit_price` trên dòng chi tiết là **ảnh chụp**. Không bao giờ join sang `DRUG.price` để tính lại một
> đơn thuốc cũ — giá trong danh mục thay đổi, còn tổng tiền lịch sử thì không được đổi.

## 2. Enum

```java
public enum DispenseStatus { PENDING, DISPENSED, FAILED }
```

## 3. Domain model

> Tên class/field trong 3 service nhánh C dùng tiếng Anh (đồng bộ với bảng/cột):
> `Thuoc` → `Drug`, `maThuoc` → `drugId`, `BanKe` → `Prescription`, `PhieuXuat` → `DispenseSlip`, ...

### `Drug`

Các trường: `drugId`, `drugName`, `activeIngredient`, `unit`, `price` (`BigDecimal`), `stockQuantity` (`int`),
`expiryDate` (`LocalDate`), `manufacturer`, `lowStockThreshold`, timestamps.

```java
public static Drug create(String drugName, String activeIngredient, String unit, BigDecimal price,
                          int stockQuantity, LocalDate expiryDate, String manufacturer, int lowStockThreshold);
public void updateInfo(String drugName, String activeIngredient, String unit,
                       BigDecimal price, LocalDate expiryDate, String manufacturer, int lowStockThreshold);
public void restock(int quantity);           // quantity > 0
public void dispenseStock(int quantity);     // BR-D1, BR-D2 — throws if not possible
public boolean hasStock(int quantity);       // stockQuantity >= quantity
public boolean isExpired();                  // expiryDate >= today
public boolean belowLowStockThreshold();     // stockQuantity <= lowStockThreshold
```

Invariants of `dispenseStock`:

| Check | Error code |
|-------|-----------|
| `quantity <= 0` | `DRUG_QUANTITY_INVALID` |
| `stockQuantity < quantity` | `DRUG_OUT_OF_STOCK` (BR-D1) |
| `expiryDate < today` | `DRUG_EXPIRED` (BR-D2) |

`create`/`updateInfo`: `drugName` not blank; `price >= 0` (`DRUG_PRICE_NEGATIVE`);
`expiryDate` not in the past at creation (`DRUG_EXPIRY_PAST`); `unit` not blank.

### `Prescription` (đơn thuốc — aggregate root)

Các trường: `prescriptionId`, `recordId`, `patientId`, `doctorId`, `departmentId`, `prescribedDate`,
`totalAmount`, `lines` (`List<PrescriptionLine>`).

```java
public static Prescription create(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
                                  LocalDate prescribedDate, List<PrescriptionLine> lines);
public BigDecimal computeTotal();            // Σ lineTotal — BR-D5
public List<PrescriptionLine> getLines();    // không cho sửa
```

Invariants: ít nhất một dòng (`PRESCRIPTION_EMPTY`); `totalAmount` được **tính ra**, không bao giờ nhận
từ client.

### `PrescriptionLine`

`lineId`, `drugId`, `quantity`, `unitPrice`, `dosage`, `lineTotal`.

```java
public static PrescriptionLine create(UUID drugId, int quantity, BigDecimal unitPrice, String dosage);
// lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP)
```

`quantity > 0` (`DRUG_QUANTITY_INVALID`).

### `DispenseSlip` (phiếu xuất thuốc)

`dispenseId`, `prescriptionId`, `status`, `dispensedAt`, `dispensedBy`, `failureReason`.

```java
public static DispenseSlip createPending(UUID prescriptionId);   // BR-D3 — luôn là PENDING
public void markDispensed(UUID dispensedBy, Instant timestamp);
public void markFailed(String reason);
public boolean isPending();
```

Transitions: `PENDING → DISPENSED | FAILED`; cả hai đều là kết thúc. Còn lại ném
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
public interface DrugRepositoryPort {
    Drug save(Drug t);
    Optional<Drug> findById(UUID id);
    /** BẮT BUỘC khóa ghi bi quan — xem §10 về tương tranh. */
    Optional<Drug> findByIdForUpdate(UUID id);
    PageResult<Drug> search(String keyword, PageQuery page);
}
public interface PrescriptionRepositoryPort {
    Prescription save(Prescription p);
    Optional<Prescription> findById(UUID id);
    List<Prescription> findByPatient(UUID patientId);
}
public interface DispenseSlipRepositoryPort {
    DispenseSlip save(DispenseSlip s);
    Optional<DispenseSlip> findById(UUID id);
    Optional<DispenseSlip> findByPrescription(UUID prescriptionId);
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
public interface DispensePrescriptionUseCase { DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy); }
public interface ReactToPaymentUseCase { void onPaymentCompleted(UUID prescriptionId, UUID invoiceId); }
```

## 6. DTO

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
    @NotNull Integer quantity,          // dương = nhập, âm = điều chỉnh giảm
    @Size(max = 255) String reason) {}

public record CreatePrescriptionRequest(
    @NotNull UUID recordId, @NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID departmentId,
    @NotNull @PastOrPresent LocalDate prescribedDate,
    @NotEmpty @Valid List<PrescriptionLineRequest> lines) {}

public record PrescriptionLineRequest(
    @NotNull UUID drugId,
    @NotNull @Min(1) Integer quantity,
    @Size(max = 255) String dosage) {}     // LƯU Ý: không có giá — server tự chụp giá

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

## 7. Thuật toán tầng application

### `createPrescription` — `@Transactional`

1. Với mỗi dòng: `drugRepo.findById(drugId)` → không có thì `DRUG_NOT_FOUND`
2. **Chụp giá**: `unitPrice = drug.getPrice()` (không bao giờ lấy từ request — client không được đặt giá)
3. Dựng `PrescriptionLine` cho mỗi dòng; `lineTotal = unitPrice × quantity`, làm tròn `HALF_UP` 2 chữ số
4. `Prescription.create(...)` → tính `totalAmount` (BR-D5)
5. Lưu đơn thuốc
6. `DispenseSlip.createPending(prescriptionId)` → lưu (**BR-D3** — luôn tạo, luôn ở `PENDING`)
7. Publish `PrescriptionCreatedEvent` sau commit → billing tạo hóa đơn

> **KHÔNG trừ kho ở đây.** Kê đơn chỉ là ghi nhận ý định. Kho chỉ biến động lúc xuất thuốc, sau khi
> đã thanh toán. Trừ kho hai lần là lỗi dễ xảy ra nhất trong service này.

### `dispense(prescriptionId, dispensedBy)` — `@Transactional`

Được gọi từ cả `PUT /prescriptions/{id}/dispense` lẫn consumer của `payment.completed`.

1. `dispenseSlipRepo.findByPrescription(prescriptionId)` → không có thì `DISPENSE_NOT_FOUND`
2. nếu `!slip.isPending()` → `DISPENSE_ALREADY_DONE` *(idempotent: một `payment.completed` bị gửi lại không được xuất thuốc hai lần)*
3. Nạp đơn thuốc và các dòng của nó
4. **Sắp xếp các dòng theo `drugId`** trước khi khóa — tránh deadlock khi có nhiều lượt xuất đồng thời
5. Với mỗi dòng: `drugRepo.findByIdForUpdate(drugId)` (khóa ghi bi quan), rồi `drug.dispenseStock(quantity)`
   - ném `DRUG_OUT_OF_STOCK` (BR-D1) hoặc `DRUG_EXPIRED` (BR-D2)
6. **Khi có bất kỳ lỗi nào:** phần trừ kho được hoàn tác (transaction lo việc đó), đặt
   `slip.markFailed(reason)`, lưu phiếu **trong một transaction mới** (`REQUIRES_NEW`), rồi publish
   `PrescriptionDispenseFailedEvent` → billing bù trừ (BR-D6)
7. Khi thành công: lưu từng thuốc; `slip.markDispensed(dispensedBy, now)`; lưu
8. Với mỗi thuốc giờ `belowLowStockThreshold()` → publish `StockLowEvent`
9. Publish `PrescriptionFilledEvent` sau commit

> Bước 6 là nhánh bù trừ. Phiếu xuất **vẫn phải ghi được trạng thái thất bại dù transaction trừ kho
> đã rollback** — vì thế mới cần `REQUIRES_NEW`. Làm sai chỗ này thì saga treo vĩnh viễn.

### `onPaymentCompleted(prescriptionId, invoiceId)` — bước tiến của saga

1. `processed.alreadyProcessed(eventId)` → return
2. gọi `dispense(prescriptionId, SYSTEM_USER)`
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
| `prescription.created` | `{envelope, prescriptionId, patientId, recordId, departmentId, totalAmount, items[]}` | sau khi `createPrescription` commit |
| `prescription.filled` | `{envelope, prescriptionId, patientId, departmentId, totalAmount, dispensedItems[]}` | sau khi xuất thuốc thành công |
| `prescription.dispense.failed` | `{envelope, prescriptionId, invoiceId, patientId, reason}` | xuất thất bại — **kích hoạt bù trừ** |
| `stock.low` | `{envelope, drugId, drugName, currentStock, threshold}` | tồn kho chạm hoặc dưới `low_stock_threshold` |

**Subscribe** — queue `pharmacy.q`

| Routing key | Xử lý |
|-------------|-------|
| `payment.completed` | `onPaymentCompleted` → xuất thuốc (`PENDING → DISPENSED`) |

## 10. Tương tranh — đọc kỹ trước khi viết `dispense`

Hai dược sĩ xuất cùng một loại thuốc cùng lúc sẽ bán vượt tồn kho nếu không khóa khi đọc. Ràng buộc
`CHECK (stock_quantity >= 0)` là tuyến phòng thủ cuối cùng, không phải tuyến đầu.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM DrugJpaEntity d WHERE d.drugId = :id")
Optional<DrugJpaEntity> findByIdForUpdate(@Param("id") UUID id);
```

Quy tắc:
1. Luôn khóa qua `findByIdForUpdate` bên trong `dispense` — không bao giờ dùng `findById` thường.
2. Luôn khóa các dòng **đã sắp xếp theo `drugId`**. Khóa không sắp xếp trên đơn nhiều thuốc sẽ gây deadlock.
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
| BR-D7 | Giá được chụp lại, không tra cứu về sau | `createPrescription_priceChangeLater_totalUnchanged` |
| BR-D8 | Giá do client gửi bị bỏ qua | `createPrescription_requestHasNoPriceField` |
| BR-D9 | `payment.completed` gửi lại chỉ xuất một lần | `onPaymentCompleted_sameEventTwice_dispensesOnce` |
| BR-D10 | Tồn kho không bao giờ âm khi tương tranh | `dispense_twoConcurrentCalls_onlyOneSucceeds` |
| BR-D11 | Chạm ngưỡng thì publish `stock.low` | `dispense_stockFallsBelowThreshold_publishesStockLow` |
| BR-D12 | Xuất thất bại vẫn ghi phiếu là `FAILED` | `dispense_failure_slipPersistedAsFailed` |

BR-D10 cần test tương tranh thật: hai luồng, `@SpringBootTest` + Testcontainers, một thuốc có
`stock_quantity = 1`, hai lượt xuất mỗi lượt 1 → đúng một lượt thành công.

## 12. Điểm dễ sai

- Luôn dùng `BigDecimal`. `setScale(2, RoundingMode.HALF_UP)` cho mọi phép tính tiền. Không bao giờ dùng `double`.
- `DISPENSE_SLIP.prescription_id` là `UNIQUE` — mỗi đơn thuốc đúng một phiếu. Hàm `findByPrescription` dựa vào ràng buộc này.
- Consumer của `payment.completed` và endpoint `PUT .../dispense` gọi **cùng một** use case. Đừng viết logic hai lần.
- `stock.low` là kiểu bắn-rồi-quên: đừng để việc publish nó thất bại làm rollback một lượt xuất thuốc đã thành công.

---

## 13. Coding map (chỉ dẫn hiện thực — bổ sung cho spec)

> Mục này dành cho coder: file nào tạo, nội dung gì, đặt ở đâu. Spec là **nơi** (bounded context),
> mục này là **cách** (cây file cụ thể). Kết hợp với boilerplate chuẩn ở `docs/ai/reference/`.

### 13.1 Bản đồ file Java (mọi file cần tạo)

Base package `com.mediflow.pharmacy`. Cây đầy đủ dưới đây (mỗi dòng = một file):

```
pharmacy-service/src/main/java/com/mediflow/pharmacy/
├── PharmacyServiceApplication.java                      # có sẵn
├── domain/model/
│   ├── DispenseStatus.java                              # enum { PENDING, DISPENSED, FAILED }
│   ├── Drug.java                                        # aggregate "danh mục thuốc" (stock rules)
│   ├── Prescription.java                                # aggregate root: đơn thuốc + lines (bất biến)
│   ├── PrescriptionLine.java                            # value: một dòng kê (price snapshot)
│   └── DispenseSlip.java                                # máy trạng thái PENDING → DISPENSED | FAILED
├── domain/exception/
│   ├── DrugNotFoundException.java                       # 404, code DRUG_NOT_FOUND
│   ├── PrescriptionNotFoundException.java               # 404, code PRESCRIPTION_NOT_FOUND
│   ├── DispenseNotFoundException.java                   # 404, code DISPENSE_NOT_FOUND
│   ├── DrugRuleException.java                           # 422, dùng chung cho mã DRUG_*
│   └── DispenseRuleException.java                       # 422, dùng chung cho mã DISPENSE_*
├── application/port/in/
│   ├── ManageDrugUseCase.java                           # create/getById/search/adjustStock
│   ├── CreatePrescriptionUseCase.java                   # create(CreatePrescriptionRequest)
│   ├── DispensePrescriptionUseCase.java                 # dispense(prescriptionId, dispensedBy)
│   └── ReactToPaymentUseCase.java                       # onPaymentCompleted(prescriptionId, invoiceId)
├── application/port/out/
│   ├── DrugRepositoryPort.java
│   ├── PrescriptionRepositoryPort.java
│   ├── DispenseSlipRepositoryPort.java
│   ├── ProcessedEventPort.java
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
│   ├── PharmacyApplicationService.java                  # hiện thực ManageDrug + CreatePrescription + Dispense + ReactToPayment
│   └── (optional) DrugApplicationService.java           # tách riêng nếu service quá lớn
├── web/                                  # DRIVING adapter (HTTP) — gọi vào application
│   ├── DrugController.java                              # /api/v1/pharmacy/drugs (4 endpoints)
│   ├── PrescriptionController.java                      # /api/v1/pharmacy/prescriptions (POST, GET /{id}, PUT /{id}/dispense)
│   └── GlobalExceptionHandler.java                      # copy từ docs/ai/reference (chỉ đổi package)
├── messaging/consumer/                   # DRIVING adapter (events) — gọi vào application
│   └── PaymentCompletedConsumer.java                    # @RabbitListener, gọi ReactToPaymentUseCase, dedupe eventId
├── infrastructure/persistence/           # DRIVEN adapter (DB) — hiện thực port out
│   ├── DrugJpaEntity.java
│   ├── DrugJpaRepository.java                           # + findByIdForUpdate (PESSIMISTIC_WRITE)
│   ├── DrugPersistenceAdapter.java
│   ├── DrugPersistenceMapper.java                       # MapStruct: Drug ↔ DrugJpaEntity
│   ├── PrescriptionJpaEntity.java
│   ├── PrescriptionJpaRepository.java
│   ├── PrescriptionPersistenceAdapter.java
│   ├── PrescriptionPersistenceMapper.java
│   ├── PrescriptionLineJpaEntity.java                   # con của PrescriptionJpaEntity (@OneToMany cascade)
│   ├── DispenseSlipJpaEntity.java
│   ├── DispenseSlipJpaRepository.java
│   ├── DispenseSlipPersistenceAdapter.java
│   ├── DispenseSlipPersistenceMapper.java
│   └── ProcessedEventPersistenceAdapter.java            # hiện thực ProcessedEventPort (bảng PROCESSED_EVENT)
└── infrastructure/messaging/             # DRIVEN adapter (RabbitMQ) — publisher + payload
    ├── PharmacyEventPublisherAdapter.java               # hiện thực PharmacyEventPublisherPort (publish sau commit)
    └── payload/
        ├── PrescriptionCreatedEvent.java
        ├── PrescriptionFilledEvent.java
        ├── PrescriptionDispenseFailedEvent.java
        └── StockLowEvent.java
```

Các file sau **cần boilerplate sẵn có**, chưa liệt kê ở trên (xem `docs/ai/reference/`):

```
infrastructure/config/RabbitConfig.java        # copy chuẩn → sửa hằng số routing key
infrastructure/security/JwtAuthFilter.java      # copy từ reference hoặc 1 service đã có
infrastructure/security/JwtProperties.java      # copy (record + @ConfigurationProperties)
infrastructure/config/SecurityConfig.java       # stateless JWT, default deny, permit actuator/OpenAPI
infrastructure/config/OpenApiConfig.java        # OpenAPI bean (tùy chọn)
src/main/resources/db/migration/V1__init.sql    # DDL §1 ở trên
```

> **Clean Architecture (hexagonal):** `web/` + `messaging/consumer/` là hai *driving adapter* (cổng
> gọi *vào* application) — chúng nằm ở tầng ngoài cùng, anh em với `infrastructure/` (driven adapter,
> application gọi *ra*). Controller/consumer **chỉ gọi in-port**, không bao giờ import persistence
> hay publisher. Xem `docs/ai/04-microservice-blueprint.md`.

> **Ghi chú về cây con:** `Prescription` chứa `lines`. Phía JPA: `PrescriptionJpaEntity` có
> `@OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true) List<PrescriptionLineJpaEntity>`
> và `PrescriptionLineJpaEntity` có `@ManyToOne(fetch = LAZY) PrescriptionJpaEntity prescription` — một aggregate,
> không có repository port riêng cho chi tiết. Domain `Prescription` trả `List<PrescriptionLine>` **không cho sửa** (unmodifiable).

### 13.2 Event payloads — dạng Java record (đúng envelope chuẩn)

Theo `06-events-rabbitmq.md`, mọi event đều bắt đầu bằng `eventId, occurredAt, correlationId`. Đầy đủ:

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
public record PrescriptionDispenseFailedEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID prescriptionId, UUID invoiceId, UUID patientId, String reason) {}

// StockLowEvent — routing key "stock.low", bắn-rồi-quên khi tồn kho ≤ ngưỡng
public record StockLowEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID drugId, String drugName, int currentStock, int threshold) {}
```

> `departmentId` trên mọi event vận hành (bắt buộc — report cần group theo khoa).
> Consumer `PaymentCompletedConsumer` nhận `PaymentCompletedEvent` do billing publish (định nghĩa ở spec 06).

### 13.3 Chi tiết persistence

**`DrugJpaRepository` — khóa ghi bi quan (BẮT BUỘC cho `dispense`):**

```java
public interface DrugJpaRepository extends JpaRepository<DrugJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DrugJpaEntity d WHERE d.drugId = :id")
    Optional<DrugJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT d FROM DrugJpaEntity d
        WHERE :keyword IS NULL
           OR LOWER(d.drugName) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    Page<DrugJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);
}
```

**Ánh xạ entity ↔ domain (MapStruct):**

| Entity field (`@Column`) | Domain field | Kiểu |
|---|---|---|
| `drug_id` | `drugId` | UUID |
| `drug_name` | `drugName` | String |
| `active_ingredient` | `activeIngredient` | String |
| `unit` | `unit` | String |
| `price` | `price` | BigDecimal |
| `stock_quantity` | `stockQuantity` | int |
| `expiry_date` | `expiryDate` | LocalDate |
| `manufacturer` | `manufacturer` | String |
| `low_stock_threshold` | `lowStockThreshold` | int |
| `created_at`/`updated_at` | `createdAt`/`updatedAt` | Instant |

**Adapter cần làm đúng 3 điều:**
1. `findByIdForUpdate` → `findById` thường cho mọi luồng đọc; **chỉ `dispense` dùng bản khóa**.
2. `search` → chuyển `PageQuery → Pageable` (page, size), và `Page<DrugJpaEntity> → PageResult<Drug>` (`map` qua entity mapper). DTO mapper tách lớp: persistence chỉ biết domain, không biết DTO.
3. `save`/`findById`/`findByPatient`... → mapper domain↔entity ở hai chiều.

**`PrescriptionPersistenceAdapter`/`DispenseSlipPersistenceAdapter`** cùng pattern (save, findById, findByPatient/findByPrescription).

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

**Ghi chú test:**
- BR-D10 là test tương tranh **thật** (2 luồng) — phải chạy bằng `@SpringBootTest` + Testcontainers, không mock được.
- BR-D12 cần kiểm tra phiếu `FAILED` **vẫn được lưu dù transaction trừ kho rollback** — tức test nhánh `REQUIRES_NEW` ở §7 (bước 6).

### 13.6 Checklist hoàn thiện pharmacy (Definition of Done)

- [ ] `V1__init.sql` đủ 5 bảng, đúng tên EN snake_case (DRUG, PRESCRIPTION, ...), index đúng spec.
- [ ] Domain: 4 model + 1 enum + 5 exception; bất biến nằm trong model (`dispenseStock`, `create`...).
- [ ] Ports đủ (5 out, 4 in); application service hiện thực, **không import Spring Data/AMQP**.
- [ ] DTO records có Bean Validation; 3 MapStruct mapper.
- [ ] Controller 3 cái + `@PreAuthorize` đúng role (ADMIN/DOCTOR/PHARMACIST).
- [ ] 4 event record + publisher adapter (sau commit); consumer `payment.completed` idempotent.
- [ ] `GlobalExceptionHandler` (từ reference), `SecurityConfig`, `RabbitConfig`, `OpenApiConfig`.
- [ ] Test 5 tầng; 12 business rule được phủ.
- [ ] `mvn -pl backend/pharmacy-service -am -q -DskipTests install` xanh.
