package com.mediflow.clinical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.AmendmentAssignmentRequest;
import com.mediflow.clinical.application.dto.request.AmendmentDecisionRequest;
import com.mediflow.clinical.application.dto.request.RecordAmendmentRequest;
import com.mediflow.clinical.application.dto.response.AmendmentAssignmentDTO;
import com.mediflow.clinical.application.dto.response.RecordAmendmentDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.RecordAmendmentApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class RecordAmendmentController {
    private final RecordAmendmentApplicationService amendments;
    private final CorrelationIdProvider correlationIds;

    public RecordAmendmentController(RecordAmendmentApplicationService amendments, CorrelationIdProvider correlationIds) {
        this.amendments = amendments;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/records/{recordId}/amendments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<RecordAmendmentDTO>> request(@PathVariable UUID recordId,
            @RequestBody RecordAmendmentRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(amendments.request(recordId, request, key));
    }

    @PutMapping("/api/v2/records/{recordId}/amendments/{amendmentId}/decision")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<RecordAmendmentDTO>> decide(@PathVariable UUID recordId, @PathVariable UUID amendmentId,
            @RequestBody AmendmentDecisionRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(amendments.decide(recordId, amendmentId, request, key));
    }

    @PostMapping("/api/v2/records/{recordId}/amendment-assignments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<AmendmentAssignmentDTO>> assign(@PathVariable UUID recordId,
            @RequestBody AmendmentAssignmentRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(amendments.assign(recordId, request, key));
    }

    @DeleteMapping("/api/v2/records/{recordId}/amendment-assignments/{assignmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> revokeAssignment(@PathVariable UUID recordId, @PathVariable UUID assignmentId) {
        amendments.revokeAssignment(recordId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v2/records/{recordId}/amendments")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<RecordAmendmentDTO>>> list(@PathVariable UUID recordId) {
        return ResponseEntity.ok(ApiResponse.ok(amendments.list(recordId), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/amendments/pending")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<List<RecordAmendmentDTO>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(amendments.pending(), correlationIds.currentOrCreate().toString()));
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(IdempotentCommandPort.Result<T> result) {
        return ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
