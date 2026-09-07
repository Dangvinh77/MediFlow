package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng doanh thu gom theo khoa, trả về từ {@code GET /revenue?departmentId&fromDate&toDate}
 * (backend-spec/06-billing.md §8, BR-B10). Số liệu do tầng persistence tổng hợp (GROUP BY khoa
 * trong khoảng ngày), không phải dựng từ một domain model.
 *
 * @param departmentId  khoa
 * @param totalRevenue  tổng doanh thu đã thanh toán trong khoảng ngày
 * @param invoiceCount  số hóa đơn đã thanh toán đóng góp vào tổng trên
 */
public record RevenueByDeptDTO(
        UUID departmentId,
        BigDecimal totalRevenue,
        long invoiceCount
) {}
