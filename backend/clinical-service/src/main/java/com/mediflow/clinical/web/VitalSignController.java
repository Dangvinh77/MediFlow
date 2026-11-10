package com.mediflow.clinical.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.VitalSignCreateRequest;
import com.mediflow.clinical.application.dto.response.VitalSignObservationDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.clinical.application.service.VitalSignApplicationService;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@ConditionalOnClinicIntake
public class VitalSignController {
    private static final String PATH = "/api/v2/outpatient-registrations/{id}/vital-signs";
    private final VitalSignApplicationService vitals;
    private final CorrelationIdProvider correlationIds;

    public VitalSignController(VitalSignApplicationService vitals, CorrelationIdProvider correlationIds) {
        this.vitals = vitals;
        this.correlationIds = correlationIds;
    }

    @PostMapping(PATH)
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<VitalSignObservationDTO>> record(@PathVariable UUID id, @Valid @RequestBody VitalSignCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var result = vitals.record(id, request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping(PATH)
    @PreAuthorize("hasAnyRole('NURSE','DOCTOR')")
    public ResponseEntity<ApiResponse<List<VitalSignObservationDTO>>> list(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(vitals.list(id), correlationIds.currentOrCreate().toString()));
    }
}
