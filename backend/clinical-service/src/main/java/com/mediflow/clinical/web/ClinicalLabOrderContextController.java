package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.response.ClinicalLabOrderContextDTO;
import com.mediflow.clinical.application.port.in.GetClinicalLabOrderContextUseCase;
import com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.exception.ForbiddenOperationException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Read-only service contract; the signed service JWT carries the separately verified human actor. */
@RestController
public class ClinicalLabOrderContextController {
    private static final Set<String> ALLOWED_ACTOR_ROLES = Set.of("ADMIN", "DOCTOR");
    private final GetClinicalLabOrderContextUseCase orders;

    public ClinicalLabOrderContextController(GetClinicalLabOrderContextUseCase orders) {
        this.orders = orders;
    }

    @GetMapping("/api/v1/lab-orders/{sourceOrderId}/context")
    @PreAuthorize("hasRole('SYSTEM') and principal instanceof T(com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal)")
    public ResponseEntity<ApiResponse<ClinicalLabOrderContextDTO>> get(
            @PathVariable UUID sourceOrderId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
            @AuthenticationPrincipal ClinicalServicePrincipal principal) {
        if (correlation == null || correlation.isBlank() || correlation.length() > 120
                || correlation.chars().anyMatch(Character::isISOControl)) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    ApiResponse.ApiError.of("INVALID_REQUEST", "Bounded correlation is required")));
        }
        try {
            ClinicalLabOrderContextDTO context = orders.get(sourceOrderId);
            if (context.exists() && (principal.actorStaffId() == null
                    || !ALLOWED_ACTOR_ROLES.contains(principal.actorRole())
                    || !principal.actorStaffId().equals(context.requestingDoctorId()))) {
                throw new ForbiddenOperationException("CLINICAL_LAB_ORDER_ACTOR_MISMATCH",
                        "The authenticated Lab actor does not match this Clinical order");
            }
            return ResponseEntity.ok().header("X-Correlation-Id", correlation)
                    .body(ApiResponse.ok(context, correlation));
        } catch (DataAccessException unavailable) {
            return ResponseEntity.status(503).header("X-Correlation-Id", correlation).body(ApiResponse.fail(
                    ApiResponse.ApiError.of("CLINICAL_LAB_ORDER_UNAVAILABLE", "Clinical order context is unavailable"),
                    correlation));
        }
    }
}
