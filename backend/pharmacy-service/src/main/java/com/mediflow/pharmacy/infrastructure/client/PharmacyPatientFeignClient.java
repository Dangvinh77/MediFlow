package com.mediflow.pharmacy.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "patient-service", contextId = "pharmacyPatientIdentityClient",
        url = "${mediflow.pharmacy.patient.base-url:}", fallbackFactory = PharmacyPatientFallbackFactory.class)
public interface PharmacyPatientFeignClient {
    @GetMapping("/api/v1/patients/{id}/exists")
    ResponseEntity<String> exists(@PathVariable("id") UUID id,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);
}
