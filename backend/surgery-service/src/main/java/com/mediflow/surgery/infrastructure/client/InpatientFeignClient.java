package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.UUID;

@FeignClient(name="inpatient-service",contextId="surgeryInpatientClient",
        url="${mediflow.surgery.inpatient.base-url:}",path="/api/v1/inpatient")
public interface InpatientFeignClient {
    @GetMapping("/admissions/{id}/lookup")
    ResponseEntity<ApiResponse<AdmissionLookupResponse>> lookupAdmission(@PathVariable("id") UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);
}
