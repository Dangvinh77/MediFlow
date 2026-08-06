# 08 — report-service

**Module** `report-service` · **Package** `com.mediflow.report` · **Cổng** 8088 · **DB** `mediflow_report` · **Tiền tố** `/api/v1/reports`

Đây là một **read model**. Nó không sở hữu dữ liệu giao dịch nào: mọi con số nó trả về đều là số liệu
tổng hợp dựng thuần từ event. Nó không bao giờ truy vấn database của service khác và không bao giờ
gọi REST sang service khác.

Nếu service này có lúc nào đó cần một JDBC URL hoặc một Feign client trỏ sang service khác, nghĩa là
thiết kế đã bị vi phạm.

## 1. Lược đồ — `V1__init.sql`

> **Bảng/cột dùng tiếng Anh** (theo thống nhất riêng cho nhánh C — pharmacy/billing/report).
> Mapping ở đầu mục này. Rest của hệ thống vẫn dùng tiếng Việt snake_case như `08-persistence-naming.md`.

```sql
-- Bộ đếm theo ngày, khóa theo (report_date, department_id). department_id NULL = số liệu toàn viện.
CREATE TABLE DAILY_VISIT_REPORT (
    report_id        UUID          PRIMARY KEY,
    report_date      DATE          NOT NULL,
    department_id    UUID,                                 -- ref organization-service KHOA
    visit_count      INT           NOT NULL DEFAULT 0,
    lab_count        INT           NOT NULL DEFAULT 0,
    prescription_count INT         NOT NULL DEFAULT 0,
    revenue          DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ
);
-- NULLS NOT DISTINCT để dòng toàn viện (department_id IS NULL) cũng là duy nhất mỗi ngày
CREATE UNIQUE INDEX uq_daily_report_date_dept
    ON DAILY_VISIT_REPORT (report_date, department_id) NULLS NOT DISTINCT;

CREATE TABLE MONTHLY_REVENUE_REPORT (
    report_id       UUID          PRIMARY KEY,
    month           INT           NOT NULL,
    year            INT           NOT NULL,
    department_id   UUID,
    total_revenue   DECIMAL(15,2) NOT NULL DEFAULT 0,
    invoice_count   INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT ck_month CHECK (month BETWEEN 1 AND 12)
);
CREATE UNIQUE INDEX uq_monthly_revenue
    ON MONTHLY_REVENUE_REPORT (year, month, department_id) NULLS NOT DISTINCT;

CREATE TABLE DRUG_STATISTIC (
    statistic_id     UUID          PRIMARY KEY,
    drug_id          UUID          NOT NULL,
    drug_name        VARCHAR(150)  NOT NULL,
    report_date      DATE          NOT NULL,
    department_id    UUID,
    dispensed_quantity INT         NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ,
    CONSTRAINT uq_drug_statistic UNIQUE (drug_id, report_date, department_id)
);
CREATE INDEX idx_drug_statistic_date ON DRUG_STATISTIC (report_date);

-- Bắt buộc ở đây: thiếu nó thì một lần gửi lại làm hỏng mọi bộ đếm vĩnh viễn.
CREATE TABLE PROCESSED_EVENT (
    event_id    UUID          PRIMARY KEY,
    routing_key VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

> `NULLS NOT DISTINCT` cần **PostgreSQL 15 trở lên**. Stack đang dùng PG 16 (`docker-compose.yml`)
> nên dùng được. Không có nó thì `(report_date, NULL)` sẽ không bị coi là trùng và bạn sẽ tích lũy hàng
> loạt dòng toàn viện trùng lặp.

> **Mapping (tiếng Việt → tiếng Anh)** — bảng dịch chính thức cho report:

| Việt (trước) | Anh (sau) | Dùng trong |
|---|---|---|
| BAO_CAO_KHAM | DAILY_VISIT_REPORT | bảng |
| ma_bao_cao | report_id | cột |
| ngay | report_date | cột |
| ma_khoa | department_id | cột |
| so_luong_kham | visit_count | cột |
| so_luong_xn | lab_count | cột |
| so_don_thuoc | prescription_count | cột |
| doanh_thu | revenue | cột |
| BAO_CAO_DOANH_THU | MONTHLY_REVENUE_REPORT | bảng |
| thang | month | cột |
| nam | year | cột |
| tong_doanh_thu | total_revenue | cột |
| so_hoa_don | invoice_count | cột |
| THONG_KE_THUOC | DRUG_STATISTIC | bảng |
| ma_thong_ke | statistic_id | cột |
| ma_thuoc | drug_id | cột |
| ten_thuoc | drug_name | cột |
| so_luong_xuat | dispensed_quantity | cột |
| SU_KIEN_DA_XU_LY | PROCESSED_EVENT | bảng |
| xu_ly_luc | processed_at | cột |
| BaoCaoKham | DailyVisitReport | domain class |
| BaoCaoDoanhThu | MonthlyRevenueReport | domain class |
| ThongKeThuoc | DrugStatistic | domain class |

## 2. Domain model

> Tên class/field trong nhánh C dùng tiếng Anh: `BaoCaoKham` → `DailyVisitReport`, ...

**`DailyVisitReport`** — `reportId`, `reportDate`, `departmentId` (nullable), `visitCount`, `labCount`,
`prescriptionCount`, `revenue`.

```java
public static DailyVisitReport initialize(LocalDate reportDate, UUID departmentId);
public void incrementVisits(int delta);
public void incrementLabs(int delta);
public void incrementPrescriptions(int delta);
public void addRevenue(BigDecimal amount);   // có thể âm khi bù trừ
```

**`MonthlyRevenueReport`** — `month`, `year`, `departmentId`, `totalRevenue`, `invoiceCount`.
**`DrugStatistic`** — `drugId`, `drugName`, `reportDate`, `departmentId`, `dispensedQuantity`.

Mọi bộ đếm đều phải chịu được **delta âm** — `payment.failed` sẽ đảo ngược doanh thu.

## 3. Port

```java
// out
public interface DailyVisitReportRepositoryPort {
    /** Tìm-hoặc-tạo dòng (reportDate, departmentId). Phải an toàn khi nhiều consumer chạy đồng thời. */
    DailyVisitReport findOrCreate(LocalDate reportDate, UUID departmentId);
    DailyVisitReport save(DailyVisitReport r);
    Optional<DailyVisitReport> find(LocalDate reportDate, UUID departmentId);
    List<DailyVisitReport> findRange(LocalDate fromDate, LocalDate toDate, UUID departmentId);
}
public interface MonthlyRevenueReportRepositoryPort {
    MonthlyRevenueReport findOrCreate(int year, int month, UUID departmentId);
    MonthlyRevenueReport save(MonthlyRevenueReport r);
    List<MonthlyRevenueReport> findByMonth(int year, int month);
}
public interface DrugStatisticRepositoryPort {
    DrugStatistic findOrCreate(UUID drugId, String drugName, LocalDate reportDate, UUID departmentId);
    DrugStatistic save(DrugStatistic s);
    List<DrugStatistic> topDrugs(LocalDate fromDate, LocalDate toDate, UUID departmentId, int limit);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }

// in
public interface UpdateAggregateUseCase {
    void onMedicalRecordCreated(UUID eventId, LocalDate reportDate, UUID departmentId);
    void onLabResultCreated(UUID eventId, LocalDate reportDate, UUID departmentId);
    void onPrescriptionFilled(UUID eventId, LocalDate reportDate, UUID departmentId, List<DispensedItem> items);
    void onPaymentCompleted(UUID eventId, LocalDate reportDate, UUID departmentId, BigDecimal amount);
    void onPaymentFailed(UUID eventId, LocalDate reportDate, UUID departmentId, BigDecimal amount);   // delta âm
    void onStaffDepartmentChanged(UUID eventId, UUID staffId, UUID oldDept, UUID newDept);
}
public interface ReadReportUseCase {
    DailyReportDTO daily(LocalDate date, UUID departmentId);
    MonthlyReportDTO monthly(int month, int year, UUID departmentId);
    List<TopMedicineDTO> topMedicines(LocalDate fromDate, LocalDate toDate, UUID departmentId, int limit);
}
```

## 4. DTO

```java
public record DailyReportDTO(LocalDate reportDate, UUID departmentId, int visitCount, int labCount,
                             int prescriptionCount, BigDecimal revenue) {}

public record MonthlyReportDTO(int month, int year, UUID departmentId, BigDecimal totalRevenue,
                               int invoiceCount, List<DailyReportDTO> dailyDetails) {}

public record TopMedicineDTO(UUID drugId, String drugName, int totalQuantity) {}
```

## 5. Luồng tổng hợp

Mọi consumer đều theo cùng bốn bước, **tất cả trong một transaction**:

```java
@RabbitListener(queues = "report.q")
@Transactional
public void on(Message msg) {
    UUID eventId = ...;
    if (processed.alreadyProcessed(eventId)) return;      // 1. khử trùng lặp TRƯỚC TIÊN
    var row = repo.findOrCreate(reportDate, departmentId);   // 2. tìm-hoặc-tạo
    row.incrementVisits(1);                               // 3. cộng delta
    repo.save(row);
    processed.markProcessed(eventId, routingKey);         // 4. cùng transaction
}
```

Mỗi event cập nhật **hai dòng**: dòng của khoa (`department_id = X`) và dòng toàn viện (`department_id = NULL`).
Chỉ làm một trong hai sẽ khiến tổng số liệu không khớp nhau.

| Event | Tác động |
|-------|----------|
| `medicalrecord.created` | `visit_count += 1` |
| `lab.result.created` | `lab_count += 1` |
| `prescription.filled` | `prescription_count += 1`; mỗi mặt hàng `DRUG_STATISTIC.dispensed_quantity += quantity` |
| `payment.completed` | `revenue += totalAmount`; theo tháng `total_revenue += totalAmount`, `invoice_count += 1` |
| `payment.failed` | `revenue -= totalAmount`; theo tháng tương tự, `invoice_count -= 1` |
| `staff.department.changed` | chỉ cập nhật ảnh chụp nhân sự; V1 không đổi bộ đếm nào |

### `findOrCreate` khi nhiều consumer chạy song song

Hai event cho cùng `(reportDate, departmentId)` có thể đến cùng lúc. Hãy dựa vào unique index thay vì
kiểm-rồi-chèn:

```sql
INSERT INTO DAILY_VISIT_REPORT (report_id, report_date, department_id) VALUES (:id, :reportDate, :departmentId)
ON CONFLICT (report_date, department_id) DO NOTHING;
```

rồi `SELECT ... FOR UPDATE`. Kiểu `findById` rồi `save` thông thường sẽ sinh lỗi trùng khóa khi tải cao.

## 6. Endpoint

| Method | Path | Trả về | Role |
|--------|------|--------|------|
| GET | `/api/v1/reports/daily?date&departmentId` | `DailyReportDTO` | ADMIN, MANAGER |
| GET | `/api/v1/reports/monthly?month&year&departmentId` | `MonthlyReportDTO` | ADMIN, MANAGER |
| GET | `/api/v1/reports/top-medicines?fromDate&toDate&departmentId&limit` | `List<TopMedicineDTO>` | ADMIN, MANAGER |

`departmentId` là tùy chọn ở cả ba: bỏ trống để lấy số liệu toàn viện, truyền vào để lấy của một khoa.
`limit` mặc định 10, tối đa 50.

## 7. Event

**Publish:** không có.

**Subscribe** — queue `report.q`, bind vào:
`medicalrecord.created` · `lab.result.created` · `prescription.filled` · `payment.completed` ·
`payment.failed` · `staff.department.changed`

## 8. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-R1 | Số liệu tổng hợp theo ngày và theo khoa | `onMedicalRecordCreated_updatesDeptAndHospitalRows` |
| BR-R2 | Gửi lại không làm đếm hai lần | `onPaymentCompleted_sameEventTwice_revenueCountedOnce` |
| BR-R3 | `payment.failed` đảo ngược doanh thu | `onPaymentFailed_subtractsRevenue` |
| BR-R4 | Không bao giờ truy vấn service khác | `reportService_hasNoFeignClientOrExternalDataSource` |
| BR-R5 | Ngày không có dữ liệu trả về số 0, không phải 404 | `daily_noData_returnsZeroedReport` |
| BR-R6 | Bỏ trống `departmentId` ⇒ trả dòng toàn viện | `daily_noDepartment_returnsNullDeptRow` |
| BR-R7 | Top thuốc tôn trọng khoảng ngày và limit | `topMedicines_respectsDateRangeAndLimit` |
| BR-R8 | Nhiều event đầu tiên cùng ngày chỉ tạo một dòng | `findOrCreate_concurrent_createsSingleRow` |

BR-R4 đáng viết hẳn một test ArchUnit:

```java
noClasses().that().resideInAPackage("..report..")
    .should().dependOnClassesThat().resideInAnyPackage("..openfeign..", "..client..")
```

## 9. Điểm dễ sai

- Số liệu tổng hợp là **dữ liệu dẫn xuất**, không bao giờ là nguồn chuẩn. Nếu chúng lệch, cách sửa là phát lại event chứ không phải sửa tay con số. Hãy ghi rõ điều này trong báo cáo.
- Báo cáo là dữ liệu tham khảo — tuyệt đối không được quay ngược lại chi phối một quyết định nghiệp vụ (`docs/ai/services/report.md`).
- `invoice_count` bị trừ khi `payment.failed`; hãy chặn để nó không xuống dưới 0 nếu event tới không đúng thứ tự.
- **Không cần** job chạy cuối ngày: bộ đếm được duy trì tăng dần theo từng event. Chỉ thêm job khi về sau cần backfill dữ liệu cũ.

---

## 10. Coding map (chỉ dẫn hiện thực — bổ sung cho spec)

> Mục này dành cho coder: file nào tạo, nội dung gì, đặt ở đâu. Spec là **nơi** (bounded context),
> mục này là **cách** (cây file cụ thể). Kết hợp với boilerplate chuẩn ở `docs/ai/reference/`.

### 10.1 Bản đồ file Java (mọi file cần tạo)

Base package `com.mediflow.report`. Cây đầy đủ:

```
report-service/src/main/java/com/mediflow/report/
├── ReportServiceApplication.java                       # có sẵn
├── domain/model/
│   ├── DailyVisitReport.java                           # bộ đếm theo ngày+dept (delta âm chấp nhận)
│   ├── MonthlyRevenueReport.java                       # theo month+year+dept
│   └── DrugStatistic.java                              # theo (drugId, reportDate, departmentId)
├── domain/exception/                                   # report ít rule ném — thường không cần domain exception riêng;
│   └── (tùy chọn) ReportNotFoundException.java         # chỉ khi có endpoint trả 404 (V1: không)
├── application/port/in/
│   ├── UpdateAggregateUseCase.java                     # onMedicalRecord/onLabResult/onPrescriptionFilled/onPaymentCompleted/onPaymentFailed/onStaffDepartmentChanged
│   └── ReadReportUseCase.java                          # daily/monthly/topMedicines
├── application/port/out/
│   ├── DailyVisitReportRepositoryPort.java
│   ├── MonthlyRevenueReportRepositoryPort.java
│   ├── DrugStatisticRepositoryPort.java
│   └── ProcessedEventPort.java
├── application/dto/response/
│   ├── DailyReportDTO.java
│   ├── MonthlyReportDTO.java
│   └── TopMedicineDTO.java
├── application/mapper/
│   └── ReportDtoMapper.java                            # MapStruct: model ↔ DTO (nếu cần)
├── application/service/
│   ├── ReportApplicationService.java                   # hiện thực ReadReportUseCase
│   └── AggregateUpdaterService.java                    # hiện thực UpdateAggregateUseCase (gọi repo findOrCreate)
├── web/                                  # DRIVING adapter (HTTP) — gọi vào application
│   ├── ReportController.java                           # 3 endpoints: daily/monthly/top-medicines
│   └── GlobalExceptionHandler.java                     # copy từ docs/ai/reference (chỉ đổi package)
├── messaging/consumer/                   # DRIVING adapter (events) — gọi vào application
│   └── ReportEventConsumer.java                        # 1 class duy nhất, switch theo routing key, @RabbitListener(queues = "report.q")
├── infrastructure/persistence/           # DRIVEN adapter (DB) — hiện thực port out
│   ├── DailyVisitReportJpaEntity.java
│   ├── DailyVisitReportJpaRepository.java              # + findOrCreate (ON CONFLICT), SELECT FOR UPDATE
│   ├── DailyVisitReportPersistenceAdapter.java
│   ├── DailyVisitReportPersistenceMapper.java
│   ├── MonthlyRevenueReportJpaEntity.java
│   ├── MonthlyRevenueReportJpaRepository.java
│   ├── MonthlyRevenueReportPersistenceAdapter.java
│   ├── MonthlyRevenueReportPersistenceMapper.java
│   ├── DrugStatisticJpaEntity.java
│   ├── DrugStatisticJpaRepository.java
│   ├── DrugStatisticPersistenceAdapter.java
│   ├── DrugStatisticPersistenceMapper.java
│   └── ProcessedEventPersistenceAdapter.java           # hiện thực ProcessedEventPort (bảng PROCESSED_EVENT)
└── infrastructure/messaging/             # DRIVEN adapter (RabbitMQ) — publisher + payload
    └── payload/                                        # (tùy chọn) khai báo lại event record cần dùng
```

> **Không có `infrastructure/client/`** — cấm tuyệt đối (BR-R4, ArchUnit test). Không có
> `infrastructure/messaging/payload/` bắt buộc nếu consumer nhận `Message` + parse JSON thủ công;
> nhưng nếu định nghĩa record để Jackson deserialize thì đặt ở đó.

### 10.2 Event consumer — pattern 4 bước trong 1 transaction

Consumer nhận **một queue `report.q`** bind 6 routing key, dispatch theo routing key:

```java
@RabbitListener(queues = "report.q")
@Transactional
public void on(Message message) {
    UUID eventId = extractEventId(message);                 // từ JSON, dù routing key nào
    if (processed.alreadyProcessed(eventId)) return;        // 1. dedupe TRƯỚC
    String rk = message.getMessageProperties().getReceivedRoutingKey();
    switch (rk) {
        case "medicalrecord.created" -> updater.onMedicalRecordCreated(eventId, recordDate, departmentId);
        case "lab.result.created"    -> updater.onLabResultCreated(eventId, resultDate, departmentId);
        case "prescription.filled"   -> updater.onPrescriptionFilled(eventId, date, departmentId, items);
        case "payment.completed"     -> updater.onPaymentCompleted(eventId, date, departmentId, totalAmount);
        case "payment.failed"        -> updater.onPaymentFailed(eventId, date, departmentId, totalAmount);
        case "staff.department.changed" -> updater.onStaffDepartmentChanged(eventId, staffId, oldDept, newDept);
        default -> throw new IllegalArgumentException("Unknown routing key: " + rk);
    }
    processed.markProcessed(eventId, rk);                   // 4. cùng transaction với hiệu ứng
}
```

Mỗi handler gọi **2 dòng**: dòng khoa (`department_id = X`) + dòng toàn viện (`department_id = NULL`). Làm thiếu một
trong hai → tổng số liệu không khớp (xem §5).

### 10.3 Chi tiết persistence — `findOrCreate` an toàn concurrency

Bảng `DAILY_VISIT_REPORT` có unique index `uq_daily_report_date_dept` (`NULLS NOT DISTINCT`). `findOrCreate`
phải dùng **`INSERT ... ON CONFLICT DO NOTHING` rồi `SELECT ... FOR UPDATE`**, không phải check-rồi-insert:

```java
@Modifying
@Query(value = """
    INSERT INTO DAILY_VISIT_REPORT (report_id, report_date, department_id)
    VALUES (:id, :reportDate, :departmentId)
    ON CONFLICT (report_date, department_id) DO NOTHING
    """, nativeQuery = true)
void insertIfAbsent(@Param("id") UUID id, @Param("reportDate") LocalDate reportDate,
                    @Param("departmentId") UUID departmentId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM DailyVisitReportJpaEntity r WHERE r.reportDate = :reportDate AND (r.departmentId = :departmentId OR (:departmentId IS NULL AND r.departmentId IS NULL))")
Optional<DailyVisitReportJpaEntity> findForUpdate(@Param("reportDate") LocalDate reportDate,
                                                  @Param("departmentId") UUID departmentId);
```

**Điều kiện `department_id IS NULL`**: Hibernate/JPA đối chiếu `WHERE x = NULL` không khớp. Phải viết
`(r.departmentId = :departmentId OR (:departmentId IS NULL AND r.departmentId IS NULL))` — đây là lỗi dễ mắc nhất ở report.

**`DrugStatistic`** `findOrCreate` tương tự, khóa `(drugId, reportDate, departmentId)`.

**Lưu ý `invoice_count` không âm**: khi `payment.failed`, dùng `Math.max(0, invoiceCount - 1)` (xem §9).

### 10.4 RabbitConfig — hằng số & binding (report)

```
EXCHANGE = "mediflow.events"        (durable topic)
DLX      = "mediflow.events.dlx"
QUEUE    = "report.q"               (durable, DLX + DLQ "report.dlq")
```

Queue `report.q` bind 6 routing key:
`medicalrecord.created` · `lab.result.created` · `prescription.filled` · `payment.completed` ·
`payment.failed` · `staff.department.changed`

> **Report không publish gì.** Không có binding xuôi; chỉ bind 6 key vào queue.

### 10.5 Test plan — map business rule → tầng + tên test

| Rule | Tầng | Tên test | Cần gì |
|---|---|---|---|
| BR-R1 (tổng hợp theo ngày+dept) | application (mock repo) | `onMedicalRecordCreated_updatesDeptAndHospitalRows` | mock 2 repo, verify 2 save |
| BR-R2 (redelivery không đếm 2 lần) | application | `onPaymentCompleted_sameEventTwice_revenueCountedOnce` | mock `ProcessedEventPort` |
| BR-R3 (payment.failed đảo doanh thu) | application | `onPaymentFailed_subtractsRevenue` | mock repo, assert delta âm |
| BR-R4 (không Feign/client) | **ArchUnit** | `reportService_hasNoFeignClientOrExternalDataSource` | kiểm cây package |
| BR-R5 (ngày không dữ liệu → 0, không 404) | web slice | `daily_noData_returnsZeroedReport` | `@WebMvcTest` |
| BR-R6 (bỏ departmentId → dòng toàn viện) | application | `daily_noDepartment_returnsNullDeptRow` | mock repo trả `departmentId = null` |
| BR-R7 (top thuốc tôn trọng khoảng/limit) | application | `topMedicines_respectsDateRangeAndLimit` | mock repo |
| BR-R8 (nhiều event đầu tiên tạo 1 dòng) | integration | `findOrCreate_concurrent_createsSingleRow` | Testcontainers PG, 2 luồng |

**Test ArchUnit BR-R4 (phần cần thêm dependency):**

```java
@AnalyzeClasses(packages = "com.mediflow.report")
class ArchitectureTest {
    @Test
    void noFeignClientOrExternalDataSource() {
        noClasses().that().resideInAPackage("..report..")
            .should().dependOnClassesThat().resideInAnyPackage("..openfeign..", "..client..")
            .check(new ClassFileImportChecks());
    }
}
```

### 10.6 Checklist hoàn thiện report (Definition of Done)

- [ ] `V1__init.sql` đủ 4 bảng + `NULLS NOT DISTINCT` unique index + `ck_month`.
- [ ] Domain: 3 model (delta âm chấp nhận); không domain exception nếu không cần.
- [ ] Ports đủ (4 out, 2 in); application service hiện thực, không import Spring Data/AMQP.
- [ ] `findOrCreate` dùng `INSERT ... ON CONFLICT DO NOTHING` + `SELECT FOR UPDATE`; xử lý đúng `department_id IS NULL`.
- [ ] Consumer 1 class, switch theo routing key, dedupe + mark trong cùng transaction.
- [ ] Controller 3 endpoints + `@PreAuthorize` (ADMIN/MANAGER); `departmentId` tùy chọn.
- [ ] `GlobalExceptionHandler`, `SecurityConfig`, `RabbitConfig`, `OpenApiConfig`.
- [ ] Test 5 tầng + ArchUnit BR-R4; 8 business rule được phủ.
- [ ] `mvn -pl backend/report-service -am -q -DskipTests install` xanh.
