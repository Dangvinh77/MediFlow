package com.mediflow.notification.domain.model;

import java.util.UUID;

/** In-app notice for the doctors of a department about a critical Lab value. */
public record StaffCriticalNotice(UUID noticeId, UUID sourceEventId, UUID labId, UUID recipientDepartmentId,
        UUID patientId, int resultVersion, String title, String content) { }
