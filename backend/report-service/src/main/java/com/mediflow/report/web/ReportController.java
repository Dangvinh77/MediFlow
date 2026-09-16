package com.mediflow.report.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;
import com.mediflow.report.application.port.in.ReadReportUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Thin HTTP adapter for the three read-only report queries. */
@RestController
@RequestMapping("/api/v1/reports")
@Validated
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReadReportUseCase readReportUseCase;

    public ReportController(ReadReportUseCase readReportUseCase) {
        this.readReportUseCase = readReportUseCase;
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Daily operational report",
            description = "Returns zero counters when no row exists. Null departmentId means hospital scope.")
    public ResponseEntity<ApiResponse<DailyReportDTO>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID departmentId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                readReportUseCase.daily(date, departmentId), correlationId(request)));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Monthly revenue report",
            description = "Daily details include every calendar day, zero-filled where absent.")
    public ResponseEntity<ApiResponse<MonthlyReportDTO>> monthly(
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) UUID departmentId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                readReportUseCase.monthly(month, year, departmentId), correlationId(request)));
    }

    @GetMapping("/top-medicines")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Top dispensed medicines",
            description = "Inclusive date range. limit defaults to 10 and is capped at 50.")
    public ResponseEntity<ApiResponse<java.util.List<TopMedicineDTO>>> topMedicines(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(readReportUseCase.topMedicines(
                fromDate, toDate, departmentId, limit), correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        return request.getHeader(JwtClaims.HEADER_CORRELATION_ID);
    }
}
