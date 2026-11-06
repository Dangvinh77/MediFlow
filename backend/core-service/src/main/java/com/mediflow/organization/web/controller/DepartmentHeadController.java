package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.request.DepartmentHeadCommand;
import com.mediflow.organization.application.dto.response.DepartmentHeadSnapshot;
import com.mediflow.organization.application.dto.response.OwnCapabilities;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.service.DepartmentHeadService;
import com.mediflow.organization.domain.model.Role;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DepartmentHeadController {
    private final DepartmentHeadService service;
    private final CorrelationIdProvider correlationIds;

    public DepartmentHeadController(
            DepartmentHeadService service, CorrelationIdProvider correlationIds) {
        this.service = service;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v1/org/me/capabilities")
    @PreAuthorize(
            "hasAnyRole('ADMIN','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH')")
    public ResponseEntity<ApiResponse<OwnCapabilities>> capabilities(
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        String trace = resolveCorrelation(correlation);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Correlation-Id", trace)
                .body(
                        ApiResponse.ok(
                                service.capabilities(
                                        accountId(authentication), signedRole(authentication)),
                                trace));
    }

    @GetMapping("/api/v2/org/departments/{id}/head-appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentHeadSnapshot>> get(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        String trace = resolveCorrelation(correlation);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Correlation-Id", trace)
                .body(
                        ApiResponse.ok(
                                service.get(
                                        accountId(authentication), signedRole(authentication), id),
                                trace));
    }

    @PutMapping("/api/v2/org/departments/{id}/head-appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentHeadSnapshot>> replace(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody DepartmentHeadCommand command,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Correlation-Id") String correlation) {
        var result =
                service.replace(
                        accountId(authentication),
                        signedRole(authentication),
                        id,
                        command,
                        key,
                        correlation);
        var response =
                ResponseEntity.ok()
                        .header("Cache-Control", "no-store")
                        .header("X-Correlation-Id", correlation);
        if (result.replayed()) response.header("Idempotency-Replayed", "true");
        return response.body(ApiResponse.ok(result.snapshot(), correlation));
    }

    private String resolveCorrelation(String value) {
        if (value == null) return correlationIds.currentOrCreate().toString();
        if (value.isBlank() || !value.matches("[ -~]{1,120}")) {
            throw new IllegalArgumentException(
                    "X-Correlation-Id must be nonblank and at most 120 characters");
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
