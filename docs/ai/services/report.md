# Service: report (Phòng Kế hoạch – Tổng hợp)

**Module:** `backend/report-service/` · **Cổng chạy:** 8088 · **Base path:** `/api/v1/reports` · **Database:** `mediflow_report`

**Nguồn tham khảo ban đầu:** `docs/eproject_general_plan/report-service.html` và
`docs/eproject_general_plan/backend-spec/08-report.md`. Các quyết định đã chốt cho implementation
là [implementation plan](../../plans/2026-09-15-report-service-implementation-plan.md).

> **V1 decision record (15/09/2026):** các tài liệu cũ của Report từng mô tả 4 bảng, check-then-mark
> idempotency và subscribe `staff.department.changed`. Baseline đã chốt hiện tại nằm trong
> [implementation plan](../../plans/2026-09-15-report-service-implementation-plan.md): V1 có 5 bảng,
> atomic `claimIfAbsent`, payment contribution theo `invoiceId`, và chỉ subscribe 5 event nghiệp vụ.

## 1. Service này làm gì?

Report là một **read model**: nó không sở hữu dữ liệu giao dịch nào, mọi con số trả về đều là **số liệu tổng hợp dựng thuần từ event** (không truy vấn DB service khác, không gọi REST, không publish event). Nó giúp ban lãnh đạo / phòng kế hoạch nhìn được bức tranh hoạt động của bệnh viện:

1. **Báo cáo theo ngày** — mỗi ngày có bao nhiêu lượt khám, xét nghiệm, đơn thuốc, doanh thu (theo khoa hoặc toàn viện).
2. **Báo cáo doanh thu theo tháng** — tổng doanh thu và số hóa đơn theo từng tháng.
3. **Top thuốc** — thuốc nào được xuất nhiều nhất trong một khoảng thời gian.

Vì là read model nên report chỉ **nghe** `medicalrecord.created`, `lab.result.created`,
`prescription.filled`, `payment.completed` và `payment.failed` để dựng projection. Số liệu tổng hợp là
**dữ liệu dẫn xuất**, không bao giờ là nguồn chuẩn — nếu lệch, cách sửa là phát lại event, không sửa tay.

## 2. Phạm vi (bounded context)

**Sở hữu:** các bảng tổng hợp (`DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`),
`PROCESSED_EVENT` và `PAYMENT_CONTRIBUTION` (sổ contribution thanh toán để đảo đúng kỳ gốc).

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

`statistic_id` UUID PK · `drug_id` UUID (tham chiếu pharmacy) · `drug_name` VARCHAR(150) — **ảnh chụp tên thuốc mới nhất từ event pharmacy** · `report_date` DATE · `department_id` UUID · `dispensed_quantity` INT. Khóa tự nhiên `(drug_id, report_date, department_id)`. Khi event cùng khóa tự nhiên đến với tên mới, phải refresh snapshot rồi mới tăng quantity.

### `PROCESSED_EVENT` — sổ ghi các event đã xử lý

`event_id` UUID PK · `routing_key` · `processed_at`. Bảng này dùng để **chống xử lý trùng** khi RabbitMQ gửi lại tin — thiếu nó, một lần gửi lại sẽ làm hỏng vĩnh viễn mọi bộ đếm (BR-R2).

### `PAYMENT_CONTRIBUTION` — contribution theo invoice

**CURRENT prescription source hardening — 2026-10-08:** V16 adds
`prescription_fill_receipt(prescription_id, fact_fingerprint, accepted_at)` for the existing
single-fill V0 contract. New delivery IDs for the same accepted prescription cannot increment
daily counts/drug quantities again. Exact business Instant, department, report zone and grouped
drug/name/quantity proof are immutable; conflicting accepted source evidence rejects. The source
receipt and delivery claim commit/rollback with both aggregate scopes, with database uniqueness
serializing concurrent replicas. No patient/narrative/raw event data is retained.
This does not change V1 wire/queues or create a replay archive. Existing historical totals cannot
be used to infer prescription source identity: upgrade leaves V16 empty and historical completeness
remains unverified. [Current distributed-flow batch](../../superpowers/plans/2026-10-08-huy-distributed-flow-priority.md).

`invoice_id` UUID PK · `completed_event_id` UUID UNIQUE · `failed_event_id` UUID UNIQUE nullable ·
`payment_date` DATE nullable · `department_id` UUID nullable · `amount` DECIMAL(15,2) nullable ·
`status` (`PENDING_REVERSAL`, `APPLIED`, `REVERSED`) · timestamps. Bảng này cần thiết vì
`payment.failed` hiện tại chỉ có `invoiceId`, không có amount/department; failure đến trước completed
được lưu pending để khi completed đến thì net effect bằng zero. `NEW` chỉ là trạng thái transient
trước lần lưu đầu tiên, không được persist.

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
| `onPrescriptionFilled(eventId, occurredAt, departmentId, prescriptionId, items)` | `prescription_count += 1`; mỗi mặt hàng tăng `DRUG_STATISTIC.dispensed_quantity` | RPT-B01…B04 |
| `onPaymentCompleted(eventId, occurredAt, invoiceId, departmentId, amount)` | apply contribution: daily/monthly revenue tăng, invoice count tăng | RPT-B05, RPT-B06 |
| `onPaymentFailed(eventId, occurredAt, invoiceId)` | đảo contribution gốc theo invoice; không lấy amount/dept từ payload | RPT-B07, RPT-B08 |

#### `ReadReportUseCase` — đọc báo cáo

**Vì sao cần:** các màn hình báo cáo (theo ngày / theo tháng / top thuốc) cần đọc số liệu tổng hợp đã dựng sẵn, gộp chung một interface vì cùng một nhóm nghiệp vụ "trả số liệu cho người xem".

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `daily(date, departmentId)` | báo cáo theo ngày; `departmentId` NULL = toàn viện; ngày không có dữ liệu trả về **số 0, không 404** | BR-R5, BR-R6 |
| `monthly(month, year, departmentId)` | báo cáo doanh thu theo tháng | BR-R5 |
| `topMedicines(fromDate, toDate, departmentId, limit)` | top thuốc xuất nhiều nhất, tôn trọng khoảng ngày + `limit` (mặc định 10, tối đa 50) | BR-R7 |

### 5.2 Cổng ra (out-ports) — 5 interface

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
| `find(year, month, departmentId)` | đọc một dòng theo đúng scope tháng |

#### `DrugStatisticRepositoryPort` — lưu và tìm thống kê thuốc

**Vì sao cần:** top thuốc phải tổng hợp theo `(drugId, reportDate, departmentId)` và trả về theo khoảng ngày + `limit` — quá riêng để nhét vào một port "đọc chung".

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `findOrCreate(drugId, drugName, reportDate, departmentId)` | tìm-hoặc-tạo dòng theo `(drugId, reportDate, departmentId)`; dòng có sẵn phải được refresh `drugName` theo event mới nhất | BR-R1, RPT-B11 |
| `save(DrugStatistic)` | lưu thống kê | |
| `topMedicines(fromDate, toDate, departmentId, limit)` | lấy top thuốc group theo drugId, latest name, deterministic order | RPT-Q05 |

#### `ProcessedEventPort` — sổ chống xử lý trùng

**Vì sao cần:** RabbitMQ có thể gửi lại cùng một event. Nếu không kiểm tra, một tin `payment.completed` đến hai lần sẽ tăng doanh thu hai lần, làm hỏng vĩnh viễn mọi bộ đếm.

| Phương thức | Làm gì | Quy tắc khớp |
|-------------|--------|--------------|
| `claimIfAbsent(UUID eventId, String routingKey)` | atomic insert; trả `true` cho consumer thắng claim | RPT-E01, RPT-E02 |

#### `PaymentContributionRepositoryPort` — sổ contribution theo invoice

| Phương thức | Làm gì |
|-------------|--------|
| `findOrCreateForUpdate(UUID invoiceId)` | lấy advisory transaction lock theo invoice rồi `SELECT`; nếu chưa có thì trả aggregate `NEW` transient (không ghi placeholder `NEW` xuống DB) |
| `save(PaymentContribution contribution)` | lưu state `PENDING_REVERSAL/APPLIED/REVERSED` |

## 6. Luồng nghiệp vụ chính

### 6.1 Nhận event — atomic claim và effect trong một transaction

Consumer chỉ parse/validate rồi gọi in-port. Application service thực hiện trong **một transaction**:

1. `claimIfAbsent(eventId, routingKey)` bằng `INSERT ... ON CONFLICT DO NOTHING`; mất claim thì return.
2. Tìm-hoặc-tạo projection bằng unique key rồi `SELECT ... FOR UPDATE`; payment contribution dùng
   advisory transaction lock theo `invoiceId` và chỉ tạo aggregate `NEW` transient khi thiếu row.
3. Cộng hoặc đảo delta theo routing key.
4. Commit claim và effect cùng nhau; lỗi làm rollback cả hai để RabbitMQ retry được.

### 6.2 Mỗi event cập nhật hai dòng — khoa + toàn viện

Mỗi event có khoa cập nhật dòng khoa (`department_id = X`) + dòng toàn viện (`department_id = NULL`).
Nếu `department_id` null (payment completed hiện tại) thì chỉ cập nhật dòng toàn viện.

| Event | Tác động |
|-------|----------|
| `medicalrecord.created` | `visit_count += 1` |
| `lab.result.created` | `lab_count += 1` |
| `prescription.filled` | `prescription_count += 1`; mỗi mặt hàng `DRUG_STATISTIC.dispensed_quantity += quantity` |
| `payment.completed` | current outpatient compatibility: apply invoice contribution; admission deposits require the classified target projection below and must not increment earned revenue |
| `payment.failed` | đảo contribution theo invoice; trừ đúng ngày/khoa gốc và giảm invoice count |

### 6.3 `findOrCreate` an toàn concurrency

Hai event cho cùng `(reportDate, departmentId)` có thể đến cùng lúc. Dựa vào unique index thay vì kiểm-rồi-chèn:

```sql
INSERT INTO DAILY_VISIT_REPORT (report_id, report_date, department_id) VALUES (:id, :reportDate, :departmentId)
ON CONFLICT (report_date, department_id) DO NOTHING;
```

rồi `SELECT ... FOR UPDATE`. Kiểu `findById` rồi `save` thông thường sẽ sinh lỗi trùng khóa khi tải cao.

Payment contribution không dùng placeholder insert: adapter khóa `invoiceId` bằng
`pg_advisory_xact_lock`, `SELECT` row hiện có hoặc trả aggregate `NEW` transient, rồi chỉ persist
state bền vững sau khi event được áp dụng.

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
| `payment.failed` | `onPaymentFailed` → đảo contribution theo invoice |

Queue: `report.q` bind **5 routing key** trên. Consumer là **một class duy nhất** (`ReportEventConsumer`) — nhận `Message`, dispatch theo routing key. Mọi event đều mang đủ `eventId`, `occurredAt`, `correlationId` theo chuẩn `docs/ai/06-events-rabbitmq.md`. `staff.department.changed` chưa bind V1 vì không có staffing report và payload producer thiếu envelope.

## 9. Quy tắc nghiệp vụ

Các rule V1 có mã `RPT-Bxx`, `RPT-Exx`, `RPT-Qxx`, `RPT-Oxx`; bảng đầy đủ, test mapping và quyết định
loại bỏ điểm mâu thuẫn nằm trong [implementation plan](../../plans/2026-09-15-report-service-implementation-plan.md).

## 10. Mã lỗi

| Mã lỗi | HTTP | Tình huống |
|--------|------|------------|
| `REPORT_DATE_RANGE_INVALID` | 422 | `fromDate > toDate` |
| `VALIDATION_ERROR` | 400 | month/year/limit hoặc field query không hợp lệ |

V1 không có endpoint trả 404 cho dữ liệu thiếu: daily/monthly đều zero-fill.

## 11. Code hiện tại: đã có gì, còn thiếu gì

### Đã có (đối chiếu với cây thư mục thật)

- `ArchitectureTest.java`: kiểm tra quy tắc kiến trúc (domain không dùng Spring/JPA, application không dùng Spring Data/AMQP/HTTP, không vòng lặp giữa các tầng) — đồng thời bắt luôn BR-R4 (không Feign/client).
- `domain/model/`: `DailyVisitReport`, `MonthlyRevenueReport`, `DrugStatistic`,
  `PaymentContribution`, immutable `TopMedicineSummary` và bộ domain tests của T01.
- T02 contracts: 2 in-port, 5 out-port, immutable `DispensedItem`, 3 response DTO và
  `ReportDtoMapper` (MapStruct).
- T03 persistence mapping: Flyway `V1__init.sql` với 5 bảng, PostgreSQL null-safe unique indexes,
  checks, 5 JPA entity và 4 persistence mapper.
- T04/T05/T06: native persistence repositories/adapters (atomic claim, upsert + row lock, advisory lock)
  và `AggregateUpdaterService` cho đủ 5 event; payment contribution là nguồn dữ liệu duy nhất để đảo
  doanh thu, có idempotency theo eventId + invoiceId và xử lý out-of-order.
- T07: `ReportApplicationService` cho ba read use case; daily/monthly zero-fill, monthly full calendar
  bằng một range query, top medicine inclusive + giới hạn deterministic; `ReportDateRangeException`
  trả code `REPORT_DATE_RANGE_INVALID`.

### T10–T11: integration gate và release audit

- `ReportCrossLayerIntegrationTest` chạy qua Spring context thật với PostgreSQL 16 + RabbitMQ 3.13,
  bao phủ daily/hospital/khoa/top thuốc, payment duplicate/cùng invoice khác event, compensation
  hai thứ tự, concurrent first events, API envelope, malformed/DLQ và rollback khi payment conflict.
- Test bất đồng bộ dùng Awaitility với timeout hữu hạn; mỗi test tự cleanup projection và queue. Không
  dùng sleep mù, không mock phần behavior mà scenario cần chứng minh.
- T11 đã hoàn tất audit: test/verify/Javadocs/dependency analyze xanh, ArchitectureTest xanh và
  static search không phát hiện Feign/client, external datasource hoặc forbidden imports.
- Runtime gate T04/T06/T10 đã chạy thật với Docker Desktop, PostgreSQL 16 và RabbitMQ 3.13:
  persistence/concurrency 16/16 pass, cross-layer 8/8 pass; toàn bộ module **121/121 pass, 0 skip**.
  Report pin Testcontainers 1.20.6 trong POM để tương thích Docker API hiện tại; máy có cấu hình
  Testcontainers cũ dùng thêm `-Dapi.version=1.40`.

### Đã hoàn tất bổ sung (T08–T10)

- `infrastructure/config/RabbitConfig`: durable exchange/queue/DLQ, đúng 5 binding V1, bounded retry và reject không requeue.
- `messaging/consumer/ReportEventConsumer`: một consumer dispatch theo routing key, fixture contract cho 5 event,
  additive-field compatibility và validation trước in-port; không suy diễn contribution từ `payment.failed`.
- `web/`: ba GET endpoint, `GlobalExceptionHandler`, stateless JWT/RBAC (`ADMIN`, `MANAGER`) và OpenAPI metadata;
  `report.http` là live contract.
- T08/T09/T10 và review hardening đã bổ sung regression coverage; module hiện **121 test, 121 pass,
  0 skip, 0 failure/error** khi chạy với Docker Desktop (16 persistence/concurrency + 8 cross-layer
  Testcontainers; phần còn lại là unit/web/config/security).

### Review hardening (16/09/2026)

- Consumer bắt buộc canonical source id (`recordId` cho medical record, `labId` cho lab result) và
  ghi log metadata không chứa full payload; retry recoverer chỉ ghi routing/message/correlation metadata.
- Validation của query trả `VALIDATION_ERROR` kèm `error.details[field,message]`; ngày tương lai vẫn là
  query hợp lệ và Swagger/actuator info mặc định yêu cầu xác thực (Swagger chỉ mở bằng explicit local opt-in).
- PostgreSQL tests bao phủ null-scope natural keys, monthly/drug find-or-create, completed/failed race
  lặp 10 vòng, top query inclusive/scope/tie-break/latest-name và future-date zero-fill.
- Report-service yêu cầu JWT có `type=access`; token thiếu type, `refresh` và `service` bị từ chối.
  Regression coverage nằm trong `JwtAuthFilterTest`.

## 12. Care-finance projection đích

**Opt-in gross-receipt intake — 2026-10-08:** `report.cash-receipts-v2.q`/DLQ accepts only
`payment.completed`, behind `care-finance-v2 AND report.cash-receipt-consumer.enabled`, both
default false. The shared strict decoder feeds the existing cash mapper/transactional V12 kernel;
the receipt transaction ID, never invoice ID, is the immutable business identity. ACK follows commit;
permanent malformed/conflict errors have no sensitive cause, storage errors have three attempts
before retained-byte DLQ. Real PG/Rabbit checks include compatibility-listener coexistence,
deposit isolation, exact decimals, conflicting/reused delivery rollback, recovery and finite V13
rebuild. No recognition/refund/settlement mapping, financial API/publication, export approval or
held-producer activation is inferred. [Current evidence](../../superpowers/plans/2026-10-08-report-cash-intake.md).

**Opt-in operational intake — 2026-10-08:** separate `report.operational-v2.q`/DLQ and
`care-finance-v2 AND report.operational-consumer.enabled` (defaults false). Exactly seven bindings:
medicalrecord.completed, lab.result.created, prescription.filled, surgery.completed,
surgery.cancelled, admission.started and admission.closed. Each approved revision-one operation
passes its existing pure mapper and transactional journal/dedupe/two-scope kernel before ACK;
admission facts originally called only the minimal pending/pairing kernel. The 2026-10-09 follow-up
below adds the start counter; medical discharge, LOS and occupancy remain separate. Unknown
version/revision or immutable source conflict rejects permanently; storage failure has bounded
three-attempt retry and original-byte DLQ recovery. Strict shared parsing rejects duplicate/trailing
JSON, noncanonical source/event UUID and unbounded body/correlation while preserving existing
operational source hashes and exact financial decimal decoding. No payment/refund/settlement binding,
legacy projection mutation, source-history approval, publication writer or read cutover is added.
Actual producer-byte Rabbit/PG and finite rebuild evidence is in
[the execution follow-up](../../superpowers/plans/2026-10-08-huy-ready-task-completion.md).
Earlier dated offline-only intake statements are history; missing finance/LOS/capacity/correction
and full runtime publication remain open.

**Admission start counter — 2026-10-09:** the existing opt-in operational receiver now joins exact
start/close evidence validation and one `ADMISSIONS` contribution in the same local transaction.
Only canonical `admission.started` counts: admission ID, implicit immutable-operation revision 1,
start department, ADMISSION episode and `admittedAt` in the configured reporting zone. Same source
with a new delivery ID is journaled but never counted twice; a changed source snapshot is rejected
even when its KPI value is unchanged. Early close pairs only with the same patient/admission and
does not reopen it. Hospital-scope failure rolls back admission evidence, delivery/source claims,
journal and both scopes together. The minimal accepted journal supports the existing finite replay.

`admission.closed` remains administrative evidence only: no discharge count, LOS, current bed,
occupancy or medical time is inferred. Unsupported correction/revision markers are rejected before
effects. Old evidence rows are not automatically backfilled: a newly received actual start must be
revalidated. No new binding, publication, migration or public read activation is introduced.
[Current verification](../../superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).

**Local telemetry 2026-10-07:** default-off, aggregate-only Report monitoring reads pending admission
evidence, separate operational/cash replay inventories/progress/ages and legacy-unverified source
hash counts with one bounded MVCC statement. V14 is index-only. Cached Micrometer meters never query
on scrape; unavailable/stale data is NaN rather than fabricated zero, and labels/logs contain no
patient/entity IDs or payloads. Explicit age/availability/inventory thresholds and their limitations
are in the [ten-deliverable runbook](../../superpowers/plans/2026-10-07-report-telemetry-batch.md).
Web exposure remains health,info; separately exposed metrics require telemetry opt-in and
ADMIN/MANAGER access tokens. No V2 activation, publication, broker lag/DLQ measurement or dashboard
deployment is implied. Failed generation counts are retained inventory, not a new-incident rate.

**Internal cash replay update 2026-10-07:** V13 adds frozen minimal receipt inputs and isolated
cash-only replay generations. Snapshot/projector V1 preserve the V12 fingerprint format and ISO
nanoseconds; no producer payload is fabricated. One MVCC-visible source statement, DB generation
lock, bounded resume, shared live/replay scope planner and full fact/proof/scope/count reconciliation
rebuild gross receipt totals without changing live tables or legacy bindings. VERIFIED is finite
manifest equality, not financial coverage/publication. Missing first-delivery proof fails freeze;
unsupported/corrupt inputs fail before batch effects. Recognition/refund/settlement/pending
reversals, historical export, catch-up and financial read acceptance remain OPEN. See the
[ten-task batch](../../superpowers/plans/2026-10-07-report-cash-replay-batch.md).

Report tiếp tục là read model thuần event. Nó không trở thành nguồn chuẩn của tiền, admission hay
surgery và không gọi REST để bù dữ liệu thiếu trong event.

**Trạng thái local 2026-10-01:** V6/V7 đã chạy trên PostgreSQL 16. Local typed operational kernel,
port/service/JDBC adapter ghi journal, delivery claims, semantic contributions và hai scopes trong
cùng transaction. Same source/new eventId không cộng lần hai; department/time thay đổi là conflict,
không trở thành một operation mới. Multi-metric batches khóa theo stable scope order; lỗi scope thứ
hai rollback toàn bộ. Corrections chưa có reversal contract bị reject thay vì cộng thêm.
Không có listener/API V2 active; feature flag vẫn false. Offline decoder đọc đúng fixture bytes
Clinical/Lab/Inpatient. Offline Lab mapper bổ sung ngày 2026-10-02 dùng exact labId/resultVersion,
requesting department, episode và completedAt theo configured report zone. Không lấy event version
làm source revision hoặc recordId làm episode; chỉ revision 1 có mapping hiện tại. Actual admission
Lab fixture nay là first-completion revision 1 đúng với producer; imported completion/correction
vẫn bị từ chối cho tới khi có contract riêng.
Unit mapping và PostgreSQL effect tests đã chạy thật khi Docker bật lại; evidence mới nhất ở Huy plan.
Local tests không thay cho producer runtime acceptance. Update 2026-10-07: offline Clinical
completion, Pharmacy fill and Surgery result/cancellation mappers now read actual producer bytes.
Immutable singleton revision-1 semantics, business times, duration rounding and null episode handling
are explicit in [canonical projections](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
New V11 source-payload hash fence detects same-operation/new-event changes even when KPIs do not
change, without retaining patient/narrative data. Existing redacted sources are LEGACY_UNVERIFIED;
new delivery needs controlled source revalidation, not an inferred hash. Admission start metric mapping
is implemented in the 2026-10-09 follow-up; other admission metrics,
financial projections, clinical policy acceptance and live publication remain OPEN.

V9 thêm offline minimal admission history và delivery fingerprint ledger. Actual start/close bytes
map thành immutable exact ID/patient/department/bed/business time/proof; không lưu clinical payload.
Close-before-start pending được persist; late start chỉ ghép đúng admission/patient, không reopen.
Row fence serialize concurrent start/close; altered time/department/proof rollback claim. Exact ISO
time giữ nanos qua reload. Canonical contract xem STARTED/CLOSED là singleton operations có implicit
revision 1, độc lập envelope version. Chưa ghi contributions/counts/medical LOS/occupancy vì duration
medical-discharge và transfer/release/capacity semantics chưa chốt. Unit/static và PG reload/rollback/race cùng
V8→V9 upgrade/constraints đã được chạy thật; không admission metric/listener mới.

Offline financial input update 2026-10-07: `BillingCashReceiptMapper` reads the two actual ledger
receipt fixtures. V12 stores minimal immutable transaction/delivery evidence and cash-only gross
receipt scopes separated by currency, timezone, classification and Billing account department.
Internal `ApplyCashReceiptUseCase` joins both scopes and evidence atomically. No listener/HTTP
caller activates it; current five bindings and legacy projections are unchanged. Monetary JSON
parsing is exact BigDecimal without changing operational V11 hash normalization. Neither receipt
amount nor a deposit receipt supplies earned revenue/unallocated liability. Full financial mapping,
recognition/refund/settlement, finite finance replay and accepted publication remain OPEN; see the
[canonical local boundary](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#report-offline-gross-receipt-evidence--2026-10-07).

Financial projection phải tách `cashReceived`, `depositLiability`, `earnedRevenue`, `refunds` và
`outstandingReceivable`. `payment.completed` cho tạm ứng chỉ tăng cash/liability; earned revenue chỉ
được ghi từ classification/settlement fact do Billing sở hữu. Refund/adjustment tham chiếu
transaction/contribution gốc để đảo đúng kỳ và department.

Target subscriptions bổ sung: `medicalrecord.completed`, `admission.started`, `admission.closed`,
`surgery.completed`, `surgery.cancelled`, `payment.refunded`, `settlement.completed`. Trong schema
V2, `eventId` là delivery provenance; semantic uniqueness dùng `sourceType + sourceId + sourceRevision`
cùng contribution/metric type và department scope cho financial allocation. Operational V7 còn có
global source/revision/metric key: một operation không được đếm lại chỉ vì department thay đổi.
Source IDs/revisions cụ thể, correction/supersedes
và finance totals vẫn phải theo fixture/contract do producer owner xác nhận. Khóa revision là bắt
buộc, không có default giả.

Hospital scope dùng `department_id IS NULL` với PostgreSQL `NULLS NOT DISTINCT`, không dùng UUID
sentinel. Huy chọn minimal accepted-input journal từ activation làm local durable source: chỉ metadata
và aggregate input snapshots, không lưu full chẩn đoán/kết quả Lab. Không tự purge trước khi retention/
export được chốt; không expose journal qua API. Lịch sử trước activation chưa rebuild được nếu thiếu
export. V8 đã có internal finite operational replay vào generation riêng: manifest chốt tập committed
journal inputs bằng một INSERT SELECT, bounded batch/DB generation lock và atomic progress/effects,
shared pure planner/codec và đối soát cả fact set lẫn department/hospital scopes. VERIFIED chỉ bằng
tập manifest, không thay live read pointer. Unit/static và PG snapshot/race/rollback/reconcile pass.
Financial pending, historical coverage/live catch-up/controlled publication/read cutover vẫn còn
R-01.4–.7; Rabbit ACK không phải archive. Pending close evidence alone is not an admission metric.

### Local accepted finite-snapshot queries — 2026-10-02

**Paired accepted cash-state rebuild follow-up 2026-10-08:** V17/internal use case freezes receipt
and refund manifests in one REPEATABLE READ snapshot and rebuilds isolated refund-day cash-out
after its receipt generation verifies. Exact original/cumulative amount/classification/day are
rechecked. PENDING/REJECTED remain explicit copied inventory, not newly retried live outcomes.
Both scopes/progress are atomic and bidirectionally reconciled; old V13 gross-only runs remain
unchanged. No financial API, publication, five-metric acceptance, source coverage or producer release.
[Canonical boundary](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#report-paired-finite-accepted-cash-rebuild--v17-2026-10-08);
[verification](../../superpowers/plans/2026-10-08-huy-master-sync-refund-replay.md).

**Cash-refund follow-up 2026-10-08:** V15/default-off intake persist actual Billing refund
bytes, exact local original receipts and refund-day cash-out scopes. Early facts remain pending;
bounded recovery quarantines invalid early evidence without poisoning the original. Shared
source locks bound cumulative amounts and evidence/two scopes are atomic. Gross receipts stay
unchanged; classification and original date come from the receipt. This supersedes the absence
of refund cash mapping only, not earned/liability reversal, settlement, full financial/refund
replay or accepted publication. [Canonical behavior](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#completed-refund-fact--v1-paymentrefunded-2026-10-08);
[verification](../../superpowers/plans/2026-10-08-billing-refund-closure.md).

V10 adds empty `operational_report_publication`: exact date range, timezone, required metric set,
accepted-coverage reference and generation ID. VERIFIED replay never creates publication. No
production publication writer/admin API exists; manual SQL is not an activation or approval path.
Reader requires VERIFIED + all manifest inputs applied and exact accepted period/zone/metrics;
missing coverage/failed/building snapshot is 404 REPORT_NOT_FOUND, not zero. A covered empty day
may be zero-filled. Values come from immutable generation scopes, not changing live tables.

Two gated aggregate snapshot routes follow target `/operations/daily` and `/operations/surgery`,
ADMIN/MANAGER/DOCTOR directly, 1..366 inclusive days, years 2000..2100; invalid input returns 400
REPORT_VALIDATION_ERROR. DTO includes generationId/reconciledAt/snapshotOnly=true and daily
camelCase counters. No patient, contact, clinical narrative or consent payload crosses this API.
Feature defaults false, existing five bindings/three legacy APIs and finance roles unchanged.
Basic counts/duration are not complete inpatient-day/occupancy/surgical category/finance KPIs.
Source acceptance, controlled publication/rollback/live catch-up and Gateway roles still gate the
full target. `report.http`, unit/web/flag/PG coverage and latest clean suite evidence are in the plan.

Mandatory handoff: [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
Không đổi current five-event projection sang semantics mới trước khi Billing/producer fixtures và
Report consumer fixtures cùng version đã pass.
