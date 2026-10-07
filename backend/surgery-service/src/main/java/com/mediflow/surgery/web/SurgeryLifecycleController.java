package com.mediflow.surgery.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.request.CompleteSurgeryRequest;
import com.mediflow.surgery.application.dto.request.SurgeryLifecycleRequest;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.application.port.in.EvaluateSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.FinalizeSurgeryScheduleUseCase;
import com.mediflow.surgery.application.port.in.StartSurgeryUseCase;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Offline HTTP boundary; enabling both flags still requires real authority-backed use-case beans. */
@RestController
@Validated
@RequestMapping("/api/v1/surgery/cases")
@ConditionalOnProperty(name={"mediflow.features.surgery.enabled","mediflow.surgery.lifecycle.api.enabled"},havingValue="true")
public class SurgeryLifecycleController {
    private final EvaluateSurgeryReadinessUseCase readiness;
    private final FinalizeSurgeryScheduleUseCase scheduling;
    private final StartSurgeryUseCase starting;
    private final CompleteSurgeryUseCase completion;

    public SurgeryLifecycleController(EvaluateSurgeryReadinessUseCase readiness, FinalizeSurgeryScheduleUseCase scheduling,
            StartSurgeryUseCase starting, CompleteSurgeryUseCase completion) {
        this.readiness=readiness; this.scheduling=scheduling; this.starting=starting; this.completion=completion;
    }

    @PostMapping("/{surgeryCaseId}/readiness/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ApiResponse<SurgeryCommandOutcome> evaluate(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody SurgeryLifecycleRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        var identity=identity(surgeryCaseId,request.expectedCaseRevision(),request.expectedScheduleRevision(),key,authentication,servletRequest);
        return ApiResponse.ok(readiness.evaluate(identity),identity.correlationId());
    }

    @PostMapping("/{surgeryCaseId}/schedule/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR')")
    public ApiResponse<SurgeryCommandOutcome> finalizeSchedule(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody SurgeryLifecycleRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        var identity=identity(surgeryCaseId,request.expectedCaseRevision(),request.expectedScheduleRevision(),key,authentication,servletRequest);
        return ApiResponse.ok(scheduling.finalizeSchedule(identity),identity.correlationId());
    }

    @PostMapping("/{surgeryCaseId}/start")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ApiResponse<SurgeryCommandOutcome> start(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody SurgeryLifecycleRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        var identity=identity(surgeryCaseId,request.expectedCaseRevision(),request.expectedScheduleRevision(),key,authentication,servletRequest);
        return ApiResponse.ok(starting.start(identity),identity.correlationId());
    }

    @PostMapping("/{surgeryCaseId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ApiResponse<SurgeryCommandOutcome> complete(@PathVariable UUID surgeryCaseId,
            @Valid @RequestBody CompleteSurgeryRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=160) String key,
            Authentication authentication, HttpServletRequest servletRequest) {
        var identity=identity(surgeryCaseId,request.expectedCaseRevision(),request.expectedScheduleRevision(),key,authentication,servletRequest);
        return ApiResponse.ok(completion.complete(request.toCommand(identity)),identity.correlationId());
    }

    private static SurgeryLifecycleCommand identity(UUID caseId,long caseRevision,long scheduleRevision,String key,
            Authentication authentication,HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID account)
                || !(authentication.getDetails() instanceof SurgeryAuthenticationDetails details)
                || !account.equals(details.accountId())) throw new AccessDeniedException("Verified lifecycle identity required");
        UUID correlation=CorrelationIdRequestAttribute.read(request);
        return new SurgeryLifecycleCommand(caseId,caseRevision,scheduleRevision,key,
                new SurgeryActorIdentity(details.accountId(),details.staffId()),
                correlation == null ? UUID.randomUUID().toString() : correlation.toString());
    }
}
