package com.mediflow.notification.application.port.out;

import java.util.Optional;

import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;

/**
 * Out-port — "tôi cần ai đó biết cách thực sự gửi thông báo qua một kênh".
 * Mỗi kênh một bản hiện thực trong {@code infrastructure/channel} (Phần 5/5):
 * {@code MockEmailSender}, {@code MockSmsSender}, {@code InAppSender}. Application chọn đúng
 * adapter theo {@link #channel()} (BR-N9 chọn kênh nằm ở tầng application, không phải trong
 * sender). Chữ ký bám sát backend-spec/07-notification.md §5.
 */
public interface NotificationSenderPort {

    /** Kênh mà bản hiện thực này phụ trách. */
    NotificationChannel channel();

    /**
     * Thử gửi thông báo.
     *
     * @return {@link Optional#empty()} nếu gửi thành công; hoặc lý do thất bại nếu hỏng.
     *         <b>Không bao giờ ném lỗi khi gửi thất bại</b> — một email hỏng là kết quả nghiệp
     *         vụ ({@code FAILED}), không phải lỗi hạ tầng (BR-N4, §12).
     */
    Optional<String> send(Notification n);
}
