package com.mediflow.billing.infrastructure.pricing;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.out.PriceListPort;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link PriceListPort} — tra giá phí khám/xét nghiệm từ {@link PriceListProperties}.
 * Không có mục riêng thì trả giá mặc định (không bao giờ trả null): billing luôn sinh được khoản
 * phí kể cả khi bảng giá chưa cấu hình đầy đủ.
 */
@Component
@RequiredArgsConstructor
public class PriceListAdapter implements PriceListPort {

    private final PriceListProperties props;

    @Override
    public BigDecimal examFee(UUID departmentId) {
        return props.getExamFeeByDepartment().getOrDefault(departmentId, props.getDefaultExamFee());
    }

    @Override
    public BigDecimal labFee(String labType) {
        return props.getLabFeeByType().getOrDefault(labType, props.getDefaultLabFee());
    }
}
