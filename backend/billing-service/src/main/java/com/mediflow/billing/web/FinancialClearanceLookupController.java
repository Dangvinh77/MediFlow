package com.mediflow.billing.web;

import java.util.UUID;
import com.mediflow.billing.application.dto.response.FinancialClearanceLookupDTO;
import com.mediflow.billing.application.port.in.LookupFinancialClearanceUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/financial-clearances")
@ConditionalOnProperty(name = "mediflow.billing.clearance-lookup.enabled", havingValue = "true")
public class FinancialClearanceLookupController {
    private final LookupFinancialClearanceUseCase lookups;
    public FinancialClearanceLookupController(LookupFinancialClearanceUseCase lookups) { this.lookups = lookups; }

    @GetMapping("/{id}/lookup")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<FinancialClearanceLookupDTO>> lookup(@PathVariable UUID id,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlation) {
        if (correlation.isBlank() || correlation.length() > 120) {
            return ResponseEntity.badRequest().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                    .body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_CORRELATION_ID", "Correlation identity required"), correlation));
        }
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(lookups.lookup(id), correlation));
    }
}
