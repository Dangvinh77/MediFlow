package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.response.CareLabelDTO;
import com.mediflow.organization.application.port.in.GetCareLabelUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/care-labels")
public class CareLabelController {
    private final GetCareLabelUseCase labels;
    private final CorrelationIdProvider correlationIds;

    public CareLabelController(GetCareLabelUseCase labels, CorrelationIdProvider correlationIds) {
        this.labels = labels;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/{kind}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH','PATIENT')")
    public ResponseEntity<ApiResponse<CareLabelDTO>> read(@PathVariable String kind, @PathVariable UUID id) {
        return labels.read(kind, id).map(label -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(label, correlationIds.currentOrCreate().toString())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
