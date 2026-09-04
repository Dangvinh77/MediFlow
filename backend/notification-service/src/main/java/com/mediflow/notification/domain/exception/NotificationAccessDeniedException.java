package com.mediflow.notification.domain.exception;

/**
 * Bệnh nhân cố đọc thông báo không phải của mình (BR-N6, {@code @PreAuthorize} không diễn đạt được
 * điều này — phải chặn ở tầng application). Map HTTP 403 trong {@code GlobalExceptionHandler} của
 * service này; {@code common.exception} chưa có base dùng chung cho 403 như các mã 404/409/422 nên
 * lớp này kế thừa thẳng {@link RuntimeException}, giữ đúng hình dạng (code + message) như các lớp
 * base còn lại để {@code GlobalExceptionHandler} xử lý đồng nhất.
 */
public class NotificationAccessDeniedException extends RuntimeException {

    private final String code;

    public NotificationAccessDeniedException(String message) {
        super(message);
        this.code = "NOTIFICATION_ACCESS_DENIED";
    }

    public String getCode() {
        return code;
    }
}
