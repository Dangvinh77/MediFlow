package com.mediflow.notification.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class NotificationNotFoundException extends ResourceNotFoundException {
    public NotificationNotFoundException(String message) {
        super("NOTIFICATION_NOT_FOUND", message);
    }
}
