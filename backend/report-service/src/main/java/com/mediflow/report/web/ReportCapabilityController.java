package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.servlet.http.HttpServletRequest;
import com.mediflow.report.application.dto.response.ReportCapabilitiesDTO;
import com.mediflow.report.application.dto.response.ReportCoverageDTO;
import com.mediflow.report.application.port.in.InspectReportCapabilitiesUseCase;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/financial")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.financial-publication.enabled"}, havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class ReportCapabilityController {
    private final InspectReportCapabilitiesUseCase capabilities;
    private final String reportZone;
    public ReportCapabilityController(InspectReportCapabilitiesUseCase capabilities,
            @Value("${mediflow.report.zone-id:Asia/Bangkok}") String reportZone) {
        this.capabilities = capabilities; this.reportZone = reportZone;
    }
    @GetMapping("/capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportCapabilitiesDTO> capabilities(HttpServletRequest http) {
        return ApiResponse.ok(capabilities.capabilities(), http.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
    @GetMapping("/coverage")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<ReportCoverageDTO> coverage(@RequestParam LocalDate from, @RequestParam LocalDate to, HttpServletRequest http) {
        return ApiResponse.ok(capabilities.coverage(from, to, "VND", reportZone), http.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
}
