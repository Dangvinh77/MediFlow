package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.response.PrescriptionContextDTO;
import com.mediflow.clinical.application.port.in.GetPrescriptionContextUseCase;
import com.mediflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "mediflow.clinical.prescription-context-lookup.enabled", havingValue = "true")
public class PrescriptionContextController {
    private final GetPrescriptionContextUseCase contexts;
    public PrescriptionContextController(GetPrescriptionContextUseCase contexts) { this.contexts = contexts; }

    @GetMapping("/api/v1/records/{id}/prescription-context")
    @PreAuthorize("hasRole('SYSTEM') and principal instanceof T(com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal)")
    public ResponseEntity<ApiResponse<PrescriptionContextDTO>> get(@PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        if (correlation == null || correlation.isBlank() || correlation.length() > 120 || correlation.chars().anyMatch(Character::isISOControl))
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    ApiResponse.ApiError.of("INVALID_REQUEST", "Bounded correlation is required")));
        try {
            return ResponseEntity.ok().header("X-Correlation-Id", correlation).body(ApiResponse.ok(contexts.get(id), correlation));
        } catch (DataAccessException unavailable) {
            return ResponseEntity.status(503).header("X-Correlation-Id", correlation).body(ApiResponse.fail(
                    ApiResponse.ApiError.of("CLINICAL_CONTEXT_UNAVAILABLE", "Clinical context is unavailable"), correlation));
        }
    }
}
