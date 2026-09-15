package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.response.OutboxReplayResult;
import com.mediflow.pharmacy.application.port.in.ReplayPharmacyOutboxUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides authenticated operator actions for durable pharmacy event delivery. */
@RestController
@RequestMapping("/api/v1/pharmacy/admin/outbox")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {
        "mediflow.pharmacy.outbox.enabled",
        "mediflow.pharmacy.outbox.maintenance-enabled" }, havingValue = "true", matchIfMissing = true)
public class PharmacyOutboxAdminController {

    private final ReplayPharmacyOutboxUseCase replayPharmacyOutboxUseCase;

    /**
     * Requeues one outbox event while retaining its immutable event id and payload.
     *
     * @param eventId event selected by an operator from quarantine monitoring
     * @param correlationId request trace identifier propagated by the gateway
     * @return replay result, or an envelope-shaped 404 when the event no longer exists
     */
    @PostMapping("/{eventId}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OutboxReplayResult>> replay(
            @PathVariable UUID eventId,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        String traceId = normalizeCorrelationId(correlationId);
        if (!replayPharmacyOutboxUseCase.replay(eventId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(
                    ApiError.of("OUTBOX_EVENT_NOT_FOUND", "Không tìm thấy outbox event id=" + eventId),
                    traceId));
        }
        return ResponseEntity.ok(ApiResponse.ok(new OutboxReplayResult(eventId, true), traceId));
    }

    /** Generates a trace id for direct operational calls that bypass the gateway. */
    private String normalizeCorrelationId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
}
