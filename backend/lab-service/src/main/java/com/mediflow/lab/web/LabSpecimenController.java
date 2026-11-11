package com.mediflow.lab.web;

import java.util.List;
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
import com.mediflow.lab.application.dto.request.LabSpecimenRequests.CollectSpecimenRequest;
import com.mediflow.lab.application.dto.request.LabSpecimenRequests.SpecimenDecisionRequest;
import com.mediflow.lab.application.dto.response.LabSpecimenDTO;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.service.LabSpecimenApplicationService;

import jakarta.validation.Valid;

@RestController
public class LabSpecimenController {
    private final LabSpecimenApplicationService specimens;
    private final CorrelationIdProvider correlationIds;

    public LabSpecimenController(LabSpecimenApplicationService specimens, CorrelationIdProvider correlationIds) {
        this.specimens = specimens;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/lab/{testId}/specimens")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','LAB_TECH')")
    public ResponseEntity<ApiResponse<List<LabSpecimenDTO>>> forTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(ApiResponse.ok(specimens.forTest(testId), correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/api/v2/lab/{testId}/specimens")
    @PreAuthorize("hasAnyRole('NURSE','LAB_TECH')")
    public ResponseEntity<ApiResponse<LabSpecimenDTO>> collect(@PathVariable UUID testId,
            @Valid @RequestBody CollectSpecimenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(specimens.collect(testId, request),
                correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/api/v2/lab/specimens/{specimenId}/decision")
    @PreAuthorize("hasRole('LAB_TECH')")
    public ResponseEntity<ApiResponse<LabSpecimenDTO>> decide(@PathVariable UUID specimenId,
            @Valid @RequestBody SpecimenDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(specimens.decide(specimenId, request),
                correlationIds.currentOrCreate().toString()));
    }
}
