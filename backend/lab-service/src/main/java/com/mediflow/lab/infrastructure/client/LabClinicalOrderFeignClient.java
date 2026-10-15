package com.mediflow.lab.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** Narrow read-only call to Clinical's persisted Lab-order authority. */
@FeignClient(name = "clinical-service", contextId = "labClinicalOrderContextClient",
        url = "${mediflow.lab.clinical.base-url:}")
public interface LabClinicalOrderFeignClient {
    @GetMapping("/api/v1/lab-orders/{sourceOrderId}/context")
    ResponseEntity<String> lookup(@PathVariable("sourceOrderId") UUID sourceOrderId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);
}
