package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;

@FeignClient(name = "${mediflow.clients.organization-service:organization-service}",
        contextId = "clinicalRecordReviewAuthorityClient", path = "/api/v2/org")
public interface ClinicalRecordReviewAuthorityFeignClient {

    @GetMapping("/record-review-authority")
    ApiResponse<RecordReviewAuthority> authority(
            @RequestParam UUID actorAccountId,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RecordReviewAuthority(UUID actorAccountId, UUID staffId, Boolean canReviewRecords) {}
}
