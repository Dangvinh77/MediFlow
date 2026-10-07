package com.mediflow.surgery.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.application.dto.request.CreateSurgeryRequest;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in driving adapter. Neither flag supplies the missing referral/clinical authority. */
@RestController
@Validated
@RequestMapping("/api/v1/surgery/cases")
@ConditionalOnProperty(name = {"mediflow.features.surgery.enabled", "mediflow.surgery.creation.api.enabled"},
        havingValue = "true")
public class SurgeryCreationController {
    private final CreateSurgeryCaseUseCase creation;

    public SurgeryCreationController(CreateSurgeryCaseUseCase creation) {
        this.creation = creation;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<SurgeryCreationOutcome>> create(
            @Valid @RequestBody CreateSurgeryRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        SurgeryActorIdentity actor = verifiedActor(authentication);
        // HTTP retries and future referral delivery use the same globally fenced business UUID.
        if (!request.surgeryRequestId().toString().equals(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid creation request identity");
        }
        UUID correlation = CorrelationIdRequestAttribute.read(servletRequest);
        String correlationId = correlation == null ? UUID.randomUUID().toString() : correlation.toString();
        var outcome = creation.create(request.toCommand(actor, correlationId));
        URI location = URI.create("/api/v1/surgery/cases/" + outcome.surgeryCaseId());
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .location(location).body(ApiResponse.ok(outcome, correlationId));
    }

    private static SurgeryActorIdentity verifiedActor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID account)
                || !(authentication.getDetails() instanceof SurgeryAuthenticationDetails details)
                || !account.equals(details.accountId()) || details.staffId() == null) {
            throw new AccessDeniedException("Verified account and staff identity required");
        }
        // Even ADMIN needs an explicit authority decision for delegated requester/department access.
        return new SurgeryActorIdentity(details.accountId(), details.staffId());
    }
}
