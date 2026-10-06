package com.mediflow.surgery.infrastructure.client;

import java.util.UUID;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "billing-service", contextId = "surgeryBillingClient",
        url = "${mediflow.surgery.billing.base-url:}", path = "/api/v1/billing")
public interface BillingFeignClient {
    @GetMapping("/financial-clearances/{id}/lookup")
    ResponseEntity<ApiResponse<FinancialClearanceLookupResponse>> lookup(@PathVariable("id") UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);
}
