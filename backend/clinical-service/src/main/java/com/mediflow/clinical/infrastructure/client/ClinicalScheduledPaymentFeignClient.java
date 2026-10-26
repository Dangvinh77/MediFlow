package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.mediflow.clinical.application.dto.authority.ScheduledPaymentSnapshot;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

/** Clinical's service-credential-only Billing reconciliation routes. */
@FeignClient(name = "${mediflow.clients.billing-service:billing-service}",
        contextId = "clinicalScheduledPaymentReconciliationClient")
public interface ClinicalScheduledPaymentFeignClient {
    @GetMapping("/api/v2/billing/payment-operations/{id}")
    ResponseEntity<ApiResponse<ScheduledPaymentSnapshot>> lookup(
            @PathVariable("id") UUID paymentOperationId,
            @RequestParam UUID registrationId,
            @RequestParam UUID reservationId,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);

    @PostMapping("/api/v2/billing/payment-operations/{id}/close-if-unpaid")
    ResponseEntity<ApiResponse<ScheduledPaymentSnapshot>> closeIfUnpaid(
            @PathVariable("id") UUID paymentOperationId,
            @RequestBody CloseIfUnpaidRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);

    record CloseIfUnpaidRequest(UUID registrationId, UUID reservationId, long fenceToken, String reason) { }
}
