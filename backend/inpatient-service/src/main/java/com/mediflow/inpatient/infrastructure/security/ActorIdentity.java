package com.mediflow.inpatient.infrastructure.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Verifies audited actor IDs against explicit authenticated JWT staff identity. */
@Component
public class ActorIdentity {
    public void assertStaff(UUID requestedActorId) {
        InpatientAuthenticationDetails details = authenticatedDetails();
        if (details.staffId() == null || !details.staffId().equals(requestedActorId)) {
            throw new AccessDeniedException("Actor must match the authenticated staff identity");
        }
    }

    public void assertApprover(UUID requestedActorId, String requestedRole) {
        assertStaff(requestedActorId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String requiredAuthority = "ROLE_" + requestedRole;
        boolean roleMatches = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredAuthority::equals);
        if (!roleMatches) {
            throw new AccessDeniedException("Approver role must match the authenticated role");
        }
    }

    private static InpatientAuthenticationDetails authenticatedDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof InpatientAuthenticationDetails details)) {
            throw new AccessDeniedException("Authenticated staff identity is required");
        }
        return details;
    }
}
