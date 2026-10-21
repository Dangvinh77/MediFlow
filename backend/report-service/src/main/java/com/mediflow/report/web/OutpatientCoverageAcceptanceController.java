package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.exception.OutpatientCoverageConflictException;
import com.mediflow.report.application.port.in.AcceptOutpatientCoverageUseCase;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort.Snapshot;
import com.mediflow.report.application.dto.response.OutpatientCoverageDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Operator approval surface, not an upstream source-completeness oracle. */
@RestController
@RequestMapping("/api/v1/reports/outpatient/coverage")
@Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.outpatient-read.enabled",
        "mediflow.report.outpatient-coverage-acceptance.enabled"}, havingValue="true")
public class OutpatientCoverageAcceptanceController {
    private final AcceptOutpatientCoverageUseCase approvals;
    public OutpatientCoverageAcceptanceController(AcceptOutpatientCoverageUseCase approvals) { this.approvals=approvals; }

    @GetMapping("/preview") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Snapshot>> preview(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required=false) UUID departmentId, HttpServletRequest http) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(ApiResponse.ok(approvals.preview(fromDate,toDate,departmentId),correlation(http)));
    }

    @PostMapping("/acceptances") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OutpatientCoverageDTO>> accept(@RequestBody AcceptOutpatientCoverageRequest request,
            Authentication actor, HttpServletRequest http) {
        UUID account;
        try {
            account=UUID.fromString(actor.getName());
            if (!account.toString().equals(actor.getName())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new AccessDeniedException("Signed account UUID required"); }
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(ApiResponse.ok(approvals.accept(request,account),correlation(http)));
    }

    @ExceptionHandler(OutpatientCoverageConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(OutpatientCoverageConflictException exception, HttpServletRequest http) {
        return ResponseEntity.status(409).body(ApiResponse.fail(
                ApiResponse.ApiError.of("REPORT_COVERAGE_CONFLICT",exception.getMessage()),correlation(http)));
    }

    private String correlation(HttpServletRequest http) {
        Object value=http.getAttribute(JwtClaims.CORRELATION_ID);
        return value instanceof String s?s:null;
    }
}
