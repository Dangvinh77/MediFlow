package com.mediflow.billing.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.billing.application.dto.command.PaymentRequestSourcePurpose;
import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;
import com.mediflow.billing.application.port.in.DiscoverPaymentRequestUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

/**
 * L1 discovery boundary (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §3). ADMIN/CASHIER only —
 * a signed human access token, never SYSTEM/service/refresh/PATIENT. Query binding is done against
 * the raw parameter map (not {@code @RequestParam}) so a repeated or unknown key is rejected instead
 * of silently picked.
 */
@RestController
@RequestMapping("/api/v1/billing/payment-requests")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.payment-request-discovery.enabled"},
        havingValue = "true")
public class PaymentRequestDiscoveryController {

    private static final List<String> ALLOWED_KEYS = List.of("purpose", "sourceId", "patientId", "careEpisodeId");

    private final DiscoverPaymentRequestUseCase discovery;

    public PaymentRequestDiscoveryController(DiscoverPaymentRequestUseCase discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/by-source")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<PaymentRequestDiscoveryDTO>> bySource(HttpServletRequest request,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        String correlation = suppliedCorrelation == null || suppliedCorrelation.isBlank()
                ? UUID.randomUUID().toString() : suppliedCorrelation;
        PaymentRequestSourceQuery query = parse(request.getParameterMap());
        if (query == null) return invalid(correlation);
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(discovery.discover(query), correlation));
    }

    private static PaymentRequestSourceQuery parse(Map<String, String[]> params) {
        for (String key : params.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) return null;
        }
        for (String key : ALLOWED_KEYS) {
            String[] values = params.get(key);
            if (values == null || values.length != 1 || values[0] == null || values[0].isBlank()) return null;
        }
        try {
            return new PaymentRequestSourceQuery(
                    PaymentRequestSourcePurpose.valueOf(params.get("purpose")[0]),
                    UUID.fromString(params.get("sourceId")[0]),
                    UUID.fromString(params.get("patientId")[0]),
                    UUID.fromString(params.get("careEpisodeId")[0]));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static ResponseEntity<ApiResponse<PaymentRequestDiscoveryDTO>> invalid(String correlation) {
        return ResponseEntity.badRequest().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_REQUEST",
                        "Query requires exactly purpose, sourceId, patientId, careEpisodeId, each a single valid value"),
                        correlation));
    }
}
