package com.mediflow.pharmacy.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "clinical-service", contextId = "pharmacyClinicalContextClient",
        url = "${mediflow.pharmacy.clinical.base-url:}", fallbackFactory = PharmacyClinicalContextFallbackFactory.class)
public interface PharmacyClinicalFeignClient {
    @GetMapping("/api/v1/records/{id}/prescription-context")
    ResponseEntity<String> lookup(@PathVariable("id") UUID id, @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlation);
}
