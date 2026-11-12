package com.mediflow.clinical.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.RequestHandoverRequest;
import com.mediflow.clinical.application.dto.response.RecordHandoverDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.RecordHandoverApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class RecordHandoverController {
    private final RecordHandoverApplicationService handovers;
    private final CorrelationIdProvider correlationIds;

    public RecordHandoverController(RecordHandoverApplicationService handovers, CorrelationIdProvider correlationIds) {
        this.handovers = handovers;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/records/{recordId}/handovers")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<RecordHandoverDTO>> request(@PathVariable UUID recordId,
            @RequestBody RequestHandoverRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(handovers.request(recordId, request, key));
    }

    @PutMapping("/api/v2/records/{recordId}/handovers/{handoverId}/acceptance")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<RecordHandoverDTO>> accept(@PathVariable UUID recordId,
            @PathVariable UUID handoverId, @RequestHeader("Idempotency-Key") String key) {
        return respond(handovers.accept(recordId, handoverId, key));
    }

    @GetMapping("/api/v2/records/{recordId}/handovers")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<RecordHandoverDTO>>> list(@PathVariable UUID recordId) {
        return ResponseEntity.ok(ApiResponse.ok(handovers.list(recordId), correlationIds.currentOrCreate().toString()));
    }

    private ResponseEntity<ApiResponse<RecordHandoverDTO>> respond(IdempotentCommandPort.Result<RecordHandoverDTO> result) {
        var builder = ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) {
            builder.location(URI.create(result.location()));
        }
        return builder.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
