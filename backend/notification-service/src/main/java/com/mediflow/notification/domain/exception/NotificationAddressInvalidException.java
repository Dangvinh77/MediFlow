package com.mediflow.notification.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

/** Kênh EMAIL/SMS nhưng địa chỉ nhận không hợp lệ theo kênh đó (BR-N1/BR-N2). Map HTTP 422. */
public class NotificationAddressInvalidException extends BusinessRuleException {
    public NotificationAddressInvalidException(String message) {
        super("NOTIFICATION_ADDRESS_INVALID", message);
    }
}
