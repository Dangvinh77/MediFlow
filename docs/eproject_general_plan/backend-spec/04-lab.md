# 04 — lab-service (Khoa Xét nghiệm)

**Module** `lab-service` · **Package** `com.mediflow.lab` · **Cổng** 8084 · **DB** `mediflow_lab` · **Tiền tố** `/api/v1/lab`

Sở hữu chỉ định xét nghiệm và kết quả của chúng. Không gọi ai đồng bộ — mọi thứ đến qua event hoặc
qua REST từ bác sĩ lâm sàng.

> Quy ước đặt tên chuẩn toàn hệ thống: bảng/column English snake_case, field Java/JSON English camelCase,
> enum English UPPER_SNAKE. Prose (mô tả) vẫn tiếng Việt.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE LAB_TEST (
    test_id                   UUID          PRIMARY KEY,
    record_id                 UUID          NOT NULL,     -- tham chiếu clinical-service
    patient_id                UUID          NOT NULL,     -- tham chiếu patient-service
    requesting_department_id  UUID          NOT NULL,     -- tham chiếu organization-service DEPARTMENT
    test_type                 VARCHAR(50)   NOT NULL,
    requested_date            DATE          NOT NULL,
    performed_date            DATE,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    conclusion                TEXT,
    is_paid                   BOOLEAN       NOT NULL DEFAULT false,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ
);
CREATE INDEX idx_lab_test_patient   ON LAB_TEST (patient_id);
CREATE INDEX idx_lab_test_record    ON LAB_TEST (record_id);
CREATE INDEX idx_lab_test_department ON LAB_TEST (requesting_department_id);

CREATE TABLE LAB_RESULT (
    result_id         UUID          PRIMARY KEY,
    test_id           UUID          NOT NULL REFERENCES LAB_TEST(test_id) ON DELETE CASCADE,
    indicator         VARCHAR(100)  NOT NULL,
    value             VARCHAR(50)   NOT NULL,
    unit              VARCHAR(20),
    reference_range   VARCHAR(50),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_lab_result_test ON LAB_RESULT (test_id);

-- Sổ khử trùng lặp cho event consumer (xem §9)
CREATE TABLE PROCESSED_EVENT (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

## 2. Enum

```java
public enum LabTestStatus { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }
```

## 3. Domain model

**`LabTest`** — `testId`, `recordId`, `patientId`, `requestingDepartmentId`, `labType`,
`requestedDate`, `performedDate`, `status`, `conclusion`, `paid`, `results` (`List<LabResult>`), timestamps.

```java
public static LabTest create(UUID recordId, UUID patientId, UUID requestingDepartmentId,
                             String labType, LocalDate requestedDate);

/** BR-L1 + BR-L2 + BR-L3 đều nằm ở đây. Ghi kết quả là hoàn tất xét nghiệm. */
public void recordResults(List<LabResult> results, String conclusion, LocalDate performedDate);

public void changeStatus(LabTestStatus next);
public void markPaid();
public boolean isFinal();   // COMPLETED hoặc CANCELLED
```

Bất biến:

| Kiểm tra | Mã lỗi |
|----------|--------|
| `labType` không rỗng | `LAB_TYPE_REQUIRED` |
| gọi `recordResults` khi `isFinal()` | `LAB_ALREADY_FINISHED` (BR-L1) |
| `recordResults` với danh sách rỗng | `LAB_RESULT_EMPTY` |
| `performedDate` trước `requestedDate` | `LAB_DATE_BEFORE_REQUEST` (BR-L3) |
| chuyển trạng thái không hợp lệ | `LAB_INVALID_TRANSITION` |

`recordResults` tự đặt `status = COMPLETED` (BR-L2) — người gọi không bao giờ tự đặt thủ công.

Chuyển tiếp: `PENDING → IN_PROGRESS | CANCELLED`, `IN_PROGRESS → COMPLETED | CANCELLED`, `COMPLETED`/`CANCELLED` là kết thúc.

**`LabResult`** — `resultId`, `indicator` (không rỗng), `value` (không rỗng), `unit`, `referenceRange`.

## 4. Mã lỗi

`LAB_NOT_FOUND` → 404 · `LAB_ALREADY_FINISHED`, `LAB_RESULT_EMPTY`, `LAB_DATE_BEFORE_REQUEST`, `LAB_INVALID_TRANSITION`, `LAB_TYPE_REQUIRED` → 422.

## 5. Port

```java
// out
public interface LabTestRepositoryPort {
    LabTest save(LabTest lt);
    Optional<LabTest> findById(UUID id);
    List<LabTest> findByPatient(UUID patientId);
    List<LabTest> findByRecord(UUID recordId);
    PageResult<LabTest> search(UUID departmentId, LabTestStatus status, PageQuery page);
}
public interface ProcessedEventPort {          // tiện ích khử trùng lặp dùng chung
    boolean alreadyProcessed(UUID eventId);
    void markProcessed(UUID eventId, String routingKey);
}
public interface LabEventPublisherPort {
    void publishRequestCreated(LabRequestCreatedEvent e);
    void publishResultCreated(LabResultCreatedEvent e);
}

// in
public interface ManageLabTestUseCase {
    LabTestDTO create(CreateLabRequest r);
    LabTestDTO getById(UUID id);
    List<LabTestDTO> byPatient(UUID patientId);
    LabTestDTO addResults(UUID id, AddResultRequest r);
    LabTestDTO changeStatus(UUID id, LabTestStatus status);
}
public interface ReactToClinicalUseCase {
    void autoCreateFromRecord(UUID recordId, UUID patientId, UUID departmentId, String labType);
    void markPaid(UUID testId);
}
```

## 6. DTO

```java
public record CreateLabRequest(
    @NotNull UUID recordId, @NotNull UUID patientId, @NotNull UUID requestingDepartmentId,
    @NotBlank @Size(max = 50) String labType,
    @NotNull @PastOrPresent LocalDate requestedDate) {}

public record AddResultRequest(
    @NotEmpty @Valid List<LabResultItem> results,
    @Size(max = 4000) String conclusion,
    @NotNull LocalDate performedDate) {}

public record LabResultItem(
    @NotBlank @Size(max = 100) String indicator,
    @NotBlank @Size(max = 50) String value,
    @Size(max = 20) String unit,
    @Size(max = 50) String referenceRange) {}

public record LabTestDTO(UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
                         String labType, LocalDate requestedDate, LocalDate performedDate,
                         LabTestStatus status, String conclusion, boolean paid,
                         List<LabResultDTO> results, Instant createdAt, Instant updatedAt) {}

public record LabResultDTO(UUID resultId, String indicator, String value, String unit, String referenceRange) {}
```

## 7. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/lab/{id}` | — | `LabTestDTO` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/lab/patient/{patientId}` | — | `List<LabTestDTO>` | ADMIN, DOCTOR |
| GET | `/api/v1/lab?departmentId&status&page&size` | — | `PageResult<LabTestDTO>` | ADMIN, MANAGER, LAB_TECH |
| POST | `/api/v1/lab` | `CreateLabRequest` | 201 | ADMIN, DOCTOR |
| PUT | `/api/v1/lab/{id}/results` | `AddResultRequest` | 200 | ADMIN, LAB_TECH |
| PUT | `/api/v1/lab/{id}/status` | `{status}` | 200 | ADMIN, LAB_TECH |

## 8. Event

**Publish**

| Routing key | Payload |
|-------------|---------|
| `lab.request.created` | `{envelope, labId, patientId, recordId, departmentId, labType, requestedDate}` |
| `lab.result.created` | `{envelope, labId, patientId, recordId, departmentId, results, conclusion}` |

`lab.result.created` bắn từ `addResults`, sau khi commit. Trường `departmentId` lấy từ `requestingDepartmentId`.

**Subscribe** — queue `lab.q`

| Routing key | Xử lý |
|-------------|-------|
| `medicalrecord.created` | Chỉ tự tạo xét nghiệm **khi hồ sơ có chỉ định**. V1: bỏ qua nếu payload không mang chỉ định rõ ràng — đừng tạo xét nghiệm cho mọi hồ sơ một cách mù quáng. |
| `payment.completed` | `markPaid(testId)` — đặt `is_paid = true` cho các xét nghiệm thuộc hóa đơn đó |

## 9. Khử trùng lặp

Cả hai consumer đều đi qua `ProcessedEventPort`:

```java
@RabbitListener(queues = "lab.q")
public void on(LabRelevantEvent e) {
    if (processed.alreadyProcessed(e.eventId())) return;   // gửi lại — không làm gì
    useCase.handle(...);
    processed.markProcessed(e.eventId(), routingKey);
}
```

Phải làm việc này **trong cùng transaction với hiệu ứng**, nếu không sổ ghi và hiệu ứng sẽ lệch nhau.

## 10. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-L1 | Không ghi kết quả khi đã `COMPLETED` hoặc `CANCELLED` | `addResults_alreadyCompleted_throwsBusinessRule` |
| BR-L2 | Ghi kết quả tự động hoàn tất xét nghiệm | `addResults_valid_marksStatusCompleted` |
| BR-L3 | `performed_date >= requested_date` | `addResults_dateBeforeRequest_throwsBusinessRule` |
| BR-L4 | Ghi kết quả thì publish `lab.result.created` | `addResults_valid_publishesResultCreated` |
| BR-L5 | Danh sách kết quả không được rỗng | `addResults_emptyList_throwsBusinessRule` |
| BR-L6 | Consumer idempotent | `paymentCompletedConsumer_sameEventTwice_marksPaidOnce` |
| BR-L7 | Từ chối chuyển trạng thái không hợp lệ | `changeStatus_completedToPending_throwsInvalidTransition` |

## 11. Điểm dễ sai

- `LAB_RESULT` là một phần của aggregate `LabTest` — dùng cascade + `orphanRemoval`, **không** tạo repository port riêng cho nó.
- `value` là `VARCHAR`, không phải số: giá trị xét nghiệm hoàn toàn có thể là `"<0.01"`, `"âm tính"`, `"3+"`. Đừng "sửa" nó thành kiểu số.
- `PROCESSED_EVENT` là bảng riêng của từng service. Chép cùng một bảng đó sang mọi service có tiêu thụ event (lab, pharmacy, billing, clinical, notification, report).
