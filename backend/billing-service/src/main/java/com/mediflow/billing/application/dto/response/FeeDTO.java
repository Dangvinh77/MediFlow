package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.billing.domain.model.FeeType;

/**
 * DTO một khoản viện phí, hiển thị trong chi tiết hóa đơn (backend-spec/06-billing.md §8).
 * Chỉ mang những trường cần cho client — không lộ {@code recordId}, {@code sourceRefId},
 * {@code invoiceId} nội bộ. {@code Fee} (domain) không bao giờ đi thẳng ra ngoài.
 *
 * @param feeId        mã khoản phí
 * @param feeType      loại phí: {@code EXAM | LAB | DRUG | SERVICE}
 * @param departmentId khoa phát sinh khoản phí
 * @param incurredDate ngày phát sinh
 * @param amount       số tiền
 * @param isPaid       đã thanh toán chưa
 */
public record FeeDTO(
        UUID feeId,
        FeeType feeType,
        UUID departmentId,
        LocalDate incurredDate,
        BigDecimal amount,
        boolean isPaid
) {}
