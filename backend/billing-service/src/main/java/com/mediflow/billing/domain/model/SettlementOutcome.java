package com.mediflow.billing.domain.model;

/** Kết quả một {@link Settlement} — khớp {@code ck_settlement_outcome}. */
public enum SettlementOutcome {
    PAID_IN_FULL, ADDITIONAL_PAYMENT_REQUIRED, REFUND_DUE, DEBT_APPROVED, WAIVED
}
