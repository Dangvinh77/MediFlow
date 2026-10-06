package com.mediflow.notification.domain.exception;

/** Poison event identity collision, distinct from a retriable database outage. */
public class NotificationEventConflictException extends com.mediflow.common.exception.DuplicateResourceException {
    public NotificationEventConflictException() {
        super("NOTIFICATION_EVENT_CONFLICT", "Notification event identity was reused with another payload/version");
    }
}
