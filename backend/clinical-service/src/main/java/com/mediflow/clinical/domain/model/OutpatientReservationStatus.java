package com.mediflow.clinical.domain.model;

public enum OutpatientReservationStatus {
    HELD,
    PAYMENT_LOCKED,
    CONFIRMED,
    CONSUMED,
    EXPIRED,
    CANCELLED,
    RECONCILIATION_REQUIRED
}
