package com.mediflow.notification.domain.model;

/** Trạng thái gửi. {@code PENDING → SENT | FAILED}, cả hai đều là trạng thái kết thúc. */
public enum NotificationStatus { PENDING, SENT, FAILED }
