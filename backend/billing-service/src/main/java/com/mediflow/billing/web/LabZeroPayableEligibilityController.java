package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.request.LabZeroPayableEligibilityRequest;
import com.mediflow.billing.application.dto.response.LabZeroPayableEligibilityDTO;
import com.mediflow.billing.application.port.in.GrantLabZeroPayableEligibilityUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/billing/payment-requests")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled",
        "mediflow.billing.zero-payable-eligibility.enabled"}, havingValue = "true")
public class LabZeroPayableEligibilityController {
    private final GrantLabZeroPayableEligibilityUseCase eligibility;

    public LabZeroPayableEligibilityController(GrantLabZeroPayableEligibilityUseCase eligibility) {
        this.eligibility = eligibility;
    }

    @PostMapping("/{id}/lab-zero-payable-eligibility")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<LabZeroPayableEligibilityDTO> grant(@PathVariable UUID id,
            @Valid @RequestBody LabZeroPayableEligibilityRequest request, Principal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        UUID actor = actor(principal);
        String correlation = correlation(suppliedCorrelation);
        var result = eligibility.grant(id, request.quoteId(), actor, idempotencyKey, correlation);
        return ApiResponse.ok(LabZeroPayableEligibilityDTO.from(result), correlation);
    }

    private static UUID actor(Principal principal) {
        try { return UUID.fromString(principal.getName()); }
        catch (RuntimeException invalid) { throw new AccessDeniedException("Verified account UUID is required"); }
    }

    private static String correlation(String supplied) {
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
