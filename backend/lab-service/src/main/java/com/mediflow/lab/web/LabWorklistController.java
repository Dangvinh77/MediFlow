package com.mediflow.lab.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.dto.response.LabWorklistItemDTO;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.service.LabWorklistService;

@RestController
public class LabWorklistController {
    private final LabWorklistService worklist;
    private final CorrelationIdProvider correlationIds;

    public LabWorklistController(LabWorklistService worklist, CorrelationIdProvider correlationIds) {
        this.worklist = worklist;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/lab/worklist")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")
    public ResponseEntity<ApiResponse<List<LabWorklistItemDTO>>> worklist() {
        return ResponseEntity.ok(ApiResponse.ok(worklist.worklist(), correlationIds.currentOrCreate().toString()));
    }
}
