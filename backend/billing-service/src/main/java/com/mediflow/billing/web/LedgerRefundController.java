package com.mediflow.billing.web;

import java.security.Principal;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerRefundDTO;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

@RestController
@RequestMapping("/api/v1/billing/transactions")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.refunds.enabled"}, havingValue = "true")
public class LedgerRefundController {
    private final RefundLedgerPaymentUseCase refunds;
    public LedgerRefundController(RefundLedgerPaymentUseCase refunds) { this.refunds = refunds; }

    @PostMapping("/{id}/refunds")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LedgerRefundDTO>> refund(@PathVariable UUID id,
            @Valid @RequestBody RefundLedgerPaymentRequest command, Principal principal,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        UUID actor;
        try { actor = UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException invalid) {
            throw new org.springframework.security.access.AccessDeniedException("Verified account UUID is required");
        }
        String correlation = suppliedCorrelation == null || suppliedCorrelation.isBlank()
                ? UUID.randomUUID().toString() : suppliedCorrelation;
        return ResponseEntity.status(201).body(ApiResponse.ok(refunds.refund(id, command, actor, correlation), correlation));
    }
}
