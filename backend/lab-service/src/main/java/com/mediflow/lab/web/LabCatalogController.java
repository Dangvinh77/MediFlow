package com.mediflow.lab.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.dto.response.LabCatalogDTOs.LabCatalogItemDTO;
import com.mediflow.lab.application.dto.response.LabCatalogDTOs.LabTestTemplateDTO;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.service.LabCatalogApplicationService;

@RestController
public class LabCatalogController {
    private final LabCatalogApplicationService catalog;
    private final CorrelationIdProvider correlationIds;

    public LabCatalogController(LabCatalogApplicationService catalog, CorrelationIdProvider correlationIds) {
        this.catalog = catalog;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/lab/catalog")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','LAB_TECH')")
    public ResponseEntity<ApiResponse<List<LabCatalogItemDTO>>> services(
            @RequestParam(value = "group", required = false) String group) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.services(group), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/lab/{testId}/template")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','LAB_TECH')")
    public ResponseEntity<ApiResponse<LabTestTemplateDTO>> template(@PathVariable UUID testId) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.templateOf(testId), correlationIds.currentOrCreate().toString()));
    }
}
