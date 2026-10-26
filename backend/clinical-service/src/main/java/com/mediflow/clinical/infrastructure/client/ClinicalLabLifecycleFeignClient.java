package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

@FeignClient(name = "${mediflow.clients.lab-service:lab-service}",
        contextId = "clinicalLabLifecycleClient", path = "/api/v2/lab")
public interface ClinicalLabLifecycleFeignClient {
    @GetMapping("/source-orders/{sourceOrderId}/lifecycle")
    ApiResponse<LabOrderLifecycleSnapshot> lifecycle(
            @PathVariable UUID sourceOrderId,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);
}
