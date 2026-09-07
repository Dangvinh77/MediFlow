package com.mediflow.billing.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;

/**
 * In-port cho báo cáo doanh thu: {@code GET /api/v1/billing/revenue?departmentId&fromDate&toDate}
 * (backend-spec/06-billing.md §8, BR-B10).
 *
 * <p>Spec §6 liệt kê in-port theo nhóm nghiệp vụ chính và không đặt tên riêng cho phần doanh
 * thu, nhưng §8 (endpoint) + §10 (test {@code revenue_groupsByDepartmentIdAndDateRange}) + §12.1
 * ({@code RevenueController}, {@code RevenueMapper}) đều yêu cầu nó. Tách riêng khỏi
 * {@link ManageInvoiceUseCase} để giữ đúng danh sách 4 method của use case đó theo §12.1.
 * {@code BillingApplicationService} (Phần 3/5) hiện thực.
 */
public interface QueryRevenueUseCase {

    /**
     * Doanh thu đã thanh toán gom theo khoa trong khoảng ngày.
     *
     * @param departmentId lọc theo một khoa; {@code null} = tất cả các khoa
     * @param fromDate     ngày bắt đầu (bao gồm)
     * @param toDate       ngày kết thúc (bao gồm)
     */
    List<RevenueByDeptDTO> revenueByDepartment(UUID departmentId, LocalDate fromDate, LocalDate toDate);
}
