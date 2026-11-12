package com.mediflow.lab.infrastructure.client;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.mediflow.common.api.ApiResponse;

/** Patient's service-only demographics read (Vietnamese wire names, as Patient publishes them). */
@FeignClient(name = "${mediflow.clients.patient-service:patient-service}",
        contextId = "labPatientDemographicsClient", path = "/api/v1/patients")
public interface LabPatientDemographicsFeignClient {
    @GetMapping("/{id}/demographics")
    ApiResponse<DemographicsBody> demographics(@PathVariable("id") UUID patientId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Correlation-Id") String correlationId);

    record DemographicsBody(UUID maBenhNhan, String gioiTinh, LocalDate ngaySinh) {}
}
