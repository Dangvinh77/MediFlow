package com.mediflow.lab.infrastructure.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;

/** Reads the staff identity and role copied from the verified JWT into the principal. */
@Component
public class AuthenticatedStaffContextAdapter implements AuthenticatedStaffContextPort {

    @Override
    public Optional<AuthenticatedStaffContext> currentStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LabAuthenticatedPrincipal principal)
                || principal.staffId() == null || principal.role() == null) {
            return Optional.empty();
        }
        String expectedAuthority = "ROLE_" + principal.role();
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> expectedAuthority.equals(authority.getAuthority()))) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedStaffContext(principal.staffId(), principal.role()));
    }
}
