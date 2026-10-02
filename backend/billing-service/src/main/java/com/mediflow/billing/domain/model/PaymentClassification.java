package com.mediflow.billing.domain.model;

/**
 * Cách một {@link PaymentTransaction} được ghi nhận vào doanh thu/công nợ. Tiền tạm ứng
 * ({@code ADMISSION_DEPOSIT}) chỉ tăng tiền mặt + công nợ, không phải doanh thu cho tới khi được
 * phân bổ qua quyết toán — xem backend-spec/care-finance-v2/06-billing.md §4.
 */
public enum PaymentClassification { SERVICE_PAYMENT, ADMISSION_DEPOSIT, SETTLEMENT_PAYMENT }
