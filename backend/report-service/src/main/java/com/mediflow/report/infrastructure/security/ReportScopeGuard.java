package com.mediflow.report.infrastructure.security;

import java.util.UUID;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.port.out.CurrentReportAuthorityPort;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Called by method security after the endpoint's unchanged role policy. */
public class ReportScopeGuard {
    private final CurrentReportAuthorityPort authority;

    public ReportScopeGuard(CurrentReportAuthorityPort authority) {
        this.authority = authority;
    }

    public boolean permits(Authentication authentication, UUID departmentId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        if (departmentId == null || !(authentication.getCredentials() instanceof VerifiedReportBearer credential)) {
            return false;
        }
        String role = authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.equals("ROLE_DOCTOR")).findFirst().orElse(null);
        if (role == null) return false;
        try {
            UUID actor = UUID.fromString(authentication.getName());
            String correlation = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                    ? attributes.getRequest().getHeader(JwtClaims.HEADER_CORRELATION_ID) : null;
            return authority.permits(actor, role.substring(5), departmentId, credential.authorization(), correlation);
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
