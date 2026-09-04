package com.mediflow.billing.domain.model;

/**
 * Trạng thái saga kê đơn → hóa đơn → thanh toán → xuất thuốc.
 * Chỉ hóa đơn sinh ra từ đơn thuốc mới rời khỏi {@code NONE} (xem backend-spec/06-billing.md §3).
 */
public enum SagaStatus { NONE, AWAITING_PAYMENT, PAID, AWAITING_DISPENSE, COMPLETED, REFUNDED }
