package com.mediflow.clinical.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.ClinicQueueAdjustmentRequest;
import com.mediflow.clinical.application.dto.request.ClinicQueueCallRequest;
import com.mediflow.clinical.application.dto.response.ClinicQueueEntryDTO;
import com.mediflow.clinical.application.dto.response.ClinicQueueTicketDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.ClinicQueueApplicationService;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@ConditionalOnClinicIntake
public class ClinicQueueController {
    private final ClinicQueueApplicationService queue;
    private final CorrelationIdProvider correlationIds;

    public ClinicQueueController(ClinicQueueApplicationService queue, CorrelationIdProvider correlationIds) {
        this.queue = queue;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/outpatient-registrations/{id}/room-queue-ticket")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<ClinicQueueTicketDTO>> issue(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return response(queue.issue(id, idempotencyKey));
    }

    @GetMapping("/api/v2/clinic-queue")
    @PreAuthorize("hasAnyRole('NURSE','DOCTOR')")
    public ResponseEntity<ApiResponse<List<ClinicQueueEntryDTO>>> board(@RequestParam LocalDate date,
            @RequestParam(required = false) UUID roomId) {
        return ResponseEntity.ok(ApiResponse.ok(queue.board(date, roomId), correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/api/v2/clinic-queue-tickets/{id}/call")
    @PreAuthorize("hasAnyRole('NURSE','DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicQueueTicketDTO>> call(@PathVariable UUID id, @Valid @RequestBody ClinicQueueCallRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return response(queue.call(id, request, idempotencyKey));
    }

    @PostMapping("/api/v2/clinic-queue-tickets/{id}/adjustments")
    @PreAuthorize("hasAnyRole('NURSE','DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicQueueTicketDTO>> adjust(@PathVariable UUID id,
            @Valid @RequestBody ClinicQueueAdjustmentRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return response(queue.adjust(id, request, idempotencyKey));
    }

    private <T> ResponseEntity<ApiResponse<T>> response(IdempotentCommandPort.Result<T> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
