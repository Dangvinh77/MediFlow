package com.mediflow.billing.domain.model;

/**
 * Mục đích một {@link PaymentRequest} được mở ra. Rộng hơn {@link ClearancePurpose} đúng một giá
 * trị ({@code SETTLEMENT}) vì yêu cầu thanh toán quyết toán không cấp một
 * {@code financial.clearance.granted} nghiệp vụ (khớp {@code ck_payment_request_purpose} —
 * backend-spec/care-finance-v2/06-billing.md §3).
 */
public enum PaymentRequestPurpose {
    EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY, SETTLEMENT
}
