# 07 — notification-service

**Module** `notification-service` · **Package** `com.mediflow.notification` · **Cổng** 8087 · **DB** `mediflow_notification` · **Tiền tố** `/api/v1/notifications`

Gần như hoàn toàn hướng sự kiện: subscribe sáu routing key, lưu bản ghi thông báo, thử gửi, rồi ghi
lại kết quả. Nó không sở hữu dữ liệu nghiệp vụ cốt lõi nào.

Đây **không phải một khoa phòng** — nó là kênh kỹ thuật. Đừng trình bày nó như một phòng ban.

## 1. Lược đồ — `V1__init.sql`

```sql
CREATE TABLE THONG_BAO (
    ma_thong_bao   UUID          PRIMARY KEY,
    ma_benh_nhan   UUID          NOT NULL,     -- tham chiếu patient-service
    tieu_de        VARCHAR(255)  NOT NULL,
    noi_dung       TEXT          NOT NULL,
    loai           VARCHAR(10)   NOT NULL,     -- EMAIL | SMS | IN_APP
    dia_chi_nhan   VARCHAR(150),               -- email hoặc số điện thoại thực tế đã dùng
    trang_thai     VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    ly_do_that_bai VARCHAR(255),
    so_lan_thu     INT           NOT NULL DEFAULT 0,
    ngay_tao       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ngay_gui       TIMESTAMPTZ
);
CREATE INDEX idx_thong_bao_benh_nhan ON THONG_BAO (ma_benh_nhan, ngay_tao DESC);
CREATE INDEX idx_thong_bao_trang_thai ON THONG_BAO (trang_thai);

CREATE TABLE SU_KIEN_DA_XU_LY (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    xu_ly_luc    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

## 2. Enum

```java
public enum LoaiThongBao      { EMAIL, SMS, IN_APP }
public enum TrangThaiThongBao { PENDING, SENT, FAILED }
```

## 3. Domain model — `ThongBao`

Các trường: `maThongBao`, `maBenhNhan`, `tieuDe`, `noiDung`, `loai`, `diaChiNhan`, `trangThai`,
`lyDoThatBai`, `soLanThu`, `ngayTao`, `ngayGui`.

```java
public static ThongBao taoMoi(UUID maBenhNhan, String tieuDe, String noiDung,
                              LoaiThongBao loai, String diaChiNhan);
public void danhDauDaGui(Instant thoiDiem);
public void danhDauThatBai(String lyDo);
public boolean coTheGui();      // trangThai == PENDING

/** BR-N1 / BR-N2 — một kênh chỉ dùng được khi địa chỉ của nó hợp lệ. */
public static boolean emailHopLe(String email);   // regex kiểu RFC đơn giản
public static boolean sdtHopLe(String sdt);       // ^0\d{9,10}$
```

Bất biến: `tieuDe` và `noiDung` không rỗng; `loai` không null; với `EMAIL` thì địa chỉ phải thỏa
`emailHopLe`, với `SMS` thì phải thỏa `sdtHopLe` — nếu không thì ném `NOTIFICATION_ADDRESS_INVALID`.
`IN_APP` không cần địa chỉ.

Chuyển tiếp: `PENDING → SENT | FAILED`, cả hai đều kết thúc. Gửi lại một thông báo đã kết thúc →
`NOTIFICATION_ALREADY_FINALISED`.

## 4. Mã lỗi

`NOTIFICATION_NOT_FOUND` → 404 · `NOTIFICATION_ACCESS_DENIED` → 403 ·
`NOTIFICATION_ADDRESS_INVALID`, `NOTIFICATION_ALREADY_FINALISED` → 422.

## 5. Port

```java
// out
public interface ThongBaoRepositoryPort {
    ThongBao save(ThongBao tb);
    Optional<ThongBao> findById(UUID id);
    PageResult<ThongBao> findByPatient(UUID maBenhNhan, PageQuery page);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }

/** Mỗi kênh một bản hiện thực, chọn theo LoaiThongBao. */
public interface NotificationSenderPort {
    LoaiThongBao kenh();
    /** @return rỗng nếu thành công, hoặc lý do thất bại. Không bao giờ ném lỗi khi gửi thất bại. */
    Optional<String> gui(ThongBao tb);
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
    PageResult<NotificationDTO> byPatient(UUID maBenhNhan, PageQuery page);
}
```

`NotificationTrigger` là một record nội bộ do consumer dựng, để use case không bao giờ thấy kiểu của AMQP:

```java
public record NotificationTrigger(UUID eventId, String routingKey, UUID maBenhNhan,
                                  String tieuDe, String noiDung,
                                  String email, String sdt) {}
```

## 6. DTO

```java
public record SendNotificationRequest(
    @NotNull UUID maBenhNhan,
    @NotBlank @Size(max = 255) String tieuDe,
    @NotBlank String noiDung,
    @NotNull LoaiThongBao loai,
    @Size(max = 150) String diaChiNhan) {}

public record NotificationDTO(UUID maThongBao, UUID maBenhNhan, String tieuDe, String noiDung,
                              LoaiThongBao loai, TrangThaiThongBao trangThai, String lyDoThatBai,
                              Instant ngayTao, Instant ngayGui) {}
```

Không bao giờ lộ `diaChiNhan` hay `soLanThu` — chúng là PII / dữ liệu nội bộ.

## 7. Luồng xử lý

Với mọi event được tiêu thụ:

1. khử trùng lặp theo `eventId` → đã xử lý thì return
2. dựng nội dung từ **template** (xem §8)
3. chọn kênh: `EMAIL` nếu bệnh nhân có email hợp lệ, không thì `SMS` nếu số điện thoại hợp lệ, không nữa thì `IN_APP`
4. `ThongBao.taoMoi(...)` → lưu với trạng thái `PENDING`
5. `sender.gui(tb)` → rỗng thì `danhDauDaGui(now)`, ngược lại `danhDauThatBai(reason)`
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
if (!isStaff && !notification.getMaBenhNhan().equals(callerPatientId))
    throw new AccessDeniedDomainException("NOTIFICATION_ACCESS_DENIED", "Không có quyền xem thông báo này");
```

Id bệnh nhân của người gọi lấy từ claim `sub`/claim riêng trong JWT, **không bao giờ** lấy từ tham số
request.

## 10. Event

**Publish:** `notification.sent` — `{envelope, notificationId, patientId, type, status}`

**Subscribe** — queue `notification.q`, bind vào sáu key:

`patient.created` · `appointment.created` · `lab.result.created` · `prescription.filled` ·
`payment.completed` · `payment.failed`

Các event mang theo id chứ không mang thông tin liên hệ. V1: đưa `email`/`sdt` vào
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

## 12. Điểm dễ sai

- `spring-boot-starter-mail` đã có trong POM nhưng nằm im cho tới khi cấu hình `spring.mail.*`. Hãy làm sẵn một `MockEmailSender` (ghi log, luôn thành công) làm mặc định để service chạy được khi không có SMTP; sau này cắm `JavaMailSender` thật vào sau cùng một port.
- Không có nhà cung cấp SMS nào cả. `MockSmsSender` ghi log và trả thành công. Đừng bịa ra một tích hợp không tồn tại.
- Bind **một** queue vào sáu routing key thay vì tạo sáu queue. Một class consumer với `switch` theo routing key giúp logic khử trùng lặp nằm gọn một chỗ.
- Không bao giờ log `noi_dung` ở mức INFO — nó có thể chứa gợi ý về chẩn đoán.
