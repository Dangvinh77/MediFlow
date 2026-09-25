package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.mediflow.common.api.ApiResponse;

@FeignClient(name = "patient-service", contextId = "clinicalPatientClient", path = "/api/v1/patients")
public interface PatientFeignClient {

    @GetMapping("/{id}/exists")
    ApiResponse<PatientLookupResponse> exists(@PathVariable UUID id);
}
