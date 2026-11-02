package com.mediflow.billing.web;

import java.util.Map;
import java.util.Set;
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
import com.mediflow.billing.application.dto.response.PaymentRequestQueueItem;
import com.mediflow.billing.application.port.in.FindPaymentRequestQueueUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.JwtClaims;

/** Read-only Billing work list. Selection still requires fresh by-source discovery and quote. */
@RestController
@RequestMapping("/api/v1/billing/payment-requests")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.payment-request-discovery.enabled"},
        havingValue = "true")
public class PaymentRequestQueueController {
    private static final Set<String> KEYS = Set.of("patientId", "careEpisodeId", "purpose", "page", "size");
    private final FindPaymentRequestQueueUseCase queue;

    public PaymentRequestQueueController(FindPaymentRequestQueueUseCase queue) {
        this.queue = queue;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<PageResult<PaymentRequestQueueItem>>> find(HttpServletRequest request,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String supplied) {
        String correlation = supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
        Map<String, String[]> params = request.getParameterMap();
        UUID patient, episode;
        PaymentRequestSourcePurpose purpose;
        PageQuery page;
        try {
            for (var entry : params.entrySet()) {
                if (!KEYS.contains(entry.getKey()) || entry.getValue().length != 1
                        || entry.getValue()[0] == null || entry.getValue()[0].isBlank()) {
                    throw new IllegalArgumentException();
                }
            }
            patient = uuid(params, "patientId");
            episode = uuid(params, "careEpisodeId");
            purpose = params.containsKey("purpose") ? PaymentRequestSourcePurpose.valueOf(params.get("purpose")[0]) : null;
            if (purpose == PaymentRequestSourcePurpose.EXAM) throw new IllegalArgumentException();
            page = PageQuery.of(integer(params, "page"), integer(params, "size"));
        } catch (IllegalArgumentException malformed) {
            return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                    .header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                    .body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_REQUEST",
                            "Invalid queue filters or pagination"), correlation));
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(queue.find(patient, episode, purpose, page), correlation));
    }

    private static UUID uuid(Map<String, String[]> params, String key) {
        if (!params.containsKey(key)) return null;
        String value = params.get(key)[0];
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return id;
    }

    private static Integer integer(Map<String, String[]> params, String key) {
        return params.containsKey(key) ? Integer.valueOf(params.get(key)[0]) : null;
    }
}
