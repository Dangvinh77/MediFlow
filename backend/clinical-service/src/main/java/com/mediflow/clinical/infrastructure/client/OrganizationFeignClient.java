package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.mediflow.common.api.ApiResponse;

@FeignClient(name = "organization-service", contextId = "clinicalOrganizationClient",
        path = "/api/v1/org/staff")
public interface OrganizationFeignClient {

    @GetMapping("/{id}/exists")
    ApiResponse<StaffExistsResponse> exists(@PathVariable UUID id);
}
