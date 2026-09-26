# Kế hoạch triển khai Report Service — domain → architecture → rules → tasks → AI implementation

> Cập nhật: **16/09/2026**
> Module: `backend/report-service`
> Owner production code: Huy (`LQHuy0210`)
> Trạng thái: **PLAN / business baseline V1 đã chốt**

## 0. Kết quả cần đạt và cách dùng plan

Plan này là bản chốt nghiệp vụ và kế hoạch code cho Report Service theo đúng thứ tự:

```text
Phân tích domain
        ↓
Chốt architecture và transaction boundary
        ↓
Chốt business rules và event semantics
        ↓
Chia feature thành task độc lập có dependency
        ↓
AI implement từng task theo test-first gate
```

Không yêu cầu AI sinh toàn bộ service trong một lượt. Mỗi task chỉ được đánh dấu `DONE` khi:

- business rule liên quan đã có test;
- code đúng dependency direction;
- test mục tiêu và test hồi quy của module đều xanh;
- không phát sinh thay đổi ngoài scope của task;
- file plan được cập nhật trạng thái và bằng chứng.

Trạng thái task:

- `TODO`: chưa bắt đầu.
- `IN_PROGRESS`: đang triển khai, chỉ một task chính tại một thời điểm.
- `BLOCKED`: thiếu contract/quyết định ngoài Report; không được tự đoán.
- `DONE`: code, test, audit và tài liệu đã đạt acceptance gate.

### 0.1 Nguồn đã đọc và thứ tự sử dụng

| Ưu tiên | Nguồn | Vai trò |
|---:|---|---|
| 1 | [Report business document](../eproject_general_plan/report-service.html) | Ý định nghiệp vụ ban đầu |
| 2 | [Report backend spec](../eproject_general_plan/backend-spec/08-report.md) | DDL, port, DTO, event và test map bản nháp |
| 3 | [Report bounded context](../ai/services/report.md) | Boundary và giải thích service |
| 4 | **Mục 2–3 của plan này** | Quyết định V1 đã chốt sau khi đối chiếu tài liệu và source thật |
| 5 | [Shared backend contract](../eproject_general_plan/backend-spec/00-overview.md) | Hợp đồng chung giữa các service |
| 6 | [Microservice blueprint](../ai/04-microservice-blueprint.md) | Package, dependency direction và adapter pattern |
| 7 | [Architecture](../ai/01-architecture.md), [API](../ai/05-api-conventions.md), [RabbitMQ](../ai/06-events-rabbitmq.md), [RBAC](../ai/07-security-rbac.md), [Persistence](../ai/08-persistence-naming.md), [Testing](../ai/09-testing.md) | Chuẩn triển khai |
| 8 | Source producer hiện tại | Bằng chứng về payload thật đang phát |

Các tài liệu Report hiện là bản nháp và có một số điểm không thể implement an toàn. Theo yêu cầu của
người dùng, **các quyết định trong mục 2 và business rules trong mục 3 là baseline V1 của plan này**.
T00 chỉ duy trì hai file deliverable: plan này và tài liệu kỹ thuật/nghiệp vụ hợp nhất
`docs/ai/services/report.md`; các spec/HTML nền được giữ nguyên làm nguồn tham khảo.

### 0.2 Hiện trạng module đã kiểm tra

`backend/report-service` đã đi qua T01–T11 phần code/audit; các gate hạ tầng còn lại được ghi ở T10:

- đã có `pom.xml`, `application.yml`, `ReportServiceApplication.java`, `README.md`, `report.http`;
- đã có `ArchitectureTest.java` kiểm dependency direction và cấm Feign/client;
- đã có domain model/state machine, application port/DTO contract, Flyway V1, JPA entity và persistence mapper;
- đã có application service, persistence adapter, consumer RabbitMQ và controller REST theo blueprint;
- `README.md` và `report.http` đã đối chiếu baseline 5 event/3 endpoint; tài liệu canonical là `report.md` và plan này;
- T04/T06 concurrency và T10 cross-layer integration đã chạy thật bằng PostgreSQL 16 và RabbitMQ 3.13 Testcontainers.

Baseline ngày 15/09/2026: `mvn -q -pl backend/report-service -am test` chạy thành công với **6/6
ArchitectureTest pass**. Checkpoint runtime ngày 16/09/2026: **121 test, 121 pass, 0 skip, 0
failure/error** với Docker Desktop và `-Dapi.version=1.40`; gồm 16 persistence/concurrency test và
8 cross-layer test. Javadocs, verify và dependency analyze cũng thành công. Lệnh changelog summary
của máy hiện thiếu `sqlite3`; đã dùng danh sách 5 commit gần nhất thay thế và việc này không ảnh
hưởng thiết kế Report.

### 0.3 Phạm vi V1

Trong phạm vi:

- read model theo ngày/khoa/toàn viện;
- doanh thu theo tháng và chi tiết từng ngày;
- top thuốc theo khoảng ngày;
- consume 5 event nghiệp vụ đã chốt;
- chống trùng, xử lý compensation và concurrency;
- PostgreSQL, RabbitMQ/DLQ, JWT/RBAC, OpenAPI;
- test domain, application, web, persistence, messaging và integration.

Ngoài phạm vi V1:

- dashboard tùy biến, export PDF/Excel, scheduled email;
- staff/headcount report;
- realtime WebSocket;
- REST/Feign sang service khác;
- truy vấn database service khác;
- endpoint sửa tay số liệu tổng hợp;
- public replay/backfill API;
- frontend/mobile;
- thay production code của producer trong task Report. Nếu contract producer phải đổi, tạo `HANDOFF`
  riêng và chờ owner xác nhận.

## 1. Phân tích domain

### 1.1 Bounded context

Report là **event-built read model**, không phải nguồn dữ liệu giao dịch. Nó nhận sự kiện đã xảy ra,
chuyển từng sự kiện thành một contribution xác định và duy trì projection phục vụ truy vấn nhanh.

Report sở hữu:

- số liệu tổng hợp ngày;
- số liệu doanh thu tháng;
- thống kê số lượng thuốc đã cấp;
- sổ event đã claim;
- sổ contribution thanh toán để bù trừ đúng theo `invoiceId`.

Report không sở hữu:

- hồ sơ khám, xét nghiệm, đơn thuốc, hóa đơn, nhân viên, khoa;
- tính đúng/sai của giao dịch nguồn;
- giá thuốc hay chính sách viện phí;
- quyết định nghiệp vụ dựa ngược vào số liệu báo cáo.

### 1.2 Ubiquitous language và định nghĩa metric

| Thuật ngữ | Định nghĩa V1 đã chốt |
|---|---|
| Lượt khám (`visitCount`) | Một `medicalrecord.created` hợp lệ, tính vào `examinationDate`. Không dùng appointment để tránh đếm người chưa khám. |
| Lượt xét nghiệm (`labCount`) | Một `lab.result.created` hợp lệ, tức một xét nghiệm đã có kết quả, tính vào `performedDate`. |
| Đơn thuốc đã cấp (`prescriptionCount`) | Một `prescription.filled` hợp lệ, tính một lần cho cả đơn, không tính theo số dòng thuốc. |
| Số lượng thuốc (`dispensedQuantity`) | Tổng `quantity` thực cấp của từng `drugId` trong `dispensedItems`. |
| Doanh thu (`revenue`) | Tổng tiền của các `payment.completed` còn hiệu lực; không dùng `PrescriptionFilledEvent.totalAmount` để tránh đếm hai lần. |
| Hóa đơn (`invoiceCount`) | Số `invoiceId` có contribution ở trạng thái `APPLIED`; hóa đơn đã đảo không còn được tính. |
| Dòng khoa | Projection có `departmentId` cụ thể. |
| Dòng toàn viện | Projection có `departmentId = NULL`. |
| Ngày nghiệp vụ | Ngày lấy từ field domain của event; nếu event không có ngày riêng thì đổi `occurredAt` theo `Asia/Bangkok`. |
| Contribution | Hiệu ứng định lượng mà một fact nguồn đóng góp vào projection. |

### 1.3 Input event contract đang tồn tại

| Routing key | Producer thật | Field Report dùng | Ngày nghiệp vụ |
|---|---|---|---|
| `medicalrecord.created` | Clinical | `eventId`, `recordId`, `departmentId`, `examinationDate` | `examinationDate` |
| `lab.result.created` | Lab | `eventId`, `labId`, `departmentId`, `performedDate` | `performedDate` |
| `prescription.filled` | Pharmacy | `eventId`, `prescriptionId`, `departmentId`, `dispensedItems[drugId,drugName,quantity]` | `occurredAt` tại `Asia/Bangkok` |
| `payment.completed` | Billing | `eventId`, `invoiceId`, `departmentId`, `totalAmount` | `occurredAt` tại `Asia/Bangkok` |
| `payment.failed` | Billing | `eventId`, `invoiceId`, `reason` | Không dùng ngày failure để trừ; đảo contribution của payment gốc |

Các field envelope `occurredAt`, `correlationId` phải deserialize được dù Report không dùng chúng để
tính mọi metric.

### 1.4 Aggregate và projection ownership

| Model | Natural/business key | Hành vi | Invariant chính |
|---|---|---|---|
| `DailyVisitReport` | `(reportDate, departmentId)` | tăng visit/lab/prescription; cộng revenue delta | count và revenue cuối cùng không âm |
| `MonthlyRevenueReport` | `(year, month, departmentId)` | cộng revenue; điều chỉnh invoice count | month 1–12; total/count không âm |
| `DrugStatistic` | `(drugId, reportDate, departmentId)` | tăng số lượng đã cấp | quantity cuối cùng không âm; name là snapshot |
| `PaymentContribution` | `invoiceId` | apply completed; apply reversal; nhận event đảo đến sớm | đúng một contribution active cho mỗi invoice |
| `ProcessedEvent` | `eventId` | claim event một cách nguyên tử | một event chỉ thắng claim một lần |

`PaymentContribution` là model bắt buộc bổ sung so với tài liệu nháp. Nếu chỉ có `PROCESSED_EVENT`,
`payment.failed` hiện tại không mang `amount` và `departmentId`, nên Report không biết phải trừ dòng
nào và bao nhiêu.

### 1.5 Luồng dữ liệu chuẩn

```text
RabbitMQ report.q
    ↓ deserialize + validate payload
ReportEventConsumer (driving adapter, không truy cập repository)
    ↓ gọi đúng một method của UpdateAggregateUseCase
AggregateUpdaterService @Transactional
    1. claim eventId bằng INSERT ... ON CONFLICT DO NOTHING
    2. nếu mất claim: return thành công
    3. lock/create contribution hoặc projection theo thứ tự cố định
    4. gọi domain behavior
    5. save; commit cả claim và effect
    ↓
PostgreSQL projections
    ↑
ReportApplicationService ← ReportController ← ADMIN/MANAGER
```

## 2. Quyết định architecture đã chốt

### 2.1 Dependency direction

```text
Driving adapters                    Core                         Driven adapters

web/ReportController ─┐                                  ┌─ infrastructure/persistence
messaging/consumer ───┼─> application/port/in            │
                      └─> application/service ─> domain ──┤
                                                        └─ application/port/out

infrastructure  ─────────────────────> application ─────────────> domain
```

- `domain`: Java thuần; không Spring/JPA/Jackson/Jakarta Validation.
- `application`: use case, transaction, port, DTO; chỉ được dùng `@Service`, `@Transactional` và
  MapStruct concession theo blueprint.
- `web` và `messaging/consumer`: driving adapter; không chứa business rule, không gọi repository.
- `infrastructure/persistence`: entity/JPA/native SQL/adapter.
- `infrastructure/config` và `infrastructure/security`: Rabbit, JWT, OpenAPI.
- **Không tạo `infrastructure/client` và không thêm OpenFeign.**
- Consumer đặt ở `messaging/consumer`, theo blueprint và cây module hiện tại. Dòng trong
  `06-events-rabbitmq.md` ghi `infrastructure/messaging/consumer` không áp dụng cho module này.

### 2.2 Cây package đích

```text
backend/report-service/
├── pom.xml
├── README.md
├── report.http
└── src/
    ├── main/
    │   ├── java/com/mediflow/report/
    │   │   ├── ReportServiceApplication.java
    │   │   ├── domain/model/
    │   │   │   ├── DailyVisitReport.java
    │   │   │   ├── MonthlyRevenueReport.java
    │   │   │   ├── DrugStatistic.java
    │   │   │   ├── PaymentContribution.java
    │   │   │   ├── PaymentContributionStatus.java
    │   │   │   └── TopMedicineSummary.java
    │   │   ├── application/port/in/
    │   │   │   ├── UpdateAggregateUseCase.java
    │   │   │   └── ReadReportUseCase.java
    │   │   ├── application/port/out/
    │   │   │   ├── DailyVisitReportRepositoryPort.java
    │   │   │   ├── MonthlyRevenueReportRepositoryPort.java
    │   │   │   ├── DrugStatisticRepositoryPort.java
    │   │   │   ├── PaymentContributionRepositoryPort.java
    │   │   │   └── ProcessedEventPort.java
   │   │   ├── application/dto/command/
   │   │   │   └── DispensedItem.java
   │   │   ├── application/dto/response/
    │   │   │   ├── DailyReportDTO.java
    │   │   │   ├── MonthlyReportDTO.java
    │   │   │   └── TopMedicineDTO.java
    │   │   ├── application/mapper/ReportDtoMapper.java
    │   │   ├── application/service/
    │   │   │   ├── AggregateUpdaterService.java
    │   │   │   └── ReportApplicationService.java
    │   │   ├── messaging/consumer/
    │   │   │   ├── ReportEventConsumer.java
    │   │   │   └── payload/
    │   │   │       ├── MedicalRecordCreatedPayload.java
    │   │   │       ├── LabResultCreatedPayload.java
    │   │   │       ├── PrescriptionFilledPayload.java
    │   │   │       ├── PaymentCompletedPayload.java
    │   │   │       └── PaymentFailedPayload.java
    │   │   ├── web/
    │   │   │   ├── ReportController.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   └── infrastructure/
    │   │       ├── persistence/
    │   │       │   ├── DailyVisitReportJpaEntity.java
    │   │       │   ├── DailyVisitReportJpaRepository.java
    │   │       │   ├── DailyVisitReportPersistenceMapper.java
    │   │       │   ├── DailyVisitReportPersistenceAdapter.java
    │   │       │   ├── MonthlyRevenueReportJpaEntity.java
    │   │       │   ├── MonthlyRevenueReportJpaRepository.java
    │   │       │   ├── MonthlyRevenueReportPersistenceMapper.java
    │   │       │   ├── MonthlyRevenueReportPersistenceAdapter.java
    │   │       │   ├── DrugStatisticJpaEntity.java
    │   │       │   ├── DrugStatisticJpaRepository.java
    │   │       │   ├── DrugStatisticPersistenceMapper.java
    │   │       │   ├── DrugStatisticPersistenceAdapter.java
    │   │       │   ├── PaymentContributionJpaEntity.java
    │   │       │   ├── PaymentContributionJpaRepository.java
    │   │       │   ├── PaymentContributionPersistenceMapper.java
    │   │       │   ├── PaymentContributionPersistenceAdapter.java
    │   │       │   ├── ProcessedEventJpaEntity.java
    │   │       │   ├── ProcessedEventJpaRepository.java
    │   │       │   └── ProcessedEventPersistenceAdapter.java
    │   │       ├── config/
    │   │       │   ├── RabbitConfig.java
    │   │       │   ├── SecurityConfig.java
    │   │       │   ├── OpenApiConfig.java
    │   │       │   └── ReportClockConfig.java
    │   │       └── security/
    │   │           ├── JwtAuthFilter.java
    │   │           └── JwtProperties.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__init.sql
    └── test/
        ├── java/com/mediflow/report/...
        └── resources/contracts/*.json
```

Không tạo domain exception chỉ để đủ folder. Chỉ thêm exception khi có một failure contract thật.

### 2.3 Port contract đích

```java
public interface UpdateAggregateUseCase {
    void onMedicalRecordCreated(UUID eventId, LocalDate reportDate, UUID departmentId);
    void onLabResultCreated(UUID eventId, LocalDate reportDate, UUID departmentId);
    void onPrescriptionFilled(UUID eventId, Instant occurredAt, UUID departmentId,
                              UUID prescriptionId, List<DispensedItem> items);
    void onPaymentCompleted(UUID eventId, Instant occurredAt, UUID invoiceId,
                            UUID departmentId, BigDecimal amount);
    void onPaymentFailed(UUID eventId, Instant occurredAt, UUID invoiceId);
}

public interface ReadReportUseCase {
    DailyReportDTO daily(LocalDate date, UUID departmentId);
    MonthlyReportDTO monthly(int month, int year, UUID departmentId);
    List<TopMedicineDTO> topMedicines(LocalDate fromDate, LocalDate toDate,
                                     UUID departmentId, int limit);
}
```

Out-port đã sửa so với bản nháp:

- `ProcessedEventPort.claimIfAbsent(eventId, routingKey) -> boolean`; bỏ cặp
  `alreadyProcessed/markProcessed` vì check-then-insert có race.
- `MonthlyRevenueReportRepositoryPort` có `find(year, month, departmentId)` thay cho
  `findByMonth(year, month)`, vì API trả đúng một scope.
- thêm `PaymentContributionRepositoryPort.findOrCreateForUpdate(invoiceId)` để xử lý completed/failed
  theo business key.
- `DrugStatisticRepositoryPort.topMedicines(...)` trả `List<TopMedicineSummary>`, không trả JPA
  projection hoặc HTTP DTO qua boundary.

### 2.4 Transaction boundary và lock order

Mỗi event hợp lệ được xử lý trong **một transaction application**. Consumer không mở transaction và
không gọi `ProcessedEventPort` trực tiếp.

Thứ tự khóa cố định:

1. atomic claim `eventId`;
2. nếu là payment: `PAYMENT_CONTRIBUTION` theo `invoiceId`;
3. `DAILY_VISIT_REPORT`: toàn viện (`NULL`) trước, khoa sau nếu có;
4. `MONTHLY_REVENUE_REPORT`: toàn viện trước, khoa sau nếu có;
5. `DRUG_STATISTIC`: sort `drugId` tăng dần; với mỗi thuốc, toàn viện trước, khoa sau.

Không có luồng nào được khóa drug trước daily hoặc monthly trước daily. Quy tắc này ngăn deadlock khi
nhiều event cùng cập nhật một ngày/khoa.

`findOrCreate` cho các natural key nullable (daily/monthly/drug) phải dùng:

1. `INSERT ... ON CONFLICT DO NOTHING`;
2. `SELECT ... FOR UPDATE` với nhánh `IS NULL` tường minh;
3. map entity sang domain;
4. save trong cùng transaction.

Payment contribution là ngoại lệ có chủ đích: vì trạng thái `NEW` chỉ là aggregate transient và
database không cho phép placeholder `NEW`, adapter phải gọi `pg_advisory_xact_lock` với khóa dẫn xuất
từ `invoiceId`, sau đó `SELECT` contribution. Nếu chưa có row, trả
`PaymentContribution.initialize(invoiceId)`; chỉ lưu sau khi xử lý event đã chuyển sang trạng thái
bền vững (`PENDING_REVERSAL`, `APPLIED` hoặc `REVERSED`).

### 2.5 Schema V1 đã chốt

V1 có **5 bảng**, không phải 4:

| Bảng | Mục đích | Ràng buộc bắt buộc |
|---|---|---|
| `DAILY_VISIT_REPORT` | số liệu ngày | unique `(report_date, department_id) NULLS NOT DISTINCT`; counts/revenue `>= 0` |
| `MONTHLY_REVENUE_REPORT` | doanh thu tháng | unique `(year, month, department_id) NULLS NOT DISTINCT`; month 1–12; totals `>= 0` |
| `DRUG_STATISTIC` | thuốc theo ngày | unique index `(drug_id, report_date, department_id) NULLS NOT DISTINCT`; quantity `>= 0` |
| `PROCESSED_EVENT` | atomic event claim | PK `event_id`, routing key, processed time |
| `PAYMENT_CONTRIBUTION` | bù trừ theo invoice | PK `invoice_id`; completed/failed event id unique; status state machine |

`PAYMENT_CONTRIBUTION` tối thiểu gồm:

```text
invoice_id UUID PK
completed_event_id UUID NULL UNIQUE
failed_event_id UUID NULL UNIQUE
payment_date DATE NULL
department_id UUID NULL
amount DECIMAL(15,2) NULL
status VARCHAR(20) NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ
```

Status persisted: `PENDING_REVERSAL`, `APPLIED`, `REVERSED`. `NEW` chỉ là trạng thái transient của
aggregate mới khởi tạo trước khi contribution được lưu lần đầu.

- `PENDING_REVERSAL` cho phép `payment.failed` đến trước `payment.completed`; amount/date/dept còn null.
- `APPLIED` bắt buộc có completed event, amount dương và payment date.
- `REVERSED` bắt buộc có cả completed và failed event cùng dữ liệu contribution gốc.

Không tạo foreign key sang service khác. Mọi `departmentId`, `drugId`, `invoiceId` là UUID trần.

## 3. Business rules V1 đã chốt

### 3.1 Quy tắc metric và aggregate

| ID | Rule đã chốt | Acceptance chính |
|---|---|---|
| RPT-B01 | Mỗi event vận hành cập nhật dòng toàn viện và, khi `departmentId != null`, thêm đúng một dòng khoa. Không cập nhật hai lần dòng toàn viện khi department null. | Unit test verify số row/save đúng 1 hoặc 2. |
| RPT-B02 | `medicalrecord.created` tăng `visitCount` đúng 1 tại `examinationDate`. | Không dùng `occurredAt` hay appointment status. |
| RPT-B03 | `lab.result.created` tăng `labCount` đúng 1 tại `performedDate`. | Một lab result event = một lượt xét nghiệm hoàn tất. |
| RPT-B04 | `prescription.filled` tăng `prescriptionCount` đúng 1; mỗi item tăng quantity tương ứng. | Item trùng `drugId` trong cùng payload được cộng gộp trước khi lock/save. |
| RPT-B05 | Chỉ `payment.completed` tạo doanh thu; `PrescriptionFilledEvent.totalAmount` bị bỏ qua. | Không double-count tiền thuốc. |
| RPT-B06 | `payment.completed` hợp lệ tạo contribution `APPLIED`, tăng daily/monthly revenue và invoice count theo invoice đúng một lần. | Duplicate invoice với eventId mới không tăng lại. |
| RPT-B07 | `payment.failed` được hiểu là **đảo một payment đã completed**, không phải một attempt chưa thu tiền. Nó đảo contribution theo ngày/khoa/số tiền gốc. | Không dùng ngày failure để trừ. |
| RPT-B08 | Failure đến trước completed tạo `PENDING_REVERSAL`; completed đến sau hoàn thiện contribution thành `REVERSED` nhưng không tạo hiệu ứng tăng rồi giảm giả. | Net projection luôn 0 cho invoice đó. |
| RPT-B09 | State cuối của daily/monthly/drug và invoice count không được âm. | Domain + DB check constraint. |
| RPT-B10 | Visit/lab/prescription/drug quantity V1 chỉ nhận delta dương. Chỉ revenue và invoice count có luồng compensation, nhưng final state vẫn không âm. | Loại bỏ câu nháp “mọi counter chịu delta âm”. |
| RPT-B11 | `drugName` là snapshot; top medicine group theo `drugId`, tổng quantity, tên hiển thị là snapshot mới nhất trong khoảng query. | Đổi tên thuốc không tách một thuốc thành hai dòng top. |

### 3.2 Quy tắc event, idempotency và concurrency

| ID | Rule đã chốt | Acceptance chính |
|---|---|---|
| RPT-E01 | Event idempotency dùng atomic `claimIfAbsent`, không dùng `alreadyProcessed` rồi `markProcessed`. | Hai thread cùng eventId chỉ một thread thắng. |
| RPT-E02 | Claim event và toàn bộ effect commit/rollback cùng một transaction. | Lỗi sau claim rollback claim; retry xử lý lại được. |
| RPT-E03 | Publisher phải giữ nguyên `eventId` khi redelivery/retry cùng event. V1 không suy luận duplicate logic của record/lab/prescription khi producer phát eventId mới. | Contract fixture/Javadoc ghi rõ giới hạn. |
| RPT-E04 | Payment có thêm business-key idempotency theo `invoiceId`, vì có lifecycle completed/reversed. | Hai completed eventId khác nhau cho cùng invoice chỉ apply một lần. |
| RPT-E05 | Natural-key create phải concurrency-safe bằng `ON CONFLICT DO NOTHING` + `SELECT FOR UPDATE`. | PostgreSQL 16, test hai luồng. |
| RPT-E06 | Event malformed, unknown routing key hoặc vi phạm field bắt buộc không được ghi `PROCESSED_EVENT`; sau retry hữu hạn phải vào `report.dlq`. | Messaging integration test. |
| RPT-E07 | Consumer chỉ deserialize/validate/dispatch; business logic, idempotency và transaction nằm trong application service. | Architecture/unit test. |
| RPT-E08 | Report không publish event. | Không có publisher port/adapter/RabbitTemplate trong application. |
| RPT-E09 | Queue V1 chỉ bind 5 key trong mục 1.3. `staff.department.changed` không bind ở V1. | Rabbit declarable test đúng 5 binding. |

### 3.3 Quy tắc query/API

| ID | Rule đã chốt | Acceptance chính |
|---|---|---|
| RPT-Q01 | `departmentId` bỏ trống nghĩa là dòng toàn viện (`NULL`), không phải cộng động tất cả dòng khoa lúc đọc. | Query dùng null-safe predicate. |
| RPT-Q02 | Daily không có row trả DTO zero, HTTP 200. | Không 404. |
| RPT-Q03 | Monthly không có row trả total/count zero và `dailyDetails` đủ mọi ngày trong tháng, zero-filled, sort tăng dần. | Tháng nhuận có 29 phần tử. |
| RPT-Q04 | Monthly có dữ liệu vẫn trả `dailyDetails` đủ mọi ngày, merge các row đã có và zero-fill ngày thiếu. | Dùng `findRange` đúng scope. |
| RPT-Q05 | Top medicine lọc inclusive `fromDate <= reportDate <= toDate`, đúng scope khoa/toàn viện, group theo drugId, order `totalQuantity DESC, drugId ASC`. | Kết quả deterministic. |
| RPT-Q06 | `limit` mặc định 10, hợp lệ 1–50; month 1–12; year 2000–2100. | Input sai trả 400. |
| RPT-Q07 | `fromDate > toDate` là business-invalid query, trả 422 với code `REPORT_DATE_RANGE_INVALID`. | Typed application exception + handler. |
| RPT-Q08 | Mọi response HTTP bọc `ApiResponse`; chỉ `ADMIN` và `MANAGER` được gọi ba endpoint. | 401/403/200 web tests. |
| RPT-Q09 | Query ngày tương lai hợp lệ và trả zero nếu chưa có dữ liệu. | Không áp quy tắc “không được ở tương lai”. |

### 3.4 Quy tắc chất lượng dữ liệu và vận hành

| ID | Rule đã chốt | Acceptance chính |
|---|---|---|
| RPT-O01 | Event domain-date bắt buộc; `occurredAt` bắt buộc cho event dùng để suy ra ngày. Zone cố định qua config `mediflow.report.zone-id`, mặc định `Asia/Bangkok`. | Unit test quanh 00:00 UTC/Bangkok. |
| RPT-O02 | `eventId`, source id, ngày, amount/item bắt buộc theo contract; amount và quantity phải dương; drug name không blank và không vượt quá 150 ký tự (khớp schema). | Payload/domain validation test. |
| RPT-O03 | `departmentId = null` vẫn cho phép với payment completed hiện tại và chỉ cập nhật toàn viện; các operational event khác thiếu department bị coi malformed. | Không tự đoán khoa. |
| RPT-O04 | Số liệu là derived data; không có update/delete API. Rebuild bằng replay event qua quy trình vận hành riêng. | Controller chỉ có 3 GET. |
| RPT-O05 | Không log full payload. Log routing key, eventId, correlationId và source id; không log PII. | Review gate. |
| RPT-O06 | Không gọi REST, không Feign, không external datasource, không scheduled end-of-day aggregation. | ArchUnit + config audit. |

### 3.5 Những điểm của tài liệu nháp đã sửa

| Điểm nháp | Quyết định cuối | Lý do |
|---|---|---|
| `alreadyProcessed` rồi `markProcessed` | Atomic `claimIfAbsent` ở đầu transaction | Check-then-act vẫn double count khi chạy song song. |
| `payment.failed` tự mang amount/dept | Dùng `PAYMENT_CONTRIBUTION` theo invoice | Payload Billing thật không có hai field này. |
| Failure trừ theo ngày failure | Trừ đúng ngày payment gốc | Báo cáo lịch sử phải được restate đúng kỳ phát sinh. |
| 4 bảng | 5 bảng | Cần payment contribution để compensation và out-of-order. |
| Unique thường cho `DRUG_STATISTIC` | Unique index `NULLS NOT DISTINCT` | PostgreSQL coi nhiều `NULL` là khác nhau nếu unique thường. |
| Mọi counter nhận delta âm | Chỉ compensation metric nhận signed delta; final không âm | Visit/lab/prescription không có event đảo trong V1. |
| Bind `staff.department.changed` | Loại khỏi V1 | Không có staffing report/schema; payload thật thiếu envelope; không được chuyển số liệu lịch sử theo staff. |
| Monthly port trả mọi department | Tìm một `(year,month,departmentId)` | Khớp endpoint trả một scope. |
| Monthly `dailyDetails` chưa rõ | Luôn full calendar, zero-filled | Contract ổn định và phù hợp biểu đồ. |
| Consumer tự dedupe/transaction | Application service chịu trách nhiệm | Consumer phải là driving adapter mỏng. |

## 4. Feature map

| Feature | Đầu vào | Core | Đầu ra | Rules |
|---|---|---|---|---|
| F1 Daily activity projection | medical/lab/prescription event | daily aggregate | daily table | B01–B04, E01–E07 |
| F2 Drug projection | prescription event items | drug aggregate | drug table/top query | B04, B11, Q05 |
| F3 Revenue projection | payment completed/failed | payment contribution + daily/monthly | revenue tables | B05–B10, E04 |
| F4 Daily query | date, department | read service | DailyReportDTO | Q01–Q02 |
| F5 Monthly query | month, year, department | monthly + daily range | MonthlyReportDTO | Q03–Q04 |
| F6 Top medicine query | range, department, limit | grouped SQL | TopMedicineDTO list | Q05–Q07 |
| F7 Messaging reliability | Rabbit delivery | claim/transaction/retry/DLQ | projections or DLQ | E01–E09 |
| F8 HTTP security | JWT + role | controller/use case | ApiResponse | Q06–Q09 |

## 5. Thứ tự task triển khai

| Task | Tên | Dependency | Trạng thái |
|---|---|---|---|
| T00 | Chốt quyết định và tài liệu kỹ thuật/nghiệp vụ hợp nhất | — | DONE |
| T01 | Domain models và state machine | T00 | DONE |
| T02 | In-port, out-port, DTO và mapper contracts | T01 | DONE |
| T03 | Flyway V1 và JPA entity mapping | T01 | DONE |
| T04 | Atomic claim + concurrency-safe persistence adapters | T02, T03 | DONE |
| T05 | Daily/lab/prescription aggregate use cases | T04 | DONE |
| T06 | Payment contribution và compensation use cases | T04 | DONE |
| T07 | Read-report queries và top medicine | T04 | DONE |
| T08 | Rabbit topology, payloads, consumer, retry và DLQ | T05, T06 | DONE |
| T09 | REST API, security, errors, OpenAPI và `.http` | T07 | DONE |
| T10 | Cross-layer integration/concurrency/recovery tests | T08, T09 | DONE |
| T11 | Documentation, quality gates và release audit | T10 | DONE |

## 6. Task cards để AI implement từng task

### T00 — Chốt quyết định và tài liệu kỹ thuật/nghiệp vụ hợp nhất

**Mục tiêu:** loại bỏ chỉ dẫn trái nhau trước khi viết production code, nhưng chỉ duy trì một tài liệu
kỹ thuật/nghiệp vụ ngoài plan.

**Phạm vi:**

- `docs/ai/services/report.md`;
- `docs/plans/2026-09-15-report-service-implementation-plan.md`;
- chỉ đọc các spec/HTML và producer source, không sửa chúng.

**Việc làm:**

1. Ghi toàn bộ quyết định V1 vào `report.md`: 5 event, 5 bảng, atomic claim, payment contribution,
   monthly detail semantics và staff event bị loại khỏi V1.
2. Đối chiếu payload producer thật để tránh suy đoán field; fixture JSON sẽ tạo ở T08 cùng consumer.
3. Chạy baseline test và ghi số test vào plan.
4. Kiểm tra git diff chỉ có đúng hai file deliverable.

**Gate:** `report.md` và plan thống nhất baseline; không có file thứ ba bị sửa trong T00.

**Bằng chứng hoàn thành:** `report.md` đã ghi baseline V1; `mvn -q -pl backend/report-service -am test`
pass 6/6 ArchitectureTest; git diff chỉ còn plan và report.md.

**Lệnh giao AI:** “Implement T00 only. Update only the plan and
`docs/ai/services/report.md` with the frozen decisions. Do not add fixtures or production Java.”

### T01 — Domain models và state machine

**Mục tiêu:** hiện thực domain thuần Java trước mọi framework adapter.

**Files chính:** `domain/model/*`, test mirror `domain/model/*Test.java`.

**Việc làm:**

1. Tạo factory `initialize/restore`; không setter công khai.
2. Dùng `BigDecimal.ZERO.setScale(2)` hoặc policy scale nhất quán với Billing; không `double`.
3. Enforce final nonnegative state trong behavior.
4. Implement `PaymentContribution` transitions:
   - absent + completed → `APPLIED`, trả effect `APPLY`;
   - absent + failed → `PENDING_REVERSAL`, effect `NONE`;
   - pending + completed → `REVERSED`, effect `NONE`;
   - applied + failed → `REVERSED`, effect `REVERSE`;
   - repeated/logically duplicate transition → effect `NONE`.
5. `TopMedicineSummary` là immutable query result.

**Test bắt buộc:** initialization, positive increments, invalid negative state, month range, payment
transition matrix, duplicate transition, out-of-order transition.

**Gate:** domain test không khởi tạo Spring và ArchitectureTest xanh.

**Bằng chứng hoàn thành:** đã implement 5 aggregate/value object (`DailyVisitReport`,
`MonthlyRevenueReport`, `DrugStatistic`, `PaymentContribution`, `TopMedicineSummary`) và
`ReportRuleException`; bổ sung 5 test class domain. Lệnh
`mvn -q -pl backend/report-service -am test` pass **36 test liên quan T01** (6 ArchitectureTest + 30
domain test, bao gồm invariant persisted-state và cập nhật snapshot tên thuốc).

**Lệnh giao AI:** “Implement T01 only, test-first. Own only domain model and domain unit tests. Do not
create ports, JPA, Spring services or controllers.”

### T02 — Port, DTO và mapper contracts

**Mục tiêu:** khóa compile-time boundary để các task sau không tự đổi chữ ký.

**Files chính:** `application/port/in`, `application/port/out`, `application/dto/response`,
`application/mapper`.

**Việc làm:**

1. Tạo hai in-port theo mục 2.3 và immutable `DispensedItem` application value.
2. Tạo năm out-port, gồm atomic claim và payment contribution.
3. Tạo ba response DTO đúng JSON English camelCase.
4. Tạo mapper chỉ map model → DTO; monthly daily details được assemble ở service, không ép MapStruct
   chứa logic.
5. Viết compile/mapper test cho null department và money.

**Gate:** application không import JPA/Spring Data/AMQP/Web/infrastructure.

**Bằng chứng hoàn thành:** đã tạo 2 in-port, 5 out-port với `claimIfAbsent` và payment contribution,
`DispensedItem` immutable, 3 response DTO và `ReportDtoMapper` MapStruct. `ReportDtoMapperTest` có 3
test cho null department, money và immutable daily details; ArchitectureTest vẫn xanh.

**Lệnh giao AI:** “Implement T02 only. Treat signatures in section 2.3 as frozen; request a plan
change if a signature cannot express a rule.”

### T03 — Flyway V1 và JPA entity mapping

**Mục tiêu:** tạo schema mới chính xác và entity chỉ làm persistence model.

**Files chính:** `db/migration/V1__init.sql`, năm nhóm JPA entity/repository skeleton/mapper.

**Việc làm:**

1. Tạo đủ 5 bảng theo mục 2.5, explicit column/table names.
2. Dùng `NULLS NOT DISTINCT` cho cả daily, monthly và drug natural keys.
3. Thêm check constraint cho month, status, money/count/quantity.
4. Thêm index phục vụ daily range và top query.
5. Entity dùng UUID, LocalDate, Instant, BigDecimal precision 15 scale 2; không `@Data`.
6. Persistence mapper restore đầy đủ timestamps và nullable department.

**Test bắt buộc:** Flyway clean database, Hibernate validate, entity round-trip, DB rejects invalid
month/status/negative final value.

**Gate:** PostgreSQL 16 Testcontainer pass; không dùng H2 để chứng minh SQL Postgres-specific.

**Bằng chứng hoàn thành:** đã tạo Flyway `V1__init.sql` cho đủ 5 bảng, các check/index PostgreSQL
`NULLS NOT DISTINCT`, 5 JPA entity và 4 persistence mapper. `PersistenceMappingTest` có 3 round-trip
test; `ReportMigrationSchemaTest` kiểm tra nội dung migration; `ReportMigrationPostgresTest` có 2 test
kiểm tra migrate/constraint với SQLState rõ ràng và được cấu hình `disabledWithoutDocker=true`; thêm
`ReportJpaValidationTest` để Hibernate validate chạy cùng Flyway. Lần chạy hiện tại pass **43 test**,
skip **3 test PostgreSQL** (2 migration test và 1 Hibernate-validation test) vì Docker client cục bộ
báo API 1.32 thấp hơn server yêu cầu 1.40 (tổng 46 test được Maven phát hiện).

**Lệnh giao AI:** “Implement T03 only. Use PostgreSQL Testcontainers because NULLS NOT DISTINCT and
native upsert are required. Do not implement application services.”

### T04 — Atomic claim và concurrency-safe persistence

**Mục tiêu:** hoàn thiện driven adapters trước use case.

**Files chính:** repositories, persistence adapters, native query/projection tests.

**Việc làm:**

1. `ProcessedEventPersistenceAdapter.claimIfAbsent` dùng insert-on-conflict và trả row count.
2. Daily/monthly/drug find-or-create dùng insert rồi lock; payment contribution dùng advisory
   transaction lock theo `invoiceId` rồi select/initialize transient như ngoại lệ ở §2.4.
3. Mọi nullable department predicate có nhánh `IS NULL` tường minh.
4. Payment contribution lock theo `invoiceId`.
5. Top query inclusive range, scope null-safe, group by drugId, latest name, deterministic order.
6. Không để JPA type rò vào port.

**Test bắt buộc:**

- two-thread same event claim → một winner;
- two-thread daily/monthly/drug findOrCreate → một row;
- null department uniqueness cho cả ba bảng;
- top range/limit/latest-name/tie-break;
- transaction rollback xóa claim chưa commit.

**Gate:** persistence tests chạy thật trên PostgreSQL 16 và không flake qua ít nhất 10 vòng concurrency.

**Lệnh giao AI:** “Implement T04 only. Focus on database atomicity and null-safe keys; do not put
business metric decisions in adapters.”

**Bằng chứng hoàn thành:** đã implement 5 Spring Data repository và 5 persistence adapter. Các native
upsert, `SELECT ... FOR UPDATE`, null-safe predicate, top query inclusive/latest-name và advisory lock
payment đã có. Persistence gate chạy thật trên PostgreSQL 16: 6/6 test pass (migration 2, JPA
validation 1, concurrency 3), không skip/failure; two-thread claim và natural-key race đều xanh.

### T05 — Daily/lab/prescription aggregate use cases

**Mục tiêu:** xử lý ba event hoạt động theo rules B01–B04.

**Files chính:** `AggregateUpdaterService`, application unit tests.

**Việc làm:**

1. Method public `@Transactional` claim event đầu tiên.
2. Dùng helper scope theo thứ tự hospital → department.
3. Medical và lab tăng đúng field/ngày.
4. Prescription tăng count một lần; group duplicate item theo drugId; sort lock order.
5. Validate operational `departmentId` non-null, source/date/item fields hợp lệ trước claim.
6. Không dùng prescription total để tăng revenue.

**Test bắt buộc:** row count theo scope, redelivery, rollback, duplicate drug item, invalid payload,
prescription does not change revenue.

**Gate:** mọi rule B01–B05 và E01–E03 liên quan có test application.

**Lệnh giao AI:** “Implement T05 only with mocked out-ports. Keep consumer and JPA out of the test.”

**Bằng chứng hoàn thành:** `AggregateUpdaterService` xử lý medical/lab/prescription trong một
transaction; validate trước claim, cập nhật hospital → department, group duplicate drug item và
sort `drugId` trước khi lock/save. Bộ test application có 12 test xanh cho row scope, redelivery,
invalid payload, grouping/order, timezone và payment regression; không có tương tác persistence khi
payload invalid.

### T06 — Payment contribution và compensation use cases

**Mục tiêu:** doanh thu đúng khi duplicate, compensation hoặc event đến sai thứ tự.

**Files chính:** nhánh payment trong `AggregateUpdaterService`, payment application tests và
persistence integration bổ sung.

**Việc làm:**

1. Convert completed occurredAt sang payment date bằng configured `ZoneId`.
2. Claim event → lock contribution → chạy state transition.
3. Effect `APPLY`: daily/monthly hospital, rồi department nếu có; revenue `+amount`, invoice `+1`.
4. Effect `REVERSE`: dùng contribution gốc; revenue `-amount`, invoice `-1`.
5. Effect `NONE`: không lock/update aggregate không cần thiết.
6. Duplicate completed có eventId mới nhưng cùng invoice không tăng lại.
7. Failure unknown invoice tạo pending; completed đến sau kết thúc reversed/net zero.

**Test bắt buộc:** completed, failed after completed, failed before completed, same event twice,
different event same invoice, null department hospital-only, midnight timezone, two-thread
completed/failed race, final values nonnegative.

**Gate:** state machine matrix và PostgreSQL race test xanh.

**Lệnh giao AI:** “Implement T06 only. PaymentContribution is the source for reversal; never infer
amount or department from payment.failed and never call Billing.”

**Bằng chứng hoàn thành:** nhánh payment trong `AggregateUpdaterService` đã hoàn tất validate timezone,
atomic claim, khóa contribution theo invoice, state transition `APPLY/REVERSE/NONE`, idempotency theo
eventId và invoiceId, xử lý failure đến trước completed, cập nhật hospital trước department và đảo đúng
ngày/khoa/số tiền gốc. `AggregateUpdaterServiceTest` hiện có 6 kịch bản payment (redelivery cùng event,
completed khác event cùng invoice, null department, completed/failed cùng kỳ và out-of-order). Gate race
hai luồng trên PostgreSQL đã chạy xanh trong bộ persistence/cross-layer Testcontainers; không có lost
update và giá trị cuối không âm.

### T07 — Read-report application và queries

**Mục tiêu:** ba use case đọc có output ổn định và không lộ persistence.

**Files chính:** `ReportApplicationService`, mapper, query tests.

**Việc làm:**

1. Daily: find đúng scope hoặc zero DTO.
2. Monthly: find aggregate hoặc zero; tạo full calendar; merge daily rows theo ngày.
3. Top: validate range, gọi port đúng inclusive range và clamped/validated limit.
4. Map BigDecimal scale ổn định.
5. Tạo typed `ReportDateRangeException` ở application/domain phù hợp, code
   `REPORT_DATE_RANGE_INVALID`.

**Test bắt buộc:** no data, null department, leap year, partial month zero-fill, top range/limit,
tie ordering delegated đúng, invalid date range.

**Gate:** không query theo từng ngày kiểu N+1; monthly dùng một `findRange`.

**Lệnh giao AI:** “Implement T07 only. Monthly dailyDetails must contain every calendar day and use
one range query.”

**Bằng chứng hoàn thành:** đã thêm `ReportApplicationService` với daily zero DTO, monthly aggregate
zero fallback + full calendar (kể cả tháng nhuận), một `findRange` để merge detail, top inclusive range
và limit mặc định 10/clamp tối đa 50; `ReportDateRangeException` dùng code
`REPORT_DATE_RANGE_INVALID`. `ReportApplicationServiceTest` có 8 test cho no-data/null scope, leap year,
partial month, zero-fill, limit và invalid range. Tại checkpoint T07, toàn module phát hiện 68 test, 65 pass và 3 skip
(các test PostgreSQL/Testcontainers do Docker API chưa tương thích).

### T08 — Rabbit topology, payloads, consumer, retry và DLQ

**Mục tiêu:** nối 5 event thật vào in-port mà không đặt business logic ở adapter.

**Files chính:** `RabbitConfig`, consumer/payload, contract tests, `application.yml` listener config.

**Việc làm:**

1. Declare durable `mediflow.events`, `mediflow.events.dlx`, `report.q`, `report.dlq`.
2. Bind đúng 5 routing key; không bind staff event.
3. Deserialize theo routing key bằng ObjectMapper/Jackson; `FAIL_ON_UNKNOWN_PROPERTIES=false` để
   additive producer fields không phá consumer.
4. Validate field bắt buộc rồi gọi đúng một in-port method.
5. Transient failure retry hữu hạn; poison message cuối cùng reject sang DLQ, không requeue vô hạn.
6. Không log full message hoặc patient data.
7. Fixture tests phải deserialize JSON lấy từ contract producer hiện tại.

**Test bắt buộc:** 5 dispatch cases, unknown key, malformed payload, additive field compatibility,
exact binding set, retry rồi DLQ.

**Gate:** consumer test verify không tương tác repository/adapter trực tiếp.

**Bằng chứng hoàn thành:** `RabbitConfig` khai báo durable topic exchange, `report.q`, `report.dlq`
và đúng 5 binding V1 (không bind `staff.department.changed`). `ReportEventConsumer` deserialize
đúng contract fixtures của Clinical, Lab, Pharmacy và Billing, bỏ qua field additive, validate
envelope/payload trước khi gọi đúng một in-port method, không log payload; retry hữu hạn reject sang
DLQ. `ReportEventConsumerTest` (8 test) và `RabbitConfigTest` (2 test) xanh.

**Lệnh giao AI:** “Implement T08 only. Create and use the contract fixtures in the test scope, and keep
all aggregation/idempotency inside UpdateAggregateUseCase.”

### T09 — REST API, security, error envelope, OpenAPI và `.http`

**Mục tiêu:** ship ba GET endpoint đúng wire contract và defense in depth.

**Files chính:** controller, exception handler, security/config, `report.http`.

**Endpoint:**

| Method | Path | Role | Result |
|---|---|---|---|
| GET | `/api/v1/reports/daily?date&departmentId` | ADMIN, MANAGER | `ApiResponse<DailyReportDTO>` |
| GET | `/api/v1/reports/monthly?month&year&departmentId` | ADMIN, MANAGER | `ApiResponse<MonthlyReportDTO>` |
| GET | `/api/v1/reports/top-medicines?fromDate&toDate&departmentId&limit` | ADMIN, MANAGER | `ApiResponse<List<TopMedicineDTO>>` |

**Việc làm:**

1. Controller mỏng, `@Validated`, `@PreAuthorize` trên từng endpoint.
2. Validate month/year/limit ở edge; range business rule ở application.
3. Security stateless, JWT verify, default deny; health public, còn Swagger/OpenAPI chỉ mở bằng
   cấu hình opt-in ở local/development.
4. Handler trả validation 400, date-range 422, unexpected 500 không lộ stack trace.
5. OpenAPI mô tả null department = hospital và no-data zero semantics.
6. Chuyển `report.http` từ DEMO sang live contract, không chứa token thật.

**Test bắt buộc:** 200/400/422/401/403; ADMIN/MANAGER allow; role khác deny; envelope fields; optional
department; default limit.

**Gate:** endpoint chưa có request trong `report.http` được coi là chưa hoàn thành.

**Bằng chứng hoàn thành:** đã thêm ba GET endpoint với `@PreAuthorize` ADMIN/MANAGER, optional
`departmentId`, default limit 10 và correlation envelope. JWT stateless được xác minh lại ở service;
health public, Swagger/OpenAPI mặc định deny và chỉ permit khi bật explicit local opt-in.
`GlobalExceptionHandler` map validation 400,
`REPORT_DATE_RANGE_INVALID`/business rule 422, 401/403 envelope và unexpected 500 không lộ stack
trace. `OpenApiConfig` mô tả zero-fill/hospital scope; `report.http` đã chuyển từ DEMO sang live.
`ReportControllerTest` có 10 test xanh cho 200/400/422/401/403, role matrix, envelope và default
limit.

**Lệnh giao AI:** “Implement T09 only. Mock ReadReportUseCase in web slice; do not start a database
for controller tests.”

### T10 — Cross-layer integration, concurrency và recovery

**Mục tiêu:** chứng minh các rule khó bằng hạ tầng thật.

**Phạm vi test:** `@SpringBootTest` + PostgreSQL 16 + RabbitMQ Testcontainers.

**Scenario bắt buộc:**

1. publish medical/lab/prescription → daily + hospital/dept + top đúng;
2. publish payment completed → daily/monthly đúng;
3. publish same event twice → single effect;
4. publish completed eventId khác cùng invoice → single effect;
5. failed after completed → original day/month trở về đúng;
6. failed before completed → net zero;
7. malformed → DLQ, không processed/effect;
8. concurrent first events → one projection row và không lost update;
9. API đọc projection vừa consume đúng envelope;
10. restart consumer/database transaction rollback → message xử lý lại an toàn.

**Gate:** test không dùng sleep mù; dùng Awaitility/eventual assertion với timeout ngắn, cleanup độc lập.

**Bằng chứng hoàn thành:** `ReportCrossLayerIntegrationTest` đã chạy qua Spring context thật với
PostgreSQL 16 và RabbitMQ 3.13 Testcontainers khi Docker khả dụng. Bộ test bao phủ event vận hành
(hospital/khoa/top thuốc), payment duplicate và cùng invoice khác eventId, compensation hai thứ tự,
concurrent first events, API envelope, malformed/DLQ và transaction rollback khi payment conflict.
Mọi assertion bất đồng bộ dùng Awaitility; mỗi test tự dọn projection và queue. Kết quả runtime: **8/8
pass, 0 skip**; tổng module **121/121 pass**.

**Lệnh giao AI:** “Implement T10 only. Use real PostgreSQL and RabbitMQ; do not mock the behavior that
the scenario is intended to prove.”

### T11 — Documentation, quality gates và release audit

**Mục tiêu:** đóng service với bằng chứng có thể review.

**Việc làm:**

1. Cập nhật `report.md` nếu code cuối cùng có khác biệt đã được phê duyệt; đây là tài liệu
   kỹ thuật/nghiệp vụ duy nhất ngoài plan.
2. Bổ sung ví dụ response vào `report.http`/docs nếu cần.
3. Chạy test module, verify, Javadocs và dependency audit.
4. Search cấm: Feign/client/external datasource, raw fetch không liên quan, forbidden imports.
5. Đối chiếu từng rule trong traceability matrix; không để rule `PARTIAL`.
6. Ghi số test, command và kết quả vào plan.
7. Kiểm tra git diff không đụng module ngoài scope.

**Gate cuối:**

```text
mvn -q -pl backend/report-service -am test
mvn -q -pl backend/report-service -am verify
mvn -q -pl backend/report-service -am -DskipTests javadoc:javadoc
```

**Lệnh giao AI:** “Implement T11 only. Do not add features; audit and document the service against
the Definition of Done and report exact verification evidence.”

**Bằng chứng hoàn thành:** `report.md`, README và `report.http` đã đồng nhất với code T08–T10.
Đã chạy thành công `mvn -q -pl backend/report-service -am -Dapi.version=1.40 test` và `verify` với
Docker Desktop (**121/121 pass, 0 skip, 0 failure/error**), cùng `mvn -q -pl backend/report-service
-am -DskipTests javadoc:javadoc` và `mvn -q -pl backend/report-service -am dependency:analyze`.
ArchitectureTest vẫn xanh; static search không phát hiện Feign/client, external datasource,
forbidden imports hay publisher ngoài bounded context. Không có thay đổi production ngoài
`backend/report-service/**`.

## 7. Quy trình AI bắt buộc cho mỗi task

### 7.1 Trước khi code

1. Chạy changelog theo root `AGENTS.md`.
2. Đọc plan này và các tài liệu trực tiếp liên quan task.
3. Đọc nested `backend/report-service/AGENTS.md`.
4. Kiểm tra `git status`; giữ nguyên thay đổi không liên quan của người dùng.
5. Xác nhận dependency của task trước đã `DONE`.
6. Liệt kê rule, file ownership, test sẽ viết và lệnh verify.

### 7.2 Trong khi code

1. Viết test thất bại cho rule chính.
2. Implement tối thiểu để test xanh.
3. Refactor trong scope, không đổi contract đã freeze âm thầm.
4. Chạy test hẹp sau mỗi nhóm thay đổi.
5. Nếu source producer khác fixture, dừng task và tạo HANDOFF/decision note; không tự suy ra field.
6. Không sửa module producer để “cho khớp Report”.

### 7.3 Sau khi code

1. Chạy test task + full module test.
2. Chạy ArchitectureTest.
3. Review diff, forbidden imports, secrets và phạm vi.
4. Cập nhật status task, rule coverage, test count và quyết định mới trong plan.
5. Chỉ commit khi người dùng yêu cầu; commit không có AI co-author trailer.

### 7.4 Mẫu prompt chung giao task cho AI

```text
Implement exactly task Txx from docs/plans/2026-09-15-report-service-implementation-plan.md.
Read root and nested AGENTS.md plus the task's source documents first.
Stay within backend/report-service/** and explicitly assigned docs.
Do not edit producer services or infer missing cross-service fields.
Use test-first: rule → failing test → code → refactor → architecture audit.
Run the task-specific gate and the report module regression tests.
Update only Txx status/evidence in the plan when all acceptance criteria pass.
Do not start the next task.
```

## 8. Traceability: rule → code → test → task

| Rule | Code owner | Test tối thiểu | Task |
|---|---|---|---|
| RPT-B01 | `AggregateUpdaterService` | `event_updatesHospitalAndDepartmentScopes` | T05/T06 |
| RPT-B02 | daily domain/use case | `medicalRecord_usesExaminationDate` | T05 |
| RPT-B03 | daily domain/use case | `labResult_usesPerformedDate` | T05 |
| RPT-B04 | prescription use case | `prescription_groupsDuplicateDrugItems` | T05 |
| RPT-B05 | prescription/payment use cases | `prescriptionFilled_doesNotChangeRevenue` | T05 |
| RPT-B06 | payment contribution/use case | `completed_appliesInvoiceOnce` | T06 |
| RPT-B07 | payment contribution/use case | `failed_reversesOriginalPeriod` | T06 |
| RPT-B08 | payment state machine | `failedBeforeCompleted_netZero` | T01/T06 |
| RPT-B09 | domain + DB constraint | `aggregate_neverEndsNegative` | T01/T03 |
| RPT-B10 | domain behavior | `nonCompensableCounter_rejectsNegativeDelta` | T01 |
| RPT-B11 | top SQL | `top_usesLatestNamePerDrug` | T04/T07 |
| RPT-E01 | processed event adapter | `claimConcurrent_singleWinner` | T04 |
| RPT-E02 | updater transaction | `effectFailure_rollsBackClaim` | T04/T10 |
| RPT-E03 | fixture/docs | `contract_requiresStableEventId` | T08 |
| RPT-E04 | payment contribution | `sameInvoiceDifferentEvents_singleEffect` | T06 |
| RPT-E05 | persistence adapters | `findOrCreateConcurrent_singleRow` | T04 |
| RPT-E06 | Rabbit error handling | `malformedMessage_deadLettersWithoutClaim` | T08/T10 |
| RPT-E07 | consumer | `consumer_dispatchesOnlyToUseCase` | T08 |
| RPT-E08 | architecture | `report_hasNoPublisherDependency` | T11 |
| RPT-E09 | Rabbit config | `rabbitConfig_bindsExactlyFiveKeys` | T08 |
| RPT-Q01 | read repo/service | `nullDepartment_readsHospitalRow` | T04/T07 |
| RPT-Q02 | read service/web | `daily_noData_returnsZero200` | T07/T09 |
| RPT-Q03 | monthly service | `monthly_noData_fullZeroCalendar` | T07 |
| RPT-Q04 | monthly service | `monthly_partialData_zeroFillsMissingDays` | T07 |
| RPT-Q05 | top repo/service | `top_rangeScopeLimitAndTieBreak` | T04/T07 |
| RPT-Q06 | controller | `queryBounds_invalid_returns400` | T09 |
| RPT-Q07 | read service/handler | `top_fromAfterTo_returns422` | T07/T09 |
| RPT-Q08 | controller/security | `reportEndpoints_roleMatrix` | T09 |
| RPT-Q09 | read service | `daily_futureDate_returnsZero` | T07 |
| RPT-O01 | zone config/updater | `occurredAt_aroundMidnight_usesBangkokDate` | T06 |
| RPT-O02 | consumer validation | `invalidPayload_deadLetters` | T08/T10 |
| RPT-O03 | updater | `paymentNullDepartment_hospitalOnly` | T06 |
| RPT-O04 | controller architecture | `controller_exposesOnlyThreeGets` | T09 |
| RPT-O05 | logging review | code review/static search | T11 |
| RPT-O06 | ArchitectureTest | `noFeignClientOrExternalDataSource` | T11 |

## 9. Dependency graph và milestone

```text
T00 contracts/docs
  └─ T01 domain
      ├─ T02 ports/DTO
      └─ T03 schema/entities
           └────┬──── T04 persistence atomicity
                ├─ T05 activity projections ─┐
                ├─ T06 revenue compensation ├─ T08 messaging ─┐
                └─ T07 read queries ─────────┴─ T09 web ──────┤
                                                              └─ T10 integration
                                                                  └─ T11 release audit
```

Milestone:

- M1 — Core frozen: T00–T02.
- M2 — Database reliable: T03–T04.
- M3 — Business features complete: T05–T07.
- M4 — Adapters complete: T08–T09.
- M5 — Release-ready: T10–T11.

## 10. Risk register

| Risk | Mức | Kiểm soát |
|---|---|---|
| Producer thay payload mà không version/fixture | Cao | Contract fixtures T08; additive deserialization; HANDOFF khi breaking. |
| `payment.failed` đến trước completed | Cao | Payment contribution `PENDING_REVERSAL`. |
| Duplicate event song song | Cao | Atomic claim trong cùng transaction. |
| Duplicate hospital row do nullable unique | Cao | `NULLS NOT DISTINCT` trên cả 3 natural key. |
| Lost update/deadlock | Cao | Insert+lock và global lock order; concurrency integration. |
| Poison message requeue vô hạn | Cao | Retry hữu hạn + DLQ; no claim on malformed. |
| Report lệch vì producer publish không bền | Cao, ngoài scope | Ghi rõ operational dependency; Report không thể tự sửa bằng cross-DB query. |
| Timezone làm lệch ngày | Trung bình | Config ZoneId + boundary tests. |
| Top query sai khi đổi tên thuốc | Trung bình | Group drugId, latest snapshot name. |
| Monthly N+1 | Trung bình | Một range query + in-memory zero-fill. |
| Tài liệu lại lệch code | Trung bình | T00 freeze `report.md` + plan, T11 audit, `.http` live contract. |

## 11. Definition of Done toàn Report Service

- [x] T00–T11 đều `DONE`, có bằng chứng test.
- [x] 5 bảng đúng schema và migration chạy trên PostgreSQL 16 sạch.
- [x] 5 inbound event có fixture, dispatch test và integration test.
- [x] Atomic event claim và natural-key concurrency được chứng minh bằng test hai luồng.
- [x] Payment completed/failed đúng cả duplicate và out-of-order.
- [x] Ba endpoint đúng DTO/envelope/RBAC/no-data semantics.
- [x] Monthly full-calendar và top medicine deterministic.
- [x] Retry/DLQ không tạo poison loop; rollback không làm mất event.
- [x] Domain/application không vi phạm forbidden imports.
- [x] Không Feign, client, external datasource, publisher hoặc manual mutation endpoint.
- [x] `report.md`, `report.http` và plan thống nhất code thật; spec/HTML nền không bị sửa ngoài task được giao.
- [x] Module test, verify và Javadocs xanh.
- [x] Không có thay đổi production ngoài `backend/report-service/**`.

## 12. Trạng thái sau T11

T00–T11 và review hardening trong report-service đã hoàn tất. Runtime gate đã chạy xanh bằng Docker
Desktop, PostgreSQL 16 và RabbitMQ 3.13; số liệu test hiện tại được ghi theo kết quả Maven mới nhất.
Report pin Testcontainers 1.20.6 trong POM (thay mặc định 1.19.8 dùng Docker API 1.32); khi máy có
cấu hình Testcontainers cũ, dùng `-Dapi.version=1.40` như lệnh verify đã ghi ở trên.

Gateway/Common đã phát hành và chia sẻ claim `type`. Report hiện yêu cầu chính xác `type=access`;
token thiếu type, refresh và service bị từ chối. Regression tests nằm trong `JwtAuthFilterTest`.

Baseline V1 được giữ nguyên: 5 bảng, atomic claim, 5 binding và `payment.failed` chỉ mang `invoiceId`.
