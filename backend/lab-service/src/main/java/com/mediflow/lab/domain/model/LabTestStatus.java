package com.mediflow.lab.domain.model;

/** Lifecycle of a lab test. */
public enum LabTestStatus {
    PENDING,
    AWAITING_PAYMENT,
    READY,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
