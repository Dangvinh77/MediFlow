package com.mediflow.notification.application.port.out;

import java.util.UUID;
import com.mediflow.notification.domain.model.CareNotificationIntent;

public interface CareNotificationRepositoryPort {
    boolean claim(UUID eventId, String eventType, String fingerprint);
    void deliverInApp(CareNotificationIntent intent);
    void recordSuppressedInApp(CareNotificationIntent intent);
}
