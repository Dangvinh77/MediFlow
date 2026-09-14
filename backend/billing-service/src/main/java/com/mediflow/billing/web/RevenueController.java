package com.mediflow.billing.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;
import com.mediflow.billing.application.port.in.QueryRevenueUseCase;
import com.mediflow.common.api.ApiResponse;

/**
 * Báo cáo doanh thu theo khoa (backend-spec/06-billing.md §8, BR-B10). Tách khỏi
 * {@link InvoiceController} vì §12.1 liệt kê riêng {@code RevenueController}.
 */
@RestController
@RequestMapping("/api/v1/billing/revenue")
public class RevenueController {

    private final QueryRevenueUseCase queryRevenueUseCase;

    public RevenueController(QueryRevenueUseCase queryRevenueUseCase) {
        this.queryRevenueUseCase = queryRevenueUseCase;
    }

    /** Doanh thu đã thanh toán gom theo khoa trong khoảng ngày. {@code departmentId} bỏ trống = mọi khoa. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<RevenueByDeptDTO>>> revenueByDepartment(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(
                ApiResponse.ok(queryRevenueUseCase.revenueByDepartment(departmentId, fromDate, toDate)));
    }
}
