package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.organization.application.dto.request.ScheduleManagementScopeCommand;
import com.mediflow.organization.application.dto.response.ConsultationEligibility;
import com.mediflow.organization.application.dto.response.CurrentDoctorAuthority;
import com.mediflow.organization.application.dto.response.RoomSummary;
import com.mediflow.organization.application.dto.response.ScheduleManagementScopeSnapshot;
import com.mediflow.organization.application.port.in.ManageScheduleManagementScopeUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.domain.model.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;

/** V2 Core authority endpoints used by scheduling staff and Clinical's internal client. */
@RestController
@RequestMapping("/api/v2/org")
public class ScheduleManagementScopeController {
    private final ManageScheduleManagementScopeUseCase authority;
    private final CorrelationIdProvider correlationIds;

    public ScheduleManagementScopeController(ManageScheduleManagementScopeUseCase authority,
            CorrelationIdProvider correlationIds) {
        this.authority = authority;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/accounts/{id}/schedule-management-scope")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScheduleManagementScopeSnapshot>> getScope(@PathVariable UUID id,
            Authentication authentication,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            HttpServletResponse response) {
        String requestCorrelationId = resolveCorrelation(correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, requestCorrelationId);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(authority.getScope(accountId(authentication),
                        signedRole(authentication), id), requestCorrelationId));
    }

    @PutMapping("/accounts/{id}/schedule-management-scope")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScheduleManagementScopeSnapshot>> replaceScope(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleManagementScopeCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            Authentication authentication,
            HttpServletResponse response) {
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, correlationId);
        var result = authority.replaceScope(accountId(authentication), signedRole(authentication), id,
                command, idempotencyKey, correlationId);
        ResponseEntity.BodyBuilder resultResponse = ResponseEntity.status(result.status());
        if (result.location() != null) resultResponse.location(URI.create(result.location()));
        if (result.replayed()) resultResponse.header("Idempotency-Replayed", "true");
        return resultResponse.body(ApiResponse.ok(result.snapshot(), correlationId));
    }

    @GetMapping("/scheduling-authority")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and principal == 'clinical-service'")
    public ResponseEntity<ApiResponse<com.mediflow.organization.application.dto.response.SchedulingAuthority>> getSchedulingAuthority(
            @RequestParam UUID actorAccountId,
            @RequestParam UUID departmentId,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            HttpServletResponse response) {
        String requestCorrelationId = resolveInternalCorrelation(correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, requestCorrelationId);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(authority.getSchedulingAuthority(actorAccountId, departmentId),
                        requestCorrelationId));
    }

    @GetMapping("/current-doctor-authority")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and principal == 'clinical-service'")
    public ResponseEntity<ApiResponse<CurrentDoctorAuthority>> getCurrentDoctorAuthority(
            @RequestParam UUID actorAccountId,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            HttpServletResponse response) {
        String requestCorrelationId = resolveInternalCorrelation(correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, requestCorrelationId);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(authority.getCurrentDoctorAuthority(actorAccountId),
                        requestCorrelationId));
    }

    @GetMapping("/consultation-eligibility")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE') and principal == 'clinical-service'")
    public ResponseEntity<ApiResponse<ConsultationEligibility>> getConsultationEligibility(
            @RequestParam UUID doctorId,
            @RequestParam UUID departmentId,
            @RequestParam UUID roomId,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            HttpServletResponse response) {
        String requestCorrelationId = resolveInternalCorrelation(correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, requestCorrelationId);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(authority.getConsultationEligibility(doctorId, departmentId, roomId),
                        requestCorrelationId));
    }

    @GetMapping("/consultation-rooms")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PageResult<RoomSummary>>> listConsultationRooms(
            @RequestParam UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String correlationId,
            HttpServletResponse response) {
        String requestCorrelationId = resolveCorrelation(correlationId);
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, requestCorrelationId);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(authority.listConsultationRooms(accountId(authentication),
                        signedRole(authentication), departmentId, page, size), requestCorrelationId));
    }

    private String resolveCorrelation(String value) {
        if (value == null) return correlationIds.currentOrCreate().toString();
        if (value.isBlank() || value.length() > 120) {
            throw new IllegalArgumentException("X-Correlation-Id must be nonblank and at most 120 characters");
        }
        return value;
    }

    private String resolveInternalCorrelation(String value) {
        if (value == null || value.isBlank() || value.length() > 120) {
            throw new IllegalArgumentException("Internal requests require a nonblank X-Correlation-Id");
        }
        return value;
    }

    private static UUID accountId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException invalid) {
            throw new AccessDeniedException("A verified account UUID is required");
        }
    }

    private static Role signedRole(Authentication authentication) {
        for (var authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (!value.startsWith("ROLE_") || "ROLE_SYSTEM_SERVICE".equals(value)) continue;
            try {
                return Role.valueOf(value.substring("ROLE_".length()));
            } catch (IllegalArgumentException ignored) {
                // Continue until a recognized role is found.
            }
        }
        throw new AccessDeniedException("A verified human role is required");
    }
}
