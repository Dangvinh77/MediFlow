package com.mediflow.clinical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.response.OpenVisitDTO;
import com.mediflow.clinical.application.dto.response.ReturnSourceDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.OpenVisitApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class OpenVisitController {
    private final OpenVisitApplicationService visits;
    private final CorrelationIdProvider correlationIds;

    public OpenVisitController(OpenVisitApplicationService visits, CorrelationIdProvider correlationIds) {
        this.visits = visits;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/patients/{patientId}/open-visits")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<OpenVisitDTO>>> forPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok(visits.forPatient(patientId), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/patients/{patientId}/return-sources")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<List<ReturnSourceDTO>>> returnSources(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok(visits.returnSources(patientId), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/doctors/me/open-visits")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<List<OpenVisitDTO>>> forCurrentDoctor() {
        return ResponseEntity.ok(ApiResponse.ok(visits.forCurrentDoctor(), correlationIds.currentOrCreate().toString()));
    }
}
