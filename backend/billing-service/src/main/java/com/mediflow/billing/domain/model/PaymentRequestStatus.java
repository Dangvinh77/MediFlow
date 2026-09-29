package com.mediflow.billing.domain.model;

/** Vòng đời {@link PaymentRequest} — khớp {@code ck_payment_request_status}. */
public enum PaymentRequestStatus { PENDING, PARTIALLY_PAID, PAID, EXPIRED, CANCELLED }
