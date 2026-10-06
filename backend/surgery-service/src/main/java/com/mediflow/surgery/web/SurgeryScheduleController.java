package com.mediflow.surgery.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.dto.request.PrepareSurgeryScheduleRequest;
import com.mediflow.surgery.application.dto.response.PrepareSurgeryScheduleResponse;
import com.mediflow.surgery.application.port.in.PrepareSurgeryScheduleUseCase;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/surgery/cases")
@ConditionalOnProperty(prefix = "mediflow.features.surgery", name = "enabled", havingValue = "true")
@Validated
public class SurgeryScheduleController {
    private final PrepareSurgeryScheduleUseCase scheduling;
    public SurgeryScheduleController(PrepareSurgeryScheduleUseCase scheduling) { this.scheduling = scheduling; }

    @PutMapping("/{surgeryCaseId}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR')")
    public ApiResponse<PrepareSurgeryScheduleResponse> prepare(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody PrepareSurgeryScheduleRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID account)
                || !(authentication.getDetails() instanceof SurgeryAuthenticationDetails identity)
                || !account.equals(identity.accountId())) throw new AccessDeniedException("Verified scheduler identity required");
        UUID correlation = CorrelationIdRequestAttribute.read(servletRequest);
        String correlationId = correlation == null ? UUID.randomUUID().toString() : correlation.toString();
        var value = scheduling.prepare(PrepareSurgeryScheduleUseCase.Command.fromRequest(surgeryCaseId,
                request,key,new SurgeryActorIdentity(identity.accountId(),identity.staffId()),correlationId));
        return ApiResponse.ok(new PrepareSurgeryScheduleResponse(value.surgeryCaseId(), value.caseRevision(),
                value.subjectId(), value.subjectRevision(), value.state(), value.occurredAt(), value.replayed()), correlationId);
    }
}
