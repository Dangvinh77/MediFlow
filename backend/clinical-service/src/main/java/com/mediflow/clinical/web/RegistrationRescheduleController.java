package com.mediflow.clinical.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.RegistrationRescheduleRequest;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.clinical.application.service.RegistrationRescheduleApplicationService;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@ConditionalOnClinicIntake
public class RegistrationRescheduleController {
    private final RegistrationRescheduleApplicationService reschedules;
    private final CorrelationIdProvider correlationIds;

    public RegistrationRescheduleController(RegistrationRescheduleApplicationService reschedules, CorrelationIdProvider correlationIds) {
        this.reschedules = reschedules;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/outpatient-registrations/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> reschedule(@PathVariable UUID id,
            @Valid @RequestBody RegistrationRescheduleRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var result = reschedules.reschedule(id, request, idempotencyKey);
        return ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
