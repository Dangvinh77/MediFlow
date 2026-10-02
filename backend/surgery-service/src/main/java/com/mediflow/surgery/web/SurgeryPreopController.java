package com.mediflow.surgery.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.request.BeginPreopRequest;
import com.mediflow.surgery.application.dto.response.BeginPreopResponse;
import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HTTP driving adapter for the feature-gated transition into pre-operative work. */
@RestController
@RequestMapping("/api/v1/surgery/cases")
@ConditionalOnProperty(prefix = "mediflow.features.surgery", name = "enabled", havingValue = "true")
@Validated
public class SurgeryPreopController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final BeginPreopUseCase beginPreop;

    public SurgeryPreopController(BeginPreopUseCase beginPreop) {
        this.beginPreop = beginPreop;
    }

    @PostMapping("/{surgeryCaseId}/preop")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<BeginPreopResponse>> begin(
            @PathVariable UUID surgeryCaseId,
            @Valid @RequestBody BeginPreopRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank @Size(max = 160) String idempotencyKey,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        SurgeryAuthenticationDetails verified = verifiedActor(authentication);
        String correlationId = correlationId(servletRequest);
        var outcome = beginPreop.begin(new BeginPreopUseCase.Command(
                surgeryCaseId,
                request.expectedCaseRevision(),
                idempotencyKey,
                new SurgeryActorIdentity(verified.accountId(), verified.staffId()),
                correlationId));
        BeginPreopResponse response = new BeginPreopResponse(outcome.surgeryCaseId(),
                outcome.caseRevision(), outcome.state(), outcome.occurredAt(), outcome.replayed());
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    private static SurgeryAuthenticationDetails verifiedActor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UUID accountId)
                || !(authentication.getDetails() instanceof SurgeryAuthenticationDetails details)
                || !accountId.equals(details.accountId())) {
            throw new AccessDeniedException("Không xác định được identity đã xác thực từ JWT");
        }
        return details;
    }

    private static String correlationId(HttpServletRequest request) {
        UUID correlationId = CorrelationIdRequestAttribute.read(request);
        return correlationId == null ? UUID.randomUUID().toString() : correlationId.toString();
    }
}
