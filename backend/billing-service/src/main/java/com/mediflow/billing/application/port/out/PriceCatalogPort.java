package com.mediflow.billing.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Out-port — bảng giá V2 theo price code (backend-spec/care-finance-v2/06-billing.md).
 * Khác {@link PriceListPort} (V1, có giá mặc định): price code chưa khai báo phải bị từ chối,
 * không được mặc định về 0 (CONTRACT-SURGERY-BILLING-01).
 */
public interface PriceCatalogPort {

    /** Trả về giá đang hiệu lực của price code tại thời điểm {@code at}; ném lỗi nếu không có. */
    PriceSnapshot requireActive(String priceCode, Instant at);

    record PriceSnapshot(String description, BigDecimal unitAmount) {
    }
}
