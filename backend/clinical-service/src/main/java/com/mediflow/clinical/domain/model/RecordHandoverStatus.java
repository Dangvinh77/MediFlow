package com.mediflow.clinical.domain.model;

public enum RecordHandoverStatus {
    PENDING,
    ACCEPTED,
    /** Replaced by a newer request from the responsible doctor before anyone accepted it. */
    SUPERSEDED
}
