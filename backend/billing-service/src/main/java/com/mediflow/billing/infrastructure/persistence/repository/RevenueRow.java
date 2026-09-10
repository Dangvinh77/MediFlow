package com.mediflow.billing.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dòng kết quả thô của truy vấn tổng hợp doanh thu (JPQL {@code SELECT new ...}), nằm hẳn trong
 * tầng hạ tầng. {@code InvoicePersistenceAdapter} đổi nó thành
 * {@code InvoiceRepositoryPort.DepartmentRevenue} (projection tầng application) — application
 * không bao giờ thấy kiểu này.
 *
 * @param departmentId  khoa của các khoản phí
 * @param totalRevenue  tổng {@code SUM(fee.amount)} của các khoản phí thuộc hóa đơn đã thanh toán
 * @param invoiceCount  số hóa đơn phân biệt ({@code COUNT(DISTINCT invoice_id)})
 */
public record RevenueRow(UUID departmentId, BigDecimal totalRevenue, Long invoiceCount) {}
