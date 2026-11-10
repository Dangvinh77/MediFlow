package com.mediflow.clinical.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.response.ClinicQueueTicketDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.ClinicQueueApplicationService;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.common.api.ApiResponse;

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

    private <T> ResponseEntity<ApiResponse<T>> response(IdempotentCommandPort.Result<T> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
