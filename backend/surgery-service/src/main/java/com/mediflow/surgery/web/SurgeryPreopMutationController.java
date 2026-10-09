package com.mediflow.surgery.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.request.RecordSurgeryConsentRequest;
import com.mediflow.surgery.application.dto.request.UpdateSurgeryChecklistRequest;
import com.mediflow.surgery.application.port.in.RecordSurgeryPreopUseCase;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/surgery/cases")
@ConditionalOnProperty(name = {"mediflow.features.surgery.enabled", "mediflow.surgery.preop.api.enabled"}, havingValue = "true")
public class SurgeryPreopMutationController {
    private final RecordSurgeryPreopUseCase preop;
    public SurgeryPreopMutationController(RecordSurgeryPreopUseCase preop) { this.preop = preop; }

    @PutMapping("/{surgeryCaseId}/checklist")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ApiResponse<SurgeryCommandOutcome> checklist(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody UpdateSurgeryChecklistRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        String correlation = correlation(servletRequest);
        return ApiResponse.ok(preop.updateChecklist(request.toCommand(surgeryCaseId, key,
                identity(authentication), correlation)), correlation);
    }

    @PostMapping("/{surgeryCaseId}/consents")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<SurgeryCommandOutcome>> consent(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody RecordSurgeryConsentRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        String correlation = correlation(servletRequest);
        var outcome = preop.recordConsent(request.toCommand(surgeryCaseId, key,
                identity(authentication), correlation));
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .location(URI.create("/api/v1/surgery/cases/" + surgeryCaseId + "/consents/" + outcome.subjectId()))
                .body(ApiResponse.ok(outcome, correlation));
    }

    private static SurgeryActorIdentity identity(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID account)
                || !(authentication.getDetails() instanceof SurgeryAuthenticationDetails details)
                || !account.equals(details.accountId()) || details.staffId() == null)
            throw new AccessDeniedException("Verified staff recorder required");
        return new SurgeryActorIdentity(account, details.staffId());
    }

    private static String correlation(HttpServletRequest request) {
        UUID correlation = CorrelationIdRequestAttribute.read(request);
        return correlation == null ? UUID.randomUUID().toString() : correlation.toString();
    }
}
