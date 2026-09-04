package com.mediflow.notification.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Thông báo đã ở trạng thái kết thúc (SENT/FAILED), không thể gửi lại (BR-N7). Map HTTP 422. */
public class NotificationAlreadyFinalisedException extends BusinessRuleException {
    public NotificationAlreadyFinalisedException(String message) {
        super("NOTIFICATION_ALREADY_FINALISED", message);
    }
}
