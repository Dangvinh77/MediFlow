package com.mediflow.lab.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mediflow.lab.application.port.out.AuthenticatedStaffIdPort;

/** Reads only the explicit staffId claim copied into the verified principal. */
@Component
public class AuthenticatedStaffIdAdapter implements AuthenticatedStaffIdPort {

    @Override
    public Optional<UUID> currentStaffId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LabAuthenticatedPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.staffId());
    }
}
