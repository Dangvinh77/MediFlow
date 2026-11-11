package com.mediflow.clinical.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.AddAllergyRequest;
import com.mediflow.clinical.application.dto.request.AllergyStatusRequest;
import com.mediflow.clinical.application.dto.response.PatientAllergiesDTO;
import com.mediflow.clinical.application.dto.response.PatientAllergyDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.PatientAllergyApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
public class PatientAllergyController {
    private final PatientAllergyApplicationService allergies;
    private final CorrelationIdProvider correlationIds;

    public PatientAllergyController(PatientAllergyApplicationService allergies, CorrelationIdProvider correlationIds) {
        this.allergies = allergies;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/patients/{patientId}/allergies")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<PatientAllergiesDTO>> list(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok(allergies.list(patientId), correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/api/v2/patients/{patientId}/allergies")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<PatientAllergyDTO>> add(@PathVariable UUID patientId,
            @RequestBody AddAllergyRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(allergies.add(patientId, request, key));
    }

    @PutMapping("/api/v2/patients/{patientId}/allergies/{allergyId}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<PatientAllergyDTO>> changeStatus(@PathVariable UUID patientId,
            @PathVariable UUID allergyId, @RequestBody AllergyStatusRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return respond(allergies.changeStatus(patientId, allergyId, request, key));
    }

    private ResponseEntity<ApiResponse<PatientAllergyDTO>> respond(IdempotentCommandPort.Result<PatientAllergyDTO> result) {
        return ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
