package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only question to Clinical; the answer carries no clinical content. */
@FeignClient(name = "clinical-service", contextId = "pharmacyClinicalReadDecisionClient",
        url = "${mediflow.pharmacy.clinical.base-url:}")
public interface PharmacyClinicalReadDecisionFeignClient {
    @GetMapping("/api/v2/clinical/patients/{patientId}/read-decision")
    ApiResponse<ReadDecisionBody> decide(@PathVariable("patientId") UUID patientId,
            @RequestParam("actorAccountId") UUID actorAccountId,
            @RequestParam(value = "actorStaffId", required = false) UUID actorStaffId,
            @RequestParam("actorRole") String actorRole,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);

    record ReadDecisionBody(UUID patientId, Boolean allowed, String basis, String denialCode, Instant observedAt) {}
}
