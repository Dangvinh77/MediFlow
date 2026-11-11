package com.mediflow.lab.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.dto.response.LabCriticalNoticeDTO;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.service.LabCriticalNoticeService;

@RestController
public class LabCriticalNoticeController {
    private final LabCriticalNoticeService notices;
    private final CorrelationIdProvider correlationIds;

    public LabCriticalNoticeController(LabCriticalNoticeService notices, CorrelationIdProvider correlationIds) {
        this.notices = notices;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/lab/critical-notices")
    @PreAuthorize("hasAnyRole('DOCTOR','LAB_TECH')")
    public ResponseEntity<ApiResponse<List<LabCriticalNoticeDTO>>> list(
            @RequestParam(defaultValue = "OPEN") String status) {
        return ResponseEntity.ok(ApiResponse.ok(notices.list(status), correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/api/v2/lab/{testId}/critical-notices/{noticeId}/acknowledgement")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<LabCriticalNoticeDTO>> acknowledge(@PathVariable UUID testId,
            @PathVariable UUID noticeId) {
        return ResponseEntity.ok(ApiResponse.ok(notices.acknowledge(testId, noticeId),
                correlationIds.currentOrCreate().toString()));
    }
}
