package com.mediflow.pharmacy.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "inpatient-service", contextId = "pharmacyInpatientClient",
        url = "${mediflow.pharmacy.inpatient.base-url:}", path = "/api/v1/inpatient",
        fallbackFactory = PharmacyAdmissionFallbackFactory.class)
public interface PharmacyInpatientFeignClient {
    @GetMapping("/admissions/{id}/lookup")
    ResponseEntity<String> lookup(@PathVariable("id") UUID id,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);
}
