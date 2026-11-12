package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.request.PrescriptionRevisionRequest;
import com.mediflow.billing.application.dto.response.PrescriptionRevisionResult;
import com.mediflow.billing.application.port.in.RevisePrescriptionChargeUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct Pharmacy-to-Billing coordination API. Gateway does not route internal service paths. */
@RestController
@RequestMapping("/internal/api/v1/billing/prescription-revisions")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.prescription-charge-consumer.enabled"}, havingValue = "true")
public class PrescriptionRevisionController {
    private final RevisePrescriptionChargeUseCase revisions;

    public PrescriptionRevisionController(RevisePrescriptionChargeUseCase revisions) {
        this.revisions = revisions;
    }

    @PostMapping("/{prescriptionId}/{operationId}/prepare")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<PrescriptionRevisionResult>> prepare(@PathVariable UUID prescriptionId,
            @PathVariable UUID operationId, @Valid @RequestBody PrescriptionRevisionRequest request,
            @RequestHeader(name = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requirePharmacy(authentication);
        return respond(revisions.prepare(request.toCommand(prescriptionId, operationId)), operationId, correlationId);
    }

    @PostMapping("/{prescriptionId}/{operationId}/commit")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<PrescriptionRevisionResult>> commit(@PathVariable UUID prescriptionId,
            @PathVariable UUID operationId, @Valid @RequestBody PrescriptionRevisionRequest request,
            @RequestHeader(name = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requirePharmacy(authentication);
        return respond(revisions.commit(request.toCommand(prescriptionId, operationId)), operationId, correlationId);
    }

    @PostMapping("/{prescriptionId}/{operationId}/abort")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<PrescriptionRevisionResult>> abort(@PathVariable UUID prescriptionId,
            @PathVariable UUID operationId, @Valid @RequestBody PrescriptionRevisionRequest request,
            @RequestHeader(name = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            Authentication authentication) {
        requirePharmacy(authentication);
        return respond(revisions.abort(request.toCommand(prescriptionId, operationId)), operationId, correlationId);
    }

    private ResponseEntity<ApiResponse<PrescriptionRevisionResult>> respond(PrescriptionRevisionResult result,
            UUID operationId, String correlationId) {
        String resolvedCorrelation = correlationId == null || correlationId.isBlank()
                ? operationId.toString() : correlationId;
        if (resolvedCorrelation.length() > 120 || resolvedCorrelation.chars().anyMatch(Character::isISOControl)) {
            return ResponseEntity.badRequest().header(JwtClaims.HEADER_CORRELATION_ID, resolvedCorrelation)
                    .body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_CORRELATION_ID", "Correlation identity is invalid"), resolvedCorrelation));
        }
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, resolvedCorrelation)
                .body(ApiResponse.ok(result, resolvedCorrelation));
    }

    private static void requirePharmacy(Authentication authentication) {
        if (authentication == null || !"pharmacy-service".equals(authentication.getName())) {
            throw new AccessDeniedException("Only Pharmacy may coordinate prescription revisions");
        }
    }
}
