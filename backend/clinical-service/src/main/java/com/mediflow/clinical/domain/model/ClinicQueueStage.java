package com.mediflow.clinical.domain.model;

public enum ClinicQueueStage {
    AWAITING_PAYMENT,
    NOT_ARRIVED,
    WAITING_ROOM_TICKET,
    WAITING_CALL,
    CALLED,
    IN_EXAM,
    ABSENT
}
