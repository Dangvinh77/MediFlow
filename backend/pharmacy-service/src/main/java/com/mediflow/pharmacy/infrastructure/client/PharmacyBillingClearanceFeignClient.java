package com.mediflow.pharmacy.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="billing-service", contextId="pharmacyBillingClearanceClient",
        url="${mediflow.pharmacy.billing.base-url:}", fallbackFactory=PharmacyBillingClearanceFallbackFactory.class)
public interface PharmacyBillingClearanceFeignClient {
    @GetMapping("/api/v1/billing/financial-clearances/{id}/lookup")
    ResponseEntity<String> lookup(@PathVariable("id") UUID id,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);
}
