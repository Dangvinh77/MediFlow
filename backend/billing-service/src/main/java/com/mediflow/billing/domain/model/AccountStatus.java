package com.mediflow.billing.domain.model;

/** Vòng đời {@link BillingAccount} — xem backend-spec/care-finance-v2/06-billing.md §3. */
public enum AccountStatus { OPEN, CHARGE_CLOSED, SETTLEMENT_PENDING, SETTLED, CLOSED }
