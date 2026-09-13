package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "patient-service", contextId = "clinicalPatientClient", path = "/api/v1/patients")
public interface PatientFeignClient {

    @GetMapping("/{id}")
    ResponseEntity<Void> findById(@PathVariable UUID id);
}
