package com.mediflow.billing.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.billing.application.dto.response.OutboxReplayResult;
import com.mediflow.billing.application.port.in.ReplayBillingOutboxUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;

import lombok.RequiredArgsConstructor;

/** Authenticated operator endpoint for replaying quarantined Billing events. */
@RestController
@RequestMapping("/api/v1/billing/admin/outbox")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {
        "mediflow.billing.outbox.enabled",
        "mediflow.billing.outbox.maintenance-enabled"
}, havingValue = "true", matchIfMissing = true)
public class BillingOutboxAdminController {

    private final ReplayBillingOutboxUseCase replayBillingOutboxUseCase;

    @PostMapping("/{eventId}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OutboxReplayResult>> replay(
            @PathVariable UUID eventId,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false)
            String correlationId) {
        String traceId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        if (!replayBillingOutboxUseCase.replay(eventId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(
                    ApiError.of("OUTBOX_EVENT_NOT_FOUND", "Không tìm thấy outbox event id=" + eventId),
                    traceId));
        }
        return ResponseEntity.ok(ApiResponse.ok(new OutboxReplayResult(eventId, true), traceId));
    }
}
