package com.mediflow.lab.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Private Billing endpoints used to check and durably admit one Lab test target. */
@FeignClient(name = "billing-service", contextId = "labBillingExecutionClient",
        url = "${mediflow.lab.billing.base-url:}")
public interface LabBillingClearanceFeignClient {

    @GetMapping("/api/v1/billing/financial-clearances/{clearanceId}/lookup")
    ResponseEntity<String> lookup(@PathVariable UUID clearanceId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);

    @PostMapping("/api/v1/billing/financial-clearances/{clearanceId}/execution-starts")
    ResponseEntity<String> start(@PathVariable UUID clearanceId,
            @RequestBody LabBillingExecutionStartRequest request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);
}
