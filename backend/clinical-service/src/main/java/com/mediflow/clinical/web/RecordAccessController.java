package com.mediflow.clinical.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.RecordAccessDeclarationRequest;
import com.mediflow.clinical.application.dto.response.RecordAccessDeclarationDTO;
import com.mediflow.clinical.application.dto.response.RecordCapabilitiesDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.RecordAccessApplicationService;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

/** Purpose declarations and per-record capabilities for staff clients. */
@RestController
@RequestMapping("/api/v2/records")
public class RecordAccessController {
    private final RecordAccessApplicationService access;
    private final CorrelationIdProvider correlationIds;

    public RecordAccessController(RecordAccessApplicationService access, CorrelationIdProvider correlationIds) {
        this.access = access;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/access-declarations")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<RecordAccessDeclarationDTO>> declare(
            @Valid @RequestBody RecordAccessDeclarationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(access.declare(request), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/{id}/capabilities")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<RecordCapabilitiesDTO>> capabilities(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(access.capabilities(id), correlationIds.currentOrCreate().toString()));
    }
}
