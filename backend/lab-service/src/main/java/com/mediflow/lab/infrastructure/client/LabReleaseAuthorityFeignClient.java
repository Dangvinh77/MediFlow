package com.mediflow.lab.infrastructure.client;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.mediflow.common.api.ApiResponse;

@FeignClient(name = "${mediflow.clients.organization-service:organization-service}",
        contextId = "labReleaseAuthorityClient", path = "/api/v2/org")
public interface LabReleaseAuthorityFeignClient {
    @GetMapping("/lab-release-authority")
    ApiResponse<ReleaseAuthorityBody> authority(@RequestParam("actorAccountId") UUID actorAccountId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);

    record ReleaseAuthorityBody(UUID actorAccountId, Boolean accountActive, UUID staffId, Boolean staffActive,
            Boolean canReleaseLabResults, Instant observedAt) {}
}
