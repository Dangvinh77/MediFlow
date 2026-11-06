package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.OutpatientStatisticsDTO;
import com.mediflow.report.application.port.in.ReadOutpatientStatisticsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Provisional outpatient operations for charts; reconciled figures stay on {@link OutpatientReportController}. */
@RestController @RequestMapping("/api/v1/reports/outpatient") @Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.report.outpatient-read.enabled"}, havingValue = "true")
public class OutpatientStatisticsController {
    private final ReadOutpatientStatisticsUseCase reader;

    public OutpatientStatisticsController(ReadOutpatientStatisticsUseCase reader) {
        this.reader = reader;
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR') and @reportScopeGuard.permits(authentication, #departmentId)")
    public ApiResponse<OutpatientStatisticsDTO> statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID departmentId, HttpServletRequest request) {
        Object correlation = request.getAttribute(JwtClaims.CORRELATION_ID);
        return ApiResponse.ok(reader.statistics(fromDate, toDate, departmentId),
                correlation instanceof String value ? value : null);
    }
}
