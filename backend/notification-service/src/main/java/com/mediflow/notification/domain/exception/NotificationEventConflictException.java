package com.mediflow.notification.domain.exception;

/** Poison event identity collision, distinct from a retriable database outage. */
public class NotificationEventConflictException extends com.mediflow.common.exception.DuplicateResourceException {
    public NotificationEventConflictException() {
        this("Notification event identity was reused with another payload/version");
    }

    public NotificationEventConflictException(String reason) {
        super("NOTIFICATION_EVENT_CONFLICT", reason);
    }
}
