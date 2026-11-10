package com.mediflow.clinical.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.CloseRecordRequest;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.ExamClosureApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ExamClosureController {
    private final ExamClosureApplicationService closure;
    private final CorrelationIdProvider correlationIds;

    public ExamClosureController(ExamClosureApplicationService closure, CorrelationIdProvider correlationIds) {
        this.closure = closure;
        this.correlationIds = correlationIds;
    }

    @PutMapping("/api/v2/records/{id}/close-without-conclusion")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<MedicalRecordDTO>> close(@PathVariable UUID id, @RequestBody CloseRecordRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var result = closure.closeWithoutConclusion(id, request, idempotencyKey);
        return ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
