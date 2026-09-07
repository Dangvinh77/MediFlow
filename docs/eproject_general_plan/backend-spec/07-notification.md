# 07 — notification-service

**Module** `notification-service` · **Package** `com.mediflow.notification` · **Cổng** 8087 · **DB** `mediflow_notification` · **Tiền tố** `/api/v1/notifications`

Gần như hoàn toàn hướng sự kiện: subscribe sáu routing key, lưu bản ghi thông báo, thử gửi, rồi ghi
lại kết quả. Nó không sở hữu dữ liệu nghiệp vụ cốt lõi nào.

Đây **không phải một khoa phòng** — nó là kênh kỹ thuật. Đừng trình bày nó như một phòng ban.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE NOTIFICATION (
    notification_id    UUID          PRIMARY KEY,
    patient_id         UUID          NOT NULL,     -- tham chiếu logic patient-service
    title              VARCHAR(255)  NOT NULL,
    content            TEXT          NOT NULL,
    channel            VARCHAR(10)   NOT NULL,     -- EMAIL | SMS | IN_APP
    recipient_address  VARCHAR(150),               -- email hoặc số điện thoại thực tế đã dùng
    status             VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    failure_reason     VARCHAR(255),
    retry_count        INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at            TIMESTAMPTZ
);
CREATE INDEX idx_notification_patient ON NOTIFICATION (patient_id, created_at DESC);
CREATE INDEX idx_notification_status ON NOTIFICATION (status);

CREATE TABLE PROCESSED_EVENT (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

## 2. Enum

```java
public enum NotificationChannel { EMAIL, SMS, IN_APP }
public enum NotificationStatus  { PENDING, SENT, FAILED }
```

## 3. Domain model — `Notification`

Các trường: `notificationId`, `patientId`, `title`, `content`, `channel`, `recipientAddress`,
`status`, `failureReason`, `retryCount`, `createdAt`, `sentAt`.

> **Tên class/field dùng tiếng Anh**, áp dụng thống nhất cho toàn bộ service (không chỉ nhánh C
> pharmacy/billing/report — đây là chuẩn hoá riêng cho notification, chốt sau khi đối chiếu quy tắc
> đặt tên cao nhất của nhóm là toàn bộ tiếng Anh). Bản cũ dùng tên tiếng Việt (`ThongBao`,
> `maThongBao`, `tieuDe`...) — đã đổi hết, không dùng nữa. Message lỗi hiển thị cho người dùng vẫn
> giữ tiếng Việt.

```java
public static Notification create(UUID patientId, String title, String content,
                                   NotificationChannel channel, String recipientAddress);
public void markSent(Instant at);
public void markFailed(String reason);
public boolean canSend();      // status == PENDING

/** BR-N1 / BR-N2 — một kênh chỉ dùng được khi địa chỉ của nó hợp lệ. */
public static boolean isValidEmail(String email);   // regex kiểu RFC đơn giản
public static boolean isValidPhone(String phone);    // ^0\d{9,10}$
```

Bất biến: `title` và `content` không rỗng; `channel` không null; với `EMAIL` thì địa chỉ phải thỏa
`isValidEmail`, với `SMS` thì phải thỏa `isValidPhone` — nếu không thì ném `NOTIFICATION_ADDRESS_INVALID`.
`IN_APP` không cần địa chỉ.

Chuyển tiếp: `PENDING → SENT | FAILED`, cả hai đều kết thúc. Gửi lại một thông báo đã kết thúc →
`NOTIFICATION_ALREADY_FINALISED`.

## 4. Mã lỗi

`NOTIFICATION_NOT_FOUND` → 404 · `NOTIFICATION_ACCESS_DENIED` → 403 ·
`NOTIFICATION_ADDRESS_INVALID`, `NOTIFICATION_ALREADY_FINALISED` → 422.

## 5. Port

```java
// out
public interface NotificationRepositoryPort {
    Notification save(Notification n);
    Optional<Notification> findById(UUID id);
    PageResult<Notification> findByPatient(UUID patientId, PageQuery page);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }

/** Mỗi kênh một bản hiện thực, chọn theo NotificationChannel. */
public interface NotificationSenderPort {
    NotificationChannel channel();
    /** @return rỗng nếu thành công, hoặc lý do thất bại. Không bao giờ ném lỗi khi gửi thất bại. */
    Optional<String> send(Notification n);
}

public interface NotificationEventPublisherPort {
    void publishSent(NotificationSentEvent e);
}

// in
public interface SendNotificationUseCase {
    NotificationDTO send(SendNotificationRequest r);
    void handleEvent(NotificationTrigger trigger);   // do các consumer gọi
}
public interface ReadNotificationUseCase {
    NotificationDTO getById(UUID id, UUID callerPatientId, boolean isStaff);
    PageResult<NotificationDTO> byPatient(UUID patientId, PageQuery page);
}
```

`NotificationTrigger` là một record nội bộ do consumer dựng, để use case không bao giờ thấy kiểu của AMQP:

```java
public record NotificationTrigger(UUID eventId, String routingKey, UUID patientId,
                                  String title, String content,
                                  String email, String phone) {}
```

## 6. DTO

```java
public record SendNotificationRequest(
    @NotNull UUID patientId,
    @NotBlank @Size(max = 255) String title,
    @NotBlank String content,
    @NotNull NotificationChannel channel,
    @Size(max = 150) String recipientAddress) {}

public record NotificationDTO(UUID notificationId, UUID patientId, String title, String content,
                              NotificationChannel channel, NotificationStatus status, String failureReason,
                              Instant createdAt, Instant sentAt) {}
```

Không bao giờ lộ `recipientAddress` hay `retryCount` — chúng là PII / dữ liệu nội bộ.

## 7. Luồng xử lý

Với mọi event được tiêu thụ:

1. khử trùng lặp theo `eventId` → đã xử lý thì return
2. dựng nội dung từ **template** (xem §8)
3. chọn kênh theo thứ tự ưu tiên: `EMAIL` nếu bệnh nhân có email hợp lệ, không thì `SMS` nếu
   số điện thoại hợp lệ, không nữa thì `IN_APP` (BR-N9)
4. `Notification.create(...)` → lưu với trạng thái `PENDING`
5. `sender.send(n)` → rỗng thì `markSent(now)`, ngược lại `markFailed(reason)`
6. lưu; đánh dấu event đã xử lý **trong cùng transaction với bước 4**
7. publish `notification.sent` kèm trạng thái cuối cùng

> Lưu `PENDING` **trước khi** thử gửi. Nếu tiến trình chết giữa chừng bạn vẫn còn bản ghi; nếu chỉ
> lưu sau khi gửi xong thì thông báo biến mất không dấu vết.

Bộ gửi không được ném lỗi khi gửi thất bại — một email gửi hỏng là kết quả nghiệp vụ (`FAILED`),
không phải lỗi hạ tầng. Chỉ khi bản thân đường truyền hỏng mới cho message vào dead-letter.

## 8. Template

Đặt tất cả trong một class, `application/service/NotificationTemplates`, tiếng Việt, không chứa PII
ngoài tên:

| Sự kiện | Tiêu đề | Nội dung |
|---------|---------|----------|
| `patient.created` | `Chào mừng đến với MediFlow` | `Xin chào {hoTen}, hồ sơ của bạn đã được tạo.` |
| `appointment.created` | `Nhắc lịch khám` | `Bạn có lịch khám ngày {ngayHen} lúc {gioHen}.` |
| `lab.result.created` | `Kết quả xét nghiệm đã có` | `Kết quả {loaiXn} của bạn đã sẵn sàng.` |
| `prescription.filled` | `Thuốc đã sẵn sàng` | `Đơn thuốc của bạn đã được chuẩn bị xong.` |
| `payment.completed` | `Thanh toán thành công` | `Hóa đơn {maHoaDon} đã được thanh toán: {tongTien} VNĐ.` |
| `payment.failed` | `Thanh toán không thành công` | `Hóa đơn {maHoaDon} gặp sự cố: {reason}.` |

## 9. Endpoint

| Method | Path | Body | Trả về | Role |
|--------|------|------|--------|------|
| GET | `/api/v1/notifications/patient/{patientId}?page&size` | — | `PageResult<NotificationDTO>` | ADMIN, NURSE, PATIENT |
| GET | `/api/v1/notifications/{id}` | — | `NotificationDTO` | ADMIN, PATIENT |
| POST | `/api/v1/notifications/send` | `SendNotificationRequest` | 201 | ADMIN, SYSTEM |

### Kiểm tra quyền sở hữu — không phải tùy chọn

`PATIENT` chỉ được đọc thông báo **của chính mình**. `@PreAuthorize` không diễn đạt được điều này;
phải chặn ở tầng service:

```java
if (!isStaff && !notification.getPatientId().equals(callerPatientId))
    throw new NotificationAccessDeniedException("Không có quyền xem thông báo này");
```

Id bệnh nhân của người gọi lấy từ claim `sub`/claim riêng trong JWT, **không bao giờ** lấy từ tham số
request.

## 10. Event

**Publish:** `notification.sent` — `{envelope, notificationId, patientId, channel, status}`

**Subscribe** — queue `notification.q`, bind vào sáu key:

`patient.created` · `appointment.created` · `lab.result.created` · `prescription.filled` ·
`payment.completed` · `payment.failed`

Các event mang theo id chứ không mang thông tin liên hệ. V1: đưa `email`/`phone` vào
`patient.created` và `patient.updated` (chúng đã có sẵn) rồi giữ một bảng chiếu nhỏ ở local,
**hoặc** chấp nhận chỉ dùng `IN_APP` cho các sự kiện còn lại. **Đừng** gọi REST sang patient-service
từ trong consumer — làm vậy là biến một luồng bất đồng bộ thành phụ thuộc đồng bộ.

## 11. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-N1 | Kênh email cần địa chỉ hợp lệ | `send_emailChannelInvalidAddress_throwsBusinessRule` |
| BR-N2 | Kênh SMS cần số 10–11 chữ số hợp lệ | `send_smsChannelBadPhone_throwsBusinessRule` |
| BR-N3 | Lưu `PENDING` trước khi gửi | `handleEvent_persistsPendingBeforeSend` |
| BR-N4 | Gửi thất bại thì ghi `FAILED` + lý do, không ném lỗi | `handleEvent_senderFails_marksFailedNoThrow` |
| BR-N5 | Consumer idempotent | `handleEvent_sameEventTwice_createsOneNotification` |
| BR-N6 | `PATIENT` không đọc được thông báo của người khác | `getById_otherPatient_throwsAccessDenied` |
| BR-N7 | Thông báo đã kết thúc không gửi lại được | `send_alreadySent_throwsAlreadyFinalised` |
| BR-N8 | `notification.sent` publish kèm trạng thái cuối | `handleEvent_publishesSentWithStatus` |
| BR-N9 | Chọn kênh gửi theo thứ tự ưu tiên EMAIL → SMS → IN_APP | `handleEvent_choosesChannelByPriorityEmailSmsThenInApp` |

## 12. Điểm dễ sai

- `spring-boot-starter-mail` đã có trong POM nhưng nằm im cho tới khi cấu hình `spring.mail.*`. Hãy làm sẵn một `MockEmailSender` (ghi log, luôn thành công) làm mặc định để service chạy được khi không có SMTP; sau này cắm `JavaMailSender` thật vào sau cùng một port.
- Không có nhà cung cấp SMS nào cả. `MockSmsSender` ghi log và trả thành công. Đừng bịa ra một tích hợp không tồn tại.
- Bind **một** queue vào sáu routing key thay vì tạo sáu queue. Một class consumer với `switch` theo routing key giúp logic khử trùng lặp nằm gọn một chỗ.
- Không bao giờ log cột `content` ở mức INFO — nó có thể chứa gợi ý về chẩn đoán.

---

## 13. Coding map (chỉ dẫn hiện thực — bổ sung cho spec)

> Mục này dành cho coder: file nào tạo, nội dung gì, đặt ở đâu. Spec là **nơi** (bounded context),
> mục này là **cách** (cây file cụ thể). Kết hợp với boilerplate chuẩn ở `docs/ai/reference/`.

### 13.1 Bản đồ file Java (mọi file cần tạo)

Base package `com.mediflow.notification`. Cây đầy đủ:

```
notification-service/src/main/java/com/mediflow/notification/
├── NotificationServiceApplication.java                  # có sẵn
├── domain/model/
│   ├── NotificationChannel.java                         # enum { EMAIL, SMS, IN_APP }
│   ├── NotificationStatus.java                          # enum { PENDING, SENT, FAILED }
│   └── Notification.java                                # aggregate; isValidEmail/isValidPhone (BR-N1/N2), canSend (BR-N7)
├── domain/exception/
│   ├── NotificationNotFoundException.java               # 404, code NOTIFICATION_NOT_FOUND
│   ├── NotificationAddressInvalidException.java         # 422, code NOTIFICATION_ADDRESS_INVALID (BR-N1/N2)
│   ├── NotificationAlreadyFinalisedException.java       # 422, code NOTIFICATION_ALREADY_FINALISED (BR-N7)
│   └── NotificationAccessDeniedException.java           # 403 — map riêng trong GlobalExceptionHandler (BR-N6, xem §9)
├── application/port/in/
│   ├── SendNotificationUseCase.java                     # send(request) + handleEvent(trigger)
│   └── ReadNotificationUseCase.java                     # getById(id, callerPatientId, isStaff), byPatient
├── application/port/out/
│   ├── NotificationRepositoryPort.java
│   ├── ProcessedEventPort.java
│   ├── NotificationSenderPort.java                      # channel() + send() — không bao giờ throw khi gửi thất bại
│   └── NotificationEventPublisherPort.java
├── application/dto/request/
│   └── SendNotificationRequest.java
├── application/dto/response/
│   └── NotificationDTO.java                             # KHÔNG có recipientAddress/retryCount (PII, §6)
├── application/mapper/
│   └── NotificationDtoMapper.java                       # MapStruct: Notification ↔ NotificationDTO
├── application/service/
│   ├── NotificationApplicationService.java              # hiện thực Send + Read; luồng 7 bước §7 (chọn kênh = BR-N9)
│   └── NotificationTemplates.java                       # kho mẫu 6 sự kiện, §8
├── web/                                  # DRIVING adapter (HTTP) — gọi vào application
│   ├── NotificationController.java                      # 3 endpoint §9
│   └── GlobalExceptionHandler.java                      # copy từ docs/ai/reference + thêm handler riêng cho AccessDenied → 403
├── messaging/consumer/                   # DRIVING adapter (events) — gọi vào application
│   └── NotificationEventConsumer.java                   # 1 class, @RabbitListener("notification.q"), switch theo routing key (6 nhánh)
├── infrastructure/persistence/           # DRIVEN adapter (DB) — hiện thực port out
│   ├── NotificationJpaEntity.java
│   ├── NotificationJpaRepository.java
│   ├── NotificationPersistenceAdapter.java
│   ├── NotificationPersistenceMapper.java
│   ├── ProcessedEventJpaEntity.java
│   ├── ProcessedEventJpaRepository.java
│   └── ProcessedEventPersistenceAdapter.java            # hiện thực ProcessedEventPort (bảng PROCESSED_EVENT)
├── infrastructure/messaging/             # DRIVEN adapter (RabbitMQ) — publisher + payload
│   ├── NotificationEventPublisherAdapter.java           # hiện thực NotificationEventPublisherPort
│   └── payload/
│       └── NotificationSentEvent.java
├── infrastructure/channel/               # DRIVEN adapter — hiện thực NotificationSenderPort
│   │                                       (thư mục KHÔNG có sẵn trong khung chuẩn §3 của 00-overview.md —
│   │                                        tự thêm vì "kênh gửi" không khớp persistence/messaging/client;
│   │                                        cùng tinh thần với `PriceListAdapter` bên billing: 1 out-port,
│   │                                        nhiều adapter, đổi sau này không đụng application/domain)
│   ├── MockEmailSender.java                             # channel() = EMAIL, log + luôn Optional.empty() (thành công)
│   ├── MockSmsSender.java                                # channel() = SMS, log + luôn thành công (chưa có nhà cung cấp SMS thật)
│   └── InAppSender.java                                  # channel() = IN_APP, thành công ngay (đã lưu DB là đủ, không gọi ra ngoài)
└── infrastructure/                        # phần còn lại
    ├── security/    JwtAuthFilter.java, JwtProperties.java
    └── config/      SecurityConfig.java, RabbitConfig.java, OpenApiConfig.java, MailConfig.java (tùy chọn, xem §12)
```

Các file boilerplate cần copy từ `docs/ai/reference/` hoặc một service đã có (giống billing/pharmacy §13.1):
`infrastructure/config/RabbitConfig.java`, `security/JwtAuthFilter.java`, `security/JwtProperties.java`,
`config/SecurityConfig.java`, `config/OpenApiConfig.java`, `db/migration/V1__init.sql`.

> **Clean Architecture (hexagonal):** `web/` + `messaging/consumer/` là hai *driving adapter* (cổng gọi
> *vào* application). Controller/consumer **chỉ gọi in-port**, không bao giờ import persistence hay
> publisher trực tiếp. Xem `docs/ai/04-microservice-blueprint.md`.

### 13.2 Event payload — dạng Java record

```java
// NotificationTrigger — record NỘI BỘ do consumer dựng, use case không bao giờ thấy kiểu AMQP
public record NotificationTrigger(UUID eventId, String routingKey, UUID patientId,
                                  String title, String content,
                                  String email, String phone) {}

// NotificationSentEvent — routing key "notification.sent", publish sau khi cập nhật trạng thái cuối
public record NotificationSentEvent(
    UUID eventId, Instant occurredAt, String correlationId,
    UUID notificationId, UUID patientId, NotificationChannel channel, NotificationStatus status) {}
```

> 6 event tiêu thụ (`patient.created`, `appointment.created`, `lab.result.created`,
> `prescription.filled`, `payment.completed`, `payment.failed`) được khai báo lại làm record trong
> chính `notification-service` (contract qua JSON, không phải import Java) — giống cách billing làm
> ở §12.1: mỗi service tự định nghĩa event nó tiêu thụ.

### 13.3 Chi tiết persistence

**`NotificationJpaRepository`:**

```java
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {
    Page<NotificationJpaEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
```

**Ánh xạ entity ↔ domain (MapStruct):**

| Entity field | Domain field | Kiểu |
|---|---|---|
| `notification_id` | `notificationId` | UUID |
| `patient_id` | `patientId` | UUID |
| `title` | `title` | String |
| `content` | `content` | String |
| `channel` | `channel` | NotificationChannel |
| `recipient_address` | `recipientAddress` | String (nullable) |
| `status` | `status` | NotificationStatus |
| `failure_reason` | `failureReason` | String (nullable) |
| `retry_count` | `retryCount` | int |
| `created_at` | `createdAt` | Instant |
| `sent_at` | `sentAt` | Instant (nullable) |

> Tên field entity và domain nay trùng nhau (cả hai đều tiếng Anh) — MapStruct tự ánh xạ theo tên,
> không cần khai `@Mapping` tường minh cho từng trường như khi field còn khác ngôn ngữ.

**Adapter cần làm đúng:**
1. `findByPatientIdOrderByCreatedAtDesc` — dùng cho `ReadNotificationUseCase.byPatient`, ánh xạ truy vấn vào index `idx_notification_patient`.
2. `NotificationDtoMapper` — **loại bỏ** tường minh `recipientAddress`/`retryCount` khỏi `NotificationDTO` (không phải MapStruct tự bỏ, phải khai báo `@Mapping(target = "...", ignore = true)` nếu field trùng tên mà không muốn map, hoặc đơn giản là DTO không có field đó nên MapStruct tự bỏ qua — kiểm tra lại DTO record).
3. `MockEmailSender`/`MockSmsSender`/`InAppSender` implement `NotificationSenderPort`; Spring tự inject cả ba vào `NotificationApplicationService` dưới dạng `List<NotificationSenderPort>` hoặc `Map<NotificationChannel, NotificationSenderPort>` — chọn đúng theo `channel` khi gọi `send()` (BR-N9 chọn kênh nằm ở tầng application, **không** phải trong sender).

### 13.4 RabbitConfig — hằng số & binding (notification)

```
EXCHANGE = "mediflow.events"        (durable topic)
DLX      = "mediflow.events.dlx"
QUEUE    = "notification.q"         (durable, DLX + DLQ "notification.dlq")
```

| Routing key | Hướng | Dùng cho |
|---|---|---|
| `notification.sent` | publish | ghi log trạng thái gửi cuối cùng |
| `patient.created` | **subscribe** | chào mừng bệnh nhân mới |
| `appointment.created` | **subscribe** | nhắc lịch khám |
| `lab.result.created` | **subscribe** | báo có kết quả xét nghiệm |
| `prescription.filled` | **subscribe** | báo thuốc đã sẵn sàng |
| `payment.completed` | **subscribe** | xác nhận thanh toán |
| `payment.failed` | **subscribe** | báo lỗi thanh toán (nhánh bù trừ của billing) |

Queue `notification.q` bind **6 routing key** (bỏ `notification.sent` vì là publish) — **một** queue,
**một** class consumer với `switch`, không tạo sáu queue riêng.

### 13.5 Test plan — map business rule → tầng + tên test

| Rule | Tầng | Tên test | Cần gì |
|---|---|---|---|
| BR-N1 (EMAIL cần địa chỉ hợp lệ) | domain unit | `send_emailChannelInvalidAddress_throwsBusinessRule` | không Spring, test `Notification.isValidEmail` |
| BR-N2 (SMS cần số hợp lệ) | domain unit | `send_smsChannelBadPhone_throwsBusinessRule` | không Spring, test `Notification.isValidPhone` |
| BR-N3 (lưu PENDING trước khi gửi) | application (mock port) | `handleEvent_persistsPendingBeforeSend` | mock `NotificationRepositoryPort`, verify thứ tự gọi |
| BR-N4 (gửi thất bại → FAILED, không throw) | application | `handleEvent_senderFails_marksFailedNoThrow` | mock `NotificationSenderPort` trả `Optional.of(reason)` |
| BR-N5 (consumer idempotent) | application | `handleEvent_sameEventTwice_createsOneNotification` | mock `ProcessedEventPort.alreadyProcessed` |
| BR-N6 (PATIENT không đọc thông báo người khác) | application | `getById_otherPatient_throwsAccessDenied` | gọi `getById` với `isStaff=false`, `callerPatientId` khác chủ |
| BR-N7 (không gửi lại thông báo đã kết thúc) | domain unit | `send_alreadySent_throwsAlreadyFinalised` | test `Notification.canSend()` |
| BR-N8 (`notification.sent` publish kèm trạng thái cuối) | application | `handleEvent_publishesSentWithStatus` | mock `NotificationEventPublisherPort`, verify payload |
| BR-N9 (chọn kênh EMAIL → SMS → IN_APP) | application | `handleEvent_choosesChannelByPriorityEmailSmsThenInApp` | 3 case: có email hợp lệ / chỉ có sđt hợp lệ / không có cả hai |

### 13.6 Checklist hoàn thiện notification (Definition of Done)

- [ ] `V1__init.sql` tạo đủ 2 bảng (`NOTIFICATION`, `PROCESSED_EVENT`), với tên bảng/cột tiếng Anh đúng quy ước.
- [ ] Domain: 2 enum + 1 model (`Notification`) + 4 exception; `isValidEmail`/`isValidPhone`/`canSend` không phụ thuộc Spring.
- [ ] Ports đủ (4 out, 2 in); application service hiện thực, không import Spring Data/AMQP.
- [ ] DTO record có Bean Validation; 1 MapStruct mapper — xác nhận `recipientAddress`/`retryCount` không lộ ra `NotificationDTO`.
- [ ] Controller với đúng danh sách role `@PreAuthorize` (ADMIN/NURSE/PATIENT/SYSTEM theo từng endpoint, §9); kiểm tra quyền sở hữu thủ công cho `PATIENT` (BR-N6).
- [ ] `GlobalExceptionHandler` map đủ 403 cho `NotificationAccessDeniedException` (ngoài bảng chuẩn ở `00-overview.md` §10).
- [ ] 1 event publish (`notification.sent`) + 1 consumer bind 6 routing key, idempotent theo `eventId`.
- [ ] 3 adapter kênh (`MockEmailSender`, `MockSmsSender`, `InAppSender`) trong `infrastructure/channel/`.
- [ ] `GlobalExceptionHandler`, `SecurityConfig`, `RabbitConfig`, `OpenApiConfig`.
- [ ] Test 5 tầng; **9 business rule** (BR-N1 → BR-N9) được phủ.
- [ ] `mvn -pl backend/notification-service -am -q -DskipTests install` chạy thành công.
