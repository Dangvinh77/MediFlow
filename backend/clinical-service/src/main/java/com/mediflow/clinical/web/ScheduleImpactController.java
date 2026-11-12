package com.mediflow.clinical.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.response.ScheduleImpactDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.clinical.application.service.ScheduleImpactApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
@ConditionalOnClinicIntake
public class ScheduleImpactController {
    private final ScheduleImpactApplicationService impact;
    private final CorrelationIdProvider correlations;

    public ScheduleImpactController(ScheduleImpactApplicationService impact, CorrelationIdProvider correlations) {
        this.impact = impact;
        this.correlations = correlations;
    }

    @GetMapping("/api/v2/consultation-sessions/{id}/impact")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ApiResponse<ScheduleImpactDTO>> session(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(impact.forSession(id), correlations.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/doctor-work-shifts/{id}/impact")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ApiResponse<ScheduleImpactDTO>> shift(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(impact.forShift(id), correlations.currentOrCreate().toString()));
    }
}
