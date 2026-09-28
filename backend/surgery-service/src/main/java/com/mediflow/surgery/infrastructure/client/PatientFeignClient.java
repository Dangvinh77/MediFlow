package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "patient-service", contextId = "surgeryPatientClient",
        url = "${mediflow.surgery.patient.base-url:}", path = "/api/v1/patients")
public interface PatientFeignClient {

    @GetMapping("/{id}/exists")
    ApiResponse<PatientLookupResponse> exists(
            @PathVariable("id") UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);
}
