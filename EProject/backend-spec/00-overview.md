# 00 — Tổng quan Backend (đọc trước mọi spec service)

Mọi thứ trong file này **giống hệt nhau ở cả tám service nghiệp vụ**. Các spec riêng từng service
mặc định bạn đã đọc file này và sẽ không nhắc lại.

## 1. Công nghệ

Java 21 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · PostgreSQL · RabbitMQ · Eureka · Maven multi-module.
Phiên bản được ghim ở `pom.xml` gốc — **module con không bao giờ khai báo version**.

## 2. Tọa độ từng module

| Module | Package | Cổng | Database | Tiền tố URL |
|--------|---------|------|----------|-------------|
| `organization-service` | `com.mediflow.organization` | 8089 | `mediflow_organization` | `/api/v1/org` |
| `patient-service` | `com.mediflow.patient` | 8081 | `mediflow_patient` | `/api/v1/patients` |
| `clinical-service` | `com.mediflow.clinical` | 8082 | `mediflow_clinical` | `/api/v1/appointments`, `/api/v1/records` |
| `lab-service` | `com.mediflow.lab` | 8084 | `mediflow_lab` | `/api/v1/lab` |
| `pharmacy-service` | `com.mediflow.pharmacy` | 8085 | `mediflow_pharmacy` | `/api/v1/pharmacy` |
| `billing-service` | `com.mediflow.billing` | 8086 | `mediflow_billing` | `/api/v1/billing` |
| `notification-service` | `com.mediflow.notification` | 8087 | `mediflow_notification` | `/api/v1/notifications` |
| `report-service` | `com.mediflow.report` | 8088 | `mediflow_report` | `/api/v1/reports` |
| `gateway` | `com.mediflow.gateway` | 8080 | — | định tuyến tất cả các mục trên |
| `eureka-server` | `com.mediflow.eureka` | 8761 | — | — |

Tất cả module đã tồn tại sẵn dưới dạng khung, với `pom.xml`, `application.yml`, class `Application`
và cây package đúng chuẩn. **Đừng tạo lại** — chỉ điền vào.

## 3. Cấu trúc package (bắt buộc)

Giải thích đầy đủ ở [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md).
Phụ thuộc chỉ hướng vào trong: `infrastructure → application → domain`.

```
com.mediflow.<svc>/
├── domain/model/           Java thuần. Bất biến nghiệp vụ nằm ở đây.
├── domain/exception/       kế thừa các lớp base trong common
├── application/port/in/    một interface cho mỗi use case
├── application/port/out/   những gì service cần từ bên ngoài
├── application/dto/request|response/   Java record
├── application/mapper/     MapStruct: domain model ↔ DTO
├── application/service/    hiện thực các in-port
└── infrastructure/
    ├── web/                @RestController + @RestControllerAdvice
    ├── persistence/        JpaEntity + JpaRepository + Mapper + Adapter
    ├── messaging/          publisher adapter, payload/, consumer/
    ├── client/             Feign + fallback
    ├── security/           JwtAuthFilter, JwtProperties
    └── config/             SecurityConfig, RabbitConfig, OpenApiConfig
```

**Import bị cấm** — kiểm tra trước khi tuyên bố service đã xong:
- dưới `domain/`: mọi `org.springframework.*`, `jakarta.persistence.*`, `jakarta.validation.*`
- dưới `application/`: `org.springframework.data.*`, `jakarta.persistence.*`, `org.springframework.amqp.*`, `org.springframework.web.*`

Ngoại lệ được phép trong `application/`: `@Service`, `@Transactional`, annotation của MapStruct, và
annotation `jakarta.validation` đặt trên các record DTO.

## 4. Kiểu dùng chung trong `common` (xây trước nếu còn thiếu)

`common` **thuần Java, không phụ thuộc gì cả**. Tuyệt đối không thêm starter của Spring vào đây.

Đã có sẵn:

```java
// com.mediflow.common.api
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp, String correlationId) {
    public static <T> ApiResponse<T> ok(T data);
    public static <T> ApiResponse<T> fail(ApiError error);
    public record ApiError(String code, String message, List<ErrorDetail> details) {
        public static ApiError of(String code, String message);
    }
    public record ErrorDetail(String field, String message) {}
}

// com.mediflow.common.exception  — mỗi lớp có (String code, String message) + getCode()
ResourceNotFoundException      // → HTTP 404
DuplicateResourceException     // → HTTP 409
BusinessRuleException          // → HTTP 422

// com.mediflow.common.security
Roles      // ADMIN, DOCTOR, NURSE, PHARMACIST, CASHIER, LAB_TECH, MANAGER, PATIENT, SYSTEM
JwtClaims  // hằng số tên claim
```

**Còn thiếu — tạo trong `common` trước khi làm bất kỳ endpoint phân trang nào:**

```java
package com.mediflow.common.api;

/** Yêu cầu phân trang không phụ thuộc framework. Tầng application không bao giờ được thấy Pageable của Spring. */
public record PageQuery(int page, int size) {
    public static final int MAX_SIZE = 100;
    public static PageQuery of(Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, MAX_SIZE);
        return new PageQuery(p, s);
    }
}

/** Serialize ra đúng hình dạng JSON như Page của Spring Data, nên hợp đồng với frontend không đổi. */
public record PageResult<T>(List<T> content, long totalElements, int totalPages, int number, int size) {
    public static <T> PageResult<T> of(List<T> content, long totalElements, int number, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResult<>(content, totalElements, totalPages, number, size);
    }
    public <R> PageResult<R> map(Function<T, R> fn) {
        return new PageResult<>(content.stream().map(fn).toList(), totalElements, totalPages, number, size);
    }
}
```

Persistence adapter chịu trách nhiệm chuyển `PageQuery → PageRequest` và `Page<Entity> → PageResult<DomainModel>`.

## 5. Quy tắc đặt tên song ngữ (phải làm đúng tuyệt đối)

| Tầng | Kiểu | Ví dụ |
|------|------|-------|
| Bảng DB | Tiếng Việt UPPER_SNAKE | `BENH_NHAN`, `LICH_HEN` |
| Cột DB | Tiếng Việt snake_case | `ma_benh_nhan`, `ho_ten` |
| Class Java | Tiếng Anh PascalCase | `Patient`, `PatientJpaEntity` |
| Field Java | Tiếng Việt camelCase | `maBenhNhan`, `hoTen` |
| Field JSON | giống hệt field Java | `maBenhNhan`, `hoTen` |
| Đường dẫn REST | Tiếng Anh, số nhiều, kebab | `/api/v1/patients` |
| Class event | Tiếng Anh + `Event` | `PatientCreatedEvent` |
| Routing key | dot.case | `patient.created` |

**Luôn luôn** viết `@Column(name = "...")` và `@Table(name = "...")` tường minh. Không bao giờ dựa vào
naming strategy — tên là tiếng Việt và không theo quy luật nào.

## 6. Quy ước DDL

Flyway, `src/main/resources/db/migration/V1__init.sql`, chỉ được thêm mới. Mọi bảng:

```sql
CREATE TABLE TEN_BANG (
    ma_...        UUID          PRIMARY KEY,
    ...           ...           ...,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);
```

- Khóa chính = `UUID`, sinh bởi Hibernate (`@GeneratedValue(strategy = GenerationType.UUID)`)
- Tiền = `DECIMAL(15,2)` ↔ `BigDecimal` với `@Column(precision = 15, scale = 2)`
- Enum = `VARCHAR(n)` + `@Enumerated(EnumType.STRING)` — **tuyệt đối không dùng ORDINAL**
- Ngày giờ: `DATE` ↔ `LocalDate`, `TIME` ↔ `LocalTime`, `TIMESTAMPTZ` ↔ `Instant`
- Tham chiếu sang service khác là **cột `UUID` trần** — không `REFERENCES`, không `@ManyToOne`
- Quan hệ trong cùng service thì được dùng khóa ngoại thật
- Đánh index mọi cột dùng trong `WHERE` hoặc dùng để tra cứu kiểu khóa ngoại

## 7. Mẫu JPA entity

```java
@Entity
@Table(name = "BENH_NHAN")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PatientJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ma_benh_nhan", updatable = false, nullable = false)
    private UUID maBenhNhan;

    @Column(name = "ho_ten", length = 100, nullable = false)
    private String hoTen;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private Instant updatedAt;
}
```

Không dùng `@Data` trên entity. Không đặt logic nghiệp vụ trong entity — chúng chỉ là vật chứa dữ liệu.

## 8. Mẫu domain model

Java thuần. Bất biến được kiểm tra trong static factory và trong các method hành vi, **không bao giờ**
trong setter.

```java
public class Patient {
    private final UUID maBenhNhan;
    private String hoTen;
    private final String soCmnd;      // bất biến sau khi tạo
    private final Instant createdAt;
    private final Instant updatedAt;

    private Patient(...) { ... }

    /** Aggregate mới. Id và timestamp do tầng persistence gán. */
    public static Patient taoMoi(String hoTen, LocalDate ngaySinh, ...) {
        if (hoTen == null || hoTen.isBlank())
            throw new InvalidPatientDataException("PATIENT_HOTEN_REQUIRED", "Họ tên không được để trống");
        if (ngaySinh.isAfter(LocalDate.now()))
            throw new InvalidPatientDataException("PATIENT_NGAYSINH_FUTURE", "Ngày sinh không được ở tương lai");
        return new Patient(null, hoTen, ...);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại các quy tắc lúc khởi tạo. */
    public static Patient khoiPhuc(UUID id, ..., Instant createdAt, Instant updatedAt) { ... }

    /** Hành vi thay đổi trạng thái. Trường tùy chọn nếu null thì giữ nguyên giá trị cũ. */
    public void capNhat(...) { ... }
}
```

## 9. Hợp đồng REST chuẩn

- Mọi response bọc trong `ApiResponse<T>`; `POST` trả **201**, `DELETE` trả **204**.
- Endpoint danh sách: `?page=0&size=20` (tối đa 100) cộng bộ lọc riêng, trả `PageResult<T>`.
- Mọi endpoint có `@PreAuthorize` với đúng danh sách role trong spec của nó. Mặc định là từ chối.
- Controller phải mỏng: validate, gọi đúng một in-port, trả về. Không logic nghiệp vụ, không đụng repository.

```java
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {
    private final CreatePatientUseCase createPatient;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<PatientDTO>> create(@Valid @RequestBody CreatePatientRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(createPatient.create(req)));
    }
}
```

## 10. Ánh xạ exception → HTTP

Mỗi service có đúng một `@RestControllerAdvice GlobalExceptionHandler`, đặt trong `infrastructure/web/`:

| Exception | Status |
|-----------|--------|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `BusinessRuleException` (và mọi domain exception kế thừa nó) | 422 |
| `MethodArgumentNotValidException` | 400, kèm danh sách `ErrorDetail` theo từng field, mã `VALIDATION_ERROR` |
| còn lại | 500, mã `INTERNAL_ERROR`, thông điệp `"Đã xảy ra lỗi hệ thống"` — không bao giờ lộ stack trace |

Mã lỗi viết `UPPER_SNAKE` và ổn định. Mỗi spec service liệt kê mã lỗi riêng của nó.

## 11. Messaging

- Một topic exchange bền: **`mediflow.events`**. Routing key = tên event viết dot.case.
- Mỗi service tiêu thụ khai báo queue bền riêng `<service>.q` bind vào các key nó cần, kèm DLX `mediflow.events.dlx` và `<service>.dlq`.
- JSON qua `Jackson2JsonMessageConverter`.
- Mọi payload event bắt đầu bằng cùng một bộ trường envelope:

```java
public record XxxCreatedEvent(
    UUID eventId,          // duy nhất — consumer dùng để khử trùng lặp
    Instant occurredAt,
    String correlationId,
    // ... các trường nghiệp vụ, bao gồm maKhoa trên mọi event vận hành
) {}
```

- **Publish sau khi commit.** Dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` hoặc outbox cho saga billing/pharmacy.
- Tầng application phụ thuộc vào out-port (`XxxEventPublisherPort`); chỉ `infrastructure/messaging/` được đụng `RabbitTemplate`.
- **Consumer bắt buộc idempotent** — khử trùng lặp theo `eventId`, hoặc làm cho hiệu ứng tự nó idempotent. Message bị gửi lại là chuyện bình thường.

## 12. Bảo mật (giống hệt nhau ở mọi service)

- `SecurityConfig`: stateless, tắt CSRF, `@EnableMethodSecurity`, cho phép `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`; còn lại đều phải xác thực.
- `JwtAuthFilter extends OncePerRequestFilter`: đọc `Authorization: Bearer`, xác minh HS256 bằng `mediflow.jwt.secret`, lấy claim `role`, đặt `UsernamePasswordAuthenticationToken` với quyền `ROLE_<role>`. Nếu lỗi thì để context trống — Spring sẽ trả 401/403.
- Gateway cũng đã kiểm tra JWT. Service vẫn phải kiểm lại: chúng truy cập được trực tiếp qua cổng riêng.
- Không bao giờ log token, mật khẩu, hay PII đầy đủ (CMND, BHYT, số điện thoại) ở mức INFO.

## 13. Gọi REST chéo service có khả năng chịu lỗi

Hiện chỉ `clinical-service` và `gateway` gọi service khác đồng bộ.

- Feign client đặt trong `infrastructure/client/`, gọi theo tên Eureka (`lb://patient-service`) — không bao giờ dùng host cứng.
- `connectTimeout: 2000`, `readTimeout: 3000`, bật circuit breaker (đã cấu hình sẵn trong `application.yml`).
- **Mọi client đều phải có fallback.** Downstream chết phải làm service này suy giảm, không được lan lỗi theo dây chuyền.
- Tầng application chỉ thấy out-port (ví dụ `PatientLookupPort`), không bao giờ thấy Feign.

## 14. Kiểm thử

| Tầng | Công cụ | Kiểm cái gì |
|------|---------|-------------|
| Domain unit | JUnit 5 + AssertJ | bất biến; **không Spring, không mock** |
| Application unit | + Mockito | use case; mock **out-port**, không bao giờ mock JPA repository |
| Web slice | `@WebMvcTest` | mã trạng thái, validation, kiểm role bằng `@WithMockUser` |
| Persistence slice | `@DataJpaTest` + Testcontainers | ánh xạ entity, câu truy vấn |
| Integration | `@SpringBootTest` + Testcontainers (PG + Rabbit) | request → DB → event |

Đặt tên: `methodName_condition_expectedResult`. **Mọi business rule trong spec đều phải có test.**
Mỗi spec service kèm sẵn bảng rule → test case; hiện thực đủ tất cả.

## 15. Definition of Done (áp dụng cho mọi service)

- [ ] `V1__init.sql` tạo đủ mọi bảng trong spec, đúng tiếng Việt snake_case
- [ ] Domain model đủ bất biến; domain exception
- [ ] In-port (mỗi use case một interface) + application service hiện thực chúng
- [ ] Out-port + adapter persistence/messaging/client
- [ ] Record DTO có Bean Validation; MapStruct mapper (domain↔DTO, domain↔entity)
- [ ] Controller với đúng danh sách role `@PreAuthorize` trong spec
- [ ] Event publish và subscribe đúng spec; consumer idempotent
- [ ] `GlobalExceptionHandler`, `SecurityConfig`, `RabbitConfig`, `OpenApiConfig`
- [ ] Test đủ năm tầng; mọi business rule đều được phủ
- [ ] **Đã kiểm tra quy tắc phụ thuộc** — không còn import bị cấm (§3)
- [ ] `mvn -pl <module> -am -q -DskipTests install` chạy thành công
