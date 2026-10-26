package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.request.LabQuotedPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;
import com.mediflow.billing.application.port.in.ProcessLabQuotedPaymentUseCase;
import com.mediflow.common.api.ApiResponse;
import java.security.Principal;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/billing/payment-requests")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled"}, havingValue = "true")
public class LabQuotedPaymentController {
    private final ProcessLabQuotedPaymentUseCase payments;

    public LabQuotedPaymentController(ProcessLabQuotedPaymentUseCase payments) { this.payments = payments; }

    @PostMapping("/{id}/lab-payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ApiResponse<LedgerPaymentDTO> complete(@PathVariable UUID id,
            @Valid @RequestBody LabQuotedPaymentRequest command, Principal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        UUID actor;
        try { actor = UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException invalid) {
            throw new org.springframework.security.access.AccessDeniedException("Verified account UUID is required");
        }
        return ApiResponse.ok(payments.complete(id, command, idempotencyKey, actor, correlationId), correlationId);
    }
}
