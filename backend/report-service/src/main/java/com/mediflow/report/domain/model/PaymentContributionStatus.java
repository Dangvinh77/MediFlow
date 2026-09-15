package com.mediflow.report.domain.model;

/** Lifecycle of one invoice's contribution to the report projection. */
public enum PaymentContributionStatus {
    NEW,
    PENDING_REVERSAL,
    APPLIED,
    REVERSED
}
