# Service: pharmacy (Khoa Dược)

**Module:** `backend/pharmacy-service/` · **Cổng chạy:** 8085 · **Base path:** `/api/v1/pharmacy` · **Database:** `mediflow_pharmacy`

**Nguồn chuẩn:** `docs/eproject_general_plan/pharmacy-service.html` và `docs/eproject_general_plan/backend-spec/05-pharmacy.md`.

## 1. Service này làm gì?

Pharmacy lo toàn bộ chuyện thuốc men trong bệnh viện, gồm 4 việc chính:

1. **Danh mục thuốc** — bệnh viện có những thuốc gì, giá bao nhiêu, hạn dùng ra sao.
2. **Tồn kho** — mỗi lần xuất thuốc thì giảm số lượng tồn; gửi cảnh báo khi sắp hết.
3. **Đơn thuốc** — bác sĩ kê đơn: ghi các dòng thuốc, tự tính tiền từng dòng và tổng tiền.
4. **Phiếu xuất thuốc** — sau khi bệnh nhân trả tiền, dược sĩ xuất thuốc và trừ kho.

Pharmacy là service **duy nhất có thể "hết hàng"**, nên nó tham gia saga *kê đơn → hóa đơn → thanh toán → xuất thuốc*:

- Kê đơn xong → báo `prescription.created` để billing tạo hóa đơn.
- Bệnh nhân trả tiền xong → billing báo `payment.completed` → pharmacy mới xuất thuốc.
- Xuất thất bại (hết hàng / hết hạn) → báo `prescription.dispense.failed` để billing bù trừ.

## 2. Phạm vi (bounded context)

**Sở hữu:** thuốc, tồn kho, đơn thuốc, phiếu xuất.

**Không sở hữu:** bệnh nhân, hồ sơ bệnh án, hóa đơn, tiền bạc. Pharmacy chỉ giữ **UUID tham chiếu** (`patientId`, `recordId`, `doctorId`, `departmentId`) — không nối thẳng DB của service khác (xem `docs/ai/08-persistence-naming.md`).

## 3. Cách tổ chức code (nhìn nhanh)

```
HTTP (web/)  hoặc  event từ RabbitMQ (messaging/consumer/)
                    |
                    | gọi in-port (cổng vào)
                    v
            application/service  ── kiểm tra quy tắc ──►  domain/model
                    |
                    | gọi out-port (cổng ra)
                    v
   infrastructure/persistence (PostgreSQL)   ·   infrastructure/messaging (RabbitMQ)
```

Quy tắc quan trọng nhất: **application chỉ biết tên các interface (port), không biết ai làm thật.** Việc làm thật (JPA, RabbitMQ) nằm hết trong `infrastructure/`. Nhờ vậy, muốn đổi database hay đổi cách gửi event thì không phải sửa quy tắc nghiệp vụ. Chi tiết đầy đủ ở `docs/ai/04-microservice-blueprint.md`.

## 4. Dữ liệu

> **Lưu ý:** nhánh service C (pharmacy – billing – report) dùng tên bảng **tiếng Anh** theo spec 05. Các service khác trong hệ thống dùng tên tiếng Việt. Đây là quy ước riêng của nhánh C, bảng dịch chính thức nằm ở đầu spec 05.

### `DRUG` — danh mục thuốc

| Cột | Ý nghĩa |
|-----|---------|
| `drug_id` | UUID khóa chính |
| `drug_name` | tên thuốc |
| `active_ingredient` | hoạt chất |
| `unit` | đơn vị tính (viên, ống, hộp…) |
| `price` | đơn giá, `DECIMAL(15,2)` — không bao giờ dùng `double` cho tiền |
| `stock_quantity` | số lượng tồn kho |
| `expiry_date` | hạn sử dụng |
| `manufacturer` | nhà sản xuất |
| `low_stock_threshold` | ngưỡng cảnh báo sắp hết hàng (mặc định 10) |

Ràng buộc DB: `price >= 0`, `stock_quantity >= 0` — đây là "tuyến phòng thủ cuối", quy tắc thật nằm trong domain model.

### `PRESCRIPTION` — đơn thuốc

`prescription_id` UUID PK · `record_id` (UUID, tham chiếu clinical) · `patient_id` (UUID, tham chiếu patient) · `doctor_id` (UUID, tham chiếu organization `NHAN_VIEN`) · `department_id` (UUID, tham chiếu organization `KHOA` — khoa kê đơn) · `prescribed_date` DATE · `total_amount` DECIMAL(15,2) — tổng tiền tự tính.

### `PRESCRIPTION_LINE` — từng dòng thuốc trong đơn

`line_id` UUID PK · `prescription_id` FK (xóa đơn thì xóa dòng) · `drug_id` FK · `quantity` (phải > 0) · `unit_price` **giá chụp tại thời điểm kê đơn** · `dosage` liều dùng · `line_total` = giá × số lượng.

### `DISPENSE_SLIP` — phiếu xuất thuốc

`dispense_id` UUID PK · `prescription_id` **UNIQUE** (mỗi đơn đúng một phiếu) · `status` `PENDING` / `DISPENSED` / `FAILED` · `dispensed_at` · `dispensed_by` (UUID, tham chiếu nhân viên) · `failure_reason` — lý do khi phiếu ở trạng thái `FAILED`.

> Tên trạng thái cũ trong bản thiết kế tổng: `CHO_XUAT` / `DA_XUAT` — nhánh C đổi thành `PENDING` / `DISPENSED` / `FAILED`.

### `PROCESSED_EVENT` — sổ ghi các event đã xử lý

`event_id` UUID PK · `routing_key` · `processed_at`. Bảng này dùng để **chống xử lý trùng** khi RabbitMQ gửi lại tin (xem quy tắc BR-D9).

## 5. Các cổng (ports) — phần quan trọng nhất

### 5.0 Port là gì?

**Port = bản hợp đồng** giữa application và thế giới bên ngoài: một danh sách các phương thức đã thống nhất, chỉ có tên và tham số, không có code làm thật.

- **In-port (cổng vào):** bên ngoài (web, RabbitMQ) nhờ service làm việc gì. Vd: "kê đơn", "xuất thuốc".
- **Out-port (cổng ra):** service cần bên ngoài giúp việc gì. Vd: "lưu thuốc xuống DB", "gửi event".
- **Adapter (bộ chuyển đổi):** phần code thật sự nối với DB / RabbitMQ, đứng sau out-port. Application gọi port, adapter làm thật.

Vì sao phải vẽ ra hợp đồng như vậy? Vì application giữ toàn bộ quy tắc nghiệp vụ. Nếu application gọi thẳng JPA hay RabbitMQ thì khi đổi công nghệ phải sửa cả quy tắc. Có port thì chỉ cần thay adapter.

### 5.1 Cổng vào (in-ports) — 4 interface

#### `ManageDrugUseCase` — quản lý danh mục thuốc

**Vì sao cần:** dược sĩ và admin phải thêm thuốc mới, xem thông tin thuốc, tìm thuốc và nhập kho. Gộp chung vào một interface vì chúng cùng phục vụ một nhóm nghiệp vụ "quản lý danh mục".

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `create(CreateDrugRequest)` | thêm thuốc mới | tên/đơn vị bắt buộc, giá không âm, hạn dùng không ở quá khứ (kiểm tra trong `Drug.create`) |
| `getById(UUID)` | xem 1 thuốc | không có thì trả 404 `DRUG_NOT_FOUND` |
| `search(keyword, PageQuery)` | tìm theo tên, phân trang | dùng `PageQuery`/`PageResult` của `common`, không dùng `Pageable` của Spring ở tầng application |
| `adjustStock(UUID, AdjustStockRequest)` | nhập kho / điều chỉnh số lượng | số lượng phải > 0 (`Drug.restock`). **Chỉ** chỉnh tay — không dùng để xuất thuốc, tránh trừ kho hai lần |

#### `CreatePrescriptionUseCase` — kê đơn

**Vì sao cần:** bác sĩ ghi nhận ý định dùng thuốc của bệnh nhân. Đây là bước khởi đầu của saga, nên tách riêng khỏi các việc khác để dễ kiểm soát.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `create(CreatePrescriptionRequest)` | tạo đơn + các dòng thuốc | đơn phải có ít nhất 1 dòng (mã lỗi `PRESCRIPTION_EMPTY`), giá do **server tự lấy từ kho** — client không gửi giá (BR-D7, BR-D8); tổng tiền = Σ(giá × số lượng) (BR-D5); tự tạo phiếu xuất `PENDING` (BR-D3); publish `prescription.created`; **không trừ kho ở bước này** |

#### `DispensePrescriptionUseCase` — xuất thuốc

**Vì sao cần:** đây là bước duy nhất làm thay đổi tồn kho và là nơi xảy ra nhiều quy tắc nhất (hết hàng, hết hạn, tương tranh, bù trừ). Tách riêng để luồng này được xử lý đặc biệt (khóa dòng, bù trừ).

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `dispense(prescriptionId, dispensedBy)` | xuất thuốc theo đơn | phiếu phải đang `PENDING` — không xuất 2 lần (BR-D9); đủ hàng (BR-D1); chưa hết hạn (BR-D2); khóa từng dòng thuốc khi đọc (BR-D10); trừ kho đúng 1 lần (BR-D4); thất bại thì đánh dấu phiếu `FAILED` và báo billing bù trừ (BR-D6, BR-D12); chạm ngưỡng thì gửi `stock.low` (BR-D11); thành công thì publish `prescription.filled` |

#### `ReactToPaymentUseCase` — phản ứng khi có tin "đã thanh toán"

**Vì sao cần:** saga quy định chỉ xuất thuốc **sau khi** bệnh nhân trả tiền. Event `payment.completed` từ billing là tín hiệu đó.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `onPaymentCompleted(prescriptionId, invoiceId)` | gọi lại luồng `dispense` với người thực hiện là hệ thống | kiểm tra event đã xử lý chưa qua `ProcessedEventPort` — gửi lại lần nữa cũng chỉ xuất 1 lần (BR-D9); lỗi nghiệp vụ không ném ngược về RabbitMQ (đã có event bù trừ), chỉ lỗi hệ thống mới đưa vào dead-letter |

### 5.2 Cổng ra (out-ports) — 5 interface

#### `DrugRepositoryPort` — lưu và tìm thuốc *(đã có trong code)*

**Vì sao cần:** mọi thay đổi về thuốc (tạo, nhập kho, trừ kho) đều phải ghi xuống DB, nhưng application không được phép biết JPA. Interface này là "lời hứa": *ai đó hãy lưu và tìm thuốc giúp tôi* — `DrugPersistenceAdapter` trong `infrastructure` sẽ làm thật.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `save(Drug)` | lưu mới hoặc cập nhật, trả về đối tượng đã có id + thời gian | mọi luồng thay đổi thuốc |
| `findById(UUID)` | tìm 1 thuốc để đọc | kê đơn cần lấy giá; xem chi tiết thuốc |
| `findByIdForUpdate(UUID)` | tìm 1 thuốc **kèm khóa ghi** (PESSIMISTIC_WRITE) | **chỉ** luồng `dispense` dùng — hai dược sĩ xuất cùng lúc không được bán vượt kho (BR-D10) |
| `search(keyword, PageQuery)` | tìm theo tên, phân trang | màn hình danh sách thuốc; adapter đổi `PageQuery` → `Pageable` và `Page` → `PageResult` |

#### `PrescriptionRepositoryPort` — lưu và tìm đơn thuốc

**Vì sao cần:** đơn thuốc (kèm các dòng) phải được ghi nhận và đọc lại để phục vụ xem lịch sử và xuất thuốc.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `save(Prescription)` | lưu đơn + các dòng (một aggregate) | kê đơn (BR-D3) |
| `findById(UUID)` | đọc đơn + các dòng | luồng `dispense` cần biết đơn kê những gì; xem chi tiết đơn |
| `findByPatient(UUID)` | danh sách đơn của 1 bệnh nhân | màn hình lịch sử kê đơn |

#### `DispenseSlipRepositoryPort` — lưu và tìm phiếu xuất

**Vì sao cần:** trạng thái phiếu (`PENDING` / `DISPENSED` / `FAILED`) phải được ghi nhận, vì nó là bằng chứng của saga: đã xuất chưa, xuất thất bại vì lý do gì.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `save(DispenseSlip)` | lưu phiếu | BR-D3, BR-D12 (phiếu `FAILED` vẫn phải lưu được) |
| `findById(UUID)` | đọc 1 phiếu | xem trạng thái |
| `findByPrescription(UUID)` | tìm phiếu theo đơn | mỗi đơn đúng 1 phiếu (cột UNIQUE) — luồng `dispense` lấy phiếu từ `prescriptionId` |

#### `ProcessedEventPort` — sổ chống xử lý trùng

**Vì sao cần:** RabbitMQ có thể gửi lại cùng một event. Nếu không kiểm tra, một tin `payment.completed` đến hai lần sẽ xuất thuốc hai lần.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `alreadyProcessed(UUID eventId)` | kiểm tra event đã xử lý chưa | BR-D9 |
| `markProcessed(UUID eventId, String routingKey)` | đánh dấu đã xử lý | BR-D9 |

#### `PharmacyEventPublisherPort` — gửi event ra ngoài

**Vì sao cần:** application phải "báo tin" cho các service khác khi có thay đổi, nhưng không được đụng vào RabbitMQ. Interface này liệt kê các tin pharmacy có thể gửi; `PharmacyEventPublisherAdapter` trong `infrastructure/messaging` làm thật (publish **sau khi** transaction commit để không báo tin cho giao dịch bị hủy).

| Phương thức | Gửi tin gì | Khi nào / vì sao |
|-------------|-----------|------------------|
| `publishPrescriptionCreated(...)` | `prescription.created` | sau khi kê đơn commit — billing dựa vào để tạo hóa đơn (saga) |
| `publishPrescriptionFilled(...)` | `prescription.filled` | sau khi xuất thuốc thành công — clinical/notification/report quan tâm |
| `publishPrescriptionDispenseFailed(...)` | `prescription.dispense.failed` | xuất thất bại — **kích hoạt bù trừ** của billing (BR-D6) |
| `publishStockLow(...)` | `stock.low` | tồn kho chạm ngưỡng (BR-D11). Kiểu "bắn rồi quên": lỗi gửi tin này không được làm hỏng lần xuất thuốc vừa thành công |

## 6. Luồng nghiệp vụ chính

### 6.1 Kê đơn (`createPrescription`)

1. Với từng dòng: tìm thuốc trong kho → không có thì báo `DRUG_NOT_FOUND`.
2. **Chụp giá:** lấy giá từ danh mục, **không** nhận giá từ client (BR-D7, BR-D8).
3. Tính `lineTotal = giá × số lượng`, làm tròn 2 chữ số (`HALF_UP`).
4. Dựng đơn → tính `totalAmount` (BR-D5).
5. Lưu đơn.
6. Tự tạo phiếu xuất trạng thái `PENDING` và lưu (BR-D3).
7. Sau khi commit: publish `prescription.created` → billing tạo hóa đơn.

> **Không trừ kho ở đây.** Kê đơn chỉ là ghi nhận ý định. Kho chỉ biến động khi xuất thuốc (sau khi đã thanh toán). Trừ kho hai lần là lỗi dễ gặp nhất của service này.

### 6.2 Xuất thuốc (`dispense`)

1. Tìm phiếu xuất theo đơn → không có thì báo `DISPENSE_NOT_FOUND`.
2. Phiếu phải đang `PENDING`, nếu không thì báo `DISPENSE_ALREADY_DONE` (BR-D9 — chống xuất 2 lần).
3. Đọc đơn và các dòng.
4. **Sắp xếp các dòng theo `drugId`** trước khi khóa — tránh kẹt (deadlock) khi nhiều lần xuất cùng lúc (BR-D10).
5. Từng dòng: gọi `findByIdForUpdate` (khóa ghi), kiểm tra đủ hàng (BR-D1), chưa hết hạn (BR-D2), rồi trừ kho.
6. **Nếu lỗi:** phần trừ kho bị hoàn tác (transaction rollback), đánh dấu phiếu `FAILED` và lưu trong **transaction mới** (`REQUIRES_NEW`) — phiếu thất bại vẫn phải được ghi lại (BR-D12) — rồi publish `prescription.dispense.failed` để billing bù trừ (BR-D6).
7. **Nếu thành công:** lưu lại từng thuốc, đánh dấu phiếu `DISPENSED`, gửi `stock.low` cho thuốc nào chạm ngưỡng (BR-D11), rồi publish `prescription.filled`.

### 6.3 Nhận tin "đã thanh toán" (`onPaymentCompleted`)

1. Kiểm tra `alreadyProcessed(eventId)` — đã xử lý rồi thì dừng (BR-D9).
2. Gọi `dispense(prescriptionId, SYSTEM)` — người thực hiện là hệ thống, không phải dược sĩ.
3. Đánh dấu `markProcessed(...)` trong cùng transaction.
4. Lỗi nghiệp vụ **không** ném ngược về RabbitMQ (đã có event bù trừ); chỉ lỗi hệ thống mới đưa vào dead-letter.

## 7. API

| Method | Path | Làm gì | Role |
|--------|------|--------|------|
| GET | `/api/v1/pharmacy/drugs?keyword&page&size` | danh sách thuốc (phân trang) | ADMIN, DOCTOR, PHARMACIST |
| GET | `/api/v1/pharmacy/drugs/{id}` | xem 1 thuốc | ADMIN, DOCTOR, PHARMACIST |
| POST | `/api/v1/pharmacy/drugs` | thêm thuốc mới | ADMIN, PHARMACIST |
| PUT | `/api/v1/pharmacy/drugs/{id}/stock` | nhập kho / điều chỉnh | ADMIN, PHARMACIST |
| POST | `/api/v1/pharmacy/prescriptions` | kê đơn | ADMIN, DOCTOR |
| GET | `/api/v1/pharmacy/prescriptions/{id}` | xem chi tiết đơn + trạng thái phiếu xuất | ADMIN, DOCTOR, PHARMACIST |
| PUT | `/api/v1/pharmacy/prescriptions/{id}/dispense` | xuất thuốc | ADMIN, PHARMACIST |

> Phân quyền ghi ở controller (`@PreAuthorize`) — đây là lớp "giao hàng", không phải quy tắc nghiệp vụ (xem `docs/ai/07-security-rbac.md`).

## 8. Sự kiện

### Publish (pharmacy gửi đi)

| Routing key | Nội dung | Khi nào |
|-------------|----------|---------|
| `prescription.created` | `{prescriptionId, patientId, recordId, departmentId, totalAmount, items[]}` | sau khi kê đơn commit — billing tạo hóa đơn |
| `prescription.filled` | `{prescriptionId, patientId, departmentId, totalAmount, dispensedItems[]}` | sau khi xuất thuốc thành công |
| `prescription.dispense.failed` | `{prescriptionId, invoiceId, patientId, reason}` | xuất thất bại — kích hoạt bù trừ saga |
| `stock.low` | `{drugId, drugName, currentStock, threshold}` | tồn kho ≤ ngưỡng |

### Subscribe (pharmacy nhận)

| Routing key | Xử lý |
|-------------|-------|
| `payment.completed` (từ billing) | `onPaymentCompleted` → xuất thuốc (`PENDING` → `DISPENSED`) |

Queue: `pharmacy.q` chỉ bind `payment.completed`. Mọi event đều mang đủ `eventId`, `occurredAt`, `correlationId` theo chuẩn `docs/ai/06-events-rabbitmq.md`.

## 9. Quy tắc nghiệp vụ (12 quy tắc)

| ID | Quy tắc (diễn đạt đơn giản) |
|----|------------------------------|
| BR-D1 | Hết hàng thì không xuất: tồn kho < số lượng yêu cầu → từ chối. |
| BR-D2 | Hết hạn thì không xuất: hạn dùng trước hôm nay → từ chối. |
| BR-D3 | Kê đơn xong phải tự tạo phiếu xuất, trạng thái `PENDING`. |
| BR-D4 | Mỗi lần xuất thuốc chỉ trừ kho đúng một lần. |
| BR-D5 | Tổng tiền đơn = tổng của (giá × số lượng) từng dòng, làm tròn 2 chữ số. |
| BR-D6 | Xuất thất bại phải gửi event bù trừ cho billing. |
| BR-D7 | Giá được "chụp" tại lúc kê đơn; sau này đổi giá không ảnh hưởng đơn cũ. |
| BR-D8 | Client không được gửi giá — request kê đơn không có trường giá. |
| BR-D9 | Tin `payment.completed` gửi lại lần nữa cũng chỉ xuất thuốc một lần. |
| BR-D10 | Tồn kho không bao giờ âm, kể cả khi hai người xuất cùng lúc. |
| BR-D11 | Chạm ngưỡng thì gửi cảnh báo `stock.low`. |
| BR-D12 | Xuất thất bại vẫn phải ghi phiếu trạng thái `FAILED` (dù phần trừ kho đã bị hoàn tác). |

Chi tiết ánh xạ quy tắc → tầng test → tên test: xem mục 11 và 13.5 của spec 05.

## 10. Mã lỗi

| Mã lỗi | HTTP | Tình huống |
|--------|------|------------|
| `DRUG_NOT_FOUND`, `PRESCRIPTION_NOT_FOUND`, `DISPENSE_NOT_FOUND` | 404 | không tìm thấy đối tượng |
| `DRUG_OUT_OF_STOCK`, `DRUG_EXPIRED`, `DRUG_QUANTITY_INVALID`, `DRUG_PRICE_NEGATIVE`, `DRUG_EXPIRY_PAST` | 422 | vi phạm quy tắc thuốc |
| `PRESCRIPTION_EMPTY`, `DISPENSE_INVALID_TRANSITION`, `DISPENSE_ALREADY_DONE`, `DISPENSE_NOT_PAID` | 422 | vi phạm quy tắc đơn / phiếu |

## 11. Code hiện tại: đã có gì, còn thiếu gì

### Đã có (đối chiếu với cây thư mục thật)

- `domain/model/`: `Drug`, `Prescription`, `PrescriptionLine`, `DispenseSlip`, `DispenseStatus` — quy tắc nằm ngay trong model (`Drug.dispenseStock` kiểm tra hết hàng/hết hạn, `DispenseSlip.markDispensed/markFailed` kiểm tra chuyển trạng thái hợp lệ).
- `domain/exception/`: 6 exception kế thừa base của `common` (`ResourceNotFoundException` → 404, `BusinessRuleException` → 422).
- `application/port/out/`: `DrugRepositoryPort` (4 phương thức).
- `infrastructure/persistence/`: 5 JPA entity + 4 repository tương ứng.
- `db/migration/V1__init.sql`: đủ 5 bảng theo spec.
- `ArchitectureTest.java`: kiểm tra quy tắc kiến trúc (domain không dùng Spring/JPA, application không dùng Spring Data/AMQP/HTTP, không vòng lặp giữa các tầng).

### Còn thiếu (theo spec 05 — xem phần "Coding map" và "Definition of Done")

- 4 in-port + 4 out-port còn lại (`PrescriptionRepositoryPort`, `DispenseSlipRepositoryPort`, `ProcessedEventPort`, `PharmacyEventPublisherPort`).
- `application/service/` thực hiện các in-port: kê đơn, xuất thuốc, phản ứng `payment.completed`.
- DTO request/response + mapper (MapStruct).
- `web/`: 2 controller cho 7 endpoint + `GlobalExceptionHandler`.
- `messaging/consumer/`: consumer `payment.completed` (idempotent).
- `infrastructure/messaging/`: publisher adapter + 4 payload event.
- `infrastructure/config/` + `security/`: `RabbitConfig`, `SecurityConfig`, `JwtAuthFilter`, `JwtProperties`, `OpenApiConfig`.
- Test đủ 5 tầng, phủ 12 quy tắc nghiệp vụ (danh sách test cụ thể ở spec 05 mục 13.5).