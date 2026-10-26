package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.common.security.JwtClaims;

@FeignClient(name = "${mediflow.clients.organization-service:organization-service}",
        contextId = "clinicalSchedulingAuthorityClient", path = "/api/v2/org")
public interface ClinicalSchedulingAuthorityFeignClient {

    @GetMapping("/scheduling-authority")
    ApiResponse<SchedulingAuthoritySnapshot> authority(
            @RequestParam UUID actorAccountId,
            @RequestParam UUID departmentId,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);

    @GetMapping("/consultation-eligibility")
    ApiResponse<ConsultationEligibilitySnapshot> consultationEligibility(
            @RequestParam UUID doctorId,
            @RequestParam UUID departmentId,
            @RequestParam UUID roomId,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);

    @GetMapping("/current-doctor-authority")
    ApiResponse<CurrentDoctorAuthoritySnapshot> currentDoctorAuthority(
            @RequestParam UUID actorAccountId,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);
}
