package com.mediflow.billing.web;

import java.security.Principal;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

@RestController
@RequestMapping("/api/v1/billing/payment-requests")
@ConditionalOnProperty(name = "mediflow.billing.ledger.enabled", havingValue = "true")
public class LedgerPaymentController {
    private final ProcessLedgerPaymentUseCase payments;
    public LedgerPaymentController(ProcessLedgerPaymentUseCase payments) { this.payments = payments; }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ApiResponse<LedgerPaymentDTO> complete(@PathVariable UUID id,
            @Valid @RequestBody CompleteLedgerPaymentRequest command, Principal principal,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        UUID actor;
        try { actor = UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException invalid) {
            throw new org.springframework.security.access.AccessDeniedException("Verified account UUID is required");
        }
        String correlation = suppliedCorrelation == null || suppliedCorrelation.isBlank()
                ? UUID.randomUUID().toString() : suppliedCorrelation;
        return ApiResponse.ok(payments.complete(id, command, actor, correlation), correlation);
    }
}
