package com.mediflow.billing.web;

import java.security.Principal;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mediflow.billing.application.dto.request.SettleAdmissionRequest;
import com.mediflow.billing.application.dto.response.SettlementDTO;
import com.mediflow.billing.application.port.in.SettleAdmissionUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

@RestController
@RequestMapping("/api/v1/billing/accounts")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement.enabled"}, havingValue = "true")
public class SettlementController {
    private final SettleAdmissionUseCase settlements;
    public SettlementController(SettleAdmissionUseCase settlements) { this.settlements = settlements; }

    @PostMapping("/{id}/settlements")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ApiResponse<SettlementDTO> settle(@PathVariable UUID id,
            @Valid @RequestBody(required = false) SettleAdmissionRequest request, Principal principal,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        UUID actor;
        try { actor = UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException invalid) {
            throw new org.springframework.security.access.AccessDeniedException("Verified account UUID is required");
        }
        String correlation = suppliedCorrelation == null || suppliedCorrelation.isBlank()
                ? UUID.randomUUID().toString() : suppliedCorrelation;
        SettleAdmissionRequest body = request == null ? new SettleAdmissionRequest(null, null, null) : request;
        return ApiResponse.ok(settlements.settle(id, body, actor, correlation), correlation);
    }
}
