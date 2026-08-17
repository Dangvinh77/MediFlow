# Service: report (Phòng Kế hoạch – Tổng hợp)

**Module:** `backend/report-service/` · **Cổng chạy:** 8088 · **Base path:** `/api/v1/reports` · **Database:** `mediflow_report`

**Nguồn chuẩn:** `docs/eproject_general_plan/report-service.html` và `docs/eproject_general_plan/backend-spec/08-report.md`.

## 1. Service này làm gì?

Report là một **read model**: nó không sở hữu dữ liệu giao dịch nào, mọi con số trả về đều là **số liệu tổng hợp dựng thuần từ event** (không truy vấn DB service khác, không gọi REST, không publish event). Nó giúp ban lãnh đạo / phòng kế hoạch nhìn được bức tranh hoạt động của bệnh viện:

1. **Báo cáo theo ngày** — mỗi ngày có bao nhiêu lượt khám, xét nghiệm, đơn thuốc, doanh thu (theo khoa hoặc toàn viện).
2. **Báo cáo doanh thu theo tháng** — tổng doanh thu và số hóa đơn theo từng tháng.
3. **Top thuốc** — thuốc nào được xuất nhiều nhất trong một khoảng thời gian.

Vì là read model nên report chỉ **nghe** các event từ các service khác (`medicalrecord.created`, `lab.result.created`, `prescription.filled`, `payment.completed`, `payment.failed`, `staff.department.changed`) để tăng/giảm bộ đếm. Số liệu tổng hợp là **dữ liệu dẫn xuất**, không bao giờ là nguồn chuẩn — nếu lệch, cách sửa là phát lại event, không sửa tay con số.

## 2. Phạm vi (bounded context)

**Sở hữu:** các bảng tổng hợp (`DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`) + sổ chống trùng `PROCESSED_EVENT`.

**Không sở hữu:** dữ liệu giao dịch (hồ sơ bệnh án, xét nghiệm, đơn thuốc, hóa đơn…). Report chỉ giữ **UUID tham chiếu** (`departmentId`, `drugId`) — không nối thẳng DB của service khác (xem `docs/ai/08-persistence-naming.md`).

**Cấm tuyệt đối:** thư mục `infrastructure/client/` — không Feign, không external datasource. Nếu service này cần JDBC URL hoặc Feign client trỏ sang service khác, nghĩa là thiết kế đã bị vi phạm (BR-R4).

## 3. Cách tổ chức code (nhìn nhanh)

```
HTTP (web/)  hoặc  event từ RabbitMQ (messaging/consumer/)
                    |
                    | gọi in-port (cổng vào)
                    v
            application/service  ── cập nhật bộ đếm ──►  domain/model
                    |
                    | gọi out-port (cổng ra)
                    v
   infrastructure/persistence (PostgreSQL)   ·   infrastructure/messaging (RabbitMQ)
```

Quy tắc quan trọng nhất: **application chỉ biết tên các interface (port), không biết ai làm thật.** Việc làm thật (JPA, RabbitMQ) nằm hết trong `infrastructure/`. Nhờ vậy, muốn đổi database hay đổi cách gửi event thì không phải sửa quy tắc nghiệp vụ. Chi tiết đầy đủ ở `docs/ai/04-microservice-blueprint.md`.

> **Lưu ý:** report thuộc **nhánh service C** (pharmacy – billing – report) dùng tên bảng/cột/class **tiếng Anh** theo spec 08. Các service khác trong hệ thống dùng tên tiếng Việt. Bảng dịch chính thức nằm ở đầu spec 08.

## 4. Dữ liệu

### `DAILY_VISIT_REPORT` — báo cáo theo ngày

| Cột | Ý nghĩa |
|-----|---------|
| `report_id` | UUID khóa chính |
| `report_date` | ngày báo cáo |
| `department_id` | UUID tham chiếu organization `KHOA`; **NULL = số liệu toàn viện** |
| `visit_count` | số lượt khám trong ngày |
| `lab_count` | số xét nghiệm trong ngày |
| `prescription_count` | số đơn thuốc trong ngày |
| `revenue` | doanh thu, `DECIMAL(15,2)` — không bao giờ dùng `double` cho tiền |

Khóa tự nhiên `(report_date, department_id)` với unique index `NULLS NOT DISTINCT` — nhờ vậy dòng toàn viện (`department_id IS NULL`) cũng chỉ có đúng một dòng mỗi ngày.

### `MONTHLY_REVENUE_REPORT` — báo cáo doanh thu theo tháng

`report_id` UUID PK · `month` INT (1–12) · `year` INT · `department_id` UUID (NULL = toàn viện) · `total_revenue` DECIMAL(15,2) · `invoice_count` INT. Khóa tự nhiên `(year, month, department_id)`.

Ràng buộc DB: `ck_month` — `month BETWEEN 1 AND 12`. `total_revenue >= 0` là "tuyến phòng thủ cuối", quy tắc thật nằm trong domain model.

### `DRUG_STATISTIC` — thống kê thuốc xuất

`statistic_id` UUID PK · `drug_id` UUID (tham chiếu pharmacy) · `drug_name` VARCHAR(150) — **ảnh chụp tên thuốc** tại lúc thống kê · `report_date` DATE · `department_id` UUID · `dispensed_quantity` INT. Khóa tự nhiên `(drug_id, report_date, department_id)`.

### `PROCESSED_EVENT` — sổ ghi các event đã xử lý

`event_id` UUID PK · `routing_key` · `processed_at`. Bảng này dùng để **chống xử lý trùng** khi RabbitMQ gửi lại tin — thiếu nó, một lần gửi lại sẽ làm hỏng vĩnh viễn mọi bộ đếm (BR-R2).

## 5. Các cổng (ports) — phần quan trọng nhất

### 5.0 Port là gì?

**Port = bản hợp đồng** giữa application và thế giới bên ngoài: một danh sách các phương thức đã thống nhất, chỉ có tên và tham số, không có code làm thật.

- **In-port (cổng vào):** bên ngoài (web, RabbitMQ) nhờ service làm việc gì. Vd: "cập nhật bộ đếm", "đọc báo cáo".
- **Out-port (cổng ra):** service cần bên ngoài giúp việc gì. Vd: "lưu báo cáo xuống DB".
- **Adapter (bộ chuyển đổi):** phần code thật sự nối với DB / RabbitMQ, đứng sau out-port. Application gọi port, adapter làm thật.

Vì sao phải vẽ ra hợp đồng như vậy? Vì application giữ toàn bộ quy tắc nghiệp vụ. Nếu application gọi thẳng JPA hay RabbitMQ thì khi đổi công nghệ phải sửa cả quy tắc. Có port thì chỉ cần thay adapter.

### 5.1 Cổng vào (in-ports) — 2 interface

#### `UpdateAggregateUseCase` — cập nhật bộ đếm khi có event

**Vì sao cần:** mọi event tới đều dẫn đến một phép tăng/giảm bộ đếm. Gộp các handler vào một interface vì chúng cùng phục vụ một nhóm nghiệp vụ "duy trì số liệu tổng hợp".

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `onMedicalRecordCreated(eventId, reportDate, departmentId)` | `visit_count += 1` trên dòng ngày + dòng toàn viện | BR-R1 |
| `onLabResultCreated(eventId, reportDate, departmentId)` | `lab_count += 1` | BR-R1 |
| `onPrescriptionFilled(eventId, reportDate, departmentId, items)` | `prescription_count += 1`; mỗi mặt hàng tăng `DRUG_STATISTIC.dispensed_quantity` | BR-R1 |
| `onPaymentCompleted(eventId, reportDate, departmentId, amount)` | `revenue += amount`; theo tháng `total_revenue += amount`, `invoice_count += 1` | BR-R1, BR-R3 |
| `onPaymentFailed(eventId, reportDate, departmentId, amount)` | đảo ngược: `revenue -= amount`; theo tháng tương tự, `invoice_count -= 1` (**chặn không âm**) | BR-R3 |
| `onStaffDepartmentChanged(eventId, staffId, oldDept, newDept)` | chỉ cập nhật ảnh chụp nhân sự; V1 không đổi bộ đếm nào | — |

#### `ReadReportUseCase` — đọc báo cáo

**Vì sao cần:** các màn hình báo cáo (theo ngày / theo tháng / top thuốc) cần đọc số liệu tổng hợp đã dựng sẵn, gộp chung một interface vì cùng một nhóm nghiệp vụ "trả số liệu cho người xem".

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `daily(date, departmentId)` | báo cáo theo ngày; `departmentId` NULL = toàn viện; ngày không có dữ liệu trả về **số 0, không 404** | BR-R5, BR-R6 |
| `monthly(month, year, departmentId)` | báo cáo doanh thu theo tháng | BR-R5 |
| `topMedicines(fromDate, toDate, departmentId, limit)` | top thuốc xuất nhiều nhất, tôn trọng khoảng ngày + `limit` (mặc định 10, tối đa 50) | BR-R7 |

### 5.2 Cổng ra (out-ports) — 4 interface

#### `DailyVisitReportRepositoryPort` — lưu và tìm báo cáo ngày

**Vì sao cần:** mọi cập nhật bộ đếm theo ngày đều phải ghi xuống DB, nhưng application không được phép biết JPA. Interface này là "lời hứa": *ai đó hãy lưu và tìm báo cáo ngày giúp tôi* — `DailyVisitReportPersistenceAdapter` trong `infrastructure` sẽ làm thật.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `findOrCreate(reportDate, departmentId)` | tìm-hoặc-tạo dòng theo `(reportDate, departmentId)`, **an toàn khi nhiều consumer chạy song song** | BR-R8 |
| `save(DailyVisitReport)` | lưu báo cáo | mọi luồng cập nhật |
| `find(reportDate, departmentId)` | đọc 1 dòng | đọc báo cáo |
| `findRange(fromDate, toDate, departmentId)` | đọc một khoảng ngày | chế độ chi tiết theo ngày |

#### `MonthlyRevenueReportRepositoryPort` — lưu và tìm báo cáo tháng

**Vì sao cần:** doanh thu tháng được cập nhật theo từng event thanh toán, cần một nơi ghi và đọc lại theo `(year, month, departmentId)`.

| Phương thức | Làm gì |
|-------------|--------|
| `findOrCreate(year, month, departmentId)` | tìm-hoặc-tạo dòng theo `(year, month, departmentId)` |
| `save(MonthlyRevenueReport)` | lưu báo cáo |
| `findByMonth(year, month)` | đọc toàn bộ dòng của một tháng (các khoa + toàn viện) |

#### `DrugStatisticRepositoryPort` — lưu và tìm thống kê thuốc

**Vì sao cần:** top thuốc phải tổng hợp theo `(drugId, reportDate, departmentId)` và trả về theo khoảng ngày + `limit` — quá riêng để nhét vào một port "đọc chung".

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `findOrCreate(drugId, drugName, reportDate, departmentId)` | tìm-hoặc-tạo dòng theo `(drugId, reportDate, departmentId)` | BR-R1 |
| `save(DrugStatistic)` | lưu thống kê | |
| `topDrugs(fromDate, toDate, departmentId, limit)` | lấy top thuốc theo khoảng ngày + `limit` | BR-R7 |

#### `ProcessedEventPort` — sổ chống xử lý trùng

**Vì sao cần:** RabbitMQ có thể gửi lại cùng một event. Nếu không kiểm tra, một tin `payment.completed` đến hai lần sẽ tăng doanh thu hai lần, làm hỏng vĩnh viễn mọi bộ đếm.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `alreadyProcessed(UUID eventId)` | kiểm tra event đã xử lý chưa | BR-R2 |
| `markProcessed(UUID eventId, String routingKey)` | đánh dấu đã xử lý (cùng transaction với hiệu ứng) | BR-R2 |

## 6. Luồng nghiệp vụ chính

### 6.1 Nhận event — pattern 4 bước trong 1 transaction

Mọi consumer theo **4 bước trong một transaction**:

1. **Khử trùng lặp TRƯỚC TIÊN** — `alreadyProcessed(eventId)` rồi return (BR-R2).
2. **Tìm-hoặc-tạo** dòng `(reportDate, departmentId)` — dựa vào unique index, không kiểm-rồi-chèn (BR-R8).
3. **Cộng delta** theo routing key.
4. **`markProcessed(...)` cùng transaction** với hiệu ứng.

### 6.2 Mỗi event cập nhật hai dòng — khoa + toàn viện

Mỗi event cập nhật **hai dòng**: dòng của khoa (`department_id = X`) + dòng toàn viện (`department_id = NULL`). Chỉ làm một trong hai sẽ khiến tổng số liệu không khớp nhau.

| Event | Tác động |
|-------|----------|
| `medicalrecord.created` | `visit_count += 1` |
| `lab.result.created` | `lab_count += 1` |
| `prescription.filled` | `prescription_count += 1`; mỗi mặt hàng `DRUG_STATISTIC.dispensed_quantity += quantity` |
| `payment.completed` | `revenue += totalAmount`; theo tháng `total_revenue += totalAmount`, `invoice_count += 1` |
| `payment.failed` | `revenue -= totalAmount`; theo tháng tương tự, `invoice_count -= 1` |
| `staff.department.changed` | chỉ cập nhật ảnh chụp nhân sự; V1 không đổi bộ đếm nào |

### 6.3 `findOrCreate` an toàn concurrency

Hai event cho cùng `(reportDate, departmentId)` có thể đến cùng lúc. Dựa vào unique index thay vì kiểm-rồi-chèn:

```sql
INSERT INTO DAILY_VISIT_REPORT (report_id, report_date, department_id) VALUES (:id, :reportDate, :departmentId)
ON CONFLICT (report_date, department_id) DO NOTHING;
```

rồi `SELECT ... FOR UPDATE`. Kiểu `findById` rồi `save` thông thường sẽ sinh lỗi trùng khóa khi tải cao.

> **Lỗi dễ mắc nhất ở report:** JPA đối chiếu `WHERE x = NULL` không khớp. Khi truy vấn dòng toàn viện phải viết `(r.departmentId = :departmentId OR (:departmentId IS NULL AND r.departmentId IS NULL))`.

## 7. API

| Method | Path | Làm gì | Role |
|--------|------|--------|------|
| GET | `/api/v1/reports/daily?date&departmentId` | báo cáo theo ngày | ADMIN, MANAGER |
| GET | `/api/v1/reports/monthly?month&year&departmentId` | báo cáo doanh thu theo tháng | ADMIN, MANAGER |
| GET | `/api/v1/reports/top-medicines?fromDate&toDate&departmentId&limit` | top thuốc xuất nhiều nhất | ADMIN, MANAGER |

> `departmentId` là tùy chọn ở cả ba: bỏ trống = số liệu toàn viện, truyền vào = số liệu của một khoa. `limit` mặc định 10, tối đa 50.
> Phân quyền ghi ở controller (`@PreAuthorize`) — đây là lớp "giao hàng", không phải quy tắc nghiệp vụ (xem `docs/ai/07-security-rbac.md`).

## 8. Sự kiện

### Publish (report gửi đi)

**Không có.** Report không gửi event nào ra ngoài — không có binding xuôi.

### Subscribe (report nhận)

| Routing key | Xử lý |
|-------------|-------|
| `medicalrecord.created` | `onMedicalRecordCreated` → `visit_count += 1` |
| `lab.result.created` | `onLabResultCreated` → `lab_count += 1` |
| `prescription.filled` | `onPrescriptionFilled` → `prescription_count += 1` + `DRUG_STATISTIC` |
| `payment.completed` | `onPaymentCompleted` → tăng doanh thu ngày + tháng |
| `payment.failed` | `onPaymentFailed` → đảo ngược doanh thu |
| `staff.department.changed` | `onStaffDepartmentChanged` → cập nhật ảnh chụp nhân sự |

Queue: `report.q` bind **6 routing key** trên. Consumer là **một class duy nhất** (`ReportEventConsumer`) — nhận `Message`, dispatch theo routing key. Mọi event đều mang đủ `eventId`, `occurredAt`, `correlationId` theo chuẩn `docs/ai/06-events-rabbitmq.md`.

## 9. Quy tắc nghiệp vụ (8 quy tắc)

| ID | Quy tắc (diễn đạt đơn giản) |
|----|------------------------------|
| BR-R1 | Số liệu được tổng hợp theo ngày và theo khoa (kèm dòng toàn viện). |
| BR-R2 | Gửi lại event không được đếm hai lần (khử trùng lặp trên `eventId`). |
| BR-R3 | `payment.failed` phải đảo ngược doanh thu (delta âm). |
| BR-R4 | Không bao giờ truy vấn service khác — không Feign, không external datasource. |
| BR-R5 | Ngày/tháng không có dữ liệu trả về số 0, không phải 404. |
| BR-R6 | Bỏ trống `departmentId` ⇒ trả dòng toàn viện. |
| BR-R7 | Top thuốc tôn trọng khoảng ngày và `limit`. |
| BR-R8 | Nhiều event đầu tiên cùng ngày chỉ tạo đúng một dòng (concurrency-safe). |

Chi tiết ánh xạ quy tắc → tầng test → tên test: xem mục 8 và 10.5 của spec 08.

## 10. Mã lỗi

| Mã lỗi | HTTP | Tình huống |
|--------|------|------------|
| (V1 không có mã lỗi nghiệp vụ) | — | ngày không dữ liệu → trả báo cáo số 0, không 404 |

Report rất ít quy tắc ném lỗi: V1 **không có** endpoint trả 404. Thường không cần domain exception riêng — nếu sau này có endpoint trả 404 thì thêm `ReportNotFoundException` (kế thừa `ResourceNotFoundException` của `common`).

## 11. Code hiện tại: đã có gì, còn thiếu gì

### Đã có (đối chiếu với cây thư mục thật)

- `ArchitectureTest.java`: kiểm tra quy tắc kiến trúc (domain không dùng Spring/JPA, application không dùng Spring Data/AMQP/HTTP, không vòng lặp giữa các tầng) — đồng thời bắt luôn BR-R4 (không Feign/client).

### Còn thiếu (theo spec 08 — xem phần "Coding map" và "Definition of Done")

- `db/migration/V1__init.sql`: 4 bảng (`DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`, `PROCESSED_EVENT`) + unique index `NULLS NOT DISTINCT` + `ck_month`.
- `domain/model/`: `DailyVisitReport`, `MonthlyRevenueReport`, `DrugStatistic` (bộ đếm chịu delta âm).
- 4 out-port + 2 in-port (`UpdateAggregateUseCase`, `ReadReportUseCase`).
- `application/service/`: `AggregateUpdaterService`, `ReportApplicationService`.
- DTO response (`DailyReportDTO`, `MonthlyReportDTO`, `TopMedicineDTO`) + mapper (MapStruct).
- `web/`: `ReportController` 3 endpoint + `GlobalExceptionHandler` + `SecurityConfig` + `OpenApiConfig`.
- `messaging/consumer/`: `ReportEventConsumer` (1 class, 4 bước trong 1 transaction).
- `infrastructure/persistence/`: JPA entity + repository (`findOrCreate` ON CONFLICT, `SELECT FOR UPDATE`) + adapter.
- Test đủ 5 tầng, phủ 8 quy tắc nghiệp vụ (danh sách test cụ thể ở spec 08 mục 10.5).
