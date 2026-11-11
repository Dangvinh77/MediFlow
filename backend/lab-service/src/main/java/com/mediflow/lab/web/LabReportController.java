package com.mediflow.lab.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.dto.request.LabReportRequests.ReturnLabReportRequest;
import com.mediflow.lab.application.dto.request.LabReportRequests.SaveLabReportDraftRequest;
import com.mediflow.lab.application.dto.request.LabReportRequests.SubmitLabReportRequest;
import com.mediflow.lab.application.dto.response.LabReportDTO;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.service.LabReportApplicationService;

import jakarta.validation.Valid;

@RestController
public class LabReportController {
    private final LabReportApplicationService reports;
    private final com.mediflow.lab.application.service.LabReportReleaseService release;
    private final CorrelationIdProvider correlationIds;

    public LabReportController(LabReportApplicationService reports,
            com.mediflow.lab.application.service.LabReportReleaseService release, CorrelationIdProvider correlationIds) {
        this.reports = reports;
        this.release = release;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/lab/{testId}/report")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','LAB_TECH')")
    public ResponseEntity<ApiResponse<LabReportDTO>> current(@PathVariable UUID testId) {
        return ok(reports.current(testId));
    }

    @PutMapping("/api/v2/lab/{testId}/report/draft")
    @PreAuthorize("hasRole('LAB_TECH')")
    public ResponseEntity<ApiResponse<LabReportDTO>> saveDraft(@PathVariable UUID testId,
            @Valid @RequestBody SaveLabReportDraftRequest request) {
        return ok(reports.saveDraft(testId, request));
    }

    @PostMapping("/api/v2/lab/{testId}/report/submission")
    @PreAuthorize("hasRole('LAB_TECH')")
    public ResponseEntity<ApiResponse<LabReportDTO>> submit(@PathVariable UUID testId,
            @Valid @RequestBody SubmitLabReportRequest request) {
        return ok(reports.submit(testId, request));
    }

    @PostMapping("/api/v2/lab/{testId}/report/return")
    @PreAuthorize("hasRole('LAB_TECH')")
    public ResponseEntity<ApiResponse<LabReportDTO>> returnForCorrection(@PathVariable UUID testId,
            @Valid @RequestBody ReturnLabReportRequest request) {
        return ok(reports.returnForCorrection(testId, request));
    }

    @PostMapping("/api/v2/lab/{testId}/report/release")
    @PreAuthorize("hasRole('LAB_TECH')")
    public ResponseEntity<ApiResponse<LabReportDTO>> release(@PathVariable UUID testId) {
        return ok(release.release(testId));
    }

    private ResponseEntity<ApiResponse<LabReportDTO>> ok(LabReportDTO body) {
        return ResponseEntity.ok(ApiResponse.ok(body, correlationIds.currentOrCreate().toString()));
    }
}
