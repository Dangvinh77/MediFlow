package com.mediflow.report.web;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.OperationalSnapshotReportDTO;
import com.mediflow.report.application.port.in.ReadOperationalSnapshotUseCase;
import jakarta.servlet.http.HttpServletRequest;

/** Disabled by default; snapshot-only, never the legacy report's source or a live catch-up claim. */
@RestController
@RequestMapping("/api/v1/reports/operations")
@ConditionalOnProperty(name = "mediflow.features.care-finance-v2", havingValue = "true")
public class OperationalSnapshotReportController {
    private final ReadOperationalSnapshotUseCase reader;
    public OperationalSnapshotReportController(ReadOperationalSnapshotUseCase reader) { this.reader = reader; }
    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR')")
    public ApiResponse<OperationalSnapshotReportDTO> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID departmentId, HttpServletRequest request) {
        return ApiResponse.ok(reader.daily(from, to, departmentId), request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
    @GetMapping("/surgery")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR')")
    public ApiResponse<OperationalSnapshotReportDTO> surgery(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID departmentId, HttpServletRequest request) {
        return ApiResponse.ok(reader.surgery(from, to, departmentId), request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
}
