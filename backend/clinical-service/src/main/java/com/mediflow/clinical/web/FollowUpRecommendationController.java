package com.mediflow.clinical.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.FollowUpRecommendationRequest;
import com.mediflow.clinical.application.dto.response.FollowUpRecommendationDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.FollowUpRecommendationApplicationService;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.security.JwtClaims;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v2/records")
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class FollowUpRecommendationController {
    private final FollowUpRecommendationApplicationService recommendations;
    private final CorrelationIdProvider correlationIds;

    public FollowUpRecommendationController(FollowUpRecommendationApplicationService recommendations,
            CorrelationIdProvider correlationIds) {
        this.recommendations = recommendations;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/{recordId}/follow-up-recommendations")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<FollowUpRecommendationDTO>> create(
            @PathVariable UUID recordId,
            @Valid @RequestBody FollowUpRecommendationRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        IdempotentCommandPort.Result<FollowUpRecommendationDTO> result =
                recommendations.create(recordId, request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/{recordId}/follow-up-recommendation")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE','DOCTOR','PATIENT')")
    public ResponseEntity<ApiResponse<FollowUpRecommendationDTO>> get(
            @PathVariable UUID recordId, HttpServletRequest request) {
        requireOnlyParameters(request, Set.of());
        return ResponseEntity.ok(ApiResponse.ok(recommendations.get(recordId),
                correlationIds.currentOrCreate().toString()));
    }

    private static void requireCorrelation(String value) {
        if (value == null || value.isBlank() || value.length() > 120
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new BusinessRuleException("INVALID_REQUEST",
                    "X-Correlation-Id must be printable and at most 120 characters");
        }
    }

    private static void requireOnlyParameters(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new BusinessRuleException("INVALID_REQUEST", "Unsupported query parameter");
        }
    }
}
