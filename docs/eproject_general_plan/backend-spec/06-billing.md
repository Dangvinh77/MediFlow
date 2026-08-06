# 06 — billing-service (Phòng Viện phí)

**Module** `billing-service` · **Package** `com.mediflow.billing` · **Cổng** 8086 · **DB** `mediflow_billing` · **Tiền tố** `/api/v1/billing`

Sở hữu viện phí và hóa đơn, đồng thời **điều phối saga** kê đơn → hóa đơn → thanh toán → xuất thuốc,
bao gồm cả nhánh bù trừ khi xuất thuốc thất bại. Đây là service subscribe nhiều event nhất hệ thống.

## 1. Lược đồ — `V1__init.sql`

> **Bảng/cột dùng tiếng Anh** (theo thống nhất riêng cho nhánh C — pharmacy/billing/report).
> Mapping ở đầu mục này. Rest của hệ thống vẫn dùng tiếng Việt snake_case như `08-persistence-naming.md`.

```sql
CREATE TABLE FEE (
    fee_id         UUID          PRIMARY KEY,
    patient_id     UUID          NOT NULL,        -- ref patient-service BENH_NHAN
    record_id      UUID,                          -- ref clinical-service HO_SO_BA, nullable
    department_id  UUID          NOT NULL,        -- ref organization-service KHOA
    source_ref_id  UUID,                          -- lab test / prescription that created this fee
    fee_type       VARCHAR(10)   NOT NULL,        -- EXAM | LAB | DRUG | SERVICE
    incurred_date  DATE          NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    is_paid        BOOLEAN       NOT NULL DEFAULT false,
    invoice_id     UUID,                          -- assigned when added to an invoice
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT ck_fee_amount_non_negative CHECK (amount >= 0)
);
CREATE INDEX idx_fee_patient ON FEE (patient_id, is_paid);
CREATE INDEX idx_fee_dept   ON FEE (department_id, incurred_date);
CREATE INDEX idx_fee_invoice ON FEE (invoice_id);
-- mỗi event nguồn chỉ sinh một khoản phí: giúp việc tạo phí idempotent (BR-B7)
CREATE UNIQUE INDEX uq_fee_source ON FEE (fee_type, source_ref_id)
    WHERE source_ref_id IS NOT NULL;

CREATE TABLE INVOICE (
    invoice_id     UUID          PRIMARY KEY,
    patient_id     UUID          NOT NULL,
    created_date   DATE          NOT NULL,
    total_amount   DECIMAL(15,2) NOT NULL,
    is_paid        BOOLEAN       NOT NULL DEFAULT false,
    payment_method VARCHAR(20),
    dispense_id    UUID,                           -- ref pharmacy-service DISPENSE_SLIP, nullable
    prescription_id UUID,                          -- prescription that started the saga
    saga_status    VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT ck_invoice_total_non_negative CHECK (total_amount >= 0)
);
CREATE INDEX idx_invoice_patient ON INVOICE (patient_id);
CREATE UNIQUE INDEX uq_invoice_prescription ON INVOICE (prescription_id) WHERE prescription_id IS NOT NULL;

CREATE TABLE PROCESSED_EVENT (
    event_id    UUID          PRIMARY KEY,
    routing_key VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

> **Mapping (tiếng Việt → tiếng Anh)** — bảng dịch chính thức cho billing:

| Việt (trước) | Anh (sau) | Dùng trong |
|---|---|---|
| VIEN_PHI | FEE | bảng |
| ma_vien_phi | fee_id | cột |
| ma_benh_nhan | patient_id | cột |
| ma_ho_so | record_id | cột |
| ma_khoa | department_id | cột |
| ma_tham_chieu | source_ref_id | cột |
| loai_phi | fee_type | cột |
| ngay_phat_sinh | incurred_date | cột |
| so_tien | amount | cột |
| da_thanh_toan | is_paid | cột |
| ma_hoa_don | invoice_id | cột |
| HOADON | INVOICE | bảng |
| ma_hoa_don | invoice_id | cột |
| ngay_tao | created_date | cột |
| tong_tien | total_amount | cột |
| hinh_thuc_tt | payment_method | cột |
| ma_phieu_xuat | dispense_id | cột |
| ma_ban_ke | prescription_id | cột |
| trang_thai_saga | saga_status | cột |
| ngay_thanh_toan | paid_at | cột |
| SU_KIEN_DA_XU_LY | PROCESSED_EVENT | bảng |
| xu_ly_luc | processed_at | cột |
| LoaiPhi | FeeType | enum |
| KHAM | EXAM | enum value |
| XN | LAB | enum value |
| THUOC | DRUG | enum value |
| DV | SERVICE | enum value |
| HinhThucTT | PaymentMethod | enum |
| TIEN_MAT | CASH | enum value |
| CHUYEN_KHOAN | TRANSFER | enum value |
| BHYT | INSURANCE | enum value |
| TrangThaiSaga | SagaStatus | enum |
| CHO_THANH_TOAN | AWAITING_PAYMENT | enum value |
| DA_THANH_TOAN | PAID | enum value |
| CHO_XUAT_THUOC | AWAITING_DISPENSE | enum value |
| HOAN_TAT | COMPLETED | enum value |
| DA_HOAN_TIEN | REFUNDED | enum value |

## 2. Enum

```java
public enum FeeType       { EXAM, LAB, DRUG, SERVICE }
public enum PaymentMethod { CASH, TRANSFER, INSURANCE }
public enum SagaStatus    { NONE, AWAITING_PAYMENT, PAID, AWAITING_DISPENSE, COMPLETED, REFUNDED }
```

## 3. Máy trạng thái saga

`saga_status` chỉ rời khỏi `NONE` với những hóa đơn sinh ra từ đơn thuốc.

```
prescription.created
        │
        ▼
   AWAITING_PAYMENT ──── PUT /invoices/{id}/pay ────► PAID
                                                          │ publish payment.completed
                                                          ▼
                                                   AWAITING_DISPENSE
                                     ┌────────────────────┴────────────────────┐
                     prescription.filled                        prescription.dispense.failed
                                     ▼                                          ▼
                                 COMPLETED                                 REFUNDED
                                                            (đảo viện phí, publish payment.failed)
```

Chuyển tiếp hợp lệ — còn lại ném `BILLING_INVALID_SAGA_TRANSITION`:

| Từ | Sang |
|----|------|
| `NONE` | *(kết thúc — hóa đơn thường, không có saga)* |
| `AWAITING_PAYMENT` | `PAID` |
| `PAID` | `AWAITING_DISPENSE` |
| `AWAITING_DISPENSE` | `COMPLETED`, `REFUNDED` |
| `COMPLETED`, `REFUNDED` | *(kết thúc)* |

## 4. Domain model

> Tên class/field trong nhánh C dùng tiếng Anh: `VienPhi` → `Fee`, `HoaDon` → `Invoice`, ...

### `Fee` (khoản viện phí)

`feeId`, `patientId`, `recordId`, `departmentId`, `sourceRefId`, `feeType`, `incurredDate`,
`amount`, `isPaid`, `invoiceId`.

```java
public static Fee create(UUID patientId, UUID recordId, UUID departmentId, UUID sourceRefId,
                         FeeType feeType, LocalDate incurredDate, BigDecimal amount);
public void assignToInvoice(UUID invoiceId);
public void markPaid();                // BR-B3
public void refund();                  // bù trừ: is_paid = false, gỡ khỏi hóa đơn
public boolean isUnpaid();
```

Invariants: `amount >= 0` (`BILLING_AMOUNT_NEGATIVE`); `feeType` và `departmentId` not null
(`BILLING_DEPT_REQUIRED` — mọi khoản phí đều phải thuộc về một khoa).

### `Invoice` (hóa đơn — aggregate root)

`invoiceId`, `patientId`, `createdDate`, `totalAmount`, `isPaid`, `paymentMethod`, `dispenseId`,
`prescriptionId`, `sagaStatus`, `paidAt`.

```java
public static Invoice create(UUID patientId, LocalDate createdDate, List<Fee> unpaidFees);
public static Invoice createFromPrescription(UUID patientId, UUID prescriptionId, List<Fee> fees);  // cửa vào saga
public void pay(PaymentMethod method, Instant timestamp);   // BR-B1
public void transitionSaga(SagaStatus next);
public void refund();
public boolean isAlreadyPaid();
```

Invariants:

| Check | Error code |
|-------|-----------|
| gọi `pay` trên hóa đơn đã trả | `BILLING_ALREADY_PAID` (BR-B1) |
| dựng hóa đơn từ danh sách phí rỗng | `BILLING_NO_UNPAID_FEES` |
| chuyển saga không hợp lệ | `BILLING_INVALID_SAGA_TRANSITION` |

`totalAmount` được **tính ra** từ danh sách phí (BR-B2), không bao giờ nhận từ request.

## 5. Mã lỗi

`INVOICE_NOT_FOUND`, `FEE_NOT_FOUND` → 404 ·
`BILLING_ALREADY_PAID`, `BILLING_NO_UNPAID_FEES`, `BILLING_AMOUNT_NEGATIVE`, `BILLING_DEPT_REQUIRED`, `BILLING_INVALID_SAGA_TRANSITION` → 422.

## 6. Port

```java
// out
public interface FeeRepositoryPort {
    Fee save(Fee fee);
    List<Fee> saveAll(List<Fee> list);
    Optional<Fee> findById(UUID id);
    List<Fee> findUnpaidByPatient(UUID patientId);
    List<Fee> findByInvoice(UUID invoiceId);
    boolean existsBySource(FeeType feeType, UUID sourceRefId);   // BR-B7
}
public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    Optional<Invoice> findByPrescription(UUID prescriptionId);
    PageResult<Invoice> findByPatient(UUID patientId, PageQuery page);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }
public interface BillingEventPublisherPort {
    void publishInvoiceCreated(InvoiceCreatedEvent e);
    void publishPaymentCompleted(PaymentCompletedEvent e);
    void publishPaymentFailed(PaymentFailedEvent e);
}
/** Price list for auto-generated fees. V1: a table in DB, not a separate service. */
public interface PriceListPort {
    BigDecimal examFee(UUID departmentId);
    BigDecimal labFee(String labType);
}

// in
public interface ManageInvoiceUseCase {
    InvoiceDTO create(CreateInvoiceRequest r);
    InvoiceDTO getById(UUID id);
    PageResult<InvoiceDTO> byPatient(UUID patientId, PageQuery page);
    PaymentResultDTO pay(UUID id, PayInvoiceRequest r);
}
public interface AccrueFeeUseCase {         // driven by event consumers
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
2. `invoice.pay(method, now)` → ném `BILLING_ALREADY_PAID` nếu đã trả (BR-B1)
3. `feeRepo.findByInvoice(id)` → gọi `markPaid()` trên từng khoản → `saveAll` (BR-B3)
4. nếu `sagaStatus == AWAITING_PAYMENT`: `transitionSaga(PAID)` rồi `transitionSaga(AWAITING_DISPENSE)`
5. lưu
6. publish `PaymentCompletedEvent` **sau khi commit** — đây chính là thứ kích hoạt pharmacy xuất thuốc

### `onPrescriptionCreated(e)` — cửa vào saga

1. `processed.alreadyProcessed(e.eventId())` → return
2. `invoiceRepo.findByPrescription(e.prescriptionId())` đã có → return *(unique index cũng chặn thêm lần nữa)*
3. tạo một `Fee{feeType = DRUG, sourceRefId = prescriptionId, amount = e.totalAmount(), departmentId = e.departmentId()}`
4. `Invoice.createFromPrescription(...)`, `transitionSaga(AWAITING_PAYMENT)`
5. lưu, đánh dấu đã xử lý
6. publish `InvoiceCreatedEvent`

### `onDispenseFailed(e)` — bù trừ

1. khử trùng lặp theo `eventId`
2. nạp hóa đơn theo `prescriptionId` → không có thì ghi log rồi return (không có gì để bù)
3. `invoice.refund()` — đặt `isPaid = false`, `transitionSaga(REFUNDED)`
4. gọi `refund()` trên mọi `Fee` đính kèm
5. lưu
6. publish `PaymentFailedEvent{invoiceId, patientId, reason: e.reason()}` → notification báo cho bệnh nhân

> Tiền được *đảo trong sổ sách*, không phải hoàn qua cổng thanh toán — hệ thống này không có cổng
> thanh toán nào. Hãy ghi đúng như vậy trong báo cáo thay vì nói mập mờ là "hoàn tiền".

### `onPrescriptionFilled(e)` — saga thành công

khử trùng lặp → nạp hóa đơn theo `prescriptionId` → `transitionSaga(COMPLETED)` → đặt `dispenseId` → lưu.
Không publish event nào.

### Sinh viện phí từ ba event còn lại

| Event | Khoản phí được tạo |
|-------|--------------------|
| `medicalrecord.created` | `EXAM`, số tiền `priceList.examFee(departmentId)`, `sourceRefId = recordId` |
| `lab.result.created` | `LAB`, số tiền `priceList.labFee(labType)`, `sourceRefId = labId` |
| `appointment.status.changed` khi `status = ARRIVED` | `EXAM` nếu hồ sơ đó chưa có phí |

Cả bốn consumer đều kiểm `existsBySource(feeType, sourceRefId)` trước (BR-B7) — unique partial index
đảm bảo an toàn ngay cả khi có tương tranh.

## 8. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/billing/invoices/{id}` | — | `InvoiceDTO` | ADMIN, CASHIER |
| GET | `/api/v1/billing/patient/{patientId}?page&size` | — | `PageResult<InvoiceDTO>` | ADMIN, CASHIER |
| POST | `/api/v1/billing/invoices` | `CreateInvoiceRequest` | 201 | ADMIN, CASHIER |
| PUT | `/api/v1/billing/invoices/{id}/pay` | `PayInvoiceRequest` | `PaymentResultDTO` | ADMIN, CASHIER |
| GET | `/api/v1/billing/revenue?departmentId&fromDate&toDate` | — | `List<RevenueByDeptDTO>` | ADMIN, MANAGER |

```java
public record CreateInvoiceRequest(@NotNull UUID patientId, @NotNull @PastOrPresent LocalDate createdDate) {}
// không có số tiền — server tự cộng các khoản phí chưa trả của bệnh nhân (BR-B2)

public record PayInvoiceRequest(@NotNull PaymentMethod paymentMethod) {}

public record InvoiceDTO(UUID invoiceId, UUID patientId, LocalDate createdDate, BigDecimal totalAmount,
                         boolean isPaid, PaymentMethod paymentMethod, UUID dispenseId, UUID prescriptionId,
                         SagaStatus sagaStatus, Instant paidAt, List<FeeDTO> fees) {}

public record FeeDTO(UUID feeId, FeeType feeType, UUID departmentId, LocalDate incurredDate,
                     BigDecimal amount, boolean isPaid) {}

public record PaymentResultDTO(UUID invoiceId, boolean success, BigDecimal totalAmount,
                               PaymentMethod paymentMethod, Instant paidAt, SagaStatus sagaStatus) {}

public record RevenueByDeptDTO(UUID departmentId, BigDecimal totalRevenue, long invoiceCount) {}
```

## 9. Event

**Publish**

| Routing key | Payload |
|-------------|---------|
| `invoice.created` | `{envelope, invoiceId, patientId, departmentId, totalAmount, items[]}` |
| `payment.completed` | `{envelope, invoiceId, patientId, departmentId, prescriptionId, totalAmount, paymentMethod}` |
| `payment.failed` | `{envelope, invoiceId, patientId, reason}` |

`payment.completed` mang theo `prescriptionId` để pharmacy biết phải xuất đơn thuốc nào. Thiếu trường này
thì pharmacy phải đoán — đừng bỏ nó.

**Subscribe** — queue `billing.q`

| Routing key | Xử lý |
|-------------|-------|
| `prescription.created` | cửa vào saga — tạo hóa đơn |
| `prescription.filled` | saga thành công → `COMPLETED` |
| `prescription.dispense.failed` | **bù trừ** → `REFUNDED` + `payment.failed` |
| `medicalrecord.created` | sinh phí `EXAM` |
| `lab.result.created` | sinh phí `LAB` |
| `appointment.status.changed` | sinh phí `EXAM` khi `ARRIVED` |

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-B1 | Không thanh toán hóa đơn đã trả | `pay_alreadyPaid_throwsBusinessRule` |
| BR-B2 | `total_amount = Σ` các khoản phí chưa trả | `createInvoice_sumsUnpaidFeesOnly` |
| BR-B3 | Thanh toán đánh dấu mọi khoản phí liên quan | `pay_marksAllRelatedFeesPaid` |
| BR-B4 | Xuất thuốc thất bại thì đảo thanh toán | `onDispenseFailed_reversesInvoiceAndFees` |
| BR-B5 | Xuất thất bại thì publish `payment.failed` | `onDispenseFailed_publishesPaymentFailed` |
| BR-B6 | `prescription.created` chỉ tạo đúng một hóa đơn | `onPrescriptionCreated_twice_createsOneInvoice` |
| BR-B7 | Phí idempotent theo event nguồn | `onLabResult_sameEventTwice_createsOneFee` |
| BR-B8 | Mọi khoản phí đều mang `department_id` | `accrueFee_alwaysSetsDepartmentId` |
| BR-B9 | Chuyển trạng thái saga được kiểm tra | `transitionSaga_completedToRefunded_throwsInvalidTransition` |
| BR-B10 | Doanh thu nhóm theo khoa | `revenue_groupsByDepartmentIdAndDateRange` |
| BR-B11 | Xuất thuốc thành công thì kết thúc saga | `onPrescriptionFilled_setsCompleted` |

## 11. Điểm dễ sai

- Toàn bộ saga phụ thuộc vào việc `payment.completed` tới pharmacy **sau khi DB đã commit**. Dùng `@TransactionalEventListener(AFTER_COMMIT)` hoặc outbox. Publish bên trong transaction có thể khiến thuốc được xuất cho một lần thanh toán sau đó bị rollback.
- `uq_invoice_prescription` và `uq_fee_source` là **partial unique index** (`WHERE ... IS NOT NULL`). Ràng buộc unique thường sẽ từ chối nhiều giá trị NULL trên một số engine — giữ nguyên dạng partial.
- `BigDecimal` ở mọi nơi; `setScale(2, HALF_UP)` cho mọi tổng.
- `PriceListPort` cố ý là một port. V1 có thể hiện thực bằng bảng `PRICE_LIST` hoặc `@ConfigurationProperties`; sau này muốn tách thành service bảng giá riêng thì thay adapter, tầng application không đổi một dòng.
- Bệnh nhân không còn khoản phí chưa trả nào phải nhận `BILLING_NO_UNPAID_FEES` (422), không phải một hóa đơn tổng bằng 0.

---

## 12. Coding map (chỉ dẫn hiện thực — bổ sung cho spec)

> Mục này dành cho coder: file nào tạo, nội dung gì, đặt ở đâu. Spec là **nơi** (bounded context),
> mục này là **cách** (cây file cụ thể). Kết hợp với boilerplate chuẩn ở `docs/ai/reference/`.

### 12.1 Bản đồ file Java (mọi file cần tạo)

Base package `com.mediflow.billing`. Cây đầy đủ:

```
billing-service/src/main/java/com/mediflow/billing/
├── BillingServiceApplication.java                       # có sẵn
├── domain/model/
│   ├── FeeType.java                                     # enum { EXAM, LAB, DRUG, SERVICE }
│   ├── PaymentMethod.java                               # enum { CASH, TRANSFER, INSURANCE }
│   ├── SagaStatus.java                                  # enum { NONE, AWAITING_PAYMENT, PAID, AWAITING_DISPENSE, COMPLETED, REFUNDED }
│   ├── Fee.java                                         # khoản viện phí (amount >= 0, departmentId != null)
│   └── Invoice.java                                     # aggregate root + máy trạng thái saga (BR-B1..B3, B9)
├── domain/exception/
│   ├── InvoiceNotFoundException.java                    # 404, code INVOICE_NOT_FOUND
│   ├── FeeNotFoundException.java                        # 404, code FEE_NOT_FOUND
│   └── BillingRuleException.java                        # 422, dùng chung cho mã BILLING_*
├── application/port/in/
│   ├── ManageInvoiceUseCase.java                        # create/getById/byPatient/pay
│   ├── AccrueFeeUseCase.java                            # 4 consumer event: onMedicalRecord/onLabResult/onAppointmentStatus/onPrescriptionCreated
│   └── SagaCompensationUseCase.java                     # onDispenseFailed/onPrescriptionFilled
├── application/port/out/
│   ├── FeeRepositoryPort.java
│   ├── InvoiceRepositoryPort.java
│   ├── ProcessedEventPort.java
│   ├── BillingEventPublisherPort.java
│   └── PriceListPort.java
├── application/dto/request/
│   ├── CreateInvoiceRequest.java
│   └── PayInvoiceRequest.java
├── application/dto/response/
│   ├── InvoiceDTO.java
│   ├── FeeDTO.java
│   ├── PaymentResultDTO.java
│   └── RevenueByDeptDTO.java
├── application/mapper/
│   ├── InvoiceDtoMapper.java                            # MapStruct: Invoice(+fees) ↔ InvoiceDTO
│   ├── FeeDtoMapper.java                                # MapStruct: Fee ↔ FeeDTO
│   └── RevenueMapper.java                               # MapStruct (hoặc trực tiếp) cho RevenueByDeptDTO
├── application/service/
│   ├── BillingApplicationService.java                   # hiện thực ManageInvoice
│   ├── FeeAccrualService.java                           # hiện thực AccrueFeeUseCase (4 handler)
│   └── SagaCompensationService.java                     # hiện thực SagaCompensationUseCase
├── web/                                  # DRIVING adapter (HTTP) — gọi vào application
│   ├── InvoiceController.java                           # /api/v1/billing/invoices (4 endpoints)
│   ├── RevenueController.java                           # GET /api/v1/billing/revenue (nếu tách)
│   └── GlobalExceptionHandler.java                      # copy từ docs/ai/reference (chỉ đổi package)
├── messaging/consumer/                   # DRIVING adapter (events) — gọi vào application
│   ├── PrescriptionCreatedConsumer.java                 # saga vào — tạo hóa đơn
│   ├── PrescriptionFilledConsumer.java                  # saga thành công → COMPLETED
│   ├── PrescriptionDispenseFailedConsumer.java          # bù trừ → REFUNDED + payment.failed
│   ├── MedicalRecordCreatedConsumer.java                # sinh phí EXAM
│   ├── LabResultCreatedConsumer.java                    # sinh phí LAB
│   └── AppointmentStatusChangedConsumer.java            # sinh phí EXAM khi ARRIVED
├── infrastructure/persistence/           # DRIVEN adapter (DB) — hiện thực port out
│   ├── FeeJpaEntity.java
│   ├── FeeJpaRepository.java                            # + existsBySource (partial unique index)
│   ├── FeePersistenceAdapter.java
│   ├── FeePersistenceMapper.java
│   ├── InvoiceJpaEntity.java
│   ├── InvoiceJpaRepository.java
│   ├── InvoicePersistenceAdapter.java
│   ├── InvoicePersistenceMapper.java
│   ├── ProcessedEventPersistenceAdapter.java            # hiện thực ProcessedEventPort (bảng PROCESSED_EVENT)
│   └── PriceListAdapter.java                            # hiện thực PriceListPort (bảng PRICE_LIST hoặc @ConfigurationProperties)
└── infrastructure/messaging/             # DRIVEN adapter (RabbitMQ) — publisher + payload
    ├── BillingEventPublisherAdapter.java                # hiện thực BillingEventPublisherPort (publish sau commit)
    └── payload/
        ├── InvoiceCreatedEvent.java
        ├── PaymentCompletedEvent.java
        └── PaymentFailedEvent.java
```

Các file boilerplate cần copy từ `docs/ai/reference/` hoặc một service đã có (giống pharmacy §13.1):
`infrastructure/config/RabbitConfig.java`, `security/JwtAuthFilter.java`, `security/JwtProperties.java`,
`config/SecurityConfig.java`, `config/OpenApiConfig.java`, `db/migration/V1__init.sql`.

> **Ghi chú:** 6 consumer đều nhận một event record (khai báo ở `infrastructure/messaging/payload/`
> của service *publisher* — pharmacy/lab/clinical). Billing chỉ cần đúng `class`/field tương ứng; để
> tránh dependency cross-service, **khai báo lại record cùng tên/field trong billing** (mỗi service tự
> định nghĩa event nó tiêu thụ). Đây là contract qua JSON, không phải import Java.

### 12.2 Event payloads — dạng Java record

```java
// InvoiceCreatedEvent — routing key "invoice.created", publish sau khi tạo hóa đơn
public record InvoiceCreatedEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID invoiceId, UUID patientId, UUID departmentId,
    BigDecimal totalAmount, List<Item> items) {
    public record Item(UUID feeId, FeeType type, BigDecimal amount) {}
}

// PaymentCompletedEvent — routing key "payment.completed", mang prescriptionId để pharmacy biết xuất đơn nào
public record PaymentCompletedEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID invoiceId, UUID patientId, UUID departmentId, UUID prescriptionId,
    BigDecimal totalAmount, PaymentMethod paymentMethod) {}

// PaymentFailedEvent — routing key "payment.failed", bù trừ + notification
public record PaymentFailedEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID invoiceId, UUID patientId, String reason) {}
```

### 12.3 Chi tiết persistence

**`FeeJpaRepository` — đánh index/idempotency đúng spec §1:**

```java
public interface FeeJpaRepository extends JpaRepository<FeeJpaEntity, UUID> {
    // uq_fee_source (partial unique index trên (fee_type, source_ref_id) WHERE source_ref_id IS NOT NULL)
    // → đây là tuyến phòng thủ BR-B7; phương thức này kiểm tra trước khi tạo
    boolean existsByFeeTypeAndSourceRefId(FeeType feeType, UUID sourceRefId);

    List<FeeJpaEntity> findByPatientIdAndIsPaidFalse(UUID patientId);   // phí chưa trả
    List<FeeJpaEntity> findByInvoiceId(UUID invoiceId);                 // phí trong hóa đơn
}
```

**`InvoiceJpaRepository` — `findByPrescriptionId` phụ thuộc unique index `uq_invoice_prescription`:**

```java
public interface InvoiceJpaRepository extends JpaRepository<InvoiceJpaEntity, UUID> {
    Optional<InvoiceJpaEntity> findByPrescriptionId(UUID prescriptionId);   // mỗi đơn thuốc tối đa 1 hóa đơn
    Page<InvoiceJpaEntity> findByPatientId(UUID patientId, Pageable pageable);
}
```

**Ánh xạ entity ↔ domain (MapStruct):**

| Entity field | Domain field | Kiểu |
|---|---|---|
| `fee_id` | `feeId` | UUID |
| `patient_id` | `patientId` | UUID |
| `record_id` | `recordId` | UUID (nullable) |
| `department_id` | `departmentId` | UUID |
| `source_ref_id` | `sourceRefId` | UUID (nullable) |
| `fee_type` | `feeType` | FeeType |
| `incurred_date` | `incurredDate` | LocalDate |
| `amount` | `amount` | BigDecimal |
| `is_paid` | `isPaid` | boolean |
| `invoice_id` | `invoiceId` | UUID (nullable) |
| (tương tự) `INVOICE.*` | `Invoice.*` | — |

**Adapter cần làm đúng:**
1. `findByPrescriptionId` — dựa trên unique index, trả `Optional` (nếu có 2 dòng thì là lỗi DB, không phải logic).
2. `existsByFeeTypeAndSourceRefId` — gọi **trước khi** tạo phí mới (BR-B7); partial index là lớp phòng thủ thứ hai.
3. `findByPatientIdAndIsPaidFalse` — dùng để cộng `totalAmount` khi tạo hóa đơn (BR-B2).
4. `PriceListAdapter` — V1 hiện thực bằng bảng `PRICE_LIST` (đơn giản) hoặc `@ConfigurationProperties`. Đừng tạo service riêng.

### 12.4 RabbitConfig — hằng số & binding (billing)

```
EXCHANGE = "mediflow.events"        (durable topic)
DLX      = "mediflow.events.dlx"
QUEUE    = "billing.q"              (durable, DLX + DLQ "billing.dlq")
```

| Routing key | Hướng | Dùng cho |
|---|---|---|
| `invoice.created` | publish | thông báo tạo hóa đơn |
| `payment.completed` | publish | saga — pharmacy xuất thuốc |
| `payment.failed` | publish | bù trừ + notification |
| `prescription.created` | **subscribe** | saga vào — tạo hóa đơn |
| `prescription.filled` | **subscribe** | saga thành công — `COMPLETED` |
| `prescription.dispense.failed` | **subscribe** | bù trừ — `REFUNDED` |
| `medicalrecord.created` | **subscribe** | sinh phí `EXAM` |
| `lab.result.created` | **subscribe** | sinh phí `LAB` |
| `appointment.status.changed` | **subscribe** | sinh phí `EXAM` khi `ARRIVED` |

Queue `billing.q` bind **6 routing key** (bỏ `invoice.created`, `payment.*` vì là publish).

### 12.5 Test plan — map business rule → tầng + tên test

| Rule | Tầng | Tên test | Cần gì |
|---|---|---|---|
| BR-B1 (không thanh toán hóa đơn đã trả) | domain unit | `pay_alreadyPaid_throwsBusinessRule` | không Spring |
| BR-B2 (total_amount = Σ phí chưa trả) | application (mock repo) | `createInvoice_sumsUnpaidFeesOnly` | mock `FeeRepositoryPort.findUnpaidByPatient` |
| BR-B3 (đánh dấu mọi phí đã trả) | application | `pay_marksAllRelatedFeesPaid` | mock repo, verify `saveAll` |
| BR-B4 (đảo thanh toán khi bù trừ) | application | `onDispenseFailed_reversesInvoiceAndFees` | mock repo, verify `refund` |
| BR-B5 (publish payment.failed) | application | `onDispenseFailed_publishesPaymentFailed` | mock publisher, verify |
| BR-B6 (prescription.created chỉ tạo 1 hóa đơn) | application | `onPrescriptionCreated_twice_createsOneInvoice` | mock `alreadyProcessed`, assert 1 save |
| BR-B7 (phí idempotent theo nguồn) | application | `onLabResult_sameEventTwice_createsOneFee` | mock `existsBySource` |
| BR-B8 (mọi phí có department_id) | application | `accrueFee_alwaysSetsDepartmentId` | mock PriceListPort |
| BR-B9 (chuyển saga sai bị chặn) | domain unit | `transitionSaga_completedToRefunded_throwsInvalidTransition` | không Spring |
| BR-B10 (doanh thu nhóm theo khoa) | application | `revenue_groupsByDepartmentIdAndDateRange` | mock repo |
| BR-B11 (prescription.filled → COMPLETED) | application | `onPrescriptionFilled_setsCompleted` | mock repo |

**Test saga đầy đủ (BR-B4..B11 + tương tác pharmacy) → task riêng #4.**

### 12.6 Checklist hoàn thiện billing (Definition of Done)

- [ ] `V1__init.sql` đủ 3 bảng + 2 partial unique index (giữ nguyên dạng `WHERE ... IS NOT NULL`).
- [ ] Domain: 3 enum + 2 model + 3 exception; `Invoice` thực thi máy trạng thái saga.
- [ ] Ports đủ (5 out, 3 in); application service hiện thực, không import Spring Data/AMQP.
- [ ] DTO records có Bean Validation; 3 MapStruct mapper.
- [ ] Controller + `@PreAuthorize` (ADMIN/CASHIER/MANAGER); `RevenueController` tách nếu cần.
- [ ] 3 event record + publisher adapter (sau commit); 6 consumer idempotent.
- [ ] `PriceListAdapter` hiện thực port.
- [ ] `GlobalExceptionHandler`, `SecurityConfig`, `RabbitConfig`, `OpenApiConfig`.
- [ ] Test 5 tầng; 11 business rule được phủ.
- [ ] `mvn -pl backend/billing-service -am -q -DskipTests install` xanh.
