package com.mediflow.clinical.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.RecordReadGuard;
import com.mediflow.common.api.ApiResponse;

/** Service-only answer to whether a staff member may read a patient's clinical data. Never returns that data. */
@RestController
public class RecordReadDecisionController {
    private final RecordReadGuard guard;
    private final CorrelationIdProvider correlationIds;

    public RecordReadDecisionController(RecordReadGuard guard, CorrelationIdProvider correlationIds) {
        this.guard = guard;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/clinical/patients/{patientId}/read-decision")
    @PreAuthorize("hasRole('SYSTEM') and principal instanceof T(com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal)")
    public ResponseEntity<ApiResponse<ReadDecisionResponse>> decide(
            @PathVariable UUID patientId,
            @RequestParam UUID actorAccountId,
            @RequestParam(required = false) UUID actorStaffId,
            @RequestParam String actorRole) {
        RecordReadDecision decision = guard.decideForActor(
                new ActorIdentity(actorAccountId, actorStaffId, null, actorRole), patientId);
        ReadDecisionResponse body = new ReadDecisionResponse(patientId, decision.allowed(), decision.basis(),
                decision.denialCode(), Instant.now(), decision.nurseDepartmentId());
        return ResponseEntity.ok(ApiResponse.ok(body, correlationIds.currentOrCreate().toString()));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReadDecisionResponse(UUID patientId, boolean allowed, String basis, String denialCode,
            Instant observedAt, UUID departmentScope) {
    }
}
