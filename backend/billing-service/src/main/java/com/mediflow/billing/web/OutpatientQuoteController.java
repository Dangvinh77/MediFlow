package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.OutpatientQuoteDTO;
import com.mediflow.billing.application.port.in.LookupOutpatientQuoteUseCase;
import com.mediflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/billing/payment-requests")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled"}, havingValue = "true")
public class OutpatientQuoteController {
    private final LookupOutpatientQuoteUseCase quotes;
    public OutpatientQuoteController(LookupOutpatientQuoteUseCase quotes) { this.quotes = quotes; }

    @GetMapping("/{id}/quote")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<OutpatientQuoteDTO>> get(@PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String suppliedCorrelation,
            HttpServletRequest request) {
        String correlation = suppliedCorrelation == null ? UUID.randomUUID().toString() : suppliedCorrelation;
        if (correlation.isBlank() || correlation.length() > 120 || !request.getParameterMap().isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_REQUEST", "Invalid quote query"), correlation));
        return quotes.lookup(id).map(q -> ResponseEntity.ok().header("X-Correlation-Id", correlation)
                .body(ApiResponse.ok(OutpatientQuoteDTO.from(id, q), correlation)))
                .orElseGet(() -> ResponseEntity.status(404).header("X-Correlation-Id", correlation)
                        .body(ApiResponse.fail(ApiResponse.ApiError.of("BILLING_QUOTE_NOT_FOUND", "Quote not found"), correlation)));
    }
}
