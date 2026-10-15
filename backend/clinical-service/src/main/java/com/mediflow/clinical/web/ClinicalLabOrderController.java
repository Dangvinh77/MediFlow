package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.request.CreateClinicalLabOrderRequest;
import com.mediflow.clinical.application.dto.response.ClinicalLabOrderDTO;
import com.mediflow.clinical.application.port.in.CreateClinicalLabOrderUseCase;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates a Lab source order from an authenticated, active Clinical examination. */
@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
public class ClinicalLabOrderController {
    private final CreateClinicalLabOrderUseCase orders;
    private final CorrelationIdProvider correlationIds;

    @PostMapping("/{id}/lab-orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalLabOrderDTO>> create(
            @PathVariable UUID id, @Valid @RequestBody CreateClinicalLabOrderRequest request) {
        ClinicalLabOrderDTO created = orders.create(id, request);
        return ResponseEntity.status(201).body(ApiResponse.ok(created, correlationIds.currentOrCreate().toString()));
    }
}
