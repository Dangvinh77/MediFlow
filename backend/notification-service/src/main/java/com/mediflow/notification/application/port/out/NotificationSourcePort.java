package com.mediflow.notification.application.port.out;

import java.util.UUID;

public interface NotificationSourcePort {
    boolean claim(String eventType, UUID sourceId, String sourceFingerprint);
}
