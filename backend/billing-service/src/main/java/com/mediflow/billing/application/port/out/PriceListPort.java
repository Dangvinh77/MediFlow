package com.mediflow.billing.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Out-port — bảng giá cho các khoản phí sinh tự động từ event.
 * Khi nhận {@code medicalrecord.created} hay {@code lab.result.created}, billing cần biết
 * "khám ở khoa này bao nhiêu tiền", "xét nghiệm loại này bao nhiêu tiền"
 * (backend-spec/06-billing.md §6, §7).
 *
 * <p>Cố ý là một port: V1 hiện thực bằng bảng {@code PRICE_LIST} hoặc
 * {@code @ConfigurationProperties} (Phần 4/5); sau này muốn tách thành service bảng giá riêng
 * thì chỉ thay adapter, tầng application không đổi một dòng (§11).
 */
public interface PriceListPort {

    /** Giá khám theo khoa. */
    BigDecimal examFee(UUID departmentId);

    /** Giá xét nghiệm theo loại. */
    BigDecimal labFee(String labType);
}
