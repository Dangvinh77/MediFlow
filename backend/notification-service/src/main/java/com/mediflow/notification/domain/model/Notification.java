package com.mediflow.notification.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.mediflow.notification.domain.exception.NotificationAddressInvalidException;
import com.mediflow.notification.domain.exception.NotificationAlreadyFinalisedException;

import lombok.Getter;

/**
 * Một thông báo gửi tới bệnh nhân qua email/SMS/trong ứng dụng (aggregate). Service này không sở
 * hữu dữ liệu nghiệp vụ cốt lõi nào — đây thuần túy là bản ghi kết quả gửi.
 *
 * <p>Tên class/field dùng tiếng Anh theo quyết định đặt tên mới nhất của nhóm (áp dụng thống nhất
 * cho toàn bộ service, không chỉ nhánh C) — khác với bản gốc {@code ThongBao} ở
 * {@code backend-spec/07-notification.md}; message lỗi hiển thị cho người dùng vẫn giữ tiếng Việt.
 */
@Getter
public class Notification {

    // Regex kiểu RFC đơn giản — đủ dùng để chặn địa chỉ rõ ràng sai, không nhằm xác thực RFC 5322 đầy đủ.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9,10}$");

    private final UUID notificationId;
    private final UUID patientId;
    private final String title;
    private final String content;
    private final NotificationChannel channel;
    private final String recipientAddress;
    private NotificationStatus status;
    private String failureReason;
    private int retryCount;
    private final Instant createdAt;
    private Instant sentAt;

    private Notification(UUID notificationId, UUID patientId, String title, String content,
                          NotificationChannel channel, String recipientAddress, NotificationStatus status,
                          String failureReason, int retryCount, Instant createdAt, Instant sentAt) {
        this.notificationId = notificationId;
        this.patientId = patientId;
        this.title = title;
        this.content = content;
        this.channel = channel;
        this.recipientAddress = recipientAddress;
        this.status = status;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public static Notification create(UUID patientId, String title, String content,
                                       NotificationChannel channel, String recipientAddress) {
        // Bảo vệ nội bộ: tiêu đề/nội dung/kênh là bắt buộc cho mọi lối tạo, kể cả từ consumer sự
        // kiện (NotificationTemplates) không đi qua Bean Validation của SendNotificationRequest.
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Tiêu đề thông báo không được để trống");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung thông báo không được để trống");
        }
        if (channel == null) {
            throw new IllegalArgumentException("Phải xác định kênh gửi thông báo");
        }
        // BR-N1/BR-N2: EMAIL/SMS bắt buộc địa chỉ hợp lệ theo đúng kênh; IN_APP không cần địa chỉ.
        if (channel == NotificationChannel.EMAIL && !isValidEmail(recipientAddress)) {
            throw new NotificationAddressInvalidException("Địa chỉ email không hợp lệ: " + recipientAddress);
        }
        if (channel == NotificationChannel.SMS && !isValidPhone(recipientAddress)) {
            throw new NotificationAddressInvalidException("Số điện thoại không hợp lệ: " + recipientAddress);
        }
        return new Notification(null, patientId, title, content, channel, recipientAddress,
                NotificationStatus.PENDING, null, 0, null, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static Notification restore(UUID notificationId, UUID patientId, String title, String content,
                                        NotificationChannel channel, String recipientAddress,
                                        NotificationStatus status, String failureReason, int retryCount,
                                        Instant createdAt, Instant sentAt) {
        return new Notification(notificationId, patientId, title, content, channel, recipientAddress,
                status, failureReason, retryCount, createdAt, sentAt);
    }

    /** Đánh dấu gửi thành công (BR-N3/BR-N4). Không cho gửi lại thông báo đã kết thúc (BR-N7). */
    public void markSent(Instant at) {
        if (!canSend()) {
            throw new NotificationAlreadyFinalisedException("Thông báo đã kết thúc, không thể gửi lại");
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.retryCount++;
    }

    /** Đánh dấu gửi thất bại — kết quả nghiệp vụ, không phải lỗi hạ tầng (BR-N4). */
    public void markFailed(String reason) {
        if (!canSend()) {
            throw new NotificationAlreadyFinalisedException("Thông báo đã kết thúc, không thể gửi lại");
        }
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
        this.retryCount++;
    }

    /** {@code true} khi còn ở {@code PENDING} — chưa từng gửi hoặc chưa kết thúc (BR-N7). */
    public boolean canSend() {
        return status == NotificationStatus.PENDING;
    }

    /** BR-N1 — kênh EMAIL chỉ hợp lệ khi địa chỉ khớp định dạng email. */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /** BR-N2 — kênh SMS chỉ hợp lệ khi số điện thoại có dạng {@code 0} + 9–10 chữ số. */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
}
