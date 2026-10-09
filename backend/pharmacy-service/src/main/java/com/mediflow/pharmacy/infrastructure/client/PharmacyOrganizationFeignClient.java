package com.mediflow.pharmacy.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "organization-service", contextId = "pharmacyOrganizationIdentityClient",
        url = "${mediflow.pharmacy.organization.base-url:}", fallbackFactory = PharmacyOrganizationFallbackFactory.class)
public interface PharmacyOrganizationFeignClient {
    @GetMapping("/api/v1/org/staff/{id}/lookup")
    ResponseEntity<String> staff(@PathVariable("id") UUID id,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);
    @GetMapping("/api/v1/org/departments/{id}/lookup")
    ResponseEntity<String> department(@PathVariable("id") UUID id,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);
}
