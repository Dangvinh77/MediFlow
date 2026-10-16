package com.mediflow.report.web;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.FinancialSnapshotReportDTO;
import com.mediflow.report.application.port.in.ReadFinancialSnapshotUseCase;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/reports/financial")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.financial-read.enabled"},havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class FinancialSnapshotReportController {
    private final ReadFinancialSnapshotUseCase reader;
    public FinancialSnapshotReportController(ReadFinancialSnapshotUseCase reader) { this.reader=reader; }
    @GetMapping("/daily") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<FinancialSnapshotReportDTO> daily(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) UUID departmentId,HttpServletRequest request) {
        return ApiResponse.ok(reader.daily(from,to,departmentId),request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
    @GetMapping("/monthly") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<FinancialSnapshotReportDTO> monthly(@RequestParam int year,
            @RequestParam(required=false) UUID departmentId,HttpServletRequest request) {
        return ApiResponse.ok(reader.monthly(year,departmentId),request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
}
