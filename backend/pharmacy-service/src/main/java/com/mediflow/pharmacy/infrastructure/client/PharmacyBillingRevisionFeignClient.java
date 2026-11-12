package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Billing's service-token-only prepare/commit/abort endpoints for unpaid source revisions. */
@FeignClient(name = "billing-service", contextId = "pharmacyBillingRevisionClient",
        url = "${mediflow.pharmacy.billing.base-url:}")
public interface PharmacyBillingRevisionFeignClient {
    @PostMapping("/internal/api/v1/billing/prescription-revisions/{prescriptionId}/{operationId}/prepare")
    ResponseEntity<String> prepare(@PathVariable UUID prescriptionId, @PathVariable UUID operationId,
            @RequestBody PrescriptionRevisionBillingPayload payload,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);

    @PostMapping("/internal/api/v1/billing/prescription-revisions/{prescriptionId}/{operationId}/commit")
    ResponseEntity<String> commit(@PathVariable UUID prescriptionId, @PathVariable UUID operationId,
            @RequestBody PrescriptionRevisionBillingPayload payload,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);

    @PostMapping("/internal/api/v1/billing/prescription-revisions/{prescriptionId}/{operationId}/abort")
    ResponseEntity<String> abort(@PathVariable UUID prescriptionId, @PathVariable UUID operationId,
            @RequestBody PrescriptionRevisionBillingPayload payload,
            @RequestHeader("Authorization") String authorization, @RequestHeader("X-Correlation-Id") String correlation);
}
