package com.mediflow.billing.infrastructure.pricing;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Bảng giá cho các khoản phí sinh tự động từ event, cấu hình qua {@code application.yml} tiền tố
 * {@code billing.pricing} (backend-spec/06-billing.md §11 — "V1 hiện thực bằng bảng {@code PRICE_LIST}
 * hoặc {@code @ConfigurationProperties}").
 *
 * <p>Chọn {@code @ConfigurationProperties}: V1 chưa có UUID khoa / danh mục loại xét nghiệm thật để
 * seed bảng, nên map override + giá mặc định là đủ và test được thuần Java. Muốn tách thành service
 * bảng giá riêng sau này thì chỉ thay {@link PriceListAdapter}, tầng application không đổi.
 *
 * <pre>
 * billing:
 *   pricing:
 *     default-exam-fee: 150000
 *     default-lab-fee: 80000
 *     exam-fee-by-department:
 *       "[11111111-1111-1111-1111-111111111111]": 200000
 *     lab-fee-by-type:
 *       CBC: 120000
 *       XQUANG: 250000
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "billing.pricing")
@Getter
@Setter
public class PriceListProperties {

    /** Giá khám dùng khi khoa không có mục riêng trong {@link #examFeeByDepartment}. */
    private BigDecimal defaultExamFee = new BigDecimal("150000");

    /** Giá xét nghiệm dùng khi loại xét nghiệm không có mục riêng trong {@link #labFeeByType}. */
    private BigDecimal defaultLabFee = new BigDecimal("80000");

    /** Giá khám riêng theo khoa. */
    private Map<UUID, BigDecimal> examFeeByDepartment = new HashMap<>();

    /** Giá xét nghiệm riêng theo loại (khớp {@code labType} của bảng chiếu {@code LAB_TEST_TYPE}). */
    private Map<String, BigDecimal> labFeeByType = new HashMap<>();
}
