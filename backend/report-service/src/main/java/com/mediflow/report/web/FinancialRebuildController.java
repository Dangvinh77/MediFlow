package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.servlet.http.HttpServletRequest;
import com.mediflow.report.application.dto.request.StartFinancialRebuildRequest;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.port.in.RebuildFinancialSourcesUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/financial/rebuilds")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.financial-publication.enabled"}, havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class FinancialRebuildController {
    private final RebuildFinancialSourcesUseCase rebuild;
    private final String reportZone;
    public FinancialRebuildController(RebuildFinancialSourcesUseCase rebuild,
            @Value("${mediflow.report.zone-id:Asia/Bangkok}") String reportZone) {
        this.rebuild = rebuild; this.reportZone = reportZone;
    }
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialRebuildProgress>> start(@Valid @RequestBody StartFinancialRebuildRequest request, HttpServletRequest http) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(ApiResponse.ok(rebuild.start(request.from(), request.to(), reportZone), http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
    @PostMapping("/{generationId}/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FinancialRebuildProgress> advance(@PathVariable UUID generationId, @RequestParam(defaultValue="31") int days, HttpServletRequest http) {
        return ApiResponse.ok(rebuild.advance(generationId, days), http.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
    @GetMapping("/{generationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FinancialRebuildProgress> progress(@PathVariable UUID generationId, HttpServletRequest http) {
        return ApiResponse.ok(rebuild.progress(generationId), http.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
}
