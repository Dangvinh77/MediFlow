package com.mediflow.clinical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.response.RecordLabOrderDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.RecordLabOrderApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
public class RecordLabOrderController {
    private final RecordLabOrderApplicationService orders;
    private final CorrelationIdProvider correlationIds;

    public RecordLabOrderController(RecordLabOrderApplicationService orders, CorrelationIdProvider correlationIds) {
        this.orders = orders;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/records/{id}/lab-orders")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<RecordLabOrderDTO>>> list(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orders.list(id), correlationIds.currentOrCreate().toString()));
    }
}
