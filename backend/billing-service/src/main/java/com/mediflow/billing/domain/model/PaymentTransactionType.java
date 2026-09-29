package com.mediflow.billing.domain.model;

/** Loại giao dịch trên {@link PaymentTransaction} — khớp {@code ck_payment_transaction_type}. */
public enum PaymentTransactionType { PAYMENT, REFUND, REVERSAL }
