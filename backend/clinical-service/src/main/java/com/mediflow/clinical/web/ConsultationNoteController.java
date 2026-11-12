package com.mediflow.clinical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.ConsultationNoteRequest;
import com.mediflow.clinical.application.dto.response.ConsultationNoteDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.ConsultationNoteApplicationService;
import com.mediflow.common.api.ApiResponse;

@RestController
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class ConsultationNoteController {
    private final ConsultationNoteApplicationService notes;
    private final CorrelationIdProvider correlationIds;

    public ConsultationNoteController(ConsultationNoteApplicationService notes, CorrelationIdProvider correlationIds) {
        this.notes = notes;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/records/{recordId}/consultation-notes")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ConsultationNoteDTO>> add(@PathVariable UUID recordId,
            @RequestBody ConsultationNoteRequest request, @RequestHeader("Idempotency-Key") String key) {
        var result = notes.add(recordId, request, key);
        return ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/records/{recordId}/consultation-notes")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<ConsultationNoteDTO>>> list(@PathVariable UUID recordId) {
        return ResponseEntity.ok(ApiResponse.ok(notes.list(recordId), correlationIds.currentOrCreate().toString()));
    }
}
