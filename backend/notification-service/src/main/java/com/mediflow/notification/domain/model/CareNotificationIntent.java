package com.mediflow.notification.domain.model;

import java.util.UUID;

/** Deliberately IN_APP only; contains no copied clinical payload or contact address. */
public record CareNotificationIntent(UUID notificationId, UUID patientId, UUID sourceEventId,
        String sourceEventType, UUID sourceId, String correlationId, String templateKey, String title, String content) { }
